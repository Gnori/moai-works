package com.moai.works.api.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * 공수 집계 — 예상 공수 대비 실제 투입 공수 (FR-ISS-017, 2026-11-22 사용자: "나중에 공수 정리 및 실제 투입 공수는 소요시간으로 뽑아야 해").
 *
 * <pre>
 * ea (모두 읽음)   #1 새기능 예상 10 — alice 09-01 3 · alice 09-03 2 · bob 09-03 4  = 9
 *                  #2 버그   예상 없음 — bob 09-08 1.5                               = 1.5
 * eb (모두 읽음)   #1 새기능 "=SUM(1+1)" 예상 4 — alice 10-01 5 (+ 10-02 1 은 지움)  = 5
 *                  #2 테스트 예상 3 — alice 09-02 2, 그 뒤 일감을 지움                 = 빠짐
 * er (지정 멤버)   #1 기술조사 예상 8 — bob 09-04 6                                   = 6   (carol 은 독자가 아님)
 * </pre>
 * 날짜는 모두 2025 년(일한 날은 오늘까지만 적을 수 있다). 2025-09-01 은 월요일이다.
 */
@ApiTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EffortTest {

  @Autowired
  TestApi api;

  TestUser owner;
  TestUser alice;
  TestUser bob;
  TestUser carol;
  TestUser stranger;
  String ws;

  static final String EFFORT = "/workspaces/{ws}/effort";
  static final String ISSUES = "/workspaces/{ws}/projects/{project}/issues";

  @BeforeAll
  void setUp() throws Exception {
    owner = api.user("effort-owner");
    alice = api.user("alice");
    bob = api.user("bob");
    carol = api.user("carol");
    stranger = api.user("stranger");
    ws = api.teamWorkspace(owner, "공수 시험");
    for (TestUser u : List.of(alice, bob, carol)) {
      api.join(ws, owner, u, "member");
    }
    project("ea", "에이", "WORKSPACE");
    project("eb", "비", "WORKSPACE");
    project("er", "제한", "RESTRICTED");
    role("ea", alice);
    role("ea", bob);
    role("eb", alice);
    role("er", bob);

    int ea1 = issue("ea", Map.of("type", "feature", "title", "로그인", "estimatedHours", 10));
    log("ea", ea1, alice, "2025-09-01", 3);
    log("ea", ea1, alice, "2025-09-03", 2);
    log("ea", ea1, bob, "2025-09-03", 4);
    int ea2 = issue("ea", Map.of("type", "bug", "title", "버그"));
    log("ea", ea2, bob, "2025-09-08", 1.5);

    int eb1 = issue("eb", Map.of("type", "feature", "title", "=SUM(1+1)", "estimatedHours", 4));
    log("eb", eb1, alice, "2025-10-01", 5);
    log("eb", eb1, alice, "2025-10-02", 1);
    String removed = TestApi.read(api.get(owner, "/workspaces/{ws}/issues/{n}", ws, eb1), "$.workLogs[0].id");
    api.delete(alice, "/workspaces/{ws}/issues/{n}/work-logs/{id}", ws, eb1, removed).andExpect(status().isOk());
    int eb2 = issue("eb", Map.of("type", "test", "title", "지울 일감", "estimatedHours", 3));
    log("eb", eb2, alice, "2025-09-02", 2);
    api.delete(owner, "/workspaces/{ws}/issues/{n}", ws, eb2).andExpect(status().isNoContent());

    int er1 = issue("er", Map.of("type", "research", "title", "조사", "estimatedHours", 8));
    log("er", er1, bob, "2025-09-04", 6);
  }

  private void project(String key, String name, String visibility) throws Exception {
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", key, "name", name, "visibility", visibility), ws)
        .andExpect(status().isCreated());
  }

  private void role(String project, TestUser user) throws Exception {
    api.put(owner, "/workspaces/{ws}/projects/{project}/roles/{user}", Map.of("roles", List.of("project-editor")), ws,
        project, user.id()).andExpect(status().isOk());
  }

  private int issue(String project, Map<String, Object> body) throws Exception {
    return TestApi.<Integer>read(api.post(owner, ISSUES, body, ws, project).andExpect(status().isCreated()), "$.number");
  }

  private void log(String project, int number, TestUser by, String date, double hours) throws Exception {
    api.post(by, "/workspaces/{ws}/issues/{n}/work-logs", Map.of("workDate", date, "hours", hours), ws, number)
        .andExpect(status().isOk());
  }

  private ResultActions effort(TestUser as, String query) throws Exception {
    return api.get(as, EFFORT + "?" + query, ws).andExpect(status().isOk());
  }

  private static List<String> keys(ResultActions r) throws Exception {
    return TestApi.read(r, "$.rows[*].key");
  }

  private static BigDecimal sum(ResultActions r, String path) throws Exception {
    List<Number> values = TestApi.read(r, path);
    return values.stream().map(v -> new BigDecimal(v.toString())).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  // ── 합계 ─────────────────────────────────────────────────────────────

  @Test
  void 프로젝트_사람_기간으로_모은_합계가_기록의_합과_같다() throws Exception {
    // 프로젝트 — 실제가 많은 것부터. 지운 기록 · 지운 일감의 기록은 빠지고, 지운 기록이 있었다는 수는 남는다
    ResultActions byProject = effort(owner, "groupBy=project")
        .andExpect(jsonPath("$.groupBy").value("project"))
        .andExpect(jsonPath("$.rows.length()").value(3))
        .andExpect(jsonPath("$.rows[0].key").value("ea")).andExpect(jsonPath("$.rows[0].actualHours").value(10.5))
        .andExpect(jsonPath("$.rows[0].logCount").value(4)).andExpect(jsonPath("$.rows[0].issueCount").value(2))
        .andExpect(jsonPath("$.rows[1].key").value("er")).andExpect(jsonPath("$.rows[1].actualHours").value(6))
        .andExpect(jsonPath("$.rows[2].key").value("eb")).andExpect(jsonPath("$.rows[2].actualHours").value(5))
        .andExpect(jsonPath("$.total.actualHours").value(21.5)).andExpect(jsonPath("$.total.logCount").value(6))
        .andExpect(jsonPath("$.total.issueCount").value(4)).andExpect(jsonPath("$.total.personCount").value(2))
        .andExpect(jsonPath("$.removedLogs").value(1));
    assertThat(sum(byProject, "$.rows[*].actualHours")).isEqualByComparingTo("21.5");

    // 사람 — 사람 · 기간으로 모으면 줄에 예상이 없다(예상은 일감에 하나)
    ResultActions byPerson = effort(owner, "groupBy=person")
        .andExpect(jsonPath("$.rows.length()").value(2))
        .andExpect(jsonPath("$.rows[0].person.id").value(bob.id().toString()))
        .andExpect(jsonPath("$.rows[0].actualHours").value(11.5)).andExpect(jsonPath("$.rows[0].issueCount").value(3))
        .andExpect(jsonPath("$.rows[1].person.id").value(alice.id().toString()))
        .andExpect(jsonPath("$.rows[1].actualHours").value(10)).andExpect(jsonPath("$.rows[1].logCount").value(3))
        .andExpect(jsonPath("$.rows[0].estimatedHours").doesNotExist())
        .andExpect(jsonPath("$.total.actualHours").value(21.5));
    assertThat(sum(byPerson, "$.rows[*].actualHours")).isEqualByComparingTo("21.5");

    // 주(월요일부터) · 날 · 달 — 기간 안의 기록만, 앞날부터
    effort(owner, "groupBy=week&from=2025-09-01&to=2025-09-30")
        .andExpect(jsonPath("$.rows.length()").value(2))
        .andExpect(jsonPath("$.rows[0].period").value("2025-09-01")).andExpect(jsonPath("$.rows[0].periodEnd").value("2025-09-07"))
        .andExpect(jsonPath("$.rows[0].actualHours").value(15)).andExpect(jsonPath("$.rows[0].logCount").value(4))
        .andExpect(jsonPath("$.rows[1].period").value("2025-09-08")).andExpect(jsonPath("$.rows[1].actualHours").value(1.5))
        .andExpect(jsonPath("$.total.actualHours").value(16.5))
        .andExpect(jsonPath("$.from").value("2025-09-01")).andExpect(jsonPath("$.to").value("2025-09-30"));
    ResultActions byDay = effort(owner, "groupBy=day&from=2025-09-01&to=2025-09-30")
        .andExpect(jsonPath("$.rows[1].actualHours").value(6));
    assertThat(keys(byDay)).containsExactly("2025-09-01", "2025-09-03", "2025-09-04", "2025-09-08");
    assertThat(sum(byDay, "$.rows[*].actualHours")).isEqualByComparingTo("16.5");
    ResultActions byMonth = effort(owner, "groupBy=month")
        .andExpect(jsonPath("$.rows[0].actualHours").value(16.5)).andExpect(jsonPath("$.rows[1].actualHours").value(5))
        .andExpect(jsonPath("$.rows[1].periodEnd").value("2025-10-31"));
    assertThat(keys(byMonth)).containsExactly("2025-09", "2025-10");
    // 하루만
    effort(owner, "groupBy=person&from=2025-09-03&to=2025-09-03").andExpect(jsonPath("$.total.actualHours").value(6));
  }

  @Test
  void 사람_유형_조건으로_거른다() throws Exception {
    // me = 부른 사람. 고르기 목록(people)은 사람 조건을 빼고 본 범위 — 다른 사람도 그대로 있다
    effort(alice, "groupBy=project&person=me")
        .andExpect(jsonPath("$.total.actualHours").value(10))
        .andExpect(jsonPath("$.people.length()").value(2));
    effort(owner, "groupBy=issue&person=" + bob.id()).andExpect(jsonPath("$.total.actualHours").value(11.5));
    assertThat(keys(effort(owner, "groupBy=type"))).containsExactly("feature", "research", "bug");
    ResultActions features = effort(owner, "groupBy=issue&type=feature").andExpect(jsonPath("$.total.actualHours").value(14));
    assertThat(keys(features)).containsExactly("ea#1", "eb#3");
  }

  // ── 예상 대비 실제 ───────────────────────────────────────────────────

  @Test
  void 예상_대비_실제가_일감_프로젝트별로_맞다() throws Exception {
    effort(owner, "groupBy=issue&project=ea")
        .andExpect(jsonPath("$.rows[0].key").value("ea#1"))
        .andExpect(jsonPath("$.rows[0].issue.number").value(1)).andExpect(jsonPath("$.rows[0].issue.title").value("로그인"))
        .andExpect(jsonPath("$.rows[0].estimatedHours").value(10)).andExpect(jsonPath("$.rows[0].actualHours").value(9))
        .andExpect(jsonPath("$.rows[0].diffHours").value(-1)).andExpect(jsonPath("$.rows[0].ratio").value(90))
        // 예상이 없는 일감은 차이 · 비율도 없다
        .andExpect(jsonPath("$.rows[1].key").value("ea#2"))
        .andExpect(jsonPath("$.rows[1].estimatedHours").doesNotExist()).andExpect(jsonPath("$.rows[1].ratio").doesNotExist())
        .andExpect(jsonPath("$.total.estimatedHours").value(10)).andExpect(jsonPath("$.total.actualHours").value(10.5));

    // 프로젝트 = 그 프로젝트에서 결과에 든 일감의 예상을 한 번씩 더한 것 (ea#1 은 기록이 셋이어도 10 한 번)
    effort(owner, "groupBy=project")
        .andExpect(jsonPath("$.rows[0].key").value("ea"))
        .andExpect(jsonPath("$.rows[0].estimatedHours").value(10)).andExpect(jsonPath("$.rows[0].diffHours").value(0.5))
        .andExpect(jsonPath("$.rows[0].ratio").value(105))
        .andExpect(jsonPath("$.rows[1].key").value("er"))
        .andExpect(jsonPath("$.rows[1].estimatedHours").value(8)).andExpect(jsonPath("$.rows[1].diffHours").value(-2))
        .andExpect(jsonPath("$.rows[1].ratio").value(75))
        .andExpect(jsonPath("$.rows[2].key").value("eb"))
        .andExpect(jsonPath("$.rows[2].estimatedHours").value(4)).andExpect(jsonPath("$.rows[2].ratio").value(125))
        // 합계 — 예상 22(10 + 4 + 8, 지운 일감의 3 은 빠짐) 대비 실제 21.5
        .andExpect(jsonPath("$.total.estimatedHours").value(22)).andExpect(jsonPath("$.total.diffHours").value(-0.5))
        .andExpect(jsonPath("$.total.ratio").value(98));

    // 기간을 좁히면 그 기간에 기록이 있는 일감의 예상만 든다
    effort(owner, "groupBy=project&from=2025-10-01").andExpect(jsonPath("$.rows.length()").value(1))
        .andExpect(jsonPath("$.total.estimatedHours").value(4)).andExpect(jsonPath("$.total.actualHours").value(5));
  }

  // ── 범위 ─────────────────────────────────────────────────────────────

  @Test
  void 독자가_아닌_프로젝트의_기록은_집계에_들어가지_않는다() throws Exception {
    // carol 은 er 의 독자가 아니다 — er 의 6시간이 없고, 고르기 목록에도 er 이 없다
    ResultActions carolView = effort(carol, "groupBy=project").andExpect(jsonPath("$.total.actualHours").value(15.5));
    assertThat(keys(carolView)).containsExactly("ea", "eb");
    assertThat(TestApi.<List<String>>read(carolView, "$.projects[*].key")).containsExactlyInAnyOrder("ea", "eb");
    effort(carol, "groupBy=person").andExpect(jsonPath("$.total.actualHours").value(15.5));
    // 조건으로 줘도 없는 프로젝트와 같은 404 — 있는지 숨긴다
    api.get(carol, EFFORT + "?project=er", ws).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    api.get(carol, EFFORT + "?project=nope", ws).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    // er 의 독자(bob)는 본다
    effort(bob, "project=er").andExpect(jsonPath("$.total.actualHours").value(6));
    // 워크스페이스 멤버가 아니면 404
    api.get(stranger, EFFORT, ws).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    api.get(stranger, EFFORT + ".csv", ws).andExpect(status().isNotFound());
  }

  @Test
  void 틀린_조건은_400_이다() throws Exception {
    for (String q : List.of("from=2025-13-01", "from=2025-10-02&to=2025-10-01", "groupBy=year", "person=someone",
        "type=nope")) {
      api.get(owner, EFFORT + "?" + q, ws).andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_FILTER"));
    }
  }

  // ── CSV ──────────────────────────────────────────────────────────────

  @Test
  void CSV_는_화면_결과와_같고_내려받기가_감사에_남는다() throws Exception {
    String query = "groupBy=issue&from=2025-09-01&to=2025-12-31";
    ResultActions json = effort(owner, query);
    ResultActions csv = api.get(owner, EFFORT + ".csv?" + query, ws).andExpect(status().isOk())
        .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")))
        .andExpect(header().string("Content-Disposition",
            org.hamcrest.Matchers.containsString("effort-issue-2025-09-01-2025-12-31.csv")))
        .andExpect(header().string("Cache-Control", "no-store"));
    String body = csv.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertThat(body).startsWith("﻿");
    List<String> lines = List.of(body.substring(1).split("\r\n"));
    assertThat(lines.get(0)).isEqualTo("일감,이름,예상 공수(시간),실제 투입(시간),차이(시간),비율(%),일감 수,기록 수");

    // 줄마다 화면의 값과 같다 — 기준 · 예상 · 실제 · 차이 · 비율 · 일감 수 · 기록 수
    List<Map<String, Object>> rows = TestApi.read(json, "$.rows");
    assertThat(lines).hasSize(rows.size() + 2);
    for (int i = 0; i < rows.size(); i++) {
      String[] cells = lines.get(i + 1).split(",", -1);
      Map<String, Object> row = rows.get(i);
      assertThat(cells[0]).isEqualTo(row.get("key"));
      assertThat(number(cells[2])).isEqualTo(number(row.get("estimatedHours")));
      assertThat(number(cells[3])).isEqualTo(number(row.get("actualHours")));
      assertThat(number(cells[4])).isEqualTo(number(row.get("diffHours")));
      assertThat(cells[5]).isEqualTo(row.get("ratio") == null ? "" : row.get("ratio").toString());
      assertThat(cells[6]).isEqualTo(row.get("issueCount").toString());
      assertThat(cells[7]).isEqualTo(row.get("logCount").toString());
    }
    // 남이 쓴 제목이 수식으로 실행되지 않게 앞에 ' 를 붙인다
    assertThat(lines).anySatisfy(l -> assertThat(l).startsWith("eb#3,'=SUM(1+1),4,5,1,125,1,1"));
    // 맨 끝은 합계
    Map<String, Object> total = TestApi.read(json, "$.total");
    assertThat(lines.get(lines.size() - 1)).isEqualTo("합계,," + plain(total.get("estimatedHours")) + ","
        + plain(total.get("actualHours")) + "," + plain(total.get("diffHours")) + "," + total.get("ratio") + ","
        + total.get("issueCount") + "," + total.get("logCount"));

    // 영어 머리 줄
    String en = api.get(owner, EFFORT + ".csv?groupBy=person&lang=en", ws).andExpect(status().isOk()).andReturn()
        .getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertThat(en.substring(1)).startsWith("Person,Name,Estimated (h),Actual (h),Difference (h),Ratio (%),Issues,Logs\r\n");
    assertThat(en).contains("\r\nTotal,,22,21.5,-0.5,98,4,6\r\n");

    // 독자가 아닌 프로젝트는 CSV 에도 없다
    String carolCsv = api.get(carol, EFFORT + ".csv?groupBy=project", ws).andExpect(status().isOk()).andReturn()
        .getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertThat(carolCsv).doesNotContain("\r\ner,").contains("\r\n합계,,14,15.5,");

    // 내려받기는 감사에 남는다 — 누가 · 조건 · 줄 수. 화면 보기(JSON)는 남기지 않는다
    List<Map<String, Object>> audit = TestApi.read(
        api.get(owner, "/workspaces/{ws}/audit?action=issue.work_log.export&limit=50", ws).andExpect(status().isOk()), "$");
    assertThat(audit).hasSize(3);
    assertThat(audit).anySatisfy(a -> {
      assertThat(a.get("actorId")).isEqualTo(owner.id().toString());
      String details = a.get("details").toString().replace(" ", "");
      assertThat(details).contains("\"groupBy\":\"issue\"").contains("\"from\":\"2025-09-01\"")
          .contains("\"rows\":" + rows.size());
    });
    assertThat(audit).anySatisfy(a -> assertThat(a.get("actorId")).isEqualTo(carol.id().toString()));
  }

  private static BigDecimal number(Object value) {
    if (value == null || value.toString().isEmpty()) {
      return null;
    }
    return new BigDecimal(value.toString()).stripTrailingZeros();
  }

  private static String plain(Object value) {
    return value == null ? "" : new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
  }
}
