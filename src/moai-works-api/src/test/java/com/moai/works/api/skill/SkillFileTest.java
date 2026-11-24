package com.moai.works.api.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.moai.works.api.common.ApiException;

/**
 * SKILL.md 만들기 · 읽기(서버 없이) — 저장소의 스킬 파일을 가져와 다시 내보내면 출처 표시 줄만 더해진 같은 파일이 된다
 * (2026-11-24 — 지금 스킬을 1리비전으로 가져올 때 내용이 바뀌지 않는다).
 */
class SkillFileTest {

  /** 모듈 폴더에서 저장소의 스킬 파일 */
  private static final Path REPO_SKILL = Path.of("../../.claude/skills/moai-works-issues/SKILL.md");

  @Test
  void 저장소의_스킬_파일을_가져와_다시_내보내면_출처_표시_줄만_더해진다() throws Exception {
    // 저장소 파일은 앱에서 받은 사본이라 출처 표시 줄이 있다(2026-11-24 부터) — 가져오기는 그 줄을 버리므로 빼고 견준다
    String original = Files.readString(REPO_SKILL, StandardCharsets.UTF_8).lines()
        .filter(l -> !l.startsWith(SkillFile.MARKER_PREFIX))
        .collect(java.util.stream.Collectors.joining("\n", "", "\n"));
    SkillFile.Parsed parsed = SkillFile.parse(original);
    assertThat(parsed.name()).isEqualTo("moai-works-issues");
    assertThat(parsed.description().length()).isBetween(1, SkillFile.DESCRIPTION_MAX);
    assertThat(parsed.body()).startsWith("# moai-works 일감 다루기");

    String exported = SkillFile.assemble(parsed.name(), parsed.description(), parsed.body(), 1);
    String marker = exported.lines().filter(l -> l.startsWith(SkillFile.MARKER_PREFIX)).findFirst().orElseThrow();
    assertThat(marker).contains("name=moai-works-issues revision=1 sha256=");
    // 출처 표시 줄을 빼면 frontmatter 뒤(본문)는 지금 파일과 한 글자도 다르지 않다. 지문은 출처 표시 줄을 뺀 파일의 sha256
    String plain = exported.replace(marker + "\n", "");
    assertThat(afterFrontmatter(plain)).isEqualTo(afterFrontmatter(original));
    assertThat(marker).contains("sha256=" + SkillFile.sha256(plain) + " ");
    // 설명이 이미 큰따옴표 한 줄이면(2026-11-24 에 바꿈) 파일 전체가 같다 — 처음 가져오고 내려받아도 사본이 바뀌지 않는다
    if (original.lines().skip(2).findFirst().orElse("").startsWith("description: \"")) {
      assertThat(plain).isEqualTo(original);
    }
    // 내보낸 것을 다시 가져와도 같다
    assertThat(SkillFile.parse(exported)).isEqualTo(parsed);
  }

  private static String afterFrontmatter(String text) {
    int end = text.indexOf("\n---\n", 4);
    return text.substring(end + 5);
  }

  @Test
  void YAML_값은_그대로_읽히면_따옴표_없이_아니면_큰따옴표로() {
    assertThat(SkillFile.yamlScalar("moai-works 의 일감(이슈)을 \"MCP\" 로 다룬다 → 진행")).doesNotStartWith("\"");
    assertThat(SkillFile.yamlScalar("# 제목처럼")).isEqualTo("\"# 제목처럼\"");
    assertThat(SkillFile.yamlScalar("키: 값")).isEqualTo("\"키: 값\"");
    assertThat(SkillFile.yamlScalar("뒤에 # 주석")).isEqualTo("\"뒤에 # 주석\"");
    assertThat(SkillFile.yamlScalar("두\n줄 \\ \"따옴표\"")).isEqualTo("\"두\\n줄 \\\\ \\\"따옴표\\\"\"");
    for (String special : new String[] {"yes", "No", "true", "null", "~", "123", "1e5", "2026-11-24", "- 목록", "'홑따옴표'"}) {
      assertThat(SkillFile.yamlScalar(special)).as(special).startsWith("\"");
    }
    // 큰따옴표로 감싼 것을 다시 읽으면 같은 글이다
    String tricky = "# a: \"b\" \\ c\n\td";
    SkillFile.Parsed parsed = SkillFile.parse("---\nname: x\ndescription: " + SkillFile.yamlScalar(tricky) + "\n---\n본문");
    assertThat(parsed.description()).isEqualTo(tricky.strip());
  }

  @Test
  void 설명은_늘_큰따옴표_한_줄이고_제어_문자는_받지_않는다() {
    String exported = SkillFile.assemble("a-b", "평범한 설명", "본문", 1);
    assertThat(exported).startsWith("---\nname: a-b\ndescription: \"평범한 설명\"\n---\n");
    assertThat(SkillFile.assemble("a-b", "두\n줄 \"따옴표\"", "본문", 1)).contains("\ndescription: \"두\\n줄 \\\"따옴표\\\"\"\n");
    assertThat(SkillFile.hasControl("탭\t줄바꿈\n")).isFalse();
    for (String bad : new String[] {"\u0000", "\u0007", "\u001b[31m", "\u007f", "\u0085"}) {
      assertThat(SkillFile.hasControl("a" + bad)).as(bad).isTrue();
      assertThatThrownBy(() -> SkillFile.cleanBody("본문" + bad)).hasMessageContaining("제어 문자");
      assertThatThrownBy(() -> SkillFile.cleanDescription("설명" + bad)).hasMessageContaining("제어 문자");
    }
  }

  @Test
  void 출처_표시_줄과_앞의_빈_줄은_본문이_아니다() {
    String file = "---\nname: a-b\ndescription: d\n---\n\n<!-- moai-works-skill name=a-b revision=7 sha256="
        + "0".repeat(64) + " · 안내 -->\n\n\n# 본문\n\n끝\n\n";
    assertThat(SkillFile.parse(file).body()).isEqualTo("# 본문\n\n끝");
    // 본문 가운데의 같은 모양 줄은 남긴다(맨 앞만 걷는다)
    String inner = "---\nname: a-b\ndescription: d\n---\n# 본문\n<!-- moai-works-skill name=x revision=1 sha256=" + "0".repeat(64) + " -->\n";
    assertThat(SkillFile.parse(inner).body()).contains("<!-- moai-works-skill");
  }

  @Test
  void 이름_규칙() {
    for (String ok : new String[] {"a", "a1", "moai-works-issues", "x".repeat(64)}) {
      assertThat(SkillFile.requireName(ok)).isEqualTo(ok);
    }
    for (String bad : new String[] {"", "A", "a_b", "-a", "a-", "a--b", "x".repeat(65), "한글", "a b", "a/b", "../a"}) {
      assertThatThrownBy(() -> SkillFile.requireName(bad)).as(bad).isInstanceOf(ApiException.class)
          .hasMessageContaining("스킬 이름");
    }
  }
}
