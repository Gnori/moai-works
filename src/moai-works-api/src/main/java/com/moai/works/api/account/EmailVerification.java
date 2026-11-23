package com.moai.works.api.account;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 메일 코드(가입 인증 · 비밀번호 재설정) — 원문은 저장하지 않고 HMAC 만 둔다. 10분, 5회까지, 한 번 쓰면 끝({@link VerificationCodes}). */
@Entity
@Table(name = "email_verification")
public class EmailVerification {

  public static final int MAX_ATTEMPTS = 5;

  public enum Purpose {
    SIGNUP,
    PASSWORD_RESET,
    EMAIL_CHANGE
  }

  @Id
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Purpose purpose;

  @Column(name = "code_hmac", nullable = false, length = 64)
  private String codeHmac;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected EmailVerification() {
  }

  public EmailVerification(UUID userId, Purpose purpose, String codeHmac, Instant now, Instant expiresAt) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.purpose = purpose;
    this.codeHmac = codeHmac;
    this.createdAt = now;
    this.expiresAt = expiresAt;
  }

  /** 쓸 수 있는가 (만료 · 소진 · 횟수 초과가 아님) */
  public boolean usable(Instant now) {
    return consumedAt == null && attempts < MAX_ATTEMPTS && now.isBefore(expiresAt);
  }

  /** 시도 한 번. 맞으면 소진하고 true. */
  public boolean attempt(String hmac, Instant now) {
    attempts++;
    if (java.security.MessageDigest.isEqual(codeHmac.getBytes(), hmac.getBytes())) {
      consumedAt = now;
      return true;
    }
    return false;
  }

  /**
   * 시도 한 번 — 맞는지만 보고 소진하지 않는다(비밀번호 재설정: Keycloak 이 새 비밀번호를 받아야 {@link #consume} 한다).
   * 틀려도 맞아도 횟수를 센다.
   */
  public boolean check(String hmac) {
    attempts++;
    return java.security.MessageDigest.isEqual(codeHmac.getBytes(), hmac.getBytes());
  }

  /** 다 썼다 — 다시 쓸 수 없다 */
  public void consume(Instant now) {
    consumedAt = now;
  }

  public void invalidate(Instant now) {
    if (consumedAt == null) {
      consumedAt = now;
    }
  }

  public UUID getUserId() {
    return userId;
  }

  public int getAttempts() {
    return attempts;
  }

  public interface Repository extends JpaRepository<EmailVerification, UUID> {

    @Query("select v from EmailVerification v where v.userId = ?1 and v.purpose = ?2 and v.consumedAt is null"
        + " order by v.createdAt desc")
    List<EmailVerification> findOpen(UUID userId, Purpose purpose);

    default Optional<EmailVerification> latestOpen(UUID userId, Purpose purpose) {
      return findOpen(userId, purpose).stream().findFirst();
    }

    /** 열린 코드를 잠그고 읽는다 — 같은 코드를 동시에 두 번 쓰지 못하게(비밀번호 재설정, 한 번만) */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from EmailVerification v where v.userId = ?1 and v.purpose = ?2 and v.consumedAt is null"
        + " order by v.createdAt desc")
    List<EmailVerification> lockOpen(UUID userId, Purpose purpose);
  }
}
