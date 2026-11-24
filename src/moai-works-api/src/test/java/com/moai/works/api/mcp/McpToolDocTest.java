package com.moai.works.api.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

/**
 * 도구 설명 원문 — MCP 설계 문서(docs/design/06-mcp.md)의 "서버가 Agent 에게 보내는 설명(원문)" 블록이 코드의 {@code @Tool} 설명과 한 글자도
 * 다르지 않다(일감 #217 에서 더함 — 설명을 고치면 같은 작업에서 문서도 고친다). 연결 안내의 핵심 규칙도 문서의 안내 원문에 있다. DB 없이.
 * 저장소 밖(문서 없이)에서 빌드하면 건너뛴다.
 */
class McpToolDocTest {

  /** MCP 도구 클래스 — 두 입구({@link McpServerConfiguration})가 쓰는 것 모두 */
  private static final List<Class<?>> TOOLS = List.of(ProjectMcpTools.class, IssueMcpTools.class, ModuleMcpTools.class,
      SkillMcpTools.class, MarkdownMcpTools.class, TemplateMcpTools.class, ModuleManageMcpTools.class);

  private static String doc() throws Exception {
    Path path = Path.of("../../docs/design/06-mcp.md");
    Assumptions.assumeTrue(Files.exists(path), "저장소의 docs 가 없다");
    return Files.readString(path, StandardCharsets.UTF_8);
  }

  @Test
  void 도구_설명_원문이_문서와_같다() throws Exception {
    String doc = doc();
    List<String> tools = new ArrayList<>();
    List<String> missing = new ArrayList<>();
    for (Class<?> c : TOOLS) {
      for (Method m : c.getDeclaredMethods()) {
        Tool tool = m.getAnnotation(Tool.class);
        if (tool == null) {
          continue;
        }
        tools.add(tool.name());
        if (!doc.contains("```text\n" + tool.description() + "\n```")) {
          missing.add(tool.name());
        }
      }
    }
    assertThat(tools).hasSizeGreaterThanOrEqualTo(28).contains("get_markdown_guide");
    assertThat(missing).as("06-mcp.md 의 설명 원문이 코드와 다른 도구").isEmpty();
  }

  /** update_issue 의 setSection 한 줄(일감 #237) — 절의 범위(### 소절도 든다) · mode 셋 · 지운 것을 어디서 보나가 설명에 있고 문서와 같다 */
  @Test
  void update_issue_설명의_setSection_mode_줄이_문서와_같다() throws Exception {
    String description = java.util.Arrays.stream(IssueMcpTools.class.getDeclaredMethods())
        .map(m -> m.getAnnotation(Tool.class)).filter(t -> t != null && t.name().equals("update_issue")).findFirst().orElseThrow()
        .description();
    String line = description.lines().filter(l -> l.startsWith("setSection 의 절은")).findFirst().orElseThrow();
    // 지운 것은 removed, 옛 본문 previous 는 크게 지웠을 때만(SECTION_REPLACED_LARGE 와 같은 조건 — 일감 #282)
    assertThat(line).contains("### 소절도 든다", "replace(기본", "append(절 끝, 소절 뒤", "prepend(제목 줄 바로 뒤",
        "descriptionEdit.section.removed", "크게 지웠으면 previous(옛 본문)", "SECTION_REPLACED_LARGE");
    String doc = doc();
    int at = doc.indexOf("### 8.6 `update_issue`");
    assertThat(doc.substring(at, doc.indexOf("| 입력 |", at))).contains("\n" + line + "\n");
  }

  @Test
  void 연결_안내의_핵심_규칙이_문서와_같다() throws Exception {
    assertThat(doc()).contains(McpInstructions.ISSUE_HEAD + "\n\n" + McpInstructions.ISSUE_RULES + "\n\n");
  }
}
