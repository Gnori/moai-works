package com.moai.works.api.workspace;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * 워크스페이스 주소(슬러그) = {@code workspace-} + 고유 id 12자 (예: {@code workspace-k7m2q9xd4n3p}).
 *
 * <ul>
 *   <li>사람이 고르지 않는다 — 만들 때 서버가 정한다. 이름(표시 이름)은 따로 있다</li>
 *   <li>순번이 아니다 — 무작위라 다른 워크스페이스의 주소나 개수를 추측할 수 없다</li>
 *   <li>겹치지 않는다 — 30^12 ≈ 5.3×10^17 가지 + DB UNIQUE + 만들 때 확인 후 재시도</li>
 *   <li>바뀌지 않는다 — MCP 주소와 Keycloak 조직 alias 에 쓰인다(alias 는 Keycloak 에서도 바꿀 수 없다, 26.8 실측)</li>
 * </ul>
 * 헷갈리는 글자(0 · 1 · i · l · o · u)는 쓰지 않는다.
 *
 * <p>2026-11-21 에 접두사를 {@code space-} 에서 {@code workspace-} 로 바꿨다(화면 말이 "워크스페이스"다 — Flyway V8).
 * 고유 id 는 그대로다. 그 전에 만든 Keycloak 조직의 alias 는 옛 주소로 남는다 — api 는 alias 가 아니라 조직 id 로 찾는다.
 */
public final class WorkspaceSlugs {

  public static final String PREFIX = "workspace-";
  private static final char[] ALPHABET = "abcdefghjkmnpqrstvwxyz23456789".toCharArray();
  private static final int LENGTH = 12;
  public static final Pattern FORMAT = Pattern.compile("^workspace-[a-hj-km-np-tv-z2-9]{12}$");
  private static final SecureRandom RANDOM = new SecureRandom();

  private WorkspaceSlugs() {
  }

  public static String generate() {
    StringBuilder sb = new StringBuilder(PREFIX.length() + LENGTH).append(PREFIX);
    for (int i = 0; i < LENGTH; i++) {
      sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
    }
    return sb.toString();
  }

  public static boolean valid(String slug) {
    return slug != null && FORMAT.matcher(slug).matches();
  }
}
