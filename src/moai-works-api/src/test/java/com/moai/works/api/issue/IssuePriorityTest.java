package com.moai.works.api.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * 일감 우선순위 (2026-11-26, 일감 #302) — 낮음 · 보통(기본) · 높음 · 긴급. 기본값 · 만들기 · 고치기 · 이력 · 판 · 목록 거르기 · 우선순위순.
 */
@ApiTest
class IssuePriorityTest {

  private static final String P = "prio";
  private static final String ISSUES = "/workspaces/{ws}/projects/{project}/issues";
  private static final String ISSUE = "/workspaces/{ws}/issues/{n}";

  @Autowired
  TestApi api;
  @Autowired
  JdbcTemplate jdbc;

  private record Space(String ws, TestUser owner) {
  }

  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "우선순위 시험");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "우선순위", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    return new Space(ws, owner);
  }

  private int issue(Space s, String title, String priority) throws Exception {
    Map<String, Object> body = new HashMap<>(Map.of("type", "feature", "title", title));
    if (priority != null) {
      body.put("priority", priority);
    }
    return TestApi.<Integer>read(api.post(s.owner(), ISSUES, body, s.ws(), P).andExpect(status().isCreated()), "$.number");
  }

  private void patch(Space s, int n, Map<String, Object> changes) throws Exception {
    Map<String, Object> body = new HashMap<>(changes);
    body.put("rowVersion", TestApi.<Integer>read(api.get(s.owner(), ISSUE, s.ws(), n), "$.rowVersion"));
    api.patch(s.owner(), ISSUE, body, s.ws(), n).andExpect(status().isOk());
  }

  @Test
  void 비우면_보통이고_DB_도_기본값_보통에_넷만_받는다() throws Exception {
    Space s = space();
    int n = issue(s, "기본", null);
    api.get(s.owner(), ISSUE, s.ws(), n).andExpect(jsonPath("$.priority").value("NORMAL"));
    // 만들 때 이력 줄에 보통은 적지 않는다(늘 같은 값이라 소음)
    List<Object> fields = TestApi.read(api.get(s.owner(), ISSUE, s.ws(), n), "$.journal[0].changes[*].field");
    assertThat(fields).doesNotContain("priority");

    // V30 — 열 기본값 'NORMAL'(이미 있던 일감은 이 값으로 채워졌다) · 넷 밖의 값은 DB 가 막는다
    String schema = jdbc.queryForObject("SELECT current_schema()", String.class);
    String def = jdbc.queryForObject("SELECT column_default FROM information_schema.columns WHERE table_schema = ? "
        + "AND table_name = 'issue' AND column_name = 'priority'", String.class, schema);
    assertThat(def).contains("'NORMAL'");
    String mine = "number = ? AND workspace_id = (SELECT id FROM workspace WHERE slug = ?)";
    assertThat(jdbc.queryForObject("SELECT r.priority FROM issue_revision r JOIN issue i ON i.id = r.issue_id WHERE i." + mine
        + " AND r.revision_no = 1", String.class, n, s.ws())).isEqualTo("NORMAL");
    assertThatThrownBy(() -> jdbc.update("UPDATE issue SET priority = 'MAYBE' WHERE " + mine, n, s.ws()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 만들고_고치면_이력과_판에_남는다() throws Exception {
    Space s = space();
    int n = issue(s, "급한 일", "URGENT");
    api.get(s.owner(), ISSUE, s.ws(), n).andExpect(jsonPath("$.priority").value("URGENT"))
        .andExpect(jsonPath("$.journal[0].changes[?(@.field == 'priority')].after").value("URGENT"));

    patch(s, n, Map.of("priority", "LOW"));
    api.get(s.owner(), ISSUE, s.ws(), n).andExpect(jsonPath("$.priority").value("LOW"))
        .andExpect(jsonPath("$.journal[1].changes[0].field").value("priority"))
        .andExpect(jsonPath("$.journal[1].changes[0].before").value("URGENT"))
        .andExpect(jsonPath("$.journal[1].changes[0].after").value("LOW"));
    // 판: 1판 긴급 · 2판 낮음, 차이에 우선순위
    api.get(s.owner(), ISSUE + "/revisions/1", s.ws(), n).andExpect(jsonPath("$.priority").value("URGENT"));
    api.get(s.owner(), ISSUE + "/revisions/2", s.ws(), n).andExpect(jsonPath("$.priority").value("LOW"));
    api.get(s.owner(), ISSUE + "/revisions/2/diff", s.ws(), n)
        .andExpect(jsonPath("$.fields[?(@.field == 'priority')].before").value("URGENT"))
        .andExpect(jsonPath("$.fields[?(@.field == 'priority')].after").value("LOW"));
    api.get(s.owner(), ISSUE + "/revisions", s.ws(), n).andExpect(jsonPath("$.items[0].changedFields[0]").value("priority"));

    // 비우면(null) 보통 · 소문자도 받는다 · 모르는 값은 400
    Map<String, Object> clear = new HashMap<>();
    clear.put("priority", null);
    patch(s, n, clear);
    api.get(s.owner(), ISSUE, s.ws(), n).andExpect(jsonPath("$.priority").value("NORMAL"));
    patch(s, n, Map.of("priority", "high"));
    api.get(s.owner(), ISSUE, s.ws(), n).andExpect(jsonPath("$.priority").value("HIGH"));
    int version = TestApi.read(api.get(s.owner(), ISSUE, s.ws(), n), "$.rowVersion");
    api.patch(s.owner(), ISSUE, Map.of("rowVersion", version, "priority", "ASAP"), s.ws(), n)
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ISSUE_PRIORITY"));
    // 같은 값으로 고치면 이력이 늘지 않는다
    int lines = TestApi.<List<Object>>read(api.get(s.owner(), ISSUE, s.ws(), n), "$.journal").size();
    patch(s, n, Map.of("priority", "HIGH"));
    assertThat(TestApi.<List<Object>>read(api.get(s.owner(), ISSUE, s.ws(), n), "$.journal")).hasSize(lines);
  }

  @Test
  void 목록은_우선순위로_거르고_긴급부터_정렬한다() throws Exception {
    Space s = space();
    int low = issue(s, "낮음", "LOW");
    int urgentOld = issue(s, "긴급 1", "URGENT");
    int normal = issue(s, "보통", null);
    int high = issue(s, "높음", "HIGH");
    int urgentNew = issue(s, "긴급 2", "URGENT");

    // 여러 값 — 프로젝트 목록 · 워크스페이스 목록 같다
    List<Integer> picked = TestApi.read(api.get(s.owner(), ISSUES + "?priority=URGENT&priority=HIGH", s.ws(), P),
        "$.items[*].number");
    assertThat(picked).containsExactly(urgentNew, high, urgentOld);
    List<Integer> lowOnly = TestApi.read(api.get(s.owner(), "/workspaces/{ws}/issues?priority=LOW", s.ws()), "$.items[*].number");
    assertThat(lowOnly).containsExactly(low);
    api.get(s.owner(), ISSUES + "?priority=SOON", s.ws(), P).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_FILTER"));

    // 우선순위순 — 긴급 → 높음 → 보통 → 낮음, 같으면 최신(번호 큰 것) 먼저. -priority 는 거꾸로
    List<Integer> sorted = TestApi.read(api.get(s.owner(), ISSUES + "?sort=priority", s.ws(), P), "$.items[*].number");
    assertThat(sorted).containsExactly(urgentNew, urgentOld, high, normal, low);
    List<Integer> ws = TestApi.read(api.get(s.owner(), "/workspaces/{ws}/issues?sort=priority", s.ws()), "$.items[*].number");
    assertThat(ws).containsExactly(urgentNew, urgentOld, high, normal, low);
    List<Integer> reversed = TestApi.read(api.get(s.owner(), ISSUES + "?sort=-priority", s.ws(), P), "$.items[*].number");
    assertThat(reversed).containsExactly(low, normal, high, urgentNew, urgentOld);
    // 목록 줄에도 우선순위
    api.get(s.owner(), ISSUES + "?sort=priority", s.ws(), P).andExpect(jsonPath("$.items[0].priority").value("URGENT"))
        .andExpect(jsonPath("$.items[3].priority").value("NORMAL"));
  }
}
