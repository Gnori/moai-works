package com.moai.works.api.mcp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.moai.works.api.issue.DescriptionEdit;
import com.moai.works.api.issue.Issue;
import com.moai.works.api.issue.IssueService;
import com.moai.works.api.issue.IssueTemplateService;
import com.moai.works.api.issue.IssueViews;
import com.moai.works.api.project.ProjectModuleService;
import com.moai.works.api.security.access.ProjectContext;

/**
 * MCP 일감 결과의 길잡이 — {@code summary}(핵심 값) · {@code checks}(빠진 것 · 놓친 것) · {@code next}(다음 할 일)
 * (일감 #182, 2026-11-24 사용자: "에이전트가 mcp 툴 스키마, 설명, 툴명 보고 이슈 잘 관리할 수 있는 구조인지 딥하게 고민하고, 도저히 안 되는 건
 * 스킬로 보완하는 거지").
 *
 * <ul>
 *   <li>서버가 알 수 있는 것은 서버가 알린다 — 스킬을 읽지 않은 Agent 도 결과만 보고 빠진 날짜 · 세션 · 모듈 · 작업 시간 · 처리 결과 · 체크를 채운다</li>
 *   <li><b>막지 않는다.</b> 경고일 뿐이고 쓰기 · 판정은 그대로다(사람 · 웹의 규칙은 바뀌지 않는다). REST 결과에는 없다</li>
 *   <li>채울 것을 묻는 검사는 열린 일감과 완료(DONE)에만 — 종료(사람이 확인함) · 취소한 일감에는 묻지 않는다. 지난 구간의 작업 시간
 *       ({@code WORK_LOG_MISSING})은 어느 상태로 옮기든 본다({@link #workLogCheck})</li>
 *   <li>사용자가 쓴 글(체크 목록 글 · 템플릿 제목)은 {@code message} 에 넣지 않고 {@code items} 에 따로 — 서버 글과 섞이지 않게</li>
 * </ul>
 */
@Component
class IssueGuide {

  private static final Logger log = LoggerFactory.getLogger(IssueGuide.class);

  static final ZoneId SEOUL = McpTime.SEOUL;

  /** 완료할 때 채우는 절 — 유형 템플릿에 있는 것(새기능 · 버그 처리 결과, 테스트 테스트 결과, 기술조사 결론). 템플릿에 없으면 처리 결과 */
  static final List<String> RESULT_HEADINGS = List.of("처리 결과", "테스트 결과", "결론");

  static final String WARN = "WARN";
  static final String INFO = "INFO";

  /** items 하나의 최대 글자 · 최대 개수 */
  private static final int ITEM_MAX = 60;
  private static final int ITEMS_MAX = 5;
  private static final int MODULES_MAX = 12;

  /** 손댄 뒤의 상태 — 시작일 · 완료 예정일을 묻는다(종료는 사람이 확인한 것이라 묻지 않는다. 결과 확인요청 · 확인완료는 일을 한 뒤다) */
  private static final Set<Issue.Status> STARTED = EnumSet.of(Issue.Status.IN_PROGRESS, Issue.Status.TESTING, Issue.Status.REVIEW,
      Issue.Status.CONFIRMED, Issue.Status.DONE);

  /** 작업 시간을 적는 구간 — 여기서 다른 상태로 옮기면 그 구간의 작업 시간을 본다 */
  private static final Set<Issue.Status> WORKING = EnumSet.of(Issue.Status.IN_PROGRESS, Issue.Status.TESTING);

  /**
   * 이보다 짧은 진행 · 테스트 구간은 작업 시간을 묻지 않는다 — log_work 가 받는 가장 작은 시간 0.01 시간(일감 #275: 진행 → 테스트 → 확인요청을
   * 잇따라 옮기면 3~7초 구간마다 WARN 이 났다)
   */
  static final Duration MIN_SEGMENT = Duration.ofSeconds(36);

  /** 손대기 전의 상태 — 등록과 요구사항 확인 둘(일감 #272). 여기서 바로 {@link #SKIPS_PROGRESS} 로 가면 진행 구간을 건너뛴 것 */
  private static final Set<Issue.Status> BEFORE_WORK = EnumSet.of(Issue.Status.NEW, Issue.Status.REQUIREMENT_REVIEW,
      Issue.Status.REQUIREMENT_CONFIRMED);

  /** 손대기 전에서 바로 가면 진행 구간을 건너뛴 것 — 보류 · 취소는 손대지 않고 갈 수 있다(일감 #199) */
  private static final Set<Issue.Status> SKIPS_PROGRESS = EnumSet.of(Issue.Status.TESTING, Issue.Status.REVIEW, Issue.Status.DONE);

  /** note 의 시각(24시간, HH:MM, 한국 시간 벽시계) — 21:05:12 같은 초는 앞 두 칸만 본다 */
  private static final Pattern CLOCK_TIME = Pattern.compile("(?<![\\d:])([01]?\\d|2[0-3]):([0-5]\\d)(?!\\d)");

  /** note 의 근거 구간(HH:MM → HH:MM) — 사이는 → · -> · ~ · - · – 하나, 초(:SS)는 있어도 된다(일감 #199) */
  private static final Pattern CLOCK_SPAN = Pattern.compile("(?<![\\d:])([01]?\\d|2[0-3]):([0-5]\\d)(?::[0-5]\\d)?\\s*"
      + "(?:→|->|~|-|–)\\s*([01]?\\d|2[0-3]):([0-5]\\d)(?!\\d)");

  /**
   * 사람이 한 시간이라는 note 의 표시 — 사용자가 알려 준 사람의 시간은 구간(Agent 의 상태 시각)과 견줄 수 없다(일감 #199).
   * 대소문자 무시, 들어 있으면 된다
   */
  static final List<String> HUMAN_MARKERS = List.of("사용자가 말한", "사람이 한", "reported");

  /** 작업 시간이 구간보다 길다고 볼 여유(시간) — 1분 남짓 */
  private static final BigDecimal ELAPSED_SLACK = new BigDecimal("0.02");

  private final IssueTemplateService templates;
  private final ProjectModuleService modules;
  private final IssueService issues;

  IssueGuide(IssueTemplateService templates, ProjectModuleService modules, IssueService issues) {
    this.templates = templates;
    this.modules = modules;
    this.issues = issues;
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
    if (transition != null && BEFORE_WORK.contains(transition.from()) && SKIPS_PROGRESS.contains(status)) {
      checks.add(check("STARTED_WITHOUT_IN_PROGRESS", WARN, name(transition.from()) + "(" + transition.from().name() + ")에서 바로 "
          + name(status) + "(" + status.name() + ")으로 "
          + "옮겼습니다 — 진행(IN_PROGRESS) 구간이 없어 작업 시간의 근거(statusChangedAt)가 남지 않았습니다. 일을 시작할 때 "
          + "update_issue(status=IN_PROGRESS, startDate=시작한 날)로 옮기십시오. 이미 한 일의 시간은 근거 시각(HH:MM → HH:MM)과 함께 "
          + "log_work 로 적습니다.", null));
    }
    Map<String, Object> workLog = workLogCheck(d, owner, transition, now, () -> childWorkLogs(ctx, d.number()));
    if (workLog != null) {
      checks.add(workLog);
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
            + "update_issue(checkItems=[줄의 글] 또는 \"all\")로 체크하고, 확인 못 한 항목은 새 일감으로 떼어 " + splitOff(d)
            + " 로 표시하십시오 — 줄 끝에 ' → #새 번호' 가 붙고 남은 줄로 세지 않습니다. 그 번호는 '" + heading + "' 에도 적습니다.",
            items(unchecked)));
      }
    }
    if (fill && templateCheck.missing() != null && !templateCheck.missing().isEmpty()) {
      checks.add(check("TEMPLATE_SECTIONS_MISSING", WARN, "설명에 그 유형 템플릿의 필수 항목(items)이 없습니다 — get_issue_template 의 "
          + "## 제목 그대로 절을 채우십시오(update_issue 의 setSection 으로 하나씩 붙일 수 있습니다. 해당 없으면 '해당 없음').",
          items(templateCheck.missing())));
    }
    return new Guide(summary(d, lastWorkLogAt, now), checks, next(d, checks, heading), templateCheck);
  }

  /**
   * 이번 호출이 끝낸 진행 · 테스트 구간의 작업 시간 — 알릴 것이 없으면 null.
   * <ul>
   *   <li>구간(앞 상태가 된 때 → 이번 변경)이 {@link #MIN_SEGMENT} 보다 짧으면 묻지 않는다 — 잇따라 옮긴 몇 초에는 적을 시간이 없다(일감 #275)</li>
   *   <li>키 주인이 구간 시작 뒤로 이 일감에 적었으면 없다</li>
   *   <li>아니고 키 주인이 그 구간에 바로 아래 하위 일감(유형은 가리지 않는다 — 보통 시험 일감)에 적었으면 {@code WORK_LOG_ON_CHILD}(INFO) —
   *       하위 번호와 시간의 합만 알린다(사용자 글 없음). 시험은 test 하위 일감에서 하고, 두 곳에 적으면 같은 시간을 두 번 센다(일감 #275)</li>
   *   <li>아니면 {@code WORK_LOG_MISSING}(WARN)</li>
   * </ul>
   *
   * @param childLogs 바로 아래 하위 일감의 기록 — 위 셋째에 이르렀을 때만 부른다
   */
  static Map<String, Object> workLogCheck(IssueViews.Detail d, UUID owner, Transition transition, Instant now,
      Supplier<List<IssueViews.ChildWorkLog>> childLogs) {
    if (transition == null || transition.from() == d.status() || !WORKING.contains(transition.from())) {
      return null;
    }
    Instant since = transition.since();
    Instant end = d.statusChangedAt() == null ? now : d.statusChangedAt();
    if (since != null && Duration.between(since, end).compareTo(MIN_SEGMENT) < 0) {
      return null;
    }
    if (loggedSince(d.workLogs(), owner, since)) {
      return null;
    }
    String segment = name(transition.from()) + "(" + transition.from().name() + ") 구간(" + McpTime.format(since) + " → "
        + (d.statusChangedAt() == null ? "이번 변경" : McpTime.format(d.statusChangedAt())) + ")";
    Set<Integer> children = new TreeSet<>();
    BigDecimal hours = BigDecimal.ZERO;
    for (IssueViews.ChildWorkLog w : childLogs.get()) {
      if (owner.equals(w.userId()) && w.createdAt() != null && w.hours() != null && (since == null || !w.createdAt().isBefore(since))
          && !w.createdAt().isAfter(end)) {
        children.add(w.number());
        hours = hours.add(w.hours());
      }
    }
    if (!children.isEmpty()) {
      return check("WORK_LOG_ON_CHILD", INFO, "방금 끝난 " + segment + "의 작업 시간은 키 주인이 바로 아래 하위 일감 " + numbers(children)
          + " 에 적었습니다(" + plain(hours) + "시간) — 이 일감에 같은 시간을 또 적지 않습니다(두 번 셉니다). 하위에 적지 않은 일을 이 "
          + "일감에서 했으면 그만큼만 log_work 로 적습니다.", null);
    }
    return check("WORK_LOG_MISSING", WARN, "방금 끝난 " + segment + "에 키 주인이 log_work 로 적은 작업 시간이 없습니다 — "
        + "그 구간에 실제로 일한 만큼 log_work(number, workDate, hours, note)로 적으십시오(기다린 시간 · 다른 일은 뺍니다. "
        + "그 일을 하위 일감에서 했으면 그 일감에 적습니다).", null);
  }

  /** 하위 일감의 기록 — 읽지 못하면(그럴 일은 없다) 없는 것으로 보고 WORK_LOG_MISSING 으로 알린다. 길잡이는 쓰기를 막지 않는다 */
  private List<IssueViews.ChildWorkLog> childWorkLogs(ProjectContext ctx, int number) {
    try {
      return issues.childWorkLogs(ctx, number);
    } catch (RuntimeException e) {
      log.error("[mcp] 일감 #{} 의 하위 일감 작업 시간을 읽지 못했다", number, e);
      return List.of();
    }
  }

  /** 일감 번호 목록 — #12 · #13, {@value #ITEMS_MAX}개 넘으면 "외 N개" */
  private static String numbers(Set<Integer> numbers) {
    List<String> out = new ArrayList<>();
    for (int n : numbers) {
      if (out.size() == ITEMS_MAX) {
        out.add("외 " + (numbers.size() - ITEMS_MAX) + "개");
        break;
      }
      out.add("#" + n);
    }
    return String.join(" · ", out);
  }

  // ── 절 바꾸기 (일감 #237) ─────────────────────────────────────────

  /** 새 글에 없는 옛 글이 이 글자 수를 넘으면 크게 지운 것으로 본다 */
  static final int SECTION_REMOVED_CHARS = 500;

  /**
   * update_issue 의 setSection(replace)이 크게 지웠으면 {@code SECTION_REPLACED_LARGE}(WARN) 한 줄 — 새 글에 없는 소절 제목 · 코드 블록이
   * 하나라도 있거나 새 글에 없는 옛 글이 {@value #SECTION_REMOVED_CHARS}자를 넘을 때. 막지 않는다(쓰기는 이미 됐다). 메시지에는 수만 —
   * 사용자 글(절 제목 · 본문)은 넣지 않는다. 일감 #237: Agent 가 "그 절에 결과를 쓴다" 며 replace 로 ### 소절 · 차트 11 · 흐름도 4 · 코드
   * 블록 9 를 지우고도 결과에서 알 수 없었다.
   */
  static List<Map<String, Object>> sectionChecks(DescriptionEdit.Report edit) {
    if (edit == null || edit.section() == null || !(edit.section().get("removed") instanceof DescriptionEdit.Removed r)) {
      return List.of();
    }
    if (!largeRemoval(r)) {
      return List.of();
    }
    DescriptionEdit.CodeBlocks blocks = r.codeBlocks();
    List<String> parts = new ArrayList<>();
    if (r.subHeadings() > 0) {
      parts.add("소절(### 제목) " + r.subHeadings() + "개");
    }
    if (blocks.total() > 0) {
      List<String> kinds = new ArrayList<>();
      if (blocks.chart() > 0) {
        kinds.add("chart " + blocks.chart());
      }
      if (blocks.mermaid() > 0) {
        kinds.add("mermaid " + blocks.mermaid());
      }
      if (blocks.other() > 0) {
        kinds.add("그 밖 " + blocks.other());
      }
      parts.add("코드 블록 " + blocks.total() + "개(" + String.join(" · ", kinds) + ")");
    }
    parts.add("글 " + r.lines() + "줄(" + r.chars() + "자)");
    return List.of(check("SECTION_REPLACED_LARGE", WARN, "setSection(mode=replace)이 절 본문을 통째로 바꾸며 새 글에 없는 것을 "
        + "지웠습니다 — " + String.join(" · ", parts) + ". 절은 '## 제목' 줄부터 다음 ## 제목 앞까지라 ### 소절도 함께 바뀝니다. "
        + "옛 본문은 descriptionEdit.section.previous.text(앞 " + DescriptionEdit.PREVIOUS_MAX + "자 — truncated 가 true 면 잘림)와 "
        + "웹 일감 보기의 리비전 탭(이 결과의 revisionNo 바로 앞 리비전)에 있습니다. 지울 뜻이 아니었으면 previous 로 되돌리고, "
        + "절에 더하려면 setSection(mode=append — 절 끝, 소절 뒤)을 씁니다.", null));
  }

  /**
   * setSection(replace)이 크게 지웠나 — 새 글에 없는 소절 제목 · 코드 블록이 하나라도 있거나 지운 글이 {@value #SECTION_REMOVED_CHARS}자를 넘는다.
   * {@code SECTION_REPLACED_LARGE} 를 알리는 조건이고, MCP 결과는 이때만 옛 본문({@code descriptionEdit.section.previous})을 싣는다(일감 #282)
   */
  static boolean largeRemoval(DescriptionEdit.Removed r) {
    return r != null && (r.subHeadings() > 0 || r.codeBlocks().total() > 0 || r.chars() > SECTION_REMOVED_CHARS);
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
    s.put("spentHoursHuman", spentHuman(d.workLogs()));
    s.put("agentSession", d.agentSession());
    s.put("modules", d.modules() == null ? List.of() : d.modules().stream().map(IssueViews.ModuleRef::key).toList());
    s.put("statusChangedAt", time(d.statusChangedAt()));
    s.put("lastWorkLogAt", time(lastWorkLogAt));
    s.put("serverTime", serverTime(now));
    return s;
  }

  /** 다음 할 일 — 1~3줄: 지난 구간의 시간 · 빠진 것 채우기 · 지금 상태에서 흔한 다음 걸음 */
  static List<String> next(IssueViews.Detail d, List<Map<String, Object>> checks, String heading) {
    List<String> out = new ArrayList<>();
    if (checks.stream().anyMatch(c -> "WORK_LOG_MISSING".equals(c.get("code")))) {
      out.add("먼저 지난 구간의 시간을 log_work 로 적는다(checks 의 WORK_LOG_MISSING).");
    }
    if (checks.stream().anyMatch(c -> WARN.equals(c.get("level")) && !"WORK_LOG_MISSING".equals(c.get("code")))) {
      boolean unchecked = checks.stream().anyMatch(c -> "UNCHECKED_ITEMS".equals(c.get("code")));
      out.add("checks 의 빠진 것을 update_issue(number=" + d.number() + ", rowVersion=" + d.rowVersion() + ", …) 한 번으로 채운다."
          + (unchecked ? " 확인 못 한 항목은 새 일감으로 떼어 " + splitOff(d) + "." : ""));
    }
    out.add(switch (d.status()) {
      case NEW -> "시작하면 update_issue(status=IN_PROGRESS, startDate, plannedEndDate, estimatedHours, agentSession, assignee=me).";
      case IN_PROGRESS -> "구간이 끝나면 log_work 후 TESTING · DONE 으로 — DONE 은 setSection(" + heading + ") · checkItems 와 한 번에"
          + "(확인 못 한 항목은 새 일감으로 떼어 moveItems).";
      case TESTING -> "확인이 끝나면 log_work 후 DONE(setSection(" + heading + ") · checkItems — 확인 못 한 항목은 새 일감으로 떼어 "
          + splitOff(d) + ") — 문제가 나오면 IN_PROGRESS 로 되돌린다. 관리자의 확인이 필요하면 log_work 후 "
          + "update_issue(status=REVIEW, reason=무엇을 확인할지 — 댓글로 남는다)로 옮기고 기다린다.";
      case REQUIREMENT_REVIEW -> "관리자 확인을 기다린다 — 시간을 적지 말고 다른 일감을 잡는다: search_issues(agentSession=내 세션, status=open). "
          + "관리자가 웹에서 확인하면 REQUIREMENT_CONFIRMED, 반려하면 NEW 로 돌아온다(까닭은 댓글 — get_issue 로 본다).";
      case REQUIREMENT_CONFIRMED -> "요구사항이 확인됐다 — IN_PROGRESS 로 시작: update_issue(status=IN_PROGRESS, startDate, plannedEndDate, "
          + "estimatedHours, agentSession, assignee=me).";
      case REVIEW -> "관리자 확인을 기다린다 — 시간을 적지 말고 다른 일감을 잡는다: search_issues(agentSession=내 세션, status=open). "
          + "관리자가 웹에서 확인하면 CONFIRMED, 반려하면 IN_PROGRESS 로 돌아온다(까닭은 댓글 — get_issue 로 본다).";
      case CONFIRMED -> "확인됐다 — DONE(" + heading + "): update_issue(status=DONE, setSection(" + heading + ") · checkItems — 확인 못 한 "
          + "항목은 새 일감으로 떼어 " + splitOff(d) + ").";
      case ON_HOLD -> "기다리던 것이 풀리면 update_issue(status=IN_PROGRESS, comment=무엇이 풀렸나).";
      case DONE -> "종료(CLOSED)는 사람이 확인한 뒤 — Agent 는 여기서 멈추고 다음 일감을 잡는다: search_issues(agentSession=내 세션, status=open).";
      case CLOSED -> "닫힌 일감 — 같은 문제가 다시 나오면 다시 열지 말고 새 일감을 만들어 link_issues(type=RELATES)로 잇는다.";
      case CANCELLED -> "취소한 일감(이력은 남는다) — 다시 하게 되면 NEW · IN_PROGRESS 로 되돌린다. delete_issue 는 사용자가 지우라고 할 때만.";
    });
    return out;
  }

  /**
   * 확인 못 한 체크 항목을 떼어 낼 새 일감 만들기 · 넘기기 — 한 줄(일감 #204, 써 보기 3회차: 같은 프로젝트 · 맞는 유형 · 상위 = 이 일감을
   * 스스로 알아내야 했다). 프로젝트 key 와 번호는 서버 값이다(사용자 글이 아니다)
   */
  static String splitOff(IssueViews.Detail d) {
    String key = d.project() == null ? "<이 프로젝트 key>" : d.project().key();
    return "create_issue(project=" + key + ", type=bug · test · feature 가운데 맞는 것, parent=" + d.number()
        + ") → update_issue(number=" + d.number() + ", moveItems=[{item: 줄의 글, to: 새 번호}])";
  }

  // ── 작업 시간 ──────────────────────────────────────────────────────

  /**
   * log_work 결과의 길잡이 — 적은 기록이 그럴듯한지(막지 않는다).
   * <ul>
   *   <li>{@code NOTE_TIME_IN_FUTURE}: 오늘 날짜 기록의 note 에 지금(serverTime)의 시:분보다 늦은 시각(HH:MM)이 있다
   *       (2026-11-24 써 보며 찾음: 끝 시각을 앞질러 적는 일이 하루에 세 번. 일감 #275 에서 분 단위로 — {@link #futureTimes})</li>
   *   <li>{@code HOURS_OVER_ELAPSED}: 키 주인이 오늘 날짜로 이 구간(지금 상태가 된 때 statusChangedAt 뒤)에 적은 시간의 합이 statusChangedAt 부터
   *       지금까지보다 길다. 앞 기록을 새 구간의 시작으로 보지 않는다(일감 #199 — 한 구간을 하위 Agent 의 만들기 한 줄 · 조정자의 확인 한 줄로
   *       나눠 적으면 예전 셈은 둘째 줄을 0분과 견줘 잘못 알렸다). 앞 기록 가운데 스스로 근거가 있는 것({@link #explained})은 합에서 뺀다.
   *       이번 기록이 사람이 한 시간(기록의 {@code byHuman} — 일감 #204)이거나 맞는 근거 시각을 품으면 WARN 대신 INFO</li>
   *   <li>{@code STARTED_WITHOUT_IN_PROGRESS}: 등록(NEW) 일감에 작업 시간을 적었다 — 진행으로 옮긴 때가 시간의 근거다(일감 #199)</li>
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
    if (d.status() == Issue.Status.NEW) {
      checks.add(check("STARTED_WITHOUT_IN_PROGRESS", WARN, "등록(NEW) 일감에 작업 시간을 적었습니다 — 일을 시작할 때 "
          + "update_issue(status=IN_PROGRESS, startDate=시작한 날)로 옮기십시오. 상태가 바뀐 때(statusChangedAt)가 작업 시간의 근거입니다.",
          null));
    }
    ZonedDateTime local = now.atZone(SEOUL);
    boolean today = workDate != null && workDate.equals(local.toLocalDate());
    if (today && note != null) {
      Set<String> late = futureTimes(note, now);
      if (!late.isEmpty()) {
        checks.add(check("NOTE_TIME_IN_FUTURE", WARN, "note 의 " + String.join(" · ", late) + " 가 지금(serverTime "
            + McpTime.format(now) + ")보다 늦습니다 — 끝 시각은 serverTime 의 시:분 그대로(초는 버린다 — 올리지 않는다) "
            + "쓰고, 틀렸으면 delete_work_log(workLogId=" + workLogId + ") 후 다시 적으십시오.", null));
      }
    }
    Instant start = d.statusChangedAt();
    if (today && hours != null && start != null && !start.isAfter(now)) {
      BigDecimal sum = hours;
      for (IssueViews.WorkLog w : d.workLogs() == null ? List.<IssueViews.WorkLog>of() : d.workLogs()) {
        if (!w.deleted() && !w.id().equals(workLogId) && w.user() != null && owner.equals(w.user().id())
            && workDate.equals(w.workDate()) && w.createdAt() != null && !w.createdAt().isBefore(start) && w.hours() != null
            && !explained(w)) {
          sum = sum.add(w.hours());
        }
      }
      BigDecimal elapsed = BigDecimal.valueOf(now.toEpochMilli() - start.toEpochMilli())
          .divide(BigDecimal.valueOf(3_600_000L), 2, RoundingMode.HALF_UP);
      if (sum.compareTo(elapsed.add(ELAPSED_SLACK)) > 0) {
        String what = "키 주인이 오늘 이 구간(statusChangedAt " + McpTime.format(start) + " 뒤)에 적은 시간 " + plain(sum) + "시간"
            + (sum.compareTo(hours) != 0 ? "(이번 " + plain(hours) + "시간 포함)" : "") + "이 statusChangedAt 부터 지금까지 "
            + elapsed.toPlainString() + "시간보다 깁니다";
        if (byHuman(d.workLogs(), workLogId)) {
          checks.add(check("HOURS_OVER_ELAPSED", INFO, what + " — 사람이 한 시간(byHuman=true)이라 알리기만 합니다(사용자가 알려 준 "
              + "시간은 구간과 견주지 않습니다).", null));
        } else if (evidenceFits(note, hours, now)) {
          checks.add(check("HOURS_OVER_ELAPSED", INFO, what + " — note 의 근거 시각(HH:MM → HH:MM)이 지금 앞이고 적은 시간을 덮어 그 앞에 "
              + "한 일로 봅니다. 근거가 맞는지만 다시 보십시오.", null));
        } else {
          checks.add(check("HOURS_OVER_ELAPSED", WARN, what + " — 그 앞에 한 일이면 근거 시각(예: \"근거: 진행 21:10 → 21:40\")을 "
              + "note 에, 사용자가 알려 준 사람의 시간이면 byHuman=true(note 에 '사용자가 말한 시간' 이라고 써도 된다)로, 아니면 실제 "
              + "시간으로 — delete_work_log(workLogId=" + workLogId + ") 후 log_work 로 다시 적으십시오.", null));
        }
      }
    }
    List<String> next = new ArrayList<>();
    if (checks.stream().anyMatch(c -> WARN.equals(c.get("level")) && !"STARTED_WITHOUT_IN_PROGRESS".equals(c.get("code")))) {
      next.add("checks 를 보고 틀렸으면 delete_work_log(workLogId=" + workLogId + ", reason) → log_work 로 다시 적는다.");
    }
    if (d.status() == Issue.Status.NEW) {
      next.add("일을 시작했으면 update_issue(number=" + d.number() + ", rowVersion=" + d.rowVersion()
          + ", status=IN_PROGRESS, startDate, plannedEndDate) — 지금 등록(NEW).");
    } else {
      next.add("상태를 옮기려면 update_issue(number=" + d.number() + ", rowVersion=" + d.rowVersion() + ", status=…) — 지금 "
          + name(d.status()) + "(" + d.status().name() + ").");
    }
    return new LogGuide(checks, next);
  }

  /**
   * note 의 시각(HH:MM) 가운데 앞날인 것 — {@code now}(한국 시간)의 시:분보다 늦은 분. 같은 분은 괜찮다(00:29:07 에 00:29 는 되고 00:30 은
   * 앞날 — 일감 #275: 분을 올려 쓴 끝 시각을 예전의 "1분 넘게" 는 잡지 못했다).
   * <ul>
   *   <li>여유를 두지 않는다 — 끝 시각은 serverTime(서버 시계)을 읽어 쓰고 서버가 같은 시계로 견주므로 시계 차이가 끼지 않는다. 읽은 뒤에
   *       시간은 앞으로만 가니, 읽은 분 그대로 쓴 시각은 늘 지금 이하다</li>
   *   <li>자정을 넘는 구간(HH:MM → HH:MM, 끝 &lt; 시작) — 시작이 지금 이하면 시작은 오늘 · 끝은 내일이라 끝이 앞날이고(23:59:30 에
   *       "23:30 → 00:00"), 아니면 시작은 어제라 보지 않고 끝만 오늘로 견준다(00:29 에 "23:40 → 00:29")</li>
   * </ul>
   */
  static Set<String> futureTimes(String note, Instant now) {
    Set<String> late = new LinkedHashSet<>();
    if (note == null) {
      return late;
    }
    int nowMinute = minuteOfDay(now);
    Set<Integer> yesterday = new HashSet<>();
    Set<Integer> tomorrow = new HashSet<>();
    Matcher span = CLOCK_SPAN.matcher(note);
    while (span.find()) {
      int from = minute(span.group(1), span.group(2));
      int to = minute(span.group(3), span.group(4));
      if (to < from) {
        if (from <= nowMinute) {
          tomorrow.add(span.start(3));
        } else {
          yesterday.add(span.start(1));
        }
      }
    }
    Matcher m = CLOCK_TIME.matcher(note);
    while (m.find()) {
      int t = minute(m.group(1), m.group(2));
      if (!yesterday.contains(m.start(1)) && (t > nowMinute || tomorrow.contains(m.start(1)))) {
        late.add(String.format(Locale.ROOT, "%02d:%02d", t / 60, t % 60));
      }
    }
    return late;
  }

  /** 그 때(한국 시간)의 하루 안 분 — 초는 버린다 */
  private static int minuteOfDay(Instant at) {
    LocalTime t = at.atZone(SEOUL).toLocalTime();
    return t.getHour() * 60 + t.getMinute();
  }

  private static int minute(String hour, String minute) {
    return Integer.parseInt(hour) * 60 + Integer.parseInt(minute);
  }

  /** 스스로 근거가 있는 기록 — 사람이 한 시간(byHuman)이거나, 적은 때 앞의 근거 시각이 그 시간을 덮는다. 구간 합에서 뺀다 */
  static boolean explained(IssueViews.WorkLog w) {
    return w.byHuman() || evidenceFits(w.note(), w.hours(), w.createdAt());
  }

  /** 이번 기록(workLogId)이 사람이 한 시간인가 — 저장된 byHuman(log_work 가 정한 값) */
  private static boolean byHuman(List<IssueViews.WorkLog> logs, UUID workLogId) {
    return logs != null && logs.stream().anyMatch(w -> w.id().equals(workLogId) && w.byHuman());
  }

  /** 사람이 한 시간의 합 — 지운 기록 빼고(summary 의 spentHoursHuman, 일감 #204) */
  static BigDecimal spentHuman(List<IssueViews.WorkLog> logs) {
    BigDecimal sum = BigDecimal.ZERO;
    if (logs != null) {
      for (IssueViews.WorkLog w : logs) {
        if (!w.deleted() && w.byHuman() && w.hours() != null) {
          sum = sum.add(w.hours());
        }
      }
    }
    return sum;
  }

  /**
   * note 에 사람이 한 시간이라는 표시({@link #HUMAN_MARKERS})가 있나 — log_work 가 {@code byHuman} 을 받지 않았을 때 이것으로 정한다
   * (일감 #204 전에는 이 표시만이 사람의 시간이었다)
   */
  static boolean humanReported(String note) {
    if (note == null) {
      return false;
    }
    String text = note.toLowerCase(Locale.ROOT);
    return HUMAN_MARKERS.stream().anyMatch(marker -> text.contains(marker.toLowerCase(Locale.ROOT)));
  }

  /**
   * note 의 근거 구간(HH:MM → HH:MM)이 맞나 — 하나 이상 있고, 모두 시작 ≤ 끝(자정을 넘는 구간은 근거로 보지 않는다)이며 끝이 {@code at}
   * (한국 시간)의 시:분 이하이고({@link #futureTimes} 와 같은 규칙 — 일감 #275), 구간 길이의 합이 적은 시간 이상(1분 여유)이다
   */
  static boolean evidenceFits(String note, BigDecimal hours, Instant at) {
    if (note == null || hours == null) {
      return false;
    }
    int atMinute = minuteOfDay(at);
    Matcher m = CLOCK_SPAN.matcher(note);
    int minutes = 0;
    boolean any = false;
    while (m.find()) {
      int from = minute(m.group(1), m.group(2));
      int to = minute(m.group(3), m.group(4));
      if (to < from || to > atMinute) {
        return false;
      }
      minutes += to - from;
      any = true;
    }
    return any && BigDecimal.valueOf(minutes + 1L).compareTo(hours.multiply(BigDecimal.valueOf(60))) >= 0;
  }

  private static String plain(BigDecimal hours) {
    return hours.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
  }

  // ── 도움 ──────────────────────────────────────────────────────────

  /** 지금 서버 시각 — 한국 시간, 오프셋을 붙인 ISO({@link McpTime}) */
  static String serverTime(Instant now) {
    return McpTime.format(now);
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
    return s.label();
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static String date(LocalDate d) {
    return d == null ? null : d.toString();
  }

  /** 시각 — 다른 결과 칸 · serverTime 과 같은 한국 시간 ISO({@link McpTime}, 일감 #204) */
  private static String time(Instant instant) {
    return McpTime.format(instant);
  }
}
