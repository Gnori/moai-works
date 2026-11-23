package com.moai.works.api.account;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.moai.works.api.audit.AuditService;
import com.moai.works.api.common.ApiException;
import com.moai.works.api.keycloak.KeycloakAdminClient;
import com.moai.works.api.keycloak.KeycloakException;
import com.moai.works.api.security.access.CurrentUser;

/**
 * 로그인한 사람의 비밀번호 변경 (FR-WSP-006, design/03 §8.3) — 시스템 관리자 계정도 쓴다.
 *
 * <ol>
 *   <li>틀린 횟수 제한({@link PasswordAttemptLimiter} — 15분에 5번)</li>
 *   <li>새 비밀번호가 지금 것과 같으면 400 {@code PASSWORD_UNCHANGED}</li>
 *   <li>현재 비밀번호 확인 — Keycloak 토큰 엔드포인트에 그 사람의 아이디로({@link KeycloakAdminClient#checkPassword}).
 *       틀리면 400 {@code CURRENT_PASSWORD_INVALID}(401 이 아니다 — 401 은 화면을 로그아웃시킨다)</li>
 *   <li>Keycloak 에 새 비밀번호(관리 API). 정책 위반은 400 {@code PASSWORD_POLICY}(가입과 같다)</li>
 *   <li>다른 로그인 끊기(고르면): DB 에 끊은 때와 이 로그인(sid)을 남겨 다른 로그인의 토큰을 다음 요청부터 401 로 막고,
 *       Keycloak 의 다른 로그인 세션을 지운다(갱신도 못 한다). 이 브라우저는 그대로 쓴다</li>
 *   <li>감사 {@code account.password.change}(비밀번호 없음) · "비밀번호가 바뀌었습니다" 메일</li>
 * </ol>
 */
@Service
public class PasswordService {

  private static final Logger log = LoggerFactory.getLogger(PasswordService.class);

  private final KeycloakAdminClient keycloak;
  private final UserProfileRepository users;
  private final PasswordAttemptLimiter limiter;
  private final PasswordMails mails;
  private final AuditService audit;
  private final TransactionTemplate tx;
  private final Clock clock;

  PasswordService(KeycloakAdminClient keycloak, UserProfileRepository users, PasswordAttemptLimiter limiter,
      PasswordMails mails, AuditService audit, PlatformTransactionManager txManager, Clock clock) {
    this.keycloak = keycloak;
    this.users = users;
    this.limiter = limiter;
    this.mails = mails;
    this.audit = audit;
    this.tx = new TransactionTemplate(txManager);
    this.clock = clock;
  }

  /**
   * @param otherSessionsEnded 다른 로그인을 끊었나(고르지 않았으면 false)
   * @param currentSessionKept 이 브라우저의 로그인이 그대로인가. 토큰에 세션 id(sid)가 없어 이 로그인을 가려낼 수 없으면 모두 끊고 false —
   *                           화면은 다시 로그인하게 한다
   */
  public record ChangeResult(boolean otherSessionsEnded, boolean currentSessionKept) {
  }

  /**
   * @param sessionId 이 요청 토큰의 Keycloak 세션 id(sid) — 다른 로그인을 끊을 때 이것만 남긴다
   */
  public ChangeResult change(CurrentUser user, String sessionId, String currentPassword, String newPassword,
      boolean endOtherSessions) {
    limiter.check(user.id());
    if (newPassword.equals(currentPassword)) {
      throw ApiException.badRequest("PASSWORD_UNCHANGED", "새 비밀번호가 현재 비밀번호와 같습니다.");
    }
    switch (keycloak.checkPassword(user.username(), currentPassword)) {
      case OK -> limiter.reset(user.id());
      case INVALID -> {
        int left = limiter.recordFailure(user.id());
        log.info("비밀번호 변경 — 현재 비밀번호 틀림 user={} 남은 횟수={}", user.id(), left);
        throw ApiException.badRequest("CURRENT_PASSWORD_INVALID", "현재 비밀번호가 맞지 않습니다.").with("attemptsLeft", left);
      }
      case LOCKED -> throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
          "로그인 실패가 많아 계정이 잠시 잠겼습니다. 잠시 뒤 다시 시도하십시오.");
      case DISABLED -> throw ApiException.forbidden("ACCOUNT_NOT_ACTIVE", "계정이 활성 상태가 아닙니다.");
    }

    try {
      keycloak.resetPassword(user.id().toString(), newPassword);
    } catch (KeycloakException e) {
      throw PasswordPolicyError.from(e).orElseThrow(() -> e);
    }

    boolean keep = sessionId != null && !sessionId.isBlank();
    Instant now = Instant.now(clock);
    UserProfile profile = tx.execute(s -> {
      UserProfile p = users.findById(user.id()).orElseThrow();
      if (endOtherSessions) {
        p.revokeSessions(now, keep ? sessionId : null);
      }
      audit.record(user.actor(), null, "account.password.change", "user", user.id().toString(),
          Map.of("otherSessionsEnded", endOtherSessions, "currentSessionKept", !endOtherSessions || keep));
      return p;
    });

    if (endOtherSessions) {
      endKeycloakSessions(user, keep ? sessionId : null);
    }
    mails.sendChanged(profile, PasswordMails.How.CHANGE, user.ip(), now, endOtherSessions);
    return new ChangeResult(endOtherSessions, !endOtherSessions || keep);
  }

  /**
   * Keycloak 의 다른 로그인 세션을 지운다(keep 만 남긴다, null 이면 모두). 실패해도 비밀번호는 이미 바뀌었고, 그 로그인의 토큰은 DB 표시로
   * 이미 막힌다(갱신해도 로그인 시작 때(auth_time)가 그대로라 막힌다) — 경고 로그만 남긴다.
   */
  private void endKeycloakSessions(CurrentUser user, String keep) {
    try {
      if (keep == null) {
        keycloak.logoutUser(user.id().toString());
        return;
      }
      for (String session : keycloak.userSessionIds(user.id().toString())) {
        if (!session.equals(keep)) {
          keycloak.deleteSession(session);
        }
      }
    } catch (KeycloakException e) {
      log.warn("비밀번호 변경 뒤 Keycloak 의 다른 로그인 세션을 지우지 못했습니다 user={}: {}", user.id(), e.getMessage());
    }
  }
}
