package com.moai.works.api.mcp;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.McpTestClient;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * update_issue 로 설명을 통째로 보내지 않고 끝내기 — setSection(절 채우기) · checkItems · uncheckItems 를 상태 · 댓글과 한 번에,
 * 설명 바뀜은 이력 · 판에 그대로, 맞지 않는 체크 글은 CHECK_ITEM_NOT_FOUND(아무것도 안 바뀜). 2026-11-24, 일감 #178.
 */
@ApiTest
class McpCompletionTest {

  private static final String P = "done";
  private static final String DESCRIPTION = """
      ## 배경
      MCP 로 끝내기를 쉽게.

      ## 수용 기준
      - [ ] 절을 채운다
      - [ ] 체크한다, 쉼표가 있어도

      ## 처리 결과 (선택)
      """;

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;

  private record Space(String ws, TestUser curator) {
  }

  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "끝내기");
    TestUser curator = api.user("curator");
    api.join(ws, owner, curator, "curator");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "끝내기", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    return new Space(ws, curator);
  }

  private McpTestClient client(TestUser who) throws Exception {
    return new McpTestClient(mvc, TestApi.read(api.post(who, "/me/mcp-keys", Map.of("name", "끝내기 키"))
        .andExpect(status().isCreated()), "$.key"));
  }

  private static Map<String, Object> args(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  @Test
  void 상태_절_체크_댓글을_한_번에_하고_설명_바뀜은_이력과_판에_남는다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    String created = mcp.call("create_issue", args("project", P, "type", "feature", "title", "끝내기", "description", DESCRIPTION,
        "status", "TESTING"));
    int n = read(created, "$.summary.number");
    int revision = read(created, "$.revisionNo");

    String done = mcp.call("update_issue", args("number", n, "rowVersion", read(created, "$.summary.rowVersion"), "status", "DONE",
        "comment", "끝냈습니다", "setSection", Map.of("heading", "처리 결과", "content", "- 바뀐 것: setSection\n- 커밋: abc123"),
        "checkItems", "all", "include", List.of("description", "journal")));
    assertThat((String) read(done, "$.summary.status")).isEqualTo("DONE");
    String description = read(done, "$.description");
    assertThat(description).contains("- [x] 절을 채운다").contains("- [x] 체크한다, 쉼표가 있어도")
        .endsWith("## 처리 결과 (선택)\n\n- 바뀐 것: setSection\n- 커밋: abc123").startsWith("## 배경\nMCP 로 끝내기를 쉽게.");
    assertThat((String) read(done, "$.descriptionEdit.section.action")).isEqualTo("REPLACED");
    assertThat((List<String>) read(done, "$.descriptionEdit.checked")).containsExactly("절을 채운다", "체크한다, 쉼표가 있어도");
    // 빠진 템플릿 항목은 checks 의 TEMPLATE_SECTIONS_MISSING 하나로 — templateCheck 칸은 없다(일감 #282)
    assertThat((Map<String, Object>) read(done, "$")).doesNotContainKey("templateCheck");
    // 한 이력 줄에 상태 · 설명 바뀜과 댓글, 새 판 하나
    assertThat((List<String>) read(done, "$.journal[-1].changes[*].field")).contains("status", "description");
    assertThat((String) read(done, "$.journal[-1].notes")).isEqualTo("끝냈습니다");
    assertThat((Integer) read(done, "$.revisionNo")).isEqualTo(revision + 1);
    api.get(s.curator(), "/workspaces/{ws}/issues/{n}/revisions/{no}/diff", s.ws(), n, revision + 1)
        .andExpect(status().isOk()).andExpect(jsonPath("$.added").value(org.hamcrest.Matchers.greaterThan(0)));

    // 체크 풀기 — 글 하나(쉼표를 나누지 않는다)
    String back = mcp.call("update_issue", args("number", n, "rowVersion", read(done, "$.summary.rowVersion"),
        "uncheckItems", "체크한다, 쉼표가 있어도", "include", "description"));
    assertThat((String) read(back, "$.description")).contains("- [ ] 체크한다, 쉼표가 있어도").contains("- [x] 절을 채운다");
    assertThat((List<String>) read(back, "$.descriptionEdit.unchecked")).containsExactly("체크한다, 쉼표가 있어도");
  }

  @Test
  void 맞지_않는_체크_글은_CHECK_ITEM_NOT_FOUND_이고_아무것도_바뀌지_않는다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    String created = mcp.call("create_issue", args("project", P, "type", "feature", "title", "못 찾음", "description", DESCRIPTION,
        "include", "description"));
    int n = read(created, "$.summary.number");
    int rowVersion = read(created, "$.summary.rowVersion");

    String missing = mcp.call("update_issue", args("number", n, "rowVersion", rowVersion, "status", "IN_PROGRESS",
        "setSection", Map.of("heading", "처리 결과", "content", "x"), "checkItems", List.of("절을 채운다", "없는 항목")));
    assertThat((String) read(missing, "$.error.code")).isEqualTo("CHECK_ITEM_NOT_FOUND");
    assertThat((Integer) read(missing, "$.error.status")).isEqualTo(400);
    assertThat((String) read(missing, "$.error.message")).contains("없는 항목");
    String now = mcp.call("get_issue", args("number", n));
    assertThat((Integer) read(now, "$.summary.rowVersion")).isEqualTo(rowVersion);
    assertThat((String) read(now, "$.summary.status")).isEqualTo("NEW");
    assertThat((String) read(now, "$.description")).isEqualTo(read(created, "$.description"));

    // 낡은 rowVersion 은 체크 글보다 먼저 CONCURRENT_UPDATE
    mcp.call("add_comment", args("number", n, "text", "rowVersion 은 그대로"));
    String moved = mcp.call("update_issue", args("number", n, "rowVersion", rowVersion, "title", "바뀜"));
    assertThat((String) read(mcp.call("update_issue", args("number", n, "rowVersion", rowVersion, "checkItems", "없는 항목")),
        "$.error.code")).isEqualTo("CONCURRENT_UPDATE");

    // description 을 함께 주면 그것 먼저, 그다음 절 · 체크
    String both = mcp.call("update_issue", args("number", n, "rowVersion", read(moved, "$.summary.rowVersion"),
        "description", "## 새 설명\n- [ ] 새 항목", "checkItems", "새 항목", "setSection", Map.of("heading", "결론", "content", "됨"),
        "include", List.of("description")));
    assertThat((String) read(both, "$.description")).isEqualTo("## 새 설명\n- [x] 새 항목\n\n## 결론\n\n됨");
    assertThat((String) read(both, "$.descriptionEdit.section.action")).isEqualTo("CREATED");
  }

  /** 일감 #237 의 일 — 결과 절 아래 소절 · 차트 · 흐름도 · 코드 블록. 소절 제목(비밀소절)은 메시지에 나오면 안 된다 */
  private static final String RESULTS = """
      ## 목적
      본다

      ## 테스트 항목
      - [ ] 응답 시간

      ## 테스트 결과 (선택)

      ### 1. 비밀소절
      ```chart
      {"type":"gauge","title":"진행","value":63,"unit":"%"}
      ```

      ### 2. 흐름
      ```mermaid
      flowchart LR
        A --> B
      ```

      ```java
      int x = 1;
      ```
      """;

  private static List<String> codes(String result) {
    return read(result, "$.checks[*].code");
  }

  @Test
  void setSection_의_mode_와_replace_가_지운_것_크게_지우면_SECTION_REPLACED_LARGE() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    String created = mcp.call("create_issue", args("project", P, "type", "test", "title", "결과 절", "description", RESULTS));
    int n = read(created, "$.summary.number");

    // append — 절 끝(소절 뒤)에 더하고 지운 것이 없다
    String appended = mcp.call("update_issue", args("number", n, "rowVersion", read(created, "$.summary.rowVersion"),
        "setSection", Map.of("heading", "테스트 결과", "content", "- 다시 돌림: 통과", "mode", "append"), "include", "description"));
    assertThat((String) read(appended, "$.description")).endsWith("int x = 1;\n```\n\n- 다시 돌림: 통과").contains("### 1. 비밀소절");
    assertThat((Map<String, Object>) read(appended, "$.descriptionEdit.section")).containsEntry("mode", "append")
        .containsEntry("action", "APPENDED").doesNotContainKeys("removed", "previous");
    assertThat(codes(appended)).doesNotContain("SECTION_REPLACED_LARGE");

    // prepend — 제목 줄 바로 뒤(대소문자 무시)
    String prepended = mcp.call("update_issue", args("number", n, "rowVersion", read(appended, "$.summary.rowVersion"),
        "setSection", Map.of("heading", "테스트 결과", "content", "요약: 모두 통과", "mode", "PREPEND"), "include", "description"));
    assertThat((String) read(prepended, "$.description")).contains("## 테스트 결과 (선택)\n\n요약: 모두 통과\n\n### 1. 비밀소절");
    assertThat((String) read(prepended, "$.descriptionEdit.section.action")).isEqualTo("PREPENDED");
    int rowVersion = read(prepended, "$.summary.rowVersion");

    // 모르는 mode — INVALID_ARGUMENT(field setSection.mode), 상태도 설명도 그대로
    String wrong = mcp.call("update_issue", args("number", n, "rowVersion", rowVersion, "status", "IN_PROGRESS",
        "setSection", Map.of("heading", "테스트 결과", "content", "x", "mode", "merge")));
    assertThat((String) read(wrong, "$.error.code")).isEqualTo("INVALID_ARGUMENT");
    assertThat((String) read(wrong, "$.error.field")).isEqualTo("setSection.mode");
    String now = mcp.call("get_issue", args("number", n));
    assertThat((Integer) read(now, "$.summary.rowVersion")).isEqualTo(rowVersion);
    assertThat((String) read(now, "$.summary.status")).isEqualTo("NEW");
    assertThat((String) read(now, "$.description")).isEqualTo(read(prepended, "$.description"));

    // replace(기본) — 소절 · 코드 블록을 지우면 checks 맨 앞에 SECTION_REPLACED_LARGE, 지운 수 · 옛 본문. 쓰기는 된다
    String replaced = mcp.call("update_issue", args("number", n, "rowVersion", rowVersion,
        "setSection", Map.of("heading", "테스트 결과", "content", "- 통과 12건"), "include", "description"));
    assertThat((String) read(replaced, "$.description")).endsWith("## 테스트 결과 (선택)\n\n- 통과 12건");
    assertThat((Map<String, Object>) read(replaced, "$.descriptionEdit.section")).containsEntry("mode", "replace")
        .containsEntry("action", "REPLACED");
    assertThat((Map<String, Object>) read(replaced, "$.descriptionEdit.section.removed")).containsEntry("subHeadings", 2)
        .containsEntry("codeBlocks", Map.of("chart", 1, "mermaid", 1, "other", 1));
    // 빈 줄을 뺀 옛 본문 14줄(요약 · 소절 둘 · 블록 셋의 울타리와 내용 · 더한 줄) 모두 새 글에 없다
    assertThat((Integer) read(replaced, "$.descriptionEdit.section.removed.lines")).isEqualTo(14);
    String previous = read(replaced, "$.descriptionEdit.section.previous.text");
    assertThat(previous).startsWith("요약: 모두 통과\n\n### 1. 비밀소절").endsWith("- 다시 돌림: 통과");
    assertThat((Boolean) read(replaced, "$.descriptionEdit.section.previous.truncated")).isFalse();
    assertThat((Integer) read(replaced, "$.descriptionEdit.section.previous.chars")).isEqualTo(previous.length());
    assertThat((String) read(replaced, "$.checks[0].code")).isEqualTo("SECTION_REPLACED_LARGE");
    assertThat((String) read(replaced, "$.checks[0].level")).isEqualTo("WARN");
    assertThat((String) read(replaced, "$.checks[0].message"))
        .contains("소절(### 제목) 2개", "코드 블록 3개(chart 1 · mermaid 1 · 그 밖 1)", "descriptionEdit.section.previous", "mode=append")
        .doesNotContain("비밀소절", "테스트 결과", "flowchart", "통과");

    // 되돌리고 더하기 — previous 를 옮겨 적은 replace 는 지운 것이 없어 알리지 않는다
    String restored = mcp.call("update_issue", args("number", n, "rowVersion", read(replaced, "$.summary.rowVersion"),
        "setSection", Map.of("heading", "테스트 결과", "content", previous + "\n\n- 통과 12건"), "include", "description"));
    assertThat(codes(restored)).doesNotContain("SECTION_REPLACED_LARGE");
    assertThat((Map<String, Object>) read(restored, "$.descriptionEdit.section.removed")).containsEntry("chars", 0)
        .containsEntry("subHeadings", 0);
    assertThat((String) read(restored, "$.description")).contains("### 1. 비밀소절").endsWith("- 다시 돌림: 통과\n\n- 통과 12건");

    // 템플릿 그대로 빈 결과 절에 쓰는 흔한 replace 에는 없다
    String fresh = mcp.call("create_issue", args("project", P, "type", "test", "title", "빈 절", "description",
        "## 목적\n본다\n\n## 테스트 항목\n- [ ] 하나\n\n## 테스트 결과 (선택)\n"));
    String filled = mcp.call("update_issue", args("number", read(fresh, "$.summary.number"), "rowVersion", read(fresh, "$.summary.rowVersion"),
        "setSection", Map.of("heading", "테스트 결과", "content", "- 통과 1건")));
    assertThat(codes(filled)).doesNotContain("SECTION_REPLACED_LARGE");
    // 크게 지우지 않았으면 옛 본문(previous)은 싣지 않고 지운 수(removed)만(일감 #282)
    assertThat((Map<String, Object>) read(filled, "$.descriptionEdit.section")).containsKey("removed").doesNotContainKey("previous");
    assertThat((Map<String, Object>) read(restored, "$.descriptionEdit.section")).doesNotContainKey("previous");
  }

  @Test
  void 입력_틀이_setSection_은_객체_checkItems_는_배열이나_글_하나로_알린다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    String tools = mcp.toolsJson();
    String base = "$.result.tools[?(@.name == 'update_issue')].inputSchema.properties.";
    assertThat((List<String>) read(tools, base + "setSection.type")).containsExactly("object");
    assertThat((List<String>) read(tools, base + "setSection.properties.heading.description")).singleElement().asString()
        .contains("절 제목");
    assertThat((List<List<String>>) read(tools, base + "setSection.required")).containsExactly(List.of("heading"));
    // mode(일감 #237) — 선택, 세 값을 설명에
    assertThat((List<String>) read(tools, base + "setSection.properties.mode.description")).singleElement().asString()
        .contains("replace(기본", "append", "prepend", "INVALID_ARGUMENT");
    assertThat((List<String>) read(tools, base + "setSection.description")).singleElement().asString()
        .contains("{heading, content, mode}", "설명 끝에 붙인다");
    // 절의 범위(### 소절도 든다)와 mode 셋은 도구 설명 한 곳에(일감 #282)
    assertThat(((List<String>) read(tools, "$.result.tools[?(@.name == 'update_issue')].description")).get(0))
        .contains("### 소절도 든다", "replace(기본", "append(절 끝, 소절 뒤", "prepend(제목 줄 바로 뒤");
    assertThat((List<String>) read(tools, base + "checkItems.anyOf[*].type")).containsExactly("array", "string");
    // 객체가 아니면 같은 오류 모양
    int n = read(mcp.call("create_issue", args("project", P, "type", "bug", "title", "꼴")), "$.summary.number");
    String wrong = mcp.call("update_issue", args("number", n, "rowVersion", 0, "setSection", "처리 결과"));
    assertThat((String) read(wrong, "$.error.code")).isEqualTo("INVALID_ARGUMENT");
    assertThat((String) read(wrong, "$.error.field")).isEqualTo("setSection");
  }
}
