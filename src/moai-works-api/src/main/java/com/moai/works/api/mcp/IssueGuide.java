package com.moai.works.api.mcp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.moai.works.api.issue.DescriptionEdit;
import com.moai.works.api.issue.Issue;
import com.moai.works.api.issue.IssueTemplateService;
import com.moai.works.api.issue.IssueViews;
import com.moai.works.api.project.ProjectModuleService;
import com.moai.works.api.security.access.ProjectContext;

import tools.jackson.databind.json.JsonMapper;

/**
 * MCP 일감 결과의 길잡이 — {@code summary}(핵심 값) · {@code checks}(빠진 것 · 놓친 것) · {@code next}(다음 할 일)
 * (일감 #182, 2026-11-24 사용자: "에이전트가 mcp 툴 스키마, 설명, 툴명 보고 이슈 잘 관리할 수 있는 구조인지 딥하게 고민하고, 도저히 안 되는 건
 * 스킬로 보완하는 거지").
 *
 * <ul>
 *   <li>서버가 알 수 있는 것은 서버가 알린다 — 스킬을 읽지 않은 Agent 도 결과만 보고 빠진 날짜 · 세션 · 모듈 · 작업 시간 · 처리 결과 · 체크를 채운다</li>
 *   <li><b>막지 않는다.</b> 경고일 뿐이고 쓰기 · 판정은 그대로다(사람 · 웹의 규칙은 바뀌지 않는다). REST 결과에는 없다</li>
 *   <li>채울 것을 묻는 검사는 열린 일감과 완료(DONE)에만 — 종료(사람이 확인함) · 취소한 일감에는 묻지 않는다. 지난 구간의 작업 시간
 *       ({@code WORK_LOG_MISSING})은 어느 상태로 옮기든 본다</li>
 *   <li>사용자가 쓴 글(체크 목록 글 · 템플릿 제목)은 {@code message} 에 넣지 않고 {@code items} 에 따로 — 서버 글과 섞이지 않게</li>
 * </ul>
 */
@Component
class IssueGuide {

  private static final Logger log = LoggerFactory.getLogger(IssueGuide.class);

  static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  /** serverTime — 한국 시간, 오프셋을 붙인 ISO(예: 2026-11-24T21:05:12.345+09:00) */
  static final DateTimeFormatter SERVER_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(SEOUL);

  /** 메시지 안의 시각 — 한국 시간, 분까지 */
  private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(SEOUL);

  /** 완료할 때 채우는 절 — 유형 템플릿에 있는 것(새기능 · 버그 처리 결과, 테스트 테스트 결과, 기술조사 결론). 템플릿에 없으면 처리 결과 */
  static final List<String> RESULT_HEADINGS = List.of("처리 결과", "테스트 결과", "결론");

  static final String WARN = "WARN";
  static final String INFO = "INFO";

  /** items 하나의 최대 글자 · 최대 개수 */
  private static final int ITEM_MAX = 60;
  private static final int ITEMS_MAX = 5;
  private static final int MODULES_MAX = 12;

  /** 손댄 뒤의 상태 — 시작일 · 완료 예정일을 묻는다(종료는 사람이 확인한 것이라 묻지 않는다) */
  private static final Set<Issue.Status> STARTED = EnumSet.of(Issue.Status.IN_PROGRESS, Issue.Status.TESTING, Issue.Status.DONE);

  /** 작업 시간을 적는 구간 — 여기서 다른 상태로 옮기면 그 구간의 작업 시간을 본다 */
  private static final Set<Issue.Status> WORKING = EnumSet.of(Issue.Status.IN_PROGRESS, Issue.Status.TESTING);

  /** note 의 시각(24시간, HH:MM) — 21:05:12 같은 초는 앞 두 칸만 본다 */
  private static final Pattern CLOCK_TIME = Pattern.compile("(?<![\\d:])([01]?\\d|2[0-3]):([0-5]\\d)(?!\\d)");

  /** 작업 시간이 구간보다 길다고 볼 여유(시간) — 1분 남짓 */
  private static final BigDecimal ELAPSED_SLACK = new BigDecimal("0.02");

  private final IssueTemplateService templates;
  private final ProjectModuleService modules;
  private final JsonMapper json;

  IssueGuide(IssueTemplateService templates, ProjectModuleService modules, JsonMapper json) {
    this.templates = templates;
    this.modules = modules;
    this.json = json;
  }

  /**
   * 이번 호출이 옮긴 상태 — 앞 상태와 그 상태가 된 때(update_issue 가 고치기 전에 읽는다).
   *
   * @param from  앞 상태
   * @param since 앞 상태가 된 때(앞 구간의 시작)
   */
  record Transition(Issue.Status from, Instant since) {
  }

  /** 일감 하나의 길잡이 — templateCheck 는 예전 결과 칸 그대로(빠진 필수 항목) */
  record Guide(Map<String, Object> summary, List<Map<String, Object>> checks, List<String> next,
      IssueTemplateService.Check templateCheck) {
  }

  /** log_work 결과의 길잡이 */
  record LogGuide(List<Map<String, Object>> checks, List<String> next) {
  }

  // ── 일감 ──────────────────────────────────────────────────────────

  /**
   * @param ctx        그 일감의 지금 프로젝트 문맥(옮겼으면 옮긴 프로젝트)
   * @param owner      키 주인 — 작업 시간 · lastWorkLogAt 은 이 사람의 것
   * @param transition 이번 호출이 상태를 옮겼으면 앞 상태 · 그 시작, 아니면 null
   * @param now        이 결과의 지금(serverTime)
   */
  Guide of(ProjectContext ctx, IssueViews.Detail d, UUID owner, Transition transition, Instant now) {
    try {
      return guide(ctx, d, owner, transition, now);
    } catch (RuntimeException e) {
      // 길잡이는 쓰기를 막지 않는다 — 셈하다 틀려도(버그) 쓴 결과는 그대로 돌려주고 서버 로그에 남긴다
      log.error("[mcp] 일감 #{} 의 길잡이를 만들지 못했다 — summary 만 준다", d.number(), e);
      return new Guide(summary(d, lastWorkLogAt(d.workLogs(), owner), now), List.of(),
          next(d, List.of(), RESULT_HEADINGS.get(0)), new IssueTemplateService.Check(d.type(), null, List.of()));
    }
  }

  private Guide guide(ProjectContext ctx, IssueViews.Detail d, UUID owner, Transition transition, Instant now) {
    IssueTemplateService.TemplateView template = templates.effectiveOrNull(ctx, d.type());
    IssueTemplateService.Check templateCheck = IssueTemplateService.check(template, d.type(), d.description());
    String heading = resultHeading(template);
    Instant lastWorkLogAt = lastWorkLogAt(d.workLogs(), owner);

    List<Map<String, Object>> checks = new ArrayList<>();
    Issue.Status status = d.status();
    boolean fill = status.isOpen() || status == Issue.Status.DONE;
    if (status.isOpen() && blank(d.agentSession())) {
      checks.add(check("AGENT_SESSION_MISSING", WARN, "agentSession(맡은 Agent 세션)이 비어 있습니다 — 이 일을 하는 Agent 면 자기 세션 "
          + "이름(모르면 작업 중인 저장소 · 폴더 이름, 예: moai-works — 프로젝트 key 가 아님)을 update_issue(agentSession=…) 로 넣으십시오. "
          + "사람이 하는 일이면 그대로 둡니다.", null));
    }
    if (fill && d.dueDate() == null) {
      checks.add(check("DUE_DATE_MISSING", WARN, "완료 기한(dueDate)이 없습니다 — 사용자가 말한 기한, 없으면 일의 크기로 정한 날을 "
          + "update_issue(dueDate=yyyy-MM-dd) 로 넣고 근거를 comment 에 한 줄 남기십시오.", null));
    }
    if (STARTED.contains(status) && d.startDate() == null) {
      checks.add(check("START_DATE_MISSING", WARN, "시작일(startDate)이 없습니다 — 진행으로 옮긴(손대기 시작한) 날을 "
          + "update_issue(startDate=yyyy-MM-dd) 로 넣으십시오.", null));
    }
    if (STARTED.contains(status) && d.plannedEndDate() == null) {
      checks.add(check("PLANNED_END_MISSING", WARN, "완료 예정일(plannedEndDate)이 없습니다 — 끝낼 것으로 보는 날을 "
          + "update_issue(plannedEndDate=yyyy-MM-dd) 로 넣으십시오(완료 기한보다 늦으면 까닭을 comment 로).", null));
    }
    if (status == Issue.Status.IN_PROGRESS && d.estimatedHours() == null) {
      checks.add(check("ESTIMATE_MISSING", INFO, "예상 공수(estimatedHours)가 없습니다 — 가늠할 수 있으면 update_issue(estimatedHours=시간) "
          + "로 넣으십시오(소요 시간과 견주는 기준).", null));
    }
    if (fill && (d.modules() == null || d.modules().isEmpty())) {
      List<String> active = modules.activeKeys(ctx);
      if (!active.isEmpty()) {
        checks.add(check("MODULES_MISSING", WARN, "관련 모듈(modules)이 없습니다 — 이 일이 고치는 모듈 key 를 update_issue(modules=[…]) "
            + "로 넣으십시오. 고를 수 있는 key 는 items(이 프로젝트에서 쓰는 모듈)이고, 맞는 것이 없으면 그대로 둡니다.",
            active.size() > MODULES_MAX ? withMore(active.subList(0, MODULES_MAX), active.size() - MODULES_MAX) : active));
      }
    }
    if (transition != null && transition.from() != status && WORKING.contains(transition.from())
        && !loggedSince(d.workLogs(), owner, transition.since())) {
      String end = d.statusChangedAt() == null ? "이번 변경" : CLOCK.format(d.statusChangedAt());
      checks.add(check("WORK_LOG_MISSING", WARN, "방금 끝난 " + name(transition.from()) + "(" + transition.from().name() + ") 구간("
          + CLOCK.format(transition.since()) + " → " + end + ", 한국 시간)에 키 주인이 log_work 로 적은 작업 시간이 없습니다 — "
          + "그 구간에 실제로 일한 만큼 log_work(number, workDate, hours, note)로 적으십시오(기다린 시간 · 다른 일은 뺍니다).", null));
    }
    if (status == Issue.Status.DONE) {
      String body = DescriptionEdit.sectionBody(d.description(), heading);
      String placeholder = template == null ? null : DescriptionEdit.sectionBody(template.body(), heading);
      if (body == null || emptySection(body, placeholder)) {
        checks.add(check("RESULT_SECTION_EMPTY", WARN, "'## " + heading + "' 절이 " + (body == null ? "없습니다" : "비어 있습니다")
            + " — update_issue(setSection={heading: \"" + heading + "\", content: 바뀐 것 · 커밋 · 확인한 것 · 남은 것})로 채우십시오. "
            + "설명을 통째로 다시 보내지 않습니다.", null));
      }
      List<String> unchecked = DescriptionEdit.uncheckedItems(d.description());
      if (!unchecked.isEmpty()) {
        checks.add(check("UNCHECKED_ITEMS", WARN, "체크하지 않은 체크 목록 줄이 " + unchecked.size() + "개 남았습니다(items) — 확인한 것은 "
            + "update_issue(checkItems=[줄의 글] 또는 \"all\")로 체크하고, 하지 못한 것은 새 일감으로 떼어 내 그 번호를 '" + heading
            + "' 에 적으십시오.", items(unchecked)));
      }
    }
    if (fill && templateCheck.missing() != null && !templateCheck.missing().isEmpty()) {
      checks.add(check("TEMPLATE_SECTIONS_MISSING", WARN, "설명에 그 유형 템플릿의 필수 항목(items)이 없습니다 — get_issue_template 의 "
          + "## 제목 그대로 절을 채우십시오(update_issue 의 setSection 으로 하나씩 붙일 수 있습니다. 해당 없으면 '해당 없음').",
          items(templateCheck.missing())));
    }
    return new Guide(summary(d, lastWorkLogAt, now), checks, next(d, checks, heading), templateCheck);
  }

  private Map<String, Object> summary(IssueViews.Detail d, Instant lastWorkLogAt, Instant now) {
    Map<String, Object> s = new LinkedHashMap<>();
    s.put("number", d.number());
    s.put("project", d.project() == null ? null : d.project().key());
    s.put("type", d.type());
    s.put("title", d.title());
    s.put("status", d.status().name());
    s.put("rowVersion", d.rowVersion());
    s.put("dueDate", date(d.dueDate()));
    s.put("startDate", date(d.startDate()));
    s.put("plannedEndDate", date(d.plannedEndDate()));
    s.put("completedOn", date(d.completedOn()));
    s.put("closedOn", date(d.closedOn()));
    s.put("estimatedHours", d.estimatedHours());
    s.put("spentHours", d.spentHours());
    s.put("agentSession", d.agentSession());
    s.put("modules", d.modules() == null ? List.of() : d.modules().stream().map(IssueViews.ModuleRef::key).toList());
    s.put("statusChangedAt", time(d.statusChangedAt()));
    s.put("lastWorkLogAt", time(lastWorkLogAt));
    s.put("serverTime", serverTime(now));
    return s;
  }

  /** 다음 할 일 — 1~3줄: 지난 구간의 시간 · 빠진 것 채우기 · 지금 상태에서 흔한 다음 걸음 */
  private static List<String> next(IssueViews.Detail d, List<Map<String, Object>> checks, String heading) {
    List<String> out = new ArrayList<>();
    if (checks.stream().anyMatch(c -> "WORK_LOG_MISSING".equals(c.get("code")))) {
      out.add("먼저 지난 구간의 시간을 log_work 로 적는다(checks 의 WORK_LOG_MISSING).");
    }
    if (checks.stream().anyMatch(c -> WARN.equals(c.get("level")) && !"WORK_LOG_MISSING".equals(c.get("code")))) {
      out.add("checks 의 빠진 것을 update_issue(number=" + d.number() + ", rowVersion=" + d.rowVersion() + ", …) 한 번으로 채운다.");
    }
    out.add(switch (d.status()) {
      case NEW -> "시작하면 update_issue(status=IN_PROGRESS, startDate, plannedEndDate, estimatedHours, agentSession, assignee=me).";
      case IN_PROGRESS -> "구간이 끝나면 log_work 후 TESTING · DONE 으로 — DONE 은 setSection(" + heading + ") · checkItems 와 한 번에.";
      case TESTING -> "확인이 끝나면 log_work 후 DONE(setSection(" + heading + ") · checkItems) — 문제가 나오면 IN_PROGRESS 로 되돌린다.";
      case ON_HOLD -> "기다리던 것이 풀리면 update_issue(status=IN_PROGRESS, comment=무엇이 풀렸나).";
      case DONE -> "종료(CLOSED)는 사람이 확인한 뒤 — Agent 는 여기서 멈추고 다음 일감을 잡는다: search_issues(agentSession=내 세션, status=open).";
      case CLOSED -> "닫힌 일감 — 같은 문제가 다시 나오면 다시 열지 말고 새 일감을 만들어 link_issues(type=RELATES)로 잇는다.";
      case CANCELLED -> "취소한 일감(이력은 남는다) — 다시 하게 되면 NEW · IN_PROGRESS 로 되돌린다. delete_issue 는 사용자가 지우라고 할 때만.";
    });
    return out;
  }

  // ── 작업 시간 ──────────────────────────────────────────────────────

  /**
   * log_work 결과의 길잡이 — 적은 기록이 그럴듯한지(막지 않는다).
   * <ul>
   *   <li>{@code NOTE_TIME_IN_FUTURE}: 오늘 날짜 기록의 note 에 지금(serverTime)보다 1분 넘게 늦은 시각(HH:MM)이 있다
   *       (2026-11-24 써 보며 찾음: 끝 시각을 앞질러 적는 일이 하루에 세 번)</li>
   *   <li>{@code HOURS_OVER_ELAPSED}: 오늘 날짜 기록의 시간이 구간 시작(statusChangedAt 과 키 주인의 앞 기록 가운데 늦은 것)부터 지금까지보다 길다</li>
   * </ul>
   */
  LogGuide ofLog(IssueViews.Detail d, UUID owner, UUID workLogId, LocalDate workDate, BigDecimal hours, String note, Instant now) {
    try {
      return logGuide(d, owner, workLogId, workDate, hours, note, now);
    } catch (RuntimeException e) {
      log.error("[mcp] 일감 #{} 의 작업 시간 길잡이를 만들지 못했다", d.number(), e);
      return new LogGuide(List.of(), List.of());
    }
  }

  private LogGuide logGuide(IssueViews.Detail d, UUID owner, UUID workLogId, LocalDate workDate, BigDecimal hours, String note,
      Instant now) {
    List<Map<String, Object>> checks = new ArrayList<>();
    ZonedDateTime local = now.atZone(SEOUL);
    boolean today = workDate != null && workDate.equals(local.toLocalDate());
    if (today && note != null) {
      int nowSeconds = local.toLocalTime().toSecondOfDay();
      Set<String> late = new LinkedHashSet<>();
      Matcher m = CLOCK_TIME.matcher(note);
      while (m.find()) {
        LocalTime t = LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        if (t.toSecondOfDay() > nowSeconds + 60) {
          late.add(String.format(Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute()));
        }
      }
      if (!late.isEmpty()) {
        checks.add(check("NOTE_TIME_IN_FUTURE", WARN, "note 의 " + String.join(" · ", late) + " 가 지금("
            + String.format(Locale.ROOT, "%02d:%02d", local.getHour(), local.getMinute()) + ")보다 늦습니다 — 끝 시각은 serverTime 으로 "
            + "쓰고, 틀렸으면 delete_work_log(workLogId=" + workLogId + ") 후 다시 적으십시오.", null));
      }
    }
    if (today && hours != null) {
      Instant start = d.statusChangedAt();
      for (IssueViews.WorkLog w : d.workLogs() == null ? List.<IssueViews.WorkLog>of() : d.workLogs()) {
        if (!w.deleted() && !w.id().equals(workLogId) && w.user() != null && owner.equals(w.user().id())
            && w.createdAt() != null && (start == null || w.createdAt().isAfter(start))) {
          start = w.createdAt();
        }
      }
      if (start != null && !start.isAfter(now)) {
        BigDecimal elapsed = BigDecimal.valueOf(now.toEpochMilli() - start.toEpochMilli())
            .divide(BigDecimal.valueOf(3_600_000L), 2, RoundingMode.HALF_UP);
        if (hours.compareTo(elapsed.add(ELAPSED_SLACK)) > 0) {
          checks.add(check("HOURS_OVER_ELAPSED", WARN, "적은 " + hours.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "시간이 구간 시작("
              + CLOCK.format(start) + ", statusChangedAt 과 키 주인의 앞 기록 가운데 늦은 것)부터 지금까지 " + elapsed.toPlainString()
              + "시간보다 깁니다 — 그 앞에 한 일이면 note 에 근거를 적고, 아니면 delete_work_log(workLogId=" + workLogId
              + ") 후 실제 시간으로 다시 적으십시오.", null));
        }
      }
    }
    List<String> next = new ArrayList<>();
    if (!checks.isEmpty()) {
      next.add("checks 를 보고 틀렸으면 delete_work_log(workLogId=" + workLogId + ", reason) → log_work 로 다시 적는다.");
    }
    next.add("상태를 옮기려면 update_issue(number=" + d.number() + ", rowVersion=" + d.rowVersion() + ", status=…) — 지금 "
        + name(d.status()) + "(" + d.status().name() + ").");
    return new LogGuide(checks, next);
  }

  // ── 도움 ──────────────────────────────────────────────────────────

  /** 지금 서버 시각 — 한국 시간, 오프셋을 붙인 ISO */
  static String serverTime(Instant now) {
    return SERVER_TIME.format(now);
  }

  /** 키 주인이 이 일감에 마지막으로 작업 시간을 적은 때 — 지운 기록은 뺀다. 없으면 null */
  static Instant lastWorkLogAt(List<IssueViews.WorkLog> logs, UUID owner) {
    if (logs == null) {
      return null;
    }
    return logs.stream().filter(w -> !w.deleted() && w.user() != null && owner.equals(w.user().id()) && w.createdAt() != null)
        .map(IssueViews.WorkLog::createdAt).max(Instant::compareTo).orElse(null);
  }

  /** 완료할 때 채울 절 제목 — 템플릿에 있는 처리 결과 · 테스트 결과 · 결론 가운데 첫 것, 없으면 처리 결과 */
  static String resultHeading(IssueTemplateService.TemplateView template) {
    if (template != null && template.sections() != null) {
      for (IssueTemplateService.Section s : template.sections()) {
        for (String h : RESULT_HEADINGS) {
          if (h.equalsIgnoreCase(s.title().strip())) {
            return h;
          }
        }
      }
    }
    return RESULT_HEADINGS.get(0);
  }

  private static boolean loggedSince(List<IssueViews.WorkLog> logs, UUID owner, Instant since) {
    if (logs == null) {
      return false;
    }
    return logs.stream().anyMatch(w -> !w.deleted() && w.user() != null && owner.equals(w.user().id()) && w.createdAt() != null
        && (since == null || !w.createdAt().isBefore(since)));
  }

  /** 절 본문이 비었나 — 주석만 있거나 템플릿의 안내 글 그대로면 빈 것으로 본다 */
  private static boolean emptySection(String body, String placeholder) {
    String text = body.replaceAll("(?s)<!--.*?-->", "").strip();
    if (text.isEmpty()) {
      return true;
    }
    return placeholder != null && !placeholder.isBlank() && squash(text).equals(squash(placeholder));
  }

  private static String squash(String s) {
    return s.strip().replaceAll("\\s+", " ");
  }

  private static Map<String, Object> check(String code, String level, String message, List<String> items) {
    Map<String, Object> c = new LinkedHashMap<>();
    c.put("code", code);
    c.put("level", level);
    c.put("message", message);
    if (items != null) {
      c.put("items", items);
    }
    return c;
  }

  /** 사용자 글 목록 — 한 줄로 펴고 {@value #ITEM_MAX}자 · {@value #ITEMS_MAX}개로 자른다 */
  private static List<String> items(List<String> texts) {
    List<String> out = new ArrayList<>();
    for (String t : texts) {
      if (out.size() == ITEMS_MAX) {
        out.add("외 " + (texts.size() - ITEMS_MAX) + "개");
        break;
      }
      out.add(McpInstructions.oneLine(t, ITEM_MAX));
    }
    return out;
  }

  private static List<String> withMore(List<String> head, int more) {
    List<String> out = new ArrayList<>(head);
    out.add("외 " + more + "개 — list_modules 로 본다");
    return out;
  }

  private static String name(Issue.Status s) {
    return switch (s) {
      case NEW -> "등록";
      case IN_PROGRESS -> "진행";
      case TESTING -> "테스트";
      case ON_HOLD -> "보류";
      case DONE -> "완료";
      case CLOSED -> "종료";
      case CANCELLED -> "취소";
    };
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static String date(LocalDate d) {
    return d == null ? null : d.toString();
  }

  /** 시각 — 다른 결과 칸과 같은 UTC ISO 글 */
  private Object time(Instant instant) {
    return instant == null ? null : json.convertValue(instant, String.class);
  }
}
