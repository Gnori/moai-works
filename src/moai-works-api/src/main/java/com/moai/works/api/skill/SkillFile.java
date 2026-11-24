package com.moai.works.api.skill;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import com.moai.works.api.common.ApiException;

/**
 * SKILL.md 파일 — 이름 · 설명 규칙, 만들기(frontmatter + 본문), 읽기(가져오기), 출처 표시 줄.
 *
 * <p>내보내는 모양(Claude Code 스킬 파일):
 * <pre>
 * ---
 * name: moai-works-issues
 * description: …
 * ---
 * &lt;!-- moai-works-skill name=moai-works-issues revision=3 sha256=… · … --&gt;
 *
 * # 본문 …
 * </pre>
 * 둘째 덩어리의 한 줄(출처 표시)은 "이 파일은 moai-works 시스템 관리에서 내보낸 사본"이라는 표시와 그 리비전 · 내용 지문이다.
 * 지문(sha256)은 <b>출처 표시 줄을 뺀 파일 전체</b>(UTF-8)의 것이다 — 내려받는 도구({@code tools/sync_skills.py})가 이 줄을 빼고
 * 다시 재서 같으면 손대지 않은 사본, 다르면 그 자리에서 고친 것으로 본다(고친 것은 {@code --force} 없이 덮어쓰지 않는다).
 * 가져오기는 이 줄을 버린다(내용이 아니다).
 */
public final class SkillFile {

  /** 파일 이름 — get_skill 의 files[].path, 내려받기 이름 */
  public static final String FILE_NAME = "SKILL.md";

  /** 이름 — Claude Code 스킬 이름 규칙: 영문 소문자 · 숫자 · 하이픈(앞뒤 · 연달아 금지), 64자까지 */
  public static final int NAME_MAX = 64;
  private static final Pattern NAME = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

  /** 설명(Agent 가 언제 쓸지 고르는 글) — Claude Code 한도와 같은 1024자 */
  public static final int DESCRIPTION_MAX = 1024;

  /** 본문 — 10만 자까지(moai-wiki 와 같다. 지금 가장 큰 스킬은 약 3만 자) */
  public static final int BODY_MAX = 100_000;

  /**
   * 바뀐 점 요약(리비전의 change_note) — Agent 가 사본을 새로 받을 때 보는 글이다(get_skill 의 changes · list_skills 의 latestChange ·
   * MCP 연결 안내 · 받기 도구). 5자부터 500자까지(2026-11-24 사용자: "버전 정보만 보고 업데이트하는 건 안 맞잖아? 간략한 설명도?", 일감 #183).
   */
  public static final int NOTE_MIN = 5;
  public static final int NOTE_MAX = 500;

  /** 만들 때 요약을 비우면 이 글로 남긴다 */
  public static final String CREATE_NOTE = "처음 만듦";

  /** 가져올 SKILL.md 의 크기 상한(글자 수) — 본문 상한 + frontmatter 여유 */
  public static final int FILE_MAX = BODY_MAX + 8_192;

  /** 출처 표시 줄의 앞부분 — 내려받는 도구가 이것으로 찾는다 */
  public static final String MARKER_PREFIX = "<!-- moai-works-skill ";

  private static final Pattern MARKER_LINE = Pattern.compile("^<!-- moai-works-skill .*-->\\s*$");

  /** frontmatter 에 둘 수 있는 항목 — 그 밖의 항목은 받지 않는다(저장할 곳이 없어 조용히 버리지 않으려고) */
  private static final List<String> FRONTMATTER_KEYS = List.of("name", "description");

  private SkillFile() {
  }

  // ── 규칙 ──────────────────────────────────────────────────────────

  /** 이름이 규칙에 맞는지 — 맞지 않으면 400 {@code SKILL_NAME_INVALID} */
  public static String requireName(String raw) {
    String name = raw == null ? "" : raw.trim();
    if (name.isEmpty() || name.length() > NAME_MAX || !NAME.matcher(name).matches()) {
      throw ApiException.badRequest("SKILL_NAME_INVALID",
          "스킬 이름은 영문 소문자 · 숫자 · 하이픈으로 64자까지입니다(하이픈은 앞뒤에 두거나 연달아 쓰지 않습니다): " + cut(name, 80));
    }
    return name;
  }

  /** 설명 — 앞뒤 공백을 걷고 줄바꿈은 LF 로. 비었거나 1024자를 넘거나 제어 문자(줄바꿈 · 탭 말고)가 있으면 400 */
  public static String cleanDescription(String raw) {
    String d = raw == null ? "" : lf(raw).strip();
    if (hasControl(d)) {
      throw ApiException.badRequest("SKILL_DESCRIPTION_INVALID", "스킬 설명에 쓸 수 없는 제어 문자가 있습니다(줄바꿈 · 탭만 됩니다).");
    }
    if (d.isEmpty()) {
      throw ApiException.badRequest("SKILL_DESCRIPTION_REQUIRED", "스킬 설명을 넣으십시오 — Agent 가 언제 이 스킬을 쓸지 고르는 글입니다.");
    }
    if (d.length() > DESCRIPTION_MAX) {
      throw ApiException.badRequest("SKILL_DESCRIPTION_TOO_LONG", "스킬 설명은 " + DESCRIPTION_MAX + "자까지입니다(지금 " + d.length() + "자).");
    }
    return d;
  }

  /** 본문 — 줄바꿈은 LF 로, 앞의 빈 줄과 끝의 공백은 걷는다. 비었거나 10만 자를 넘거나 제어 문자(줄바꿈 · 탭 말고)가 있으면 400 */
  public static String cleanBody(String raw) {
    String b = raw == null ? "" : stripLeadingBlankLines(lf(raw)).stripTrailing();
    if (hasControl(b)) {
      throw ApiException.badRequest("SKILL_BODY_INVALID", "스킬 본문에 쓸 수 없는 제어 문자가 있습니다(줄바꿈 · 탭만 됩니다).");
    }
    if (b.isBlank()) {
      throw ApiException.badRequest("SKILL_BODY_REQUIRED", "스킬 본문을 넣으십시오.");
    }
    if (b.length() > BODY_MAX) {
      throw ApiException.badRequest("SKILL_BODY_TOO_LONG", "스킬 본문은 " + BODY_MAX + "자까지입니다(지금 " + b.length() + "자).");
    }
    return b;
  }

  /**
   * 바뀐 점 요약(필수 — 수정 · 되돌리기 · 가져오기). 앞뒤 공백을 걷고 줄바꿈은 LF 로. 비었거나 5자보다 짧으면 400 {@code CHANGE_NOTE_REQUIRED},
   * 500자를 넘으면 400 {@code SKILL_NOTE_TOO_LONG}, 줄바꿈 · 탭 말고 제어 문자가 있으면 400 {@code CHANGE_NOTE_INVALID}
   * (Agent 의 터미널 · 문맥에 그대로 들어가는 글이라 설명 · 본문과 같은 규칙).
   */
  public static String requireNote(String raw) {
    String n = raw == null ? "" : lf(raw).strip();
    if (hasControl(n)) {
      throw ApiException.badRequest("CHANGE_NOTE_INVALID", "바뀐 점 요약에 쓸 수 없는 제어 문자가 있습니다(줄바꿈 · 탭만 됩니다).");
    }
    if (n.length() < NOTE_MIN) {
      throw ApiException.badRequest("CHANGE_NOTE_REQUIRED", "바뀐 점 요약을 " + NOTE_MIN + "자 이상 넣으십시오 — Agent 가 스킬을 새로 받을 때 "
          + "무엇이 바뀌었는지 보는 글입니다.").with("min", NOTE_MIN).with("max", NOTE_MAX);
    }
    if (n.length() > NOTE_MAX) {
      throw ApiException.badRequest("SKILL_NOTE_TOO_LONG", "바뀐 점 요약은 " + NOTE_MAX + "자까지입니다(지금 " + n.length() + "자).");
    }
    return n;
  }

  /** 만들 때의 요약 — 비우면 "처음 만듦", 넣었으면 {@link #requireNote} 와 같은 규칙 */
  public static String createNote(String raw) {
    return raw == null || raw.isBlank() ? CREATE_NOTE : requireNote(raw);
  }

  /** 줄바꿈 · 탭 말고 제어 문자(C0 · DEL · C1)가 있는가 — 설명 · 본문에 받지 않는다(moai-wiki 와 같다) */
  static boolean hasControl(String text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c != '\n' && c != '\t' && Character.isISOControl(c)) {
        return true;
      }
    }
    return false;
  }

  // ── 만들기 ────────────────────────────────────────────────────────

  /** 출처 표시 줄을 뺀 SKILL.md — 지문(sha256)을 재는 글 */
  static String withoutMarker(String name, String description, String body) {
    return head(name, description) + "\n" + body + "\n";
  }

  /** 내보내는 SKILL.md — frontmatter(name · description) + 출처 표시 줄 + 본문 */
  public static String assemble(String name, String description, String body, int revisionNo) {
    String plain = withoutMarker(name, description, body);
    return head(name, description) + marker(name, revisionNo, sha256(plain)) + "\n" + "\n" + body + "\n";
  }

  /** frontmatter — name 은 그대로 읽히면 따옴표 없이, description 은 늘 큰따옴표 한 줄(moai-wiki 와 같다) */
  private static String head(String name, String description) {
    return "---\n" + "name: " + yamlScalar(name) + "\n" + "description: " + quoted(description) + "\n" + "---\n";
  }

  static String marker(String name, int revisionNo, String sha256) {
    return MARKER_PREFIX + "name=" + name + " revision=" + revisionNo + " sha256=" + sha256
        + " · 원본은 moai-works 시스템 관리 › 스킬입니다. 이 파일을 고치지 말고 거기서 수정한 뒤 python3 -I tools/sync_skills.py 로 받으십시오 -->";
  }

  /**
   * YAML 한 줄 값(이름) — 그대로 써도 같은 글로 읽히면 따옴표 없이, 아니면 큰따옴표로 감싼다. 참 · 거짓 · 숫자처럼 읽힐 글도 따옴표로 감싼다
   * (다른 YAML 읽개가 글이 아닌 값으로 바꾸지 않게).
   */
  static String yamlScalar(String value) {
    return plainSafe(value) ? value : quoted(value);
  }

  private static final Pattern YAML_SPECIAL = Pattern.compile(
      "(?i)^(true|false|yes|no|on|off|y|n|null|~|[-+]?(\\.inf|\\.nan)|[-+]?[0-9][0-9_.:eE+-]*|0x[0-9a-f_]+|0o[0-7_]+|\\d{4}-\\d{2}-\\d{2}.*)$");

  private static boolean plainSafe(String v) {
    if (v.isEmpty() || !v.equals(v.strip()) || YAML_SPECIAL.matcher(v).matches()) {
      return false;
    }
    if ("-?:,[]{}#&*!|>'\"%@`".indexOf(v.charAt(0)) >= 0 || v.endsWith(":")) {
      return false;
    }
    if (v.contains(": ") || v.contains(" #") || v.contains("\t")) {
      return false;
    }
    for (int i = 0; i < v.length(); i++) {
      char c = v.charAt(i);
      if (c < 0x20 || c == 0x7f || c == '\u0085' || c == ' ' || c == ' ' || c == '﻿') {
        return false;
      }
    }
    return true;
  }

  /** YAML 큰따옴표 한 줄 — \ · " · 줄바꿈 · 탭 · 그 밖의 제어 문자를 이스케이프한다(JSON 문자열과 같은 규칙이라 어느 YAML 읽개로도 같은 글) */
  static String quoted(String v) {
    StringBuilder out = new StringBuilder(v.length() + 8).append('"');
    for (int i = 0; i < v.length(); i++) {
      char c = v.charAt(i);
      switch (c) {
        case '\\' -> out.append("\\\\");
        case '"' -> out.append("\\\"");
        case '\n' -> out.append("\\n");
        case '\t' -> out.append("\\t");
        case '\r' -> out.append("\\r");
        default -> {
          if (c < 0x20 || c == 0x7f || c == '\u0085' || c == ' ' || c == ' ' || c == '﻿') {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.append('"').toString();
  }

  public static String sha256(String text) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  // ── 읽기 (가져오기) ────────────────────────────────────────────────

  /** 가져온 SKILL.md 의 내용 — 이름 · 설명 · 본문(규칙을 통과한 값) */
  public record Parsed(String name, String description, String body) {
  }

  /**
   * SKILL.md 를 읽는다 — 첫 줄이 {@code ---} 인 frontmatter(YAML, name · description 만)와 그 뒤 본문.
   * 출처 표시 줄(내보낸 사본에 붙는 것)은 버린다. 맞지 않으면 400 {@code SKILL_FILE_INVALID}(무엇이 틀렸는지 문구에).
   */
  public static Parsed parse(String content) {
    if (content == null || content.isBlank()) {
      throw invalid("파일이 비었습니다.");
    }
    if (content.length() > FILE_MAX) {
      throw invalid("파일이 너무 큽니다(" + FILE_MAX + "자까지).");
    }
    String text = lf(content);
    if (text.startsWith("﻿")) {
      text = text.substring(1);
    }
    List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
    if (lines.isEmpty() || !lines.get(0).strip().equals("---")) {
      throw invalid("첫 줄이 --- 인 frontmatter(name · description)가 있어야 합니다.");
    }
    int end = -1;
    for (int i = 1; i < lines.size(); i++) {
      String l = lines.get(i).stripTrailing();
      if (l.equals("---") || l.equals("...")) {
        end = i;
        break;
      }
    }
    if (end < 0) {
      throw invalid("frontmatter 가 --- 로 닫히지 않았습니다.");
    }
    Map<String, Object> front = yaml(String.join("\n", lines.subList(1, end)));
    List<String> unknown = front.keySet().stream().filter(k -> !FRONTMATTER_KEYS.contains(k)).toList();
    if (!unknown.isEmpty()) {
      throw invalid("frontmatter 에는 name · description 만 둘 수 있습니다 — 받지 않는 항목: " + cut(String.join(", ", unknown), 200));
    }
    if (!(front.get("name") instanceof String name)) {
      throw invalid("frontmatter 에 name(글)이 없습니다.");
    }
    if (!(front.get("description") instanceof String description)) {
      throw invalid("frontmatter 에 description(글)이 없습니다.");
    }
    List<String> rest = new ArrayList<>(lines.subList(end + 1, lines.size()));
    // 앞의 빈 줄 · 출처 표시 줄(내보낸 사본)을 걷는다
    while (!rest.isEmpty() && (rest.get(0).isBlank() || MARKER_LINE.matcher(rest.get(0)).matches())) {
      rest.remove(0);
    }
    return new Parsed(requireName(name), cleanDescription(description), cleanBody(String.join("\n", rest)));
  }

  /** frontmatter YAML — 안전 모드(형 만들기 없음), 값은 모두 글로 읽는다(참 · 거짓 · 숫자 · 날짜로 바꾸지 않는다) */
  private static Map<String, Object> yaml(String source) {
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    options.setMaxAliasesForCollections(10);
    options.setCodePointLimit(64 * 1024);
    Resolver stringsOnly = new Resolver() {
      @Override
      protected void addImplicitResolvers() {
        // 암시적 형 바꾸기를 하지 않는다 — 모든 값은 글
      }
    };
    DumperOptions dumper = new DumperOptions();
    Yaml yaml = new Yaml(new SafeConstructor(options), new Representer(dumper), dumper, options, stringsOnly);
    Object loaded;
    try {
      loaded = yaml.load(source);
    } catch (YAMLException e) {
      throw invalid("frontmatter 를 YAML 로 읽을 수 없습니다: " + cut(firstLine(e.getMessage()), 200));
    }
    if (loaded == null) {
      return Map.of();
    }
    if (!(loaded instanceof Map<?, ?> map)) {
      throw invalid("frontmatter 는 이름: 값 꼴이어야 합니다.");
    }
    Map<String, Object> out = new LinkedHashMap<>();
    map.forEach((k, v) -> out.put(String.valueOf(k), v));
    return out;
  }

  private static ApiException invalid(String why) {
    return ApiException.badRequest("SKILL_FILE_INVALID", "SKILL.md 를 읽을 수 없습니다 — " + why);
  }

  // ── 글 다듬기 ──────────────────────────────────────────────────────

  private static String lf(String s) {
    return s.replace("\r\n", "\n").replace('\r', '\n');
  }

  private static String stripLeadingBlankLines(String s) {
    int i = 0;
    int lineStart = 0;
    while (i < s.length()) {
      char c = s.charAt(i);
      if (c == '\n') {
        lineStart = i + 1;
      } else if (!Character.isWhitespace(c)) {
        break;
      }
      i++;
    }
    return s.substring(Math.min(lineStart, s.length()));
  }

  private static String firstLine(String s) {
    if (s == null) {
      return "";
    }
    int nl = s.indexOf('\n');
    return nl < 0 ? s : s.substring(0, nl);
  }

  static String cut(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max) + "…";
  }
}
