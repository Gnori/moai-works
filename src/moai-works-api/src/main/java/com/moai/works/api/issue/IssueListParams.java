package com.moai.works.api.issue;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.moai.works.api.common.ApiException;

/**
 * 일감 목록의 요청 값 → {@link IssueService.ListQuery} — 프로젝트 목록과 워크스페이스 목록이 같이 쓴다(같은 조건 · 같은 오류).
 * <ul>
 *   <li>status: {@link Issue.Status#CODES} 가운데, 또는 open(등록 · 확인 넷 · 진행 · 테스트 · 보류) · closed(완료 · 종료 · 취소)</li>
 *   <li>assignee · manager: 사람 id, 또는 me(나). assignee=none 이면 담당자가 없는 것만</li>
 *   <li>parent: 일감 번호(그 바로 아래 — 번호는 워크스페이스에서 하나), 또는 top · none(맨 위 일감만)</li>
 *   <li>q: 제목 · 설명에 든 말, 또는 #번호 · dueFrom · dueTo: 완료 기한 범위(yyyy-MM-dd) · overdue=true: 기한 지난 열린 일감(확인 · 보류 포함)만</li>
 *   <li>agentSession: 에이전트 세션 이름(대소문자 무시), 또는 none(세션이 비어 있는 것만)</li>
 *   <li>module: 모듈 key — 여러 번 줄 수 있고 그 가운데 하나라도 붙은 일감(없는 key 는 400 UNKNOWN_MODULE)</li>
 *   <li>priority: {@link Issue.Priority#CODES} 가운데 — 여러 번 줄 수 있고 그 가운데 하나인 일감(일감 #302)</li>
 *   <li>sort: number · updated · due · start · planned · created · status · priority(긴급 먼저) · title (앞에 - 면 내림차순)</li>
 * </ul>
 * 알 수 없는 값은 400 INVALID_FILTER.
 */
final class IssueListParams {

  private IssueListParams() {
  }

  static IssueService.ListQuery query(UUID me, List<String> status, List<String> type, String manager, String assignee,
      String parent, String q, LocalDate dueFrom, LocalDate dueTo, boolean overdue, String agentSession,
      List<String> module, String sort, int page, int size, List<String> priority) {
    boolean noAssignee = "none".equals(assignee);
    boolean top = "top".equals(parent) || "none".equals(parent);
    Integer parentNumber = null;
    if (!top && parent != null && !parent.isBlank()) {
      try {
        parentNumber = Integer.valueOf(parent);
      } catch (NumberFormatException e) {
        throw ApiException.badRequest("INVALID_FILTER", "parent 는 일감 번호 · top · none 가운데 하나입니다.");
      }
    }
    boolean noSession = "none".equals(agentSession);
    return new IssueService.ListQuery(statuses(status), type, person(me, manager, "manager"),
        noAssignee ? null : person(me, assignee, "assignee"), noAssignee, parentNumber, top, q, dueFrom, dueTo, overdue,
        noSession ? null : agentSession, noSession, module, sort, page, size, priorities(priority));
  }

  /** 우선순위 거르기 — 쉼표로 여럿도 된다(Spring 이 나눈다). 대소문자 무시, 모르는 값은 400 INVALID_FILTER */
  static Set<Issue.Priority> priorities(List<String> values) {
    Set<Issue.Priority> result = EnumSet.noneOf(Issue.Priority.class);
    if (values == null) {
      return result;
    }
    for (String v : values) {
      if (v == null || v.isBlank()) {
        continue;
      }
      try {
        result.add(Issue.Priority.valueOf(v.trim().toUpperCase(java.util.Locale.ROOT)));
      } catch (IllegalArgumentException e) {
        throw ApiException.badRequest("INVALID_FILTER", "알 수 없는 우선순위입니다: " + v + " — " + Issue.Priority.CODES);
      }
    }
    return result;
  }

  static Set<Issue.Status> statuses(List<String> values) {
    Set<Issue.Status> result = EnumSet.noneOf(Issue.Status.class);
    if (values == null) {
      return result;
    }
    for (String v : values) {
      switch (v) {
        case "open" -> result.addAll(Issue.Status.open());
        case "closed" -> result.addAll(Issue.Status.closed());
        default -> {
          try {
            result.add(Issue.Status.valueOf(v));
          } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_FILTER", "알 수 없는 상태입니다: " + v);
          }
        }
      }
    }
    return result;
  }

  private static UUID person(UUID me, String value, String name) {
    if (value == null || value.isBlank()) {
      return null;
    }
    if ("me".equals(value)) {
      return me;
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("INVALID_FILTER", name + " 는 사람 id 나 me 여야 합니다.");
    }
  }
}
