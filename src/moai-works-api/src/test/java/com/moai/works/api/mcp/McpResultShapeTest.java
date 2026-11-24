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
 * MCP 일감 결과의 모양(일감 #282) — 본문은 summary 와 겹치지 않고, 이력은 설명의 글자 수 · 최근 줄, 사람은 아이디, 찾기 줄의 프로젝트 ·
 * 모듈은 key, 절 바꾸기의 옛 본문은 크게 지웠을 때만. REST 는 그대로.
 */
@ApiTest
class McpResultShapeTest {

  private static final String P = "shape";

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;

  private record Space(String ws, TestUser owner, TestUser curator, TestUser member, McpTestClient mcp) {
  }

  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "결과 모양");
    TestUser curator = api.user("curator");
    TestUser member = api.user("member");
    api.join(ws, owner, curator, "curator");
    api.join(ws, owner, member, "member");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "모양", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    api.post(owner, "/workspaces/{ws}/projects/{p}/modules", Map.of("key", "api", "name", "API"), ws, P)
        .andExpect(status().isCreated());
    String key = TestApi.read(api.post(curator, "/me/mcp-keys", Map.of("name", "모양 키")).andExpect(status().isCreated()), "$.key");
    return new Space(ws, owner, curator, member, new McpTestClient(mvc, key));
  }

  private static Map<String, Object> args(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  @Test
  void 일감_본문은_summary_와_겹치지_않고_사람은_아이디_빈_값은_없다() throws Exception {
    Space s = space();
    String created = s.mcp().call("create_issue", args("project", P, "type", "feature", "title", "모양", "description",
        "## 배경\n\n글", "assignee", s.member().username(), "modules", List.of("api")));
    Map<String, Object> summary = read(created, "$.summary");
    assertThat(summary).containsKeys("number", "project", "status", "rowVersion", "statusChangedAt", "serverTime")
        .doesNotContainKeys("dueDate", "agentSession", "lastWorkLogAt"); // 비어 있으면 없다
    Map<String, Object> top = read(created, "$");
    assertThat(top).doesNotContainKeys("number", "rowVersion", "status", "project", "title", "modules", "templateCheck",
        "description", "journal", "workLogs", "parent", "children", "links");
    assertThat(top).containsKeys("summary", "checks", "next", "manager", "assignee", "createdBy", "journalCount", "omitted");
    assertThat((String) read(created, "$.manager.username")).isEqualTo(s.curator().username());
    assertThat((String) read(created, "$.manager.name")).isEqualTo(s.curator().displayName());
    assertThat((List<String>) read(created, "$.omitted")).containsExactly("description", "workLogs", "journal");
    // 템플릿의 빠진 필수 항목은 checks 하나로
    assertThat((List<String>) read(created, "$.checks[?(@.code == 'TEMPLATE_SECTIONS_MISSING')].items[*]")).isNotEmpty();

    int n = read(created, "$.summary.number");
    String got = s.mcp().call("get_issue", args("number", n));
    assertThat((Map<String, Object>) read(got, "$")).doesNotContainKeys("number", "rowVersion", "status", "statusChangedAt",
        "lastWorkLogAt", "serverTime", "modules", "templateCheck").containsKeys("description", "journal", "journalCount");
    assertThat((List<String>) read(got, "$.summary.modules")).containsExactly("api");
    // include 로 더 받는다
    String more = s.mcp().call("update_issue", args("number", n, "rowVersion", read(created, "$.summary.rowVersion"), "title", "모양 2",
        "include", List.of("description", "workLogs", "journal")));
    assertThat((String) read(more, "$.description")).isEqualTo("## 배경\n\n글");
    assertThat((List<Object>) read(more, "$.journal")).hasSize(2);
    assertThat((Map<String, Object>) read(more, "$")).doesNotContainKey("omitted"); // 뺀 것이 없으면 빈 목록이라 싣지 않는다
    assertThat((String) read(s.mcp().call("get_issue", args("number", n, "include", List.of("모름"))), "$.error.field"))
        .isEqualTo("include");
  }

  @Test
  void 이력은_설명의_글자_수와_최근_20줄이고_include_로_모두와_글을_받는다_REST_는_그대로() throws Exception {
    Space s = space();
    String created = s.mcp().call("create_issue", args("project", P, "type", "bug", "title", "이력", "description", "가나다"));
    int n = read(created, "$.summary.number");
    int rv = read(created, "$.summary.rowVersion");
    for (int i = 0; i < 12; i++) {
      rv = read(s.mcp().call("update_issue", args("number", n, "rowVersion", rv, "description", "가나다" + "라".repeat(i + 1))),
          "$.summary.rowVersion");
      s.mcp().call("add_comment", args("number", n, "text", "댓글 " + i));
    }
    // 25줄(만들기 1 + 설명 12 + 댓글 12)
    String got = s.mcp().call("get_issue", args("number", n));
    assertThat((Integer) read(got, "$.journalCount")).isEqualTo(25);
    List<Map<String, Object>> journal = read(got, "$.journal");
    assertThat(journal).hasSize(IssueShapes.JOURNAL_RECENT);
    // 마지막 줄이 가장 늦은 댓글
    assertThat((String) read(got, "$.journal[-1].notes")).isEqualTo("댓글 11");
    // 설명이 바뀐 줄 — 글 대신 글자 수, 판 번호는 줄에
    Map<String, Object> edit = read(got, "$.journal[-2]");
    assertThat(edit).containsKey("revisionNo").doesNotContainKeys("id", "notes", "rowVersion", "actorType", "actorId", "actorName");
    assertThat((Map<String, Object>) read(got, "$.journal[-2].changes[0]")).containsEntry("field", "description")
        .containsEntry("chars", Map.of("before", 14, "after", 15)).doesNotContainKeys("before", "after");
    // 댓글 줄 — id(commentId) · rowVersion · actor(아이디)
    assertThat((Map<String, Object>) read(got, "$.journal[-1]")).containsKeys("id", "rowVersion")
        .containsEntry("actor", Map.of("username", s.curator().username(), "name", s.curator().displayName()));

    String all = s.mcp().call("get_issue", args("number", n, "include", List.of("journal")));
    assertThat((List<Object>) read(all, "$.journal")).hasSize(25);
    assertThat(((List<Object>) read(all, "$.journal[0].changes[?(@.field == 'description')].chars")).get(0))
        .isEqualTo(Map.of("before", 0, "after", 3));
    String text = s.mcp().call("get_issue", args("number", n, "include", "journalDescription"));
    assertThat((String) read(text, "$.journal[-2].changes[0].after")).isEqualTo("가나다" + "라".repeat(12));
    assertThat((String) read(text, "$.journal[-2].changes[0].before")).isEqualTo("가나다" + "라".repeat(11));

    // REST 일감 보기는 그대로 — 이력 전체 · 설명의 전 · 후 글 · 사람 {id, name}
    api.get(s.owner(), "/workspaces/{ws}/issues/{n}", s.ws(), n).andExpect(status().isOk())
        .andExpect(jsonPath("$.journal.length()").value(25)).andExpect(jsonPath("$.number").value(n))
        .andExpect(jsonPath("$.journal[0].actorId").value(s.curator().id().toString()))
        .andExpect(jsonPath("$.journal[0].changes[?(@.field == 'description')].after").value("가나다"))
        .andExpect(jsonPath("$.manager.id").value(s.curator().id().toString()));
  }

  @Test
  void 찾기_줄은_project_key_와_모듈_key_사람은_아이디_이름은_맨_위_projects_에_한_번() throws Exception {
    Space s = space();
    for (int i = 0; i < 3; i++) {
      s.mcp().call("create_issue", args("project", P, "type", "bug", "title", "줄 " + i, "modules", List.of("api"), "assignee",
          s.member().username()));
    }
    String found = s.mcp().call("search_issues", args("project", P));
    assertThat((List<String>) read(found, "$.items[*].project")).containsOnly(P).hasSize(3);
    assertThat((Map<String, Object>) read(found, "$.projects")).isEqualTo(Map.of(P, "모양"));
    assertThat((List<List<String>>) read(found, "$.items[*].modules")).containsOnly(List.of("api"));
    assertThat((List<Object>) read(found, "$.items[*].assignee.username")).containsOnly(s.member().username());
    Map<String, Object> row = read(found, "$.items[0]");
    assertThat(row).containsKeys("number", "rowVersion", "statusChangedAt", "status", "updatedAt")
        .doesNotContainKeys("childCount", "overdue", "dueDate", "parent", "estimatedHours");
    assertThat((Boolean) read(found, "$.hasNext")).isFalse();
    // 없으면 빈 items 그대로
    assertThat((List<Object>) read(s.mcp().call("search_issues", args("project", P, "q", "없는 말")), "$.items")).isEmpty();
    // 댓글 목록의 쓴 사람
    int n = read(found, "$.items[0].number");
    s.mcp().call("add_comment", args("number", n, "text", "메모"));
    Map<String, Object> comment = read(s.mcp().call("list_comments", args("number", n)), "$.items[0]");
    assertThat(comment).containsKeys("id", "actor", "rowVersion", "notes").doesNotContainKeys("actorId", "actorName", "actorType",
        "deleted", "withChanges");
  }

  @Test
  void 절_바꾸기의_옛_본문은_크게_지웠을_때만() throws Exception {
    Space s = space();
    String body = "## 처리 결과\n\n- 짧은 줄\n\n## 남은 것\n없음";
    String created = s.mcp().call("create_issue", args("project", P, "type", "bug", "title", "절", "description", body));
    int n = read(created, "$.summary.number");
    String small = s.mcp().call("update_issue", args("number", n, "rowVersion", read(created, "$.summary.rowVersion"),
        "setSection", Map.of("heading", "처리 결과", "content", "- 새 줄")));
    assertThat((Map<String, Object>) read(small, "$.descriptionEdit.section")).containsKey("removed").doesNotContainKey("previous")
        .containsEntry("action", "REPLACED");
    assertThat((Integer) read(small, "$.descriptionEdit.section.removed.chars")).isEqualTo(6);
    String other = s.mcp().call("update_issue", args("number", n, "rowVersion", read(small, "$.summary.rowVersion"),
        "setSection", Map.of("heading", "남은 것", "content", "다른 글")));
    assertThat((List<String>) read(other, "$.checks[*].code")).doesNotContain("SECTION_REPLACED_LARGE");
    String big = s.mcp().call("update_issue", args("number", n, "rowVersion", read(other, "$.summary.rowVersion"),
        "setSection", Map.of("heading", "처리 결과", "content", "### 소절\n\n" + "가".repeat(10))));
    String bigger = s.mcp().call("update_issue", args("number", n, "rowVersion", read(big, "$.summary.rowVersion"),
        "setSection", Map.of("heading", "처리 결과", "content", "끝")));
    assertThat((List<String>) read(bigger, "$.checks[*].code")).contains("SECTION_REPLACED_LARGE");
    assertThat((String) read(bigger, "$.descriptionEdit.section.previous.text")).isEqualTo("### 소절\n\n" + "가".repeat(10));
  }
}
