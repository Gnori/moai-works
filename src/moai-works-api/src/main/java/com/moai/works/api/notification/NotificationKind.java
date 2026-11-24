package com.moai.works.api.notification;

import java.util.Arrays;
import java.util.Optional;

/**
 * 알림의 종류 — 무엇을 누구에게 알리는가 (design/07, 2026-11-22 도출).
 *
 * <p>워크스페이스 · 멤버 다섯은 moai-wiki 와 같다. 일감 · 작업 시간 · 프로젝트 역할 · MCP 키는 moai-works 에만 있다.
 * {@code mandatory} 인 종류는 알림 설정에서 끌 수 없다(보안 · 접근이 사라지는 일). {@code emailDefault} 는 메일 받기의 기본값이다.
 */
public enum NotificationKind {
  // ── 워크스페이스 · 멤버 (moai-wiki 와 같다) ──
  /** 가입 신청이 들어왔다 → 신청을 처리할 수 있는 사람들(member.request.review) */
  JOIN_REQUESTED("join.requested", "join_request", Group.WORKSPACE, false, false),
  /** 내 가입 신청이 받아들여졌다 → 신청한 사람 */
  JOIN_APPROVED("join.approved", "workspace", Group.WORKSPACE, false, false),
  /** 내 가입 신청이 거절됐다 → 신청한 사람 */
  JOIN_REJECTED("join.rejected", "workspace", Group.WORKSPACE, false, false),
  /** 내 역할이 바뀌었다 → 그 멤버 */
  MEMBER_ROLES_CHANGED("member.roles_changed", "workspace", Group.WORKSPACE, false, false),
  /** 워크스페이스에서 내보내졌다 → 그 사람 (끌 수 없다) */
  MEMBER_REMOVED("member.removed", "workspace", Group.WORKSPACE, true, false),

  // ── 프로젝트 ──
  /** 내 프로젝트 역할이 바뀌었다(받음 · 빠짐) → 그 멤버 */
  PROJECT_ROLES_CHANGED("project.roles_changed", "project", Group.PROJECT, false, false),

  // ── 일감 ──
  /** 나를 담당자로 정했다 → 새 담당자 */
  ISSUE_ASSIGNED("issue.assigned", "issue", Group.ISSUE, false, false),
  /** 담당자에서 빠졌다 → 옛 담당자 */
  ISSUE_UNASSIGNED("issue.unassigned", "issue", Group.ISSUE, false, false),
  /** 나를 관리자로 정했다 → 새 관리자 */
  ISSUE_MANAGER_ASSIGNED("issue.manager_assigned", "issue", Group.ISSUE, false, false),
  /** 내가 관리자 · 담당자인 일감의 상태가 바뀌었다 → 관리자 · 담당자 */
  ISSUE_STATUS_CHANGED("issue.status_changed", "issue", Group.ISSUE, false, false),
  /** 내가 관리하는 일감에 확인을 요청했다(요구사항 확인요청 · 확인요청으로 옮김) → 관리자 (2026-11-25, 일감 #272) */
  ISSUE_REVIEW_REQUESTED("issue.review_requested", "issue", Group.ISSUE, false, false),
  /** 관리자가 내가 맡은 일감을 확인했다(요구사항 확인완료 · 확인완료로 옮김) → 담당자 (2026-11-25, 일감 #272) */
  ISSUE_REVIEW_CONFIRMED("issue.review_confirmed", "issue", Group.ISSUE, false, false),
  /** 확인요청이 되돌려졌다(관리자의 반려 · 요청한 쪽의 거둠 — 까닭은 댓글) → 관리자 · 담당자 (2026-11-25, 일감 #272) */
  ISSUE_REVIEW_RETURNED("issue.review_returned", "issue", Group.ISSUE, false, false),
  /** 내가 관리자 · 담당자인 일감의 내용(제목 · 설명 · 날짜 · 유형 · 상위 · 예상 공수)이 바뀌었다 → 관리자 · 담당자 */
  ISSUE_UPDATED("issue.updated", "issue", Group.ISSUE, false, false),
  /** 댓글이 달렸다 → 관리자 · 담당자 · 앞서 댓글을 단 사람 */
  ISSUE_COMMENTED("issue.commented", "issue", Group.ISSUE, false, false),
  /** 내 일감 아래에 하위 일감이 생겼다(만들거나 옮겨 옴) → 상위의 관리자 · 담당자 */
  ISSUE_CHILD_ADDED("issue.child_added", "issue", Group.ISSUE, false, false),
  /** 내 일감에 다른 일감이 연결됐다 → 상대 일감의 관리자 · 담당자 */
  ISSUE_LINKED("issue.linked", "issue", Group.ISSUE, false, false),
  /** 내가 관리자 · 담당자인 일감이 지워졌다 → 관리자 · 담당자 */
  ISSUE_DELETED("issue.deleted", "issue", Group.ISSUE, false, false),
  /** 완료 기한이 내일이다(아직 완료 · 종료 아님) → 담당자 · 관리자. 매일 아침 한 번 */
  ISSUE_DUE_SOON("issue.due_soon", "issue", Group.ISSUE, false, true),
  /** 완료 기한이 지났다(아직 완료 · 종료 아님) → 담당자 · 관리자. 기한마다 한 번 */
  ISSUE_OVERDUE("issue.overdue", "issue", Group.ISSUE, false, true),

  // ── 작업 시간 ──
  /** 다른 사람이 내가 관리하는 일감에 작업 시간을 적었다 → 관리자 */
  WORK_LOGGED("issue.work_logged", "issue", Group.WORK, false, false),
  /** 소요 시간이 예상 공수를 넘었다(넘는 순간 한 번) → 관리자 · 담당자 */
  WORK_OVER_ESTIMATE("issue.over_estimate", "issue", Group.WORK, false, false),

  // ── MCP 키 (끌 수 없다) ──
  /**
   * 다른 사람(관리자)이 내 MCP 키를 만료시켰다 → 키 주인. 2026-11-23 부터 키는 주인만 만료시키므로(ADR-029) 새로 생기지 않는다 —
   * 이미 받은 알림을 보이고 알림 설정을 맞추려고 남긴다
   */
  MCP_KEY_EXPIRED("mcp_key.expired", "mcp_key", Group.SECURITY, true, false),
  /** 내 MCP 키가 7일 안에 만료된다 → 키 주인. 키마다 한 번 */
  MCP_KEY_EXPIRING("mcp_key.expiring", "mcp_key", Group.SECURITY, true, true);

  /** 설정 화면의 묶음 */
  public enum Group {
    WORKSPACE, PROJECT, ISSUE, WORK, SECURITY
  }

  private final String code;
  private final String subjectType;
  private final Group group;
  private final boolean mandatory;
  private final boolean emailDefault;

  NotificationKind(String code, String subjectType, Group group, boolean mandatory, boolean emailDefault) {
    this.code = code;
    this.subjectType = subjectType;
    this.group = group;
    this.mandatory = mandatory;
    this.emailDefault = emailDefault;
  }

  public String code() {
    return code;
  }

  /** 무엇에 관한 알림인가 — issue · project · mcp_key · join_request · workspace */
  public String subjectType() {
    return subjectType;
  }

  public Group group() {
    return group;
  }

  /** 끌 수 없는 알림인가 */
  public boolean mandatory() {
    return mandatory;
  }

  /**
   * 메일 받기의 기본값 (FR-NOTI-006, 2026-11-23) — 아침 알림(기한 임박 · 기한 지남 · MCP 키 만료 예정)만 켜고 나머지는 끈다.
   * 하루 한 번 오는 것만 기본으로 메일까지 보내고, 일하는 동안 자주 생기는 것은 사람이 골라 켠다. 메일은 끌 수 없는 종류도 끌 수 있다
   */
  public boolean emailDefault() {
    return emailDefault;
  }

  public static Optional<NotificationKind> of(String code) {
    return Arrays.stream(values()).filter(k -> k.code.equals(code)).findFirst();
  }
}
