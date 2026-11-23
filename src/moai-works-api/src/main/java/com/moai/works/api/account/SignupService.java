package com.moai.works.api.account;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
import com.moai.works.api.platform.PlatformSettingService;
import com.moai.works.api.platform.SignupPolicy;
import com.moai.works.api.workspace.WorkspaceProvisioner;

/**
 * 회원가입 (design/03 §7) — 가입 정책 확인 → Keycloak 사용자 생성 → 계정 사본 → (인증 코드 · 승인) → 활성 → 개인 워크스페이스.
 *
 * <p>비밀번호는 Keycloak 렐름 정책이 검사하고 Keycloak 에만 있다. DB 저장이 실패하면 만든 Keycloak 사용자를 지운다(보상).
 * 인증 코드는 6자리, HMAC 만 저장, 10분 · 5회 — 비밀번호 재설정과 같은 장치({@link VerificationCodes}).
 */
@Service
public class SignupService {

  private static final Logger log = LoggerFactory.getLogger(SignupService.class);
  private final PlatformSettingService settings;
  private final UserProfileRepository users;
  private final EmailVerification.Repository verifications;
  private final KeycloakAdminClient keycloak;
  private final WorkspaceProvisioner provisioner;
  private final Mailer mailer;
  private final AuditService audit;
  private final SignupRateLimiter rateLimiter;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final VerificationCodes codes;

  public SignupService(PlatformSettingService settings, UserProfileRepository users,
      EmailVerification.Repository verifications, KeycloakAdminClient keycloak, WorkspaceProvisioner provisioner,
      Mailer mailer, AuditService audit, SignupRateLimiter rateLimiter, PlatformTransactionManager txManager,
      Clock clock, VerificationCodes codes) {
    this.settings = settings;
    this.users = users;
    this.verifications = verifications;
    this.keycloak = keycloak;
    this.provisioner = provisioner;
    this.mailer = mailer;
    this.audit = audit;
    this.rateLimiter = rateLimiter;
    this.tx = new TransactionTemplate(txManager);
    this.clock = clock;
    this.codes = codes;
  }

  public record SignupResult(UUID userId, AccountStatus status) {
  }

  public SignupResult signup(String email, String password, String displayName, String ip) {
    SignupPolicy policy = settings.signupPolicy();
    rateLimiter.check("signup", ip, policy.rateLimitPerIpHour());
    String normalized = email.trim().toLowerCase();
    if (!policy.enabled()) {
      throw ApiException.forbidden("SIGNUP_DISABLED", "지금은 가입을 받지 않습니다.");
    }
    if (!policy.allowsEmail(normalized)) {
      throw ApiException.forbidden("EMAIL_DOMAIN_NOT_ALLOWED", "가입할 수 없는 이메일 도메인입니다.");
    }
    if (users.findByEmail(normalized).isPresent()) {
      throw ApiException.conflict("EMAIL_TAKEN", "이미 가입한 이메일입니다.");
    }
    AccountStatus status = policy.requireEmailVerification() ? AccountStatus.PENDING_VERIFICATION
        : policy.requireAdminApproval() ? AccountStatus.PENDING_APPROVAL : AccountStatus.ACTIVE;

    String userId;
    try {
      userId = keycloak.createUser(new KeycloakAdminClient.NewUser(normalized, normalized, displayName, password,
          status == AccountStatus.ACTIVE, !policy.requireEmailVerification(), "SIGNUP"));
    } catch (KeycloakException e) {
      if (e.conflict()) {
        throw ApiException.conflict("EMAIL_TAKEN", "이미 가입한 이메일입니다.");
      }
      throw PasswordPolicyError.from(e).orElseThrow(() -> e);
    }

    UUID id = UUID.fromString(userId);
    try {
      tx.executeWithoutResult(s -> {
        Instant now = Instant.now(clock);
        users.saveAndFlush(new UserProfile(id, normalized, normalized, displayName, status, AccountOrigin.SIGNUP, now));
        audit.record(Actor.user(id, displayName, ip), null, "account.signup", "user", userId,
            Map.of("status", status.name()));
      });
    } catch (RuntimeException e) {
      log.warn("가입 저장 실패 — Keycloak 사용자 {} 를 지웁니다(보상)", userId);
      keycloak.deleteUser(userId);
      throw e;
    }

    if (status == AccountStatus.PENDING_VERIFICATION) {
      issueCode(id, normalized);
    } else if (status == AccountStatus.ACTIVE) {
      provisioner.ensurePersonalWorkspace(id, Actor.user(id, displayName, ip));
    }
    return new SignupResult(id, status);
  }

  /** 인증 코드 확인 → 승인 필요하면 PENDING_APPROVAL, 아니면 ACTIVE(Keycloak 켬) + 개인 워크스페이스. */
  public AccountStatus verify(String email, String code, String ip) {
    rateLimiter.check("verify", ip, 30);
    UserProfile user = users.findByEmail(email.trim().toLowerCase())
        .filter(u -> u.getStatus() == AccountStatus.PENDING_VERIFICATION)
        .orElseThrow(() -> ApiException.badRequest("INVALID_CODE", "코드가 맞지 않거나 만료됐습니다."));
    Instant now = Instant.now(clock);
    Boolean ok = tx.execute(s -> verifications.latestOpen(user.getId(), EmailVerification.Purpose.SIGNUP)
        .filter(v -> v.usable(now))
        .map(v -> v.attempt(codes.hmac(user.getId(), code.trim()), now))
        .orElse(false));
    if (!Boolean.TRUE.equals(ok)) {
      throw ApiException.badRequest("INVALID_CODE", "코드가 맞지 않거나 만료됐습니다.");
    }
    boolean approval = settings.signupPolicy().requireAdminApproval();
    AccountStatus next = approval ? AccountStatus.PENDING_APPROVAL : AccountStatus.ACTIVE;
    keycloak.updateUser(user.getId().toString(), Map.of("emailVerified", true, "enabled", next == AccountStatus.ACTIVE));
    tx.executeWithoutResult(s -> {
      UserProfile u = users.findById(user.getId()).orElseThrow();
      u.changeStatus(next, Instant.now(clock));
      audit.record(Actor.user(u.getId(), u.getDisplayName(), ip), null, "account.verify_email", "user",
          u.getId().toString(), Map.of("status", next.name()));
    });
    if (next == AccountStatus.ACTIVE) {
      provisioner.ensurePersonalWorkspace(user.getId(), Actor.user(user.getId(), user.getDisplayName(), ip));
    }
    return next;
  }

  /** 코드를 다시 보낸다. 계정이 있든 없든 같은 응답(가입 여부를 드러내지 않는다). */
  public void resend(String email, String ip) {
    rateLimiter.check("resend", ip, 10);
    Optional<UserProfile> user = users.findByEmail(email.trim().toLowerCase())
        .filter(u -> u.getStatus() == AccountStatus.PENDING_VERIFICATION);
    user.ifPresent(u -> issueCode(u.getId(), u.getEmail()));
  }

  private void issueCode(UUID userId, String email) {
    String code = tx.execute(s -> codes.issue(userId, EmailVerification.Purpose.SIGNUP));
    mailer.send(email, "[moai-works] 이메일 인증 코드",
        "인증 코드: " + code + "\n10분 안에 입력하십시오. 요청하지 않았다면 이 메일을 무시하십시오.");
  }
}
