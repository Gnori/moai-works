package com.moai.works.api.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.moai.works.api.issue.Issue;
import com.moai.works.api.issue.IssueViews;

/**
 * 시간 검사 다듬기 — 정한 시각(DB 없이). 2026-11-25, 일감 #275(써 보며 찾은 넷).
 * <ul>
 *   <li>NOTE_TIME_IN_FUTURE 는 분 단위 — 지금의 시:분보다 늦은 분이 앞날(같은 분은 괜찮다), 자정을 넘는 구간</li>
 *   <li>WORK_LOG_MISSING 은 36초(0.01 시간)보다 짧은 구간에는 없다</li>
 *   <li>그 구간의 시간을 바로 아래 하위 일감에 적었으면 WORK_LOG_ON_CHILD(INFO)</li>
 *   <li>테스트의 next 에 관리자 확인요청(REVIEW) 길</li>
 * </ul>
 */
class IssueGuideTimeTest {

  private static final UUID OWNER = UUID.randomUUID();
  private static final UUID OTHER = UUID.randomUUID();

  private final IssueGuide guide = new IssueGuide(null, null, null);

  private static Instant seoul(String local) {
    return OffsetDateTime.parse(local + "+09:00").toInstant();
  }

  private static IssueViews.WorkLog log(UUID user, LocalDate day, String hours, String note, Instant at) {
    return new IssueViews.WorkLog(UUID.randomUUID(), new IssueViews.Person(user, "u", "u"), day, new BigDecimal(hours), note, "MCP",
        false, null, at, false, null, null);
  }

  private static IssueViews.Detail detail(Issue.Status status, Instant statusChangedAt, List<IssueViews.WorkLog> logs) {
    return new IssueViews.Detail(7, new IssueViews.ProjectRef("p", "P"), "feature", "제목", "", status, Issue.Priority.NORMAL, null,
        null, null, null, null, null, null, null, null, statusChangedAt, statusChangedAt, statusChangedAt, 3, false, 1, null, BigDecimal.ZERO, null, List.of(),
        List.of(), List.of(), List.of(), logs);
  }

  // ── NOTE_TIME_IN_FUTURE — 분 단위 ─────────────────────────────────

  /** 지금 {@code now} 에 note 를 적은 log_work 결과의 NOTE_TIME_IN_FUTURE — 없으면 null */
  private Map<String, Object> future(Instant now, String note) {
    LocalDate today = LocalDate.ofInstant(now, IssueGuide.SEOUL);
    IssueViews.WorkLog w = log(OWNER, today, "0.01", note, now);
    List<IssueViews.WorkLog> logs = new ArrayList<>(List.of(w));
    IssueGuide.LogGuide g = guide.ofLog(detail(Issue.Status.IN_PROGRESS, now.minus(Duration.ofHours(2)), logs), OWNER, w.id(), today,
        w.hours(), note, now);
    return g.checks().stream().filter(c -> "NOTE_TIME_IN_FUTURE".equals(c.get("code"))).findFirst().orElse(null);
  }

  @Test
  void 지금의_시_분보다_늦은_분이면_앞날이고_같은_분은_괜찮다() {
    // 00:29:07 — 분을 올려 쓴 00:30 은 예전("1분 넘게")에는 지나쳤다
    Instant now = seoul("2026-11-25T00:29:07");
    assertThat(future(now, "구현 — 근거: 00:10 → 00:29")).isNull();
    Map<String, Object> c = future(now, "구현 — 근거: 00:10 → 00:30");
    assertThat(c).containsEntry("level", "WARN");
    assertThat((String) c.get("message")).contains("note 의 00:30 가 지금(serverTime 2026-11-25T00:29:07.000+09:00)")
        .contains("초는 버린다").contains("delete_work_log");
    // 같은 분의 끝(00:29:59.999)까지 00:29 는 괜찮고, 다음 분이 되면 00:30 도 괜찮다
    assertThat(IssueGuide.futureTimes("→ 00:29", seoul("2026-11-25T00:29:59.999"))).isEmpty();
    assertThat(IssueGuide.futureTimes("→ 00:30", seoul("2026-11-25T00:29:59.999"))).containsExactly("00:30");
    assertThat(IssueGuide.futureTimes("→ 00:30", seoul("2026-11-25T00:30:00"))).isEmpty();
    // 초를 붙인 시각도 시:분만 본다
    assertThat(IssueGuide.futureTimes("00:10:05 → 00:29:30", now)).isEmpty();
  }

  @Test
  void 자정을_넘는_구간은_지금에_따라_시작이_어제거나_끝이_내일이다() {
    // 00:29 — 23:40 에 시작한 구간의 시작은 어제다. 끝만 오늘로 견준다
    Instant afterMidnight = seoul("2026-11-25T00:29:07");
    assertThat(IssueGuide.futureTimes("근거: 23:40 → 00:29", afterMidnight)).isEmpty();
    assertThat(IssueGuide.futureTimes("근거: 23:40 → 00:30", afterMidnight)).containsExactly("00:30");
    // 구간 밖의 시각은 오늘로 읽는다 — 오늘 기록의 23:40 은 앞날
    assertThat(IssueGuide.futureTimes("23:40 부터", afterMidnight)).containsExactly("23:40");

    // 23:59 — 같은 분 23:59 는 괜찮고, 분을 올려 쓴 00:00 은 내일(앞날)이다
    Instant lastMinute = seoul("2026-11-25T23:59:59.999");
    assertThat(future(lastMinute, "근거: 23:30 → 23:59")).isNull();
    assertThat(IssueGuide.futureTimes("근거: 23:30 → 00:00", lastMinute)).containsExactly("00:00");
    assertThat(IssueGuide.futureTimes("근거: 23:30 → 00:00", seoul("2026-11-25T23:59:00"))).containsExactly("00:00");
    // 자정이 지나면(00:00:30) 같은 구간의 끝 00:00 은 지금과 같은 분 — 괜찮다
    assertThat(future(seoul("2026-11-26T00:00:30"), "근거: 23:30 → 00:00")).isNull();
    assertThat(IssueGuide.futureTimes("근거: 23:30 → 00:01", seoul("2026-11-26T00:00:30"))).containsExactly("00:01");
  }

  @Test
  void 근거_시각의_끝도_같은_규칙이다() {
    Instant now = seoul("2026-11-25T00:29:07");
    assertThat(IssueGuide.evidenceFits("근거: 00:00 → 00:29", new BigDecimal("0.48"), now)).isTrue();
    // 분을 올린 끝은 근거가 아니다 — 예전에는 1분 여유로 맞는 근거였다
    assertThat(IssueGuide.evidenceFits("근거: 00:00 → 00:30", new BigDecimal("0.48"), now)).isFalse();
    // 자정을 넘는 구간은 그대로 근거로 보지 않는다
    assertThat(IssueGuide.evidenceFits("근거: 23:59 → 00:29", new BigDecimal("0.48"), now)).isFalse();
  }

  // ── WORK_LOG_MISSING — 짧은 구간 · 하위 일감 ──────────────────────

  private static final Instant END = seoul("2026-11-25T14:00:00");

  /** TESTING 에서 since 부터 END 까지 한 구간을 DONE 으로 끝냈다 */
  private static Map<String, Object> ended(Instant since, List<IssueViews.WorkLog> own, Supplier<List<IssueViews.ChildWorkLog>> children) {
    return IssueGuide.workLogCheck(detail(Issue.Status.DONE, END, own), OWNER, new IssueGuide.Transition(Issue.Status.TESTING, since),
        END.plusMillis(5), children);
  }

  private static IssueViews.ChildWorkLog child(int number, UUID user, String hours, Instant at) {
    return new IssueViews.ChildWorkLog(number, user, new BigDecimal(hours), at);
  }

  @Test
  void 짧은_구간_36초_미만은_묻지_않는다() {
    AtomicInteger asked = new AtomicInteger();
    Supplier<List<IssueViews.ChildWorkLog>> none = () -> {
      asked.incrementAndGet();
      return List.of();
    };
    // 진행 → 테스트 → 확인요청을 잇따라 옮긴 3초 · 35.999초 — 묻지 않고 하위 일감도 읽지 않는다
    assertThat(ended(END.minusSeconds(3), List.of(), none)).isNull();
    assertThat(ended(END.minus(Duration.ofMillis(35_999)), List.of(), none)).isNull();
    assertThat(asked).hasValue(0);
    // 36초(0.01 시간)부터는 묻는다
    Map<String, Object> c = ended(END.minusSeconds(36), List.of(), none);
    assertThat(c).containsEntry("code", "WORK_LOG_MISSING").containsEntry("level", "WARN");
    assertThat((String) c.get("message")).contains("테스트(TESTING) 구간(2026-11-25T13:59:24.000+09:00 → 2026-11-25T14:00:00.000+09:00)")
        .contains("하위 일감에서 했으면 그 일감에");
    assertThat(asked).hasValue(1);
    // 진행 · 테스트가 아닌 구간 · 상태를 옮기지 않은 호출에는 없다
    assertThat(IssueGuide.workLogCheck(detail(Issue.Status.IN_PROGRESS, END, List.of()), OWNER,
        new IssueGuide.Transition(Issue.Status.NEW, END.minusSeconds(3600)), END, none)).isNull();
    assertThat(IssueGuide.workLogCheck(detail(Issue.Status.TESTING, END, List.of()), OWNER, null, END, none)).isNull();
  }

  @Test
  void 그_구간의_시간을_바로_아래_하위_일감에_적었으면_INFO_로_하위_번호와_합만_알린다() {
    Instant since = END.minus(Duration.ofMinutes(30));
    List<IssueViews.ChildWorkLog> logs = List.of(child(13, OWNER, "0.25", END.minus(Duration.ofMinutes(5))),
        child(12, OWNER, "0.2", END.minus(Duration.ofMinutes(20))), child(13, OWNER, "0.05", END.minus(Duration.ofMinutes(1))),
        // 다른 사람의 기록 · 구간 앞의 기록은 세지 않는다
        child(14, OTHER, "3", END.minus(Duration.ofMinutes(10))), child(15, OWNER, "1", since.minusSeconds(1)));
    Map<String, Object> c = ended(since, List.of(), () -> logs);
    assertThat(c).containsEntry("code", "WORK_LOG_ON_CHILD").containsEntry("level", "INFO").doesNotContainKey("items");
    assertThat((String) c.get("message")).contains("테스트(TESTING) 구간(2026-11-25T13:30:00.000+09:00 → 2026-11-25T14:00:00.000+09:00)")
        .contains("하위 일감 #12 · #13 에 적었습니다(0.5시간)").doesNotContain("#14").doesNotContain("#15");
    // 이 일감의 next 는 먼저 log_work 로 적으라고 하지 않는다
    assertThat(IssueGuide.next(detail(Issue.Status.DONE, END, List.of()), List.of(c), "처리 결과"))
        .noneMatch(l -> l.contains("log_work 로 적는다"));

    // 다른 사람 · 구간 앞의 하위 기록뿐이면 그대로 WORK_LOG_MISSING
    assertThat(ended(since, List.of(), () -> logs.subList(3, 5))).containsEntry("code", "WORK_LOG_MISSING");
    // 이 일감에 적었으면 하위 일감은 읽지 않는다
    IssueViews.WorkLog own = log(OWNER, LocalDate.of(2026, 11, 25), "0.5", "시험", END.minus(Duration.ofMinutes(2)));
    assertThat(ended(since, List.of(own), () -> {
      throw new AssertionError("하위 일감을 읽지 않는다");
    })).isNull();
  }

  // ── next ─────────────────────────────────────────────────────────

  @Test
  void 테스트의_next_는_관리자_확인이_필요하면_REVIEW_로_옮기고_기다리라고_한다() {
    List<String> next = IssueGuide.next(detail(Issue.Status.TESTING, END, List.of()), List.of(), "테스트 결과");
    assertThat(next).singleElement().asString().contains("log_work 후 DONE(setSection(테스트 결과)")
        .contains("문제가 나오면 IN_PROGRESS").contains("관리자의 확인이 필요하면 log_work 후 update_issue(status=REVIEW, reason=무엇을 확인할지")
        .contains("댓글로 남는다").endsWith("옮기고 기다린다.");
  }
}
