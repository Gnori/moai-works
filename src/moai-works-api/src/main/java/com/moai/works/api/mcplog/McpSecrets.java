package com.moai.works.api.mcplog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 접근 기록에 넣기 전에 바깥 값을 다듬는다 — 키 원문 · 긴 글 본문이 기록에 들어가지 않게(FR-MCP-006 "키 원문 · 일감 본문 전체 · 응답 본문은 남기지 않는다").
 *
 * <ul>
 *   <li>{@link #mask}: MCP 키({@code mai-wks-…}) · 첨부 티켓({@code mwt_…}) 모양의 글은 앞부분만 두고 가린다 — 누가 실수로 인자 · User-Agent 에
 *       키를 넣어도 기록에 남지 않는다</li>
 *   <li>{@link #summarizeArguments}: 도구 인자 요약 — 설명 · 댓글 · 메모 · 이유 같은 글과 200자를 넘는 글은 길이와 sha256 만, 전체 8KB 상한</li>
 * </ul>
 */
public final class McpSecrets {

  /** 본문으로 보는 인자 이름 — 짧아도 길이와 해시만 남긴다 */
  static final Set<String> BODY_ARGUMENTS = Set.of("description", "text", "comment", "body", "note", "notes", "reason");
  private static final int SHORT_TEXT = 200;
  private static final int MAX_SUMMARY_CHARS = 8 * 1024;
  private static final Pattern KEY_LIKE = Pattern.compile("(mai-wks-|mwt_)[A-Za-z0-9_-]{8,}");

  private McpSecrets() {
  }

  /** 키 · 티켓 모양을 가린다(앞말만 남김) */
  public static String mask(String value) {
    if (value == null) {
      return null;
    }
    return KEY_LIKE.matcher(value).replaceAll(m -> m.group(1) + "…(가림)");
  }

  public static String cut(String value, int max) {
    if (value == null) {
      return null;
    }
    return value.length() <= max ? value : value.substring(0, max);
  }

  /** 도구 인자 요약 — 순서는 받은 그대로. 비면 null */
  static Map<String, Object> summarizeArguments(Map<String, Object> arguments) {
    if (arguments == null || arguments.isEmpty()) {
      return null;
    }
    Map<String, Object> out = new LinkedHashMap<>();
    int size = 0;
    for (Map.Entry<String, Object> e : arguments.entrySet()) {
      String name = cut(String.valueOf(e.getKey()), 60);
      Object value = summarize(name, e.getValue());
      size += name.length() + String.valueOf(value).length() + 6;
      if (size > MAX_SUMMARY_CHARS) {
        out.put("truncated", true);
        break;
      }
      out.put(name, value);
    }
    return out;
  }

  private static Object summarize(String name, Object value) {
    if (value == null || value instanceof Number || value instanceof Boolean) {
      return value;
    }
    if (value instanceof String s) {
      if (BODY_ARGUMENTS.contains(name) || s.length() > SHORT_TEXT) {
        return digest(s);
      }
      return mask(s);
    }
    if (value instanceof Map<?, ?> m) {
      return Map.of("type", "object", "size", m.size());
    }
    if (value instanceof List<?> l) {
      return Map.of("type", "array", "size", l.size());
    }
    return digest(String.valueOf(value));
  }

  /** 글의 길이와 sha256 — 같은 글인지만 알 수 있고 내용은 남지 않는다 */
  static Map<String, Object> digest(String text) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("length", text.length());
    m.put("sha256", sha256(text));
    return m;
  }

  private static String sha256(String text) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
