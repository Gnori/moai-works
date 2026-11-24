package com.moai.works.api.mcp;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.McpTestClient;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * 도구만 보고도 일감을 바르게 다루게 — create_issue · update_issue · get_issue · log_work 결과의 summary(핵심 값) · checks(빠진 것 — 막지 않는다) ·
 * next(다음 할 일), 설명 · 이력 빼기(include), 보류 · 취소의 reason, 도구 설명. 2026-11-24, 일감 #182.
 */
@ApiTest
class McpGuideTest {

  private static final String P = "guide";
  private static final String MODULES = "/workspaces/{ws}/projects/{p}/modules";
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final String TODAY = LocalDate.now(SEOUL).toString();

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;

  private record Space(String ws, TestUser owner, TestUser curator) {
  }

  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "길잡이");
    TestUser curator = api.user("curator");
    api.join(ws, owner, curator, "curator");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "길잡이", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    return new Space(ws, owner, curator);
  }

  private McpTestClient client(TestUser who) throws Exception {
    return new McpTestClient(mvc, TestApi.read(api.post(who, "/me/mcp-keys", Map.of("name", "길잡이 키"))
        .andExpect(status().isCreated()), "$.key"));
  }

  private static Map<String, Object> args(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  private static List<String> codes(String result) {
    return read(result, "$.checks[*].code");
  }

  private static String check(String result, String code, String field) {
    List<Object> found = read(result, "$.checks[?(@.code == '" + code + "')]." + field);
    assertThat(found).as(code + " 이 있어야 한다").hasSize(1);
    return String.valueOf(found.get(0));
  }

  @Test
  void 결과_맨_위에_summary_checks_next_가_있고_설명과_이력은_include_로만_준다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    String created = mcp.call("create_issue", args("project", P, "type", "feature", "title", "요약",
        "description", "## 배경\n왜\n## 요구사항\n- [ ] 한다", "dueDate", TODAY, "agentSession", "moai-works"));
    // 키 차례 — summary · checks · next 가 맨 앞
    assertThat(created).startsWith("{\"summary\":");
    assertThat(created.indexOf("\"checks\"")).isLessThan(created.indexOf("\"next\""));
    assertThat(created.indexOf("\"next\"")).isLessThan(created.indexOf("\"templateCheck\""));
    int n = read(created, "$.summary.number");
    assertThat((String) read(created, "$.summary.project")).isEqualTo(P);
    assertThat((String) read(created, "$.summary.status")).isEqualTo("NEW");
    assertThat((Integer) read(created, "$.summary.rowVersion")).isEqualTo((Integer) read(created, "$.rowVersion"));
    assertThat((String) read(created, "$.summary.dueDate")).isEqualTo(TODAY);
    assertThat((String) read(created, "$.summary.agentSession")).isEqualTo("moai-works");
    assertThat((List<String>) read(created, "$.summary.modules")).isEmpty();
    assertThat((String) read(created, "$.summary.statusChangedAt")).isEqualTo(read(created, "$.statusChangedAt"));
    assertThat((Object) read(created, "$.summary.lastWorkLogAt")).isNull();
    assertThat((String) read(created, "$.summary.serverTime")).endsWith("+09:00");
    assertThat((List<String>) read(created, "$.next")).isNotEmpty().hasSizeLessThanOrEqualTo(3)
        .anyMatch(l -> l.contains("IN_PROGRESS"));
    // 설명 · 이력은 빼고 omitted 에 — 나머지 값(번호 · 날짜 · 사람)은 그대로
    assertThat((Map<String, Object>) read(created, "$")).doesNotContainKeys("description", "journal");
    assertThat((List<String>) read(created, "$.omitted")).containsExactly("description", "journal");
    assertThat((Integer) read(created, "$.journalCount")).isEqualTo(1);
    assertThat((Integer) read(created, "$.number")).isEqualTo(n);
    assertThat((String) read(created, "$.manager.id")).isEqualTo(s.curator().id().toString());

    // include 로 받는다 — 글 하나도 배열도
    String withJournal = mcp.call("update_issue", args("number", n, "rowVersion", read(created, "$.rowVersion"), "title", "요약 2",
        "include", "journal"));
    assertThat((List<Object>) read(withJournal, "$.journal")).hasSize(2);
    assertThat((List<String>) read(withJournal, "$.omitted")).containsExactly("description");
    String both = mcp.call("update_issue", args("number", n, "rowVersion", read(withJournal, "$.summary.rowVersion"),
        "title", "요약 3", "include", List.of("description", "journal")));
    assertThat((String) read(both, "$.description")).contains("## 배경");
    assertThat((List<String>) read(both, "$.omitted")).isEmpty();
    String wrong = mcp.call("update_issue", args("number", n, "rowVersion", read(both, "$.summary.rowVersion"), "include", "history"));
    assertThat((String) read(wrong, "$.error.code")).isEqualTo("INVALID_ARGUMENT");
    assertThat((String) read(wrong, "$.error.field")).isEqualTo("include");

    // get_issue 는 늘 전체 — 맨 위에 같은 summary · checks · next
    String got = mcp.call("get_issue", args("number", n));
    assertThat(got).startsWith("{\"summary\":");
    assertThat((String) read(got, "$.summary.title")).isEqualTo("요약 3");
    assertThat((List<Object>) read(got, "$.journal")).hasSize(3);
    assertThat((String) read(got, "$.description")).contains("## 요구사항");
    assertThat((List<String>) read(got, "$.next")).isNotEmpty();
    assertThat(codes(got)).isNotNull();
  }

  @Test
  void 등록_진행_결과의_빠진_값을_checks_로_알리고_채우면_사라진다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    // 모두 빠진 등록
    String bare = mcp.call("create_issue", args("project", P, "type", "bug", "title", "빈 버그", "description", "## 현상\n안 된다"));
    assertThat(codes(bare)).contains("AGENT_SESSION_MISSING", "DUE_DATE_MISSING", "TEMPLATE_SECTIONS_MISSING")
        .doesNotContain("START_DATE_MISSING", "PLANNED_END_MISSING", "ESTIMATE_MISSING", "MODULES_MISSING", "WORK_LOG_MISSING",
            "RESULT_SECTION_EMPTY", "UNCHECKED_ITEMS");
    assertThat(check(bare, "AGENT_SESSION_MISSING", "message")).contains("프로젝트 key 가 아님");
    assertThat(check(bare, "DUE_DATE_MISSING", "level")).isEqualTo("WARN");
    // 템플릿의 빠진 항목 — 예전 templateCheck 도 그대로
    assertThat((List<String>) read(bare, "$.checks[?(@.code == 'TEMPLATE_SECTIONS_MISSING')].items[*]"))
        .contains("재현 절차", "확인 방법");
    assertThat((List<String>) read(bare, "$.templateCheck.missing")).contains("재현 절차", "확인 방법");
    assertThat((List<String>) read(bare, "$.next")).anyMatch(l -> l.contains("checks 의 빠진 것") && l.contains("rowVersion="));

    // 진행으로 — 시작일 · 완료 예정일 · 예상 공수가 빠졌다(예상 공수는 INFO)
    String started = mcp.call("update_issue", args("number", read(bare, "$.summary.number"), "rowVersion",
        read(bare, "$.summary.rowVersion"), "status", "IN_PROGRESS"));
    assertThat(codes(started)).contains("START_DATE_MISSING", "PLANNED_END_MISSING", "ESTIMATE_MISSING")
        .doesNotContain("WORK_LOG_MISSING"); // 등록 구간은 작업 시간을 묻지 않는다
    assertThat(check(started, "ESTIMATE_MISSING", "level")).isEqualTo("INFO");

    // 모두 채우면 사라진다
    String full = "## 현상\n안 된다\n## 재현 절차\n1. 연다\n## 기대 결과\n된다\n## 실제 결과\n안 된다\n## 확인 방법\n- [ ] 된다";
    String filled = mcp.call("update_issue", args("number", read(started, "$.summary.number"), "rowVersion",
        read(started, "$.summary.rowVersion"), "description", full, "agentSession", "moai-works", "dueDate", TODAY,
        "startDate", TODAY, "plannedEndDate", TODAY, "estimatedHours", 1));
    assertThat(codes(filled)).isEmpty();
    assertThat((List<String>) read(filled, "$.next")).singleElement().asString().contains("log_work");
  }

  @Test
  void 프로젝트에_쓰는_모듈이_있으면_빠진_관련_모듈을_알린다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    String none = mcp.call("create_issue", args("project", P, "type", "research", "title", "모듈 없는 프로젝트"));
    assertThat(codes(none)).doesNotContain("MODULES_MISSING");

    api.post(s.owner(), MODULES, Map.of("key", "api", "name", "API"), s.ws(), P).andExpect(status().isCreated());
    api.post(s.owner(), MODULES, Map.of("key", "docs", "name", "문서"), s.ws(), P).andExpect(status().isCreated());
    api.post(s.owner(), MODULES, Map.of("key", "old", "name", "옛것"), s.ws(), P).andExpect(status().isCreated());
    api.patch(s.owner(), MODULES + "/old", Map.of("active", false), s.ws(), P).andExpect(status().isOk());
    String missing = mcp.call("create_issue", args("project", P, "type", "research", "title", "모듈 빠짐"));
    assertThat((List<String>) read(missing, "$.checks[?(@.code == 'MODULES_MISSING')].items[*]")).containsExactly("api", "docs");
    String with = mcp.call("create_issue", args("project", P, "type", "research", "title", "모듈 있음", "modules", "api"));
    assertThat(codes(with)).doesNotContain("MODULES_MISSING");
    assertThat((List<String>) read(with, "$.summary.modules")).containsExactly("api");
  }

  @Test
  void 진행이나_테스트_구간에_작업_시간을_적지_않고_옮기면_WORK_LOG_MISSING_이_구간_시작을_알린다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    McpTestClient owner = client(s.owner());
    String created = mcp.call("create_issue", args("project", P, "type", "feature", "title", "구간"));
    int n = read(created, "$.summary.number");
    String started = mcp.call("update_issue", args("number", n, "rowVersion", read(created, "$.summary.rowVersion"),
        "status", "IN_PROGRESS"));
    String startedAt = read(started, "$.summary.statusChangedAt");
    // 다른 사람의 기록은 키 주인의 기록이 아니다
    owner.call("log_work", args("number", n, "workDate", TODAY, "hours", 0.01));
    String testing = mcp.call("update_issue", args("number", n, "rowVersion", read(started, "$.summary.rowVersion"),
        "status", "TESTING"));
    String message = check(testing, "WORK_LOG_MISSING", "message");
    String startLocal = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(SEOUL)
        .format(java.time.Instant.parse(startedAt));
    assertThat(message).contains("진행(IN_PROGRESS)").contains(startLocal).contains("log_work");
    assertThat((List<String>) read(testing, "$.next")).first().asString().contains("log_work");

    // 구간 안에 적었으면 없다
    mcp.call("log_work", args("number", n, "workDate", TODAY, "hours", 0.01, "note", "확인"));
    String done = mcp.call("update_issue", args("number", n, "rowVersion", read(testing, "$.summary.rowVersion"),
        "status", "DONE"));
    assertThat(codes(done)).doesNotContain("WORK_LOG_MISSING");
    // 상태를 옮기지 않는 고치기에는 없다
    String renamed = mcp.call("update_issue", args("number", n, "rowVersion", read(done, "$.summary.rowVersion"), "title", "구간 2"));
    assertThat(codes(renamed)).doesNotContain("WORK_LOG_MISSING");
  }

  @Test
  void 완료하면_비어_있는_결과_절과_체크하지_않은_항목을_알리고_종료_취소에는_묻지_않는다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    // 테스트 유형 — 결과 절은 '테스트 결과'. 템플릿의 안내 글 그대로면 빈 것
    String description = "## 목적\n본다\n## 대상\nAPI\n## 테스트 항목\n- [ ] 첫 항목\n- [x] 둘째 항목\n- [ ] 셋째, 쉼표\n\n## 테스트 결과 (선택)\n";
    String created = mcp.call("create_issue", args("project", P, "type", "test", "title", "시험", "description", description,
        "dueDate", TODAY, "agentSession", "moai-works"));
    int n = read(created, "$.summary.number");
    String done = mcp.call("update_issue", args("number", n, "rowVersion", read(created, "$.summary.rowVersion"), "status", "DONE",
        "startDate", TODAY, "plannedEndDate", TODAY));
    assertThat(check(done, "RESULT_SECTION_EMPTY", "message")).contains("'## 테스트 결과' 절이 비어 있습니다")
        .contains("setSection");
    assertThat((List<String>) read(done, "$.checks[?(@.code == 'UNCHECKED_ITEMS')].items[*]"))
        .containsExactly("첫 항목", "셋째, 쉼표");
    assertThat(check(done, "UNCHECKED_ITEMS", "message")).contains("2개").doesNotContain("첫 항목");
    assertThat((List<String>) read(done, "$.next")).last().asString().contains("CLOSED").contains("사람");

    // 절 · 체크를 채우면 사라진다
    String fixed = mcp.call("update_issue", args("number", n, "rowVersion", read(done, "$.summary.rowVersion"),
        "setSection", Map.of("heading", "테스트 결과", "content", "모두 통과"), "checkItems", "all"));
    assertThat(codes(fixed)).doesNotContain("RESULT_SECTION_EMPTY", "UNCHECKED_ITEMS");

    // 절이 아예 없으면 '없습니다', 안내 글 그대로(버그 템플릿)도 빈 것
    String bug = mcp.call("create_issue", args("project", P, "type", "bug", "title", "결과 없음", "description",
        "## 현상\n가\n## 확인 방법\n- [x] 됨\n## 처리 결과 (선택)\n완료할 때 채운다 — 원인 · 고친 것 · 커밋 · 확인한 것(화면 캡처) · 남은 것",
        "status", "DONE"));
    assertThat(check(bug, "RESULT_SECTION_EMPTY", "message")).contains("'## 처리 결과' 절이 비어 있습니다");
    String research = mcp.call("create_issue", args("project", P, "type", "research", "title", "결론 없음", "status", "DONE"));
    assertThat(check(research, "RESULT_SECTION_EMPTY", "message")).contains("'## 결론' 절이 없습니다");

    // 종료(사람이 확인함) · 취소에는 채울 것을 묻지 않는다
    String closed = mcp.call("update_issue", args("number", read(bug, "$.summary.number"), "rowVersion",
        read(bug, "$.summary.rowVersion"), "status", "CLOSED"));
    assertThat(codes(closed)).isEmpty();
    String cancelled = mcp.call("update_issue", args("number", read(research, "$.summary.number"), "rowVersion",
        read(research, "$.summary.rowVersion"), "status", "CANCELLED", "reason", "중복"));
    assertThat(codes(cancelled)).isEmpty();
    assertThat((List<String>) read(cancelled, "$.next")).last().asString().contains("delete_issue");
  }

  @Test
  void 보류_취소의_까닭은_reason_으로도_되고_comment_와_함께_주면_이어_붙인다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    String a = mcp.call("create_issue", args("project", P, "type", "feature", "title", "멈춤"));
    int n = read(a, "$.summary.number");
    String held = mcp.call("update_issue", args("number", n, "rowVersion", read(a, "$.summary.rowVersion"), "status", "ON_HOLD",
        "reason", "사람의 결정을 기다린다", "include", "journal"));
    assertThat((String) read(held, "$.summary.status")).isEqualTo("ON_HOLD");
    assertThat((String) read(held, "$.journal[-1].notes")).isEqualTo("사람의 결정을 기다린다");
    assertThat((List<String>) read(held, "$.next")).last().asString().contains("IN_PROGRESS");

    String back = mcp.call("update_issue", args("number", n, "rowVersion", read(held, "$.summary.rowVersion"), "status", "NEW"));
    String same = mcp.call("update_issue", args("number", n, "rowVersion", read(back, "$.summary.rowVersion"), "status", "ON_HOLD",
        "reason", "같은 글", "comment", " 같은 글 ", "include", "journal"));
    assertThat((String) read(same, "$.journal[-1].notes")).isEqualTo("같은 글");

    String b = mcp.call("create_issue", args("project", P, "type", "feature", "title", "취소"));
    String cancelled = mcp.call("update_issue", args("number", read(b, "$.summary.number"), "rowVersion",
        read(b, "$.summary.rowVersion"), "status", "CANCELLED", "reason", "#" + n + " 과 중복", "comment", "링크도 걸었다",
        "include", "journal"));
    assertThat((String) read(cancelled, "$.journal[-1].notes")).isEqualTo("#" + n + " 과 중복\n\n링크도 걸었다");

    // 둘 다 없으면 그대로 STATUS_REASON_REQUIRED
    String c = mcp.call("create_issue", args("project", P, "type", "feature", "title", "까닭 없음"));
    assertThat((String) read(mcp.call("update_issue", args("number", read(c, "$.summary.number"), "rowVersion",
        read(c, "$.summary.rowVersion"), "status", "ON_HOLD", "reason", " ")), "$.error.code")).isEqualTo("STATUS_REASON_REQUIRED");
  }

  @Test
  void log_work_결과는_note_의_앞날_시각과_구간보다_긴_시간을_알리고_다음_할_일을_준다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    String created = mcp.call("create_issue", args("project", P, "type", "feature", "title", "시간"));
    int n = read(created, "$.summary.number");
    ZonedDateTime now = ZonedDateTime.now(SEOUL);
    DateTimeFormatter hhmm = DateTimeFormatter.ofPattern("HH:mm");

    // 지금 시각 · 짧은 시간 — 아무것도 없다. 상태 · rowVersion · next 가 함께 온다
    String fine = mcp.call("log_work", args("number", n, "workDate", TODAY, "hours", 0.01,
        "note", "확인 — 근거: " + hhmm.format(now) + " 까지"));
    assertThat(codes(fine)).isEmpty();
    assertThat((String) read(fine, "$.status")).isEqualTo("NEW");
    assertThat((Integer) read(fine, "$.rowVersion")).isEqualTo((Integer) read(created, "$.summary.rowVersion"));
    assertThat((String) read(fine, "$.statusChangedAt")).isEqualTo(read(created, "$.summary.statusChangedAt"));
    assertThat((List<String>) read(fine, "$.next")).singleElement().asString().contains("update_issue").contains("rowVersion=");

    // 구간(앞 기록부터 지금)보다 긴 시간
    String over = mcp.call("log_work", args("number", n, "workDate", TODAY, "hours", 3, "note", "많이"));
    String workLogId = read(over, "$.workLogId");
    assertThat(check(over, "HOURS_OVER_ELAPSED", "message")).contains("3시간").contains("delete_work_log(workLogId=" + workLogId);
    assertThat((List<String>) read(over, "$.next")).first().asString().contains("delete_work_log");
    // 지난 날짜는 구간 · 시각을 보지 않는다
    String yesterday = LocalDate.now(SEOUL).minusDays(1).toString();
    assertThat(codes(mcp.call("log_work", args("number", n, "workDate", yesterday, "hours", 5, "note", "23:59 까지")))).isEmpty();

    // note 의 끝 시각이 지금보다 늦다 — 자정 가까이면 앞날 시각을 만들 수 없어 건너뛴다
    Assumptions.assumeTrue(now.toLocalTime().isBefore(LocalTime.of(23, 50)));
    String later = hhmm.format(now.plusMinutes(5));
    String future = mcp.call("log_work", args("number", n, "workDate", TODAY, "hours", 0.01,
        "note", "구현 — 근거: 진행 " + hhmm.format(now) + " → " + later));
    assertThat(check(future, "NOTE_TIME_IN_FUTURE", "message")).contains("note 의 " + later + " 가 지금(")
        .contains("serverTime").contains("delete_work_log");
    assertThat(codes(future)).doesNotContain("HOURS_OVER_ELAPSED");
  }

  @Test
  void 도구_설명과_인자가_상태의_뜻_세션_이름_날짜_reason_include_를_알린다() throws Exception {
    Space s = space();
    String listed = client(s.curator()).toolsJson();
    for (String tool : List.of("create_issue", "update_issue")) {
      String d = ((List<String>) read(listed, "$.result.tools[?(@.name == '" + tool + "')].description")).get(0);
      assertThat(d).contains("DONE 완료", "CLOSED 종료 — 사람이 완료를 확인해 닫음", "CANCELLED 취소", "delete_issue",
          "프로젝트 key 가 아니다", "dueDate", "summary", "checks", "next", "include");
      assertThat((List<Object>) read(listed, "$.result.tools[?(@.name == '" + tool + "')].inputSchema.properties.include"))
          .hasSize(1);
    }
    String update = ((List<String>) read(listed, "$.result.tools[?(@.name == 'update_issue')].description")).get(0);
    assertThat(update).contains("startDate · plannedEndDate", "setSection", "checkItems", "reason");
    assertThat((List<String>) read(listed, "$.result.tools[?(@.name == 'update_issue')].inputSchema.properties.reason.description"))
        .singleElement().asString().contains("comment");
    String log = ((List<String>) read(listed, "$.result.tools[?(@.name == 'log_work')].description")).get(0);
    assertThat(log).contains("0.01", "statusChangedAt", "lastWorkLogAt", "serverTime", "delete_work_log", "NOTE_TIME_IN_FUTURE");
    String search = ((List<String>) read(listed, "$.result.tools[?(@.name == 'search_issues')].description")).get(0);
    assertThat(search).contains("status=open", "status=closed");
    String get = ((List<String>) read(listed, "$.result.tools[?(@.name == 'get_issue')].description")).get(0);
    assertThat(get).contains("summary", "checks", "next");
  }

  @Test
  void 바뀌지_않은_REST_일감_보기에는_길잡이가_없다() throws Exception {
    Space s = space();
    int n = read(client(s.curator()).call("create_issue", args("project", P, "type", "feature", "title", "REST")),
        "$.summary.number");
    api.get(s.owner(), "/workspaces/{ws}/issues/{n}", s.ws(), n).andExpect(status().isOk())
        .andExpect(jsonPath("$.summary").doesNotExist()).andExpect(jsonPath("$.checks").doesNotExist())
        .andExpect(jsonPath("$.journal").isArray()).andExpect(jsonPath("$.description").exists());
  }
}
