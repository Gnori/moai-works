package com.moai.works.api.mcpkey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * MCP 키 원문 — {@code mai-wks-} + 32바이트 난수(base64url 43자). 서명 비밀값이 없다.
 * 앞부분 {@code mai-wks-} 는 moai-works 의 MCP 키라는 표시다(2026-11-22 사용자 결정).
 * 서버는 원문의 SHA-256 으로 DB 행을 찾는다 (seed-site-keeper-api {@code McpKeyTokenCodec} 과 같은 방식).
 */
public final class McpKeyTokenCodec {

  public static final String PREFIX = "mai-wks-";
  private static final Pattern SHAPE = Pattern.compile("^" + PREFIX + "[A-Za-z0-9_-]{43}$");
  private static final SecureRandom RANDOM = new SecureRandom();

  private McpKeyTokenCodec() {
  }

  static String generate() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** 모양만 본다 — 모양이 틀리면 DB 를 조회하지 않는다. */
  public static boolean wellFormed(String token) {
    return token != null && SHAPE.matcher(token).matches();
  }

  public static String hash(String token) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** 화면 표시용 앞부분 */
  static String displayPrefix(String token) {
    return token.substring(0, Math.min(token.length(), PREFIX.length() + 4));
  }
}
