package com.moai.works.api.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.moai.works.api.issue.Issue;
import com.moai.works.api.issue.IssueViews;

/**
 * log_work 결과의 검사 — 정한 시각(DB 없이). 한 구간을 여러 줄로 나눠 적어도 잘못 알리지 않고(구간 합 · statusChangedAt 부터),
 * 사람이 한 시간 · 맞는 근거 시각이면 INFO, 등록 일감에 적으면 STARTED_WITHOUT_IN_PROGRESS. 2026-11-24, 일감 #199.
 * 써 보기 3회차(일감 #204): 사람이 한 시간은 기록의 byHuman(log_work 가 인자나 note 의 표시로 정한다), 메시지의 시각은 한국 시간 ISO.
 */
class IssueGuideLogTest {

  /** 2026-11-24 14:00 한국 시간 */
  private static final Instant NOW = Instant.parse("2026-11-24T05:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);
  private static final UUID OWNER = UUID.randomUUID();
  private static final UUID OTHER = UUID.randomUUID();

  private final IssueGuide guide = new IssueGuide(null, null);

  /** byHuman 은 log_work 가 인자를 비웠을 때처럼 note 의 사람 표시로 정한다 */
  private static IssueViews.WorkLog log(UUID user, LocalDate day, String hours, String note, Instant at, boolean deleted) {
    return log(user, day, hours, note, IssueGuide.humanReported(note), at, deleted);
  }

  private static IssueViews.WorkLog log(UUID user, LocalDate day, String hours, String note, boolean byHuman, Instant at,
      boolean deleted) {
    return new IssueViews.WorkLog(UUID.randomUUID(), new IssueViews.Person(user, "u", "u"), day, new BigDecimal(hours), note, "MCP",
        byHuman, at, deleted, deleted ? at : null, null);
  }

  private static IssueViews.WorkLog mine(String hours, String note, Instant at) {
    return log(OWNER, TODAY, hours, note, at, false);
  }

  private static IssueViews.Detail detail(Issue.Status status, Instant statusChangedAt, List<IssueViews.WorkLog> logs) {
    return new IssueViews.Detail(7, new IssueViews.ProjectRef("p", "P"), "feature", "제목", "", status, null, null, null, null, null,
        null, null, null, null, statusChangedAt, statusChangedAt, statusChangedAt, 3, false, 1, null, BigDecimal.ZERO, null, List.of(),
        List.of(), List.of(), List.of(), logs);
  }

  /** 이번 기록(마지막 줄)을 적은 결과의 검사 */
  private IssueGuide.LogGuide logged(Issue.Status status, Instant statusChangedAt, List<IssueViews.WorkLog> before, String hours,
      String note) {
    return logged(status, statusChangedAt, before, mine(hours, note, NOW));
  }

  private IssueGuide.LogGuide logged(Issue.Status status, Instant statusChangedAt, List<IssueViews.WorkLog> before,
      IssueViews.WorkLog now) {
    String hours = now.hours().toPlainString();
    String note = now.note();
    List<IssueViews.WorkLog> logs = new ArrayList<>(before);
    logs.add(now);
    return guide.ofLog(detail(status, statusChangedAt, logs), OWNER, now.id(), TODAY, new BigDecimal(hours), note, NOW);
  }

  private static Map<String, Object> check(IssueGuide.LogGuide g, String code) {
    return g.checks().stream().filter(c -> code.equals(c.get("code"))).findFirst().orElse(null);
  }

  private static Instant minutesAgo(long minutes) {
    return NOW.minus(Duration.ofMinutes(minutes));
  }

  @Test
  void 한_구간을_여러_줄로_나눠_적어도_구간_합이_statusChangedAt_부터보다_짧으면_알리지_않는다() {
    // 진행 40분 전부터 — 하위 Agent 의 만들기 30분(5분 전에 적음) 다음 조정자의 확인 9분
    IssueGuide.LogGuide g = logged(Issue.Status.IN_PROGRESS, minutesAgo(40), List.of(mine("0.5", "만들기", minutesAgo(5))), "0.15",
        "확인");
    assertThat(g.checks()).isEmpty();
    assertThat(g.next()).singleElement().asString().contains("update_issue").contains("rowVersion=3").contains("진행(IN_PROGRESS)");
  }

  @Test
  void 구간_합이_더_길면_WARN_이고_합과_이번_시간을_알린다() {
    IssueGuide.LogGuide g = logged(Issue.Status.IN_PROGRESS, minutesAgo(40), List.of(mine("0.5", "만들기", minutesAgo(5))), "0.5",
        "확인");
    Map<String, Object> c = check(g, "HOURS_OVER_ELAPSED");
    assertThat(c).containsEntry("level", "WARN");
    // 시각은 summary · serverTime 과 같은 한국 시간 ISO(일감 #204)
    assertThat((String) c.get("message")).contains("1시간(이번 0.5시간 포함)").contains("0.67시간")
        .contains("statusChangedAt 2026-11-24T13:20:00.000+09:00 뒤").contains("byHuman=true").contains("사용자가 말한")
        .contains("delete_work_log");
    assertThat(g.next()).first().asString().contains("delete_work_log");
  }

  @Test
  void 남의_기록_지운_기록_다른_날_기록_구간_앞_기록은_합에_넣지_않는다() {
    List<IssueViews.WorkLog> before = List.of(log(OTHER, TODAY, "3", null, minutesAgo(10), false),
        log(OWNER, TODAY, "3", null, minutesAgo(10), true), log(OWNER, TODAY.minusDays(1), "3", null, minutesAgo(10), false),
        mine("3", "앞 구간", minutesAgo(50)));
    assertThat(logged(Issue.Status.IN_PROGRESS, minutesAgo(40), before, "0.5", "확인").checks()).isEmpty();
  }

  @Test
  void 사람이_한_시간이라는_표시가_있으면_INFO() {
    for (String note : List.of("직접 써 봄 — 사용자가 말한 시간 20분", "사람이 한 시험", "Reported by user")) {
      Map<String, Object> c = check(logged(Issue.Status.IN_PROGRESS, minutesAgo(1), List.of(), "0.33", note), "HOURS_OVER_ELAPSED");
      assertThat(c).as(note).containsEntry("level", "INFO");
      assertThat((String) c.get("message")).contains("사람이 한 시간");
    }
    // INFO 만이면 다시 적으라는 다음 줄이 없다
    assertThat(logged(Issue.Status.IN_PROGRESS, minutesAgo(1), List.of(), "0.33", "사용자가 말한 시간").next())
        .noneMatch(l -> l.contains("delete_work_log"));
  }

  @Test
  void 사람의_시간은_저장된_byHuman_으로_정하고_note_의_글보다_앞선다() {
    // 표시 글이 없어도 byHuman=true 면 INFO
    IssueViews.WorkLog human = log(OWNER, TODAY, "0.33", "직접 써 봄", true, NOW, false);
    assertThat(check(logged(Issue.Status.IN_PROGRESS, minutesAgo(1), List.of(), human), "HOURS_OVER_ELAPSED"))
        .containsEntry("level", "INFO");
    // 표시 글이 있어도 byHuman=false 로 적었으면 Agent 의 시간 — WARN
    IssueViews.WorkLog agent = log(OWNER, TODAY, "0.33", "사용자가 말한 시간 20분", false, NOW, false);
    assertThat(check(logged(Issue.Status.IN_PROGRESS, minutesAgo(1), List.of(), agent), "HOURS_OVER_ELAPSED"))
        .containsEntry("level", "WARN");
    // 앞의 사람 기록(byHuman)은 구간 합에서 빠진다
    List<IssueViews.WorkLog> before = List.of(log(OWNER, TODAY, "2", "직접 해 봄", true, minutesAgo(5), false));
    assertThat(logged(Issue.Status.IN_PROGRESS, minutesAgo(40), before, "0.5", "구현").checks()).isEmpty();
  }

  @Test
  void spentHuman_은_지우지_않은_사람의_시간만_더한다() {
    List<IssueViews.WorkLog> logs = List.of(log(OWNER, TODAY, "1.5", null, true, NOW, false),
        log(OTHER, TODAY, "0.25", null, true, NOW, false), log(OWNER, TODAY, "3", null, true, NOW, true),
        log(OWNER, TODAY, "2", null, false, NOW, false));
    assertThat(IssueGuide.spentHuman(logs)).isEqualByComparingTo("1.75");
    assertThat(IssueGuide.spentHuman(List.of())).isEqualByComparingTo("0");
  }

  @Test
  void 지금_앞의_근거_시각이_적은_시간을_덮으면_INFO_아니면_WARN() {
    // 진행으로 옮긴 지 1분 — 그 앞 구간(12:30 → 13:30)의 일
    assertThat(check(logged(Issue.Status.TESTING, minutesAgo(1), List.of(), "1", "구현 — 근거: 진행 12:30 → 13:30(60분)"),
        "HOURS_OVER_ELAPSED")).containsEntry("level", "INFO");
    // 여러 구간 · 초 · 다른 사이 글자
    assertThat(check(logged(Issue.Status.TESTING, minutesAgo(1), List.of(), "1", "12:00:05~12:30, 13:00-13:30"),
        "HOURS_OVER_ELAPSED")).containsEntry("level", "INFO");
    // 근거가 적은 시간보다 짧다
    assertThat(check(logged(Issue.Status.TESTING, minutesAgo(1), List.of(), "1", "근거: 13:00 → 13:30"), "HOURS_OVER_ELAPSED"))
        .containsEntry("level", "WARN");
    // 근거의 끝이 지금보다 늦다 — 앞날 시각도 함께 알린다(지금은 serverTime 과 같은 꼴)
    IssueGuide.LogGuide late = logged(Issue.Status.TESTING, minutesAgo(1), List.of(), "1", "근거: 13:30 → 14:30");
    assertThat(check(late, "HOURS_OVER_ELAPSED")).containsEntry("level", "WARN");
    assertThat((String) check(late, "NOTE_TIME_IN_FUTURE").get("message")).contains("note 의 14:30 가 지금(serverTime "
        + "2026-11-24T14:00:00.000+09:00)");
    // 자정을 넘는 구간은 근거로 보지 않는다
    assertThat(check(logged(Issue.Status.TESTING, minutesAgo(1), List.of(), "1", "23:30 → 00:30"), "HOURS_OVER_ELAPSED"))
        .containsEntry("level", "WARN");
  }

  @Test
  void 스스로_근거가_있는_앞_기록은_구간_합에서_뺀다() {
    // 테스트로 옮긴 뒤 앞 구간(진행)의 1시간을 근거와 함께 적고, 테스트 10분을 적는다
    List<IssueViews.WorkLog> before = List.of(mine("1", "구현 — 근거: 진행 12:30 → 13:30", minutesAgo(14)),
        mine("2", "사용자가 말한 시간", minutesAgo(13)));
    assertThat(logged(Issue.Status.TESTING, minutesAgo(15), before, "0.17", "시험").checks()).isEmpty();
    // 근거 없는 앞 기록은 그대로 센다
    List<IssueViews.WorkLog> plain = List.of(mine("1", "구현", minutesAgo(14)));
    assertThat(check(logged(Issue.Status.TESTING, minutesAgo(15), plain, "0.17", "시험"), "HOURS_OVER_ELAPSED"))
        .containsEntry("level", "WARN");
  }

  @Test
  void 등록_일감에_적으면_STARTED_WITHOUT_IN_PROGRESS_이고_진행으로_옮기라고_한다() {
    IssueGuide.LogGuide g = logged(Issue.Status.NEW, minutesAgo(120), List.of(), "0.5", "조사");
    Map<String, Object> c = check(g, "STARTED_WITHOUT_IN_PROGRESS");
    assertThat(c).containsEntry("level", "WARN");
    assertThat((String) c.get("message")).contains("IN_PROGRESS").contains("startDate").contains("statusChangedAt");
    // 다시 적으라는 줄이 아니라 진행으로 옮기라는 줄
    assertThat(g.next()).singleElement().asString().contains("status=IN_PROGRESS").contains("startDate");
    assertThat(logged(Issue.Status.IN_PROGRESS, minutesAgo(120), List.of(), "0.5", "조사").checks()).isEmpty();
  }

  @Test
  void 지난_날짜_기록은_구간과_시각을_보지_않는다() {
    IssueViews.WorkLog old = log(OWNER, TODAY.minusDays(1), "5", "23:59 까지", NOW, false);
    IssueGuide.LogGuide g = guide.ofLog(detail(Issue.Status.IN_PROGRESS, minutesAgo(1), List.of(old)), OWNER, old.id(),
        TODAY.minusDays(1), new BigDecimal("5"), "23:59 까지", NOW);
    assertThat(g.checks()).isEmpty();
  }
}
