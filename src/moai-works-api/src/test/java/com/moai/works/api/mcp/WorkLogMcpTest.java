package com.moai.works.api.mcp;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
 * MCP 로 작업 시간 고치기(delete_work_log — 내 기록만 · 이유 필수 · 지움 표시 · 이력 · 감사)와 구간을 셈할 근거
 * (get_issue 의 statusChangedAt · lastWorkLogAt · serverTime, log_work 의 workLogId · serverTime). 2026-11-24, 일감 #176.
 */
@ApiTest
class WorkLogMcpTest {

  private static final String P = "hours";
  private static final String TODAY = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;

  private record Space(String ws, TestUser owner, TestUser curator, TestUser member) {
  }

  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "작업 시간");
    TestUser curator = api.user("curator");
    TestUser member = api.user("member");
    api.join(ws, owner, curator, "curator");
    api.join(ws, owner, member, "member");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "작업 시간", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    return new Space(ws, owner, curator, member);
  }

  private McpTestClient client(TestUser who) throws Exception {
    return new McpTestClient(mvc, TestApi.read(api.post(who, "/me/mcp-keys", Map.of("name", "시간 키"))
        .andExpect(status().isCreated()), "$.key"));
  }

  private static Map<String, Object> args(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  private static void serverTimeIsNow(String result) {
    String serverTime = read(result, "$.serverTime");
    assertThat(serverTime).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+09:00");
    assertThat(Duration.between(OffsetDateTime.parse(serverTime), OffsetDateTime.now()).abs()).isLessThan(Duration.ofMinutes(1));
  }

  @Test
  void 내가_적은_작업_시간만_이유와_함께_지우고_지운_기록은_deleted_로_남는다() throws Exception {
    Space s = space();
    McpTestClient curator = client(s.curator());
    McpTestClient owner = client(s.owner());
    int n = read(curator.call("create_issue", args("project", P, "type", "feature", "title", "시간 고치기")), "$.number");

    String logged = curator.call("log_work", args("number", n, "workDate", TODAY, "hours", 2, "note", "잘못 적음"));
    String mine = read(logged, "$.workLogId");
    serverTimeIsNow(logged);
    String others = read(owner.call("log_work", args("number", n, "workDate", TODAY, "hours", 1, "note", "소유자")), "$.workLogId");

    // 남의 기록 — 큐레이터도, project.manage 가 있는 소유자도 MCP 로는 못 지운다(웹에서)
    assertThat((String) read(curator.call("delete_work_log", args("number", n, "workLogId", others, "reason", "x")),
        "$.error.code")).isEqualTo("PERMISSION_REQUIRED");
    assertThat((String) read(owner.call("delete_work_log", args("number", n, "workLogId", mine, "reason", "x")),
        "$.error.code")).isEqualTo("PERMISSION_REQUIRED");
    // REST 와 같은 판정 — issue.edit 이 없으면 막힌다
    assertThat((String) read(client(s.member()).call("delete_work_log", args("number", n, "workLogId", mine, "reason", "x")),
        "$.error.code")).isEqualTo("PERMISSION_REQUIRED");
    assertThat((String) read(curator.call("delete_work_log", args("number", n, "workLogId", mine, "reason", " ")),
        "$.error.code")).isEqualTo("DELETE_REASON_REQUIRED");
    assertThat((String) read(curator.call("delete_work_log", args("number", n, "workLogId", "없는-id", "reason", "x")),
        "$.error.code")).isEqualTo("WORK_LOG_NOT_FOUND");

    String deleted = curator.call("delete_work_log", args("number", n, "workLogId", mine, "reason", "시간을 잘못 적어 다시 적음"));
    assertThat((Boolean) read(deleted, "$.deleted")).isTrue();
    assertThat(((Number) read(deleted, "$.spentHours")).doubleValue()).isEqualTo(1.0);
    serverTimeIsNow(deleted);
    // 이미 지운 기록
    assertThat((String) read(curator.call("delete_work_log", args("number", n, "workLogId", mine, "reason", "또")),
        "$.error.code")).isEqualTo("WORK_LOG_NOT_FOUND");

    // get_issue — 지운 기록도 id 와 deleted 로 남고, 합계에서 빠진다
    String issue = curator.call("get_issue", args("number", n));
    assertThat((List<Boolean>) read(issue, "$.workLogs[?(@.id == '" + mine + "')].deleted")).containsExactly(true);
    assertThat((List<String>) read(issue, "$.workLogs[?(@.id == '" + mine + "')].deletedBy.id"))
        .containsExactly(s.curator().id().toString());
    assertThat((List<Object>) read(issue, "$.workLogs[?(@.id == '" + mine + "')].deletedAt")).doesNotContainNull();
    assertThat((List<Boolean>) read(issue, "$.workLogs[?(@.id == '" + others + "')].deleted")).containsExactly(false);
    assertThat(((Number) read(issue, "$.spentHours")).doubleValue()).isEqualTo(1.0);
    // 큐레이터의 남은 기록이 없다 — 지운 기록은 근거가 아니다
    assertThat((Object) read(issue, "$.lastWorkLogAt")).isNull();
    // 이력의 그 줄에 지운 기록과 이유가 남는다(경로 MCP)
    List<Map<String, Object>> journal = read(issue, "$.journal");
    Map<String, Object> last = journal.get(journal.size() - 1);
    assertThat(last.get("via")).isEqualTo("MCP");
    assertThat((String) read(issue, "$.journal[-1].changes[0].field")).isEqualTo("workLog");
    assertThat((String) read(issue, "$.journal[-1].changes[0].before.id")).isEqualTo(mine);
    assertThat((String) read(issue, "$.journal[-1].changes[0].reason")).isEqualTo("시간을 잘못 적어 다시 적음");

    // 웹(REST) 상세는 그대로 지우지 않은 것만, 감사에는 경로와 이유
    api.get(s.owner(), "/workspaces/{ws}/issues/{n}", s.ws(), n)
        .andExpect(jsonPath("$.workLogs.length()").value(1))
        .andExpect(jsonPath("$.workLogs[0].id").value(others))
        .andExpect(jsonPath("$.workLogs[0].deleted").value(false));
    api.get(s.owner(), "/workspaces/{ws}/audit?action=issue.work_log.remove", s.ws())
        .andExpect(jsonPath("$[0].details").value(org.hamcrest.Matchers.allOf(
            org.hamcrest.Matchers.containsString("MCP"), org.hamcrest.Matchers.containsString("시간을 잘못 적어 다시 적음"))));
    // 웹에서는 project.manage 로 남의 기록도 지운다(그대로)
    api.delete(s.owner(), "/workspaces/{ws}/issues/{n}/work-logs/{id}", s.ws(), n, others).andExpect(status().isOk());
  }

  @Test
  void get_issue_는_지금_상태가_된_때와_내가_마지막으로_적은_때와_서버_시각을_준다() throws Exception {
    Space s = space();
    McpTestClient curator = client(s.curator());
    McpTestClient owner = client(s.owner());
    String created = curator.call("create_issue", args("project", P, "type", "bug", "title", "구간"));
    int n = read(created, "$.number");
    // 만들 때 상태가 된다 — 만든 이력 줄의 때
    assertThat((String) read(created, "$.statusChangedAt")).isEqualTo(read(created, "$.journal[0].createdAt"));

    String started = curator.call("update_issue", args("number", n, "rowVersion", read(created, "$.rowVersion"),
        "status", "IN_PROGRESS"));
    String startedAt = read(started, "$.statusChangedAt");
    assertThat(startedAt).isEqualTo(read(started, "$.journal[-1].createdAt")).isNotEqualTo(read(created, "$.statusChangedAt"));
    // 상태를 바꾸지 않는 이력(댓글 · 작업 시간)은 그대로
    curator.call("add_comment", args("number", n, "text", "진행 메모"));
    String loggedMine = curator.call("log_work", args("number", n, "workDate", TODAY, "hours", 0.5));
    owner.call("log_work", args("number", n, "workDate", TODAY, "hours", 0.25));
    String issue = curator.call("get_issue", args("number", n));
    assertThat((String) read(issue, "$.statusChangedAt")).isEqualTo(startedAt);
    serverTimeIsNow(issue);
    // lastWorkLogAt 은 키 주인(큐레이터)의 기록 — 소유자가 나중에 적은 것이 아니다
    String myLogId = read(loggedMine, "$.workLogId");
    assertThat((String) read(issue, "$.lastWorkLogAt"))
        .isEqualTo(((List<String>) read(issue, "$.workLogs[?(@.id == '" + myLogId + "')].createdAt")).get(0));

    // 진행 → 테스트 → 진행: 마지막으로 진행이 된 때
    String testing = curator.call("update_issue", args("number", n, "rowVersion", read(issue, "$.rowVersion"), "status", "TESTING"));
    String again = curator.call("update_issue", args("number", n, "rowVersion", read(testing, "$.rowVersion"),
        "status", "IN_PROGRESS"));
    assertThat((String) read(again, "$.statusChangedAt")).isEqualTo(read(again, "$.journal[-1].createdAt"))
        .isNotEqualTo(startedAt);
    // REST 상세에도 같은 값
    api.get(s.owner(), "/workspaces/{ws}/issues/{n}", s.ws(), n)
        .andExpect(jsonPath("$.statusChangedAt").value((String) read(again, "$.statusChangedAt")));
  }
}
