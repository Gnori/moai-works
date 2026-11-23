package com.moai.works.api.account;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.moai.works.api.audit.AuditService;
import com.moai.works.api.common.Actor;
import com.moai.works.api.common.ApiException;
import com.moai.works.api.keycloak.KeycloakAdminClient;
import com.moai.works.api.keycloak.KeycloakException;

/**
 * 비밀번호 찾기 — 로그인하지 않은 사람이 메일 코드로 비밀번호를 다시 정한다 (FR-WSP-006, design/03 §8.3). 공개 경로다.
 *
 * <ul>
 *   <li><b>계정이 있는지 드러내지 않는다</b> — 요청은 계정이 있든 없든 · 코드를 받을 수 없는 계정이든 같은 202 를 같은 때에 돌려준다.
 *       응답은 속도 제한(IP · 이메일)만 보고 나가고, 계정 찾기 · 코드 · 메일은 뒤에서 한다({@link BackgroundTasks}).</li>
 *   <li><b>코드를 받는 계정</b> — 활성(ACTIVE) 사람 계정만. 인증 · 승인 대기 · 정지 · 거절 계정과 시스템 관리자 계정은 받지 않는다
 *       (시스템 관리자는 로그인해 개인 설정에서 바꾼다).</li>
 *   <li><b>코드</b> — 가입 인증과 같은 장치({@link VerificationCodes}): 6자리, HMAC 만, 10분, 5번, 새 코드가 이전 코드를 무효로, 한 번만.
 *       틀리거나 만료 · 다 쓴 코드 · 없는 계정은 모두 같은 400 {@code INVALID_CODE}.</li>
 *   <li><b>바꾼 뒤</b> — 그 계정의 로그인을 모두 끊는다(Keycloak 세션 + DB 표시로 이미 나간 토큰도), Keycloak 무차별 대입 잠금을 풀고,
 *       감사 {@code account.password.reset} · "비밀번호가 바뀌었습니다" 메일. 화면은 로그인으로 보낸다.</li>
 * </ul>
 */
@Service
public class PasswordResetService {

  private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
  /** IP 당 시간당 요청 · 확인 수 (가입의 재발송 · 확인과 같다) */
  static final int REQUESTS_PER_IP_HOUR = 10;
  static final int CONFIRMS_PER_IP_HOUR = 30;
  /** 이메일 하나에 시간당 요청 수 — 계정이 있든 없든 같은 수로 센다(있는지 드러내지 않는다) */
  static final int REQUESTS_PER_EMAIL_HOUR = 5;

  private final UserProfileRepository users;
  private final EmailVerification.Repository verifications;
  private final VerificationCodes codes;
  private final KeycloakAdminClient keycloak;
  private final PasswordMails mails;
  private final AuditService audit;
  private final SignupRateLimiter rateLimiter;
  private final BackgroundTasks background;
  private final TransactionTemplate tx;
  private final Clock clock;

  PasswordResetService(UserProfileRepository users, EmailVerification.Repository verifications, VerificationCodes codes,
      KeycloakAdminClient keycloak, PasswordMails mails, AuditService audit, SignupRateLimiter rateLimiter,
      BackgroundTasks background, PlatformTransactionManager txManager, Clock clock) {
    this.users = users;
    this.verifications = verifications;
    this.codes = codes;
    this.keycloak = keycloak;
    this.mails = mails;
    this.audit = audit;
    this.rateLimiter = rateLimiter;
    this.background = background;
    this.tx = new TransactionTemplate(txManager);
    this.clock = clock;
  }

  /** 코드를 보내 달라는 요청. 늘 같은 응답 — 계정이 있는지는 뒤에서 본다. 속도 제한을 넘으면 429(계정과 상관없이 센다). */
  public void request(String email, String ip) {
    rateLimiter.check("password-reset", ip, REQUESTS_PER_IP_HOUR);
    String normalized = normalize(email);
    rateLimiter.check("password-reset-email", normalized, REQUESTS_PER_EMAIL_HOUR);
    background.run("비밀번호 재설정 코드", () -> issue(normalized));
  }

  private void issue(String email) {
    Optional<UserProfile> user = eligible(email);
    if (user.isEmpty()) {
      log.info("비밀번호 재설정 요청 — 코드를 받을 수 있는 계정이 없어 보내지 않음 받는 사람={}", Mailer.mask(email));
      return;
    }
    UserProfile u = user.get();
    String code = tx.execute(s -> codes.issue(u.getId(), EmailVerification.Purpose.PASSWORD_RESET));
    mails.sendResetCode(u.getEmail(), code);
  }

  private enum Outcome {
    OK,
    INVALID
  }

  /** 코드 확인 → 새 비밀번호. 성공하면 그 계정의 로그인을 모두 끊는다. */
  public void confirm(String email, String code, String newPassword, String ip) {
    rateLimiter.check("password-reset-confirm", ip, CONFIRMS_PER_IP_HOUR);
    UserProfile user = eligible(normalize(email)).orElseThrow(PasswordResetService::invalidCode);
    String hmac = codes.hmac(user.getId(), code.trim());
    Instant now = Instant.now(clock);
    ApiException[] policy = new ApiException[1];
    // 코드 줄을 잠근 채 확인 → Keycloak → 다 씀. 같은 코드를 동시에 두 번 쓰지 못한다.
    // 정책 위반이면 코드를 남겨(시도 횟수만 센다) 다른 비밀번호로 다시 해 볼 수 있다.
    Outcome outcome = tx.execute(s -> {
      EmailVerification v = verifications.lockOpen(user.getId(), EmailVerification.Purpose.PASSWORD_RESET).stream()
          .findFirst().filter(open -> open.usable(now)).orElse(null);
      if (v == null || !v.check(hmac)) {
        return Outcome.INVALID;
      }
      try {
        keycloak.resetPassword(user.getId().toString(), newPassword);
      } catch (KeycloakException e) {
        policy[0] = PasswordPolicyError.from(e).orElseThrow(() -> e);
        return Outcome.INVALID;
      }
      v.consume(now);
      users.findById(user.getId()).orElseThrow().revokeSessions(now, null);
      audit.record(Actor.user(user.getId(), user.getDisplayName(), ip), null, "account.password.reset", "user",
          user.getId().toString(), Map.of("via", "EMAIL_CODE"));
      return Outcome.OK;
    });
    if (policy[0] != null) {
      throw policy[0];
    }
    if (outcome != Outcome.OK) {
      throw invalidCode();
    }
    endAllSessions(user);
    mails.sendChanged(user, PasswordMails.How.RESET, ip, now, true);
  }

  /** 모든 로그인 세션을 끝내고 잠금을 푼다. 실패해도 비밀번호는 이미 바뀌었고 토큰은 DB 표시로 막힌다 — 경고 로그만 */
  private void endAllSessions(UserProfile user) {
    String id = user.getId().toString();
    try {
      keycloak.logoutUser(id);
    } catch (KeycloakException e) {
      log.warn("비밀번호 재설정 뒤 Keycloak 로그인 세션을 끝내지 못했습니다 user={}: {}", id, e.getMessage());
    }
    try {
      keycloak.clearLoginFailures(id);
    } catch (KeycloakException e) {
      log.warn("비밀번호 재설정 뒤 로그인 잠금을 풀지 못했습니다 user={}: {}", id, e.getMessage());
    }
  }

  /** 코드를 받을 · 쓸 수 있는 계정 — 활성 사람 계정, 시스템 관리자 아님 */
  private Optional<UserProfile> eligible(String email) {
    return users.findByEmail(email)
        .filter(UserProfile::isActive)
        .filter(u -> !u.hasPlatformRole(PlatformRole.SYSTEM_ADMIN));
  }

  private static String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private static ApiException invalidCode() {
    return ApiException.badRequest("INVALID_CODE", "코드가 맞지 않거나 만료됐습니다.");
  }
}
