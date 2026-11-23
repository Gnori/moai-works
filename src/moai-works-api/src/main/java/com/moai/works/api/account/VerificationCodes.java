package com.moai.works.api.account;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.moai.works.api.config.MoaiWorksProperties;

/**
 * 메일로 보내는 6자리 코드 — 가입 인증(FR-WSP-003)과 비밀번호 재설정(FR-WSP-006)이 같은 장치를 쓴다.
 *
 * <p>규칙: {@link SecureRandom} 6자리, 저장은 {@code HMAC-SHA256(MOAI_WORKS_CODE_SECRET, "<user id>:<code>")} 만, 10분,
 * {@link EmailVerification#MAX_ATTEMPTS}번까지, 새 코드를 만들면 같은 목적의 이전 코드는 무효, 비교는 상수 시간, 한 번 쓰면 끝.
 * 비밀값이 없거나 32자보다 짧으면 api 가 기동하지 않는다.
 */
@Component
public class VerificationCodes {

  static final Duration TTL = Duration.ofMinutes(10);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final EmailVerification.Repository verifications;
  private final Clock clock;
  private final byte[] secret;

  public VerificationCodes(EmailVerification.Repository verifications, Clock clock, MoaiWorksProperties properties) {
    this.verifications = verifications;
    this.clock = clock;
    String value = properties.security() == null ? null : properties.security().codeSecret();
    if (value == null || value.length() < 32) {
      throw new IllegalStateException("moai-works.security.code-secret(MOAI_WORKS_CODE_SECRET)이 없거나 32자보다 짧습니다");
    }
    this.secret = value.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * 새 코드를 만들어 HMAC 만 저장한다 — 같은 사람 · 같은 목적의 열린 코드는 무효로 한다. 부르는 쪽 트랜잭션 안에서 부른다.
   *
   * @return 코드 원문 — 메일로만 보내고 어디에도 남기지 않는다
   */
  public String issue(UUID userId, EmailVerification.Purpose purpose) {
    String code = String.format("%06d", RANDOM.nextInt(1_000_000));
    Instant now = Instant.now(clock);
    verifications.findOpen(userId, purpose).forEach(v -> v.invalidate(now));
    verifications.save(new EmailVerification(userId, purpose, hmac(userId, code), now, now.plus(TTL)));
    return code;
  }

  /** 저장하는 값 — 원문 대신 이것과 비교한다 */
  public String hmac(UUID userId, String code) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal((userId + ":" + code).getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
