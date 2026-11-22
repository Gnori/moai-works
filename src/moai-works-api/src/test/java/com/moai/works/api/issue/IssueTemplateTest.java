package com.moai.works.api.issue;

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
 * 유형별 일감 템플릿 — 워크스페이스 기본 · 프로젝트 덮어쓰기 · 되돌리기 · 권한 · 감사, 빠진 항목 알림, MCP(일감 입구 읽기 · 프로젝트 관리 입구 관리)
 * (2026-11-23 사용자: "이슈의 유형별로 작성 템플릿 넣어 주고 그걸 관리할 수 있게 해 줘. 또한 mcp 제공해서 그에 맞게 이슈 작성 및 수정할 수 있게").
 */
@ApiTest
class IssueTemplateTest {

  private static final String P = "tpl";
  private static final String WS_T = "/workspaces/{ws}/issue-templates";
  private static final String P_T = "/workspaces/{ws}/projects/{p}/issue-templates";

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;

  private record Space(String ws, TestUser owner, TestUser curator, TestUser member) {
  }

  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "템플릿");
    TestUser curator = api.user("curator");
    TestUser member = api.user("member");
    api.join(ws, owner, curator, "curator");
    api.join(ws, owner, member, "member");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "템플릿 시험", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    return new Space(ws, owner, curator, member);
  }

  private String key(TestUser who, String ws) throws Exception {
    return TestApi.read(api.post(who, "/workspaces/{ws}/mcp-keys", Map.of("name", "템플릿 키"), ws)
        .andExpect(status().isCreated()), "$.key");
  }

  private static Map<String, Object> args(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  @Test
  void 처음에는_유형마다_처음_템플릿이고_옛_유형은_없다() throws Exception {
    Space s = space();
    api.get(s.member(), P_T, s.ws(), P).andExpect(status().isOk())
        .andExpect(jsonPath("$[*].type").value(org.hamcrest.Matchers.containsInAnyOrder("feature", "bug", "test", "research")))
        .andExpect(jsonPath("$[0].source").value("BUILTIN"))
        .andExpect(jsonPath("$[0].inherited").value(true));
    api.get(s.member(), WS_T, s.ws()).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
    // 버그 처음 템플릿 — (선택) 항목은 optional
    api.get(s.member(), P_T + "/bug", s.ws(), P).andExpect(status().isOk())
        .andExpect(jsonPath("$.sections[?(@.title == '재현 순서')].optional").value(false))
        .andExpect(jsonPath("$.sections[?(@.title == '환경')].optional").value(true));
  }

  @Test
  void 워크스페이스_기본을_프로젝트가_물려받고_덮어쓰고_되돌린다() throws Exception {
    Space s = space();
    api.put(s.owner(), WS_T + "/feature", Map.of("body", "## 목표\n\n## 수용 기준\n- [ ]\n\n## 메모 (선택)\n"), s.ws())
        .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("WORKSPACE"));
    api.get(s.member(), P_T + "/feature", s.ws(), P).andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("WORKSPACE")).andExpect(jsonPath("$.inherited").value(true))
        .andExpect(jsonPath("$.rowVersion").doesNotExist())
        .andExpect(jsonPath("$.sections[*].title").value(org.hamcrest.Matchers.contains("목표", "수용 기준", "메모")));

    api.put(s.owner(), P_T + "/feature", Map.of("body", "## 이 프로젝트의 목표\n"), s.ws(), P)
        .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("PROJECT"))
        .andExpect(jsonPath("$.inherited").value(false)).andExpect(jsonPath("$.updatedBy.id").value(s.owner().id().toString()));
    api.get(s.member(), P_T + "/feature", s.ws(), P).andExpect(jsonPath("$.body").value("## 이 프로젝트의 목표\n"));

    // 프로젝트 덮어쓰기를 지우면 워크스페이스 기본, 워크스페이스 기본을 지우면 처음 템플릿
    api.delete(s.owner(), P_T + "/feature", s.ws(), P).andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("WORKSPACE"));
    api.delete(s.owner(), WS_T + "/feature", s.ws()).andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("BUILTIN"));
    api.get(s.member(), P_T + "/feature", s.ws(), P).andExpect(jsonPath("$.source").value("BUILTIN"));

    List<String> actions = TestApi.read(api.get(s.owner(), "/workspaces/{ws}/audit?action=issue_template&limit=20", s.ws()),
        "$[*].action");
    assertThat(actions).containsExactly("issue_template.reset", "issue_template.reset", "issue_template.update",
        "issue_template.update");
  }

  @Test
  void 고치기는_워크스페이스_설정_권한과_프로젝트_관리_권한이_있어야_한다() throws Exception {
    Space s = space();
    api.put(s.curator(), WS_T + "/bug", Map.of("body", "## 현상\n"), s.ws()).andExpect(status().isForbidden());
    api.put(s.curator(), P_T + "/bug", Map.of("body", "## 현상\n"), s.ws(), P).andExpect(status().isForbidden());
    api.delete(s.member(), P_T + "/bug", s.ws(), P).andExpect(status().isForbidden());
    api.get(s.member(), P_T, s.ws(), P).andExpect(status().isOk());
  }

  @Test
  void 옛_유형_없는_유형_너무_긴_글_낡은_판은_거절한다() throws Exception {
    Space s = space();
    api.put(s.owner(), WS_T + "/task", Map.of("body", "x"), s.ws()).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ISSUE_TYPE_RETIRED"));
    api.put(s.owner(), WS_T + "/nope", Map.of("body", "x"), s.ws()).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_ISSUE_TYPE"));
    api.put(s.owner(), WS_T + "/bug", Map.of("body", "x".repeat(30_001)), s.ws()).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TEMPLATE_TOO_LONG"));

    int v0 = TestApi.read(api.put(s.owner(), WS_T + "/bug", Map.of("body", "## 하나\n"), s.ws()), "$.rowVersion");
    api.put(s.owner(), WS_T + "/bug", Map.of("body", "## 둘\n", "rowVersion", v0), s.ws()).andExpect(status().isOk());
    api.put(s.owner(), WS_T + "/bug", Map.of("body", "## 셋\n", "rowVersion", v0), s.ws()).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
  }

  @Test
  void 빠진_항목은_제목_수준_대소문자_코드_블록을_가린다() {
    String template = "## 현상\n## 재현 순서\n### Expected\n## 환경 (선택)\n```\n## 코드 안의 제목\n```\n";
    assertThat(IssueTemplateService.missing(template, "# 현상\n내용\n#### expected\n")).containsExactly("재현 순서");
    assertThat(IssueTemplateService.missing(template, "```\n## 재현 순서\n```\n## 현상\n## Expected"))
        .containsExactly("재현 순서");
    assertThat(IssueTemplateService.missing(template, "## 현상\n## 재현 순서\n## expected")).isEmpty();
    assertThat(IssueTemplateService.missing("", "아무 글")).isEmpty();
  }

  @Test
  void MCP_일감_입구는_템플릿을_주고_만들기_고치기_결과에_빠진_항목을_알린다() throws Exception {
    Space s = space();
    McpTestClient mcp = new McpTestClient(mvc, key(s.curator(), s.ws()));
    assertThat(mcp.tools()).contains("get_issue_template");

    String tpl = mcp.call("get_issue_template", args("project", P, "type", "bug"));
    assertThat((String) read(tpl, "$.source")).isEqualTo("BUILTIN");
    assertThat((List<String>) read(tpl, "$.sections[*].title")).contains("현상", "재현 순서", "환경");

    // 빠진 항목은 막지 않고 알려 준다
    String created = mcp.call("create_issue", args("project", P, "type", "bug", "title", "로그인 실패", "description", "## 현상\n안 된다"));
    assertThat((Integer) read(created, "$.number")).isPositive();
    assertThat((List<String>) read(created, "$.templateCheck.missing")).containsExactly("재현 순서", "기대한 동작", "실제 동작",
        "수용 기준");
    int number = read(created, "$.number");
    int rowVersion = read(created, "$.rowVersion");

    String full = "## 현상\n안 된다\n## 재현 순서\n1. 로그인\n## 기대한 동작\n들어감\n## 실제 동작\n오류\n## 수용 기준\n- [ ] 들어간다";
    String updated = mcp.call("update_issue", args("project", P, "number", number, "rowVersion", rowVersion, "description", full));
    assertThat((List<String>) read(updated, "$.templateCheck.missing")).isEmpty();
    assertThat((String) read(updated, "$.templateCheck.source")).isEqualTo("BUILTIN");
  }

  @Test
  void MCP_프로젝트_관리_입구에서_템플릿을_관리한다_권한은_웹과_같다() throws Exception {
    Space s = space();
    McpTestClient owner = new McpTestClient(mvc, key(s.owner(), s.ws()), McpTestClient.PROJECT);
    assertThat(owner.tools()).containsExactlyInAnyOrder("list_projects", "list_issue_templates", "set_issue_template",
        "reset_issue_template");

    String set = owner.call("set_issue_template", args("type", "research", "body", "## 질문\n## 결론 (선택)\n"));
    assertThat((String) read(set, "$.source")).isEqualTo("WORKSPACE");
    String project = owner.call("set_issue_template", args("type", "research", "body", "## 이 프로젝트의 질문\n", "project", P));
    assertThat((String) read(project, "$.source")).isEqualTo("PROJECT");

    String listed = owner.call("list_issue_templates", args("project", P));
    assertThat((Boolean) read(listed, "$.canManage")).isTrue();
    assertThat((List<String>) read(listed, "$.templates[?(@.type == 'research')].source")).containsExactly("PROJECT");

    String reset = owner.call("reset_issue_template", args("type", "research", "project", P));
    assertThat((String) read(reset, "$.source")).isEqualTo("WORKSPACE");

    // 큐레이터 키 — 읽기는 되고 고치기는 PERMISSION_REQUIRED (REST 와 같은 판정)
    McpTestClient curator = new McpTestClient(mvc, key(s.curator(), s.ws()), McpTestClient.PROJECT);
    assertThat((Boolean) read(curator.call("list_issue_templates", args()), "$.canManage")).isFalse();
    assertThat((String) read(curator.call("set_issue_template", args("type", "bug", "body", "## x\n", "project", P)),
        "$.error.code")).isEqualTo("PERMISSION_REQUIRED");
    assertThat((String) read(curator.call("set_issue_template", args("type", "bug", "body", "## x\n")), "$.error.code"))
        .isEqualTo("PERMISSION_REQUIRED");

    // 감사에 경로 MCP
    List<String> via = TestApi.read(api.get(s.owner(), "/workspaces/{ws}/audit?action=issue_template&limit=20", s.ws()),
        "$[*].details");
    assertThat(via).allMatch(d -> d.contains("MCP"));
  }
}
