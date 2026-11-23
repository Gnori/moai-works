package com.moai.works.api.notification;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 알림함의 응답 모양. */
public final class NotificationViews {

  private NotificationViews() {
  }

  /**
   * 알림 한 줄.
   *
   * @param kind          종류 코드(예: {@code issue.assigned}) — 화면이 문장을 고른다
   * @param subjectType   무엇에 관한 것인가 — issue · project · mcp_key · join_request · workspace
   * @param params        문장과 링크를 만드는 값(주소 · 제목 · 이름 · 수). 본문은 들어 있지 않다
   * @param actorName     그 일을 한 사람(시스템이 한 일이면 null일 수 있다)
   * @param count         묶인 수 — 읽지 않은 동안 같은 일이 몇 번 있었나
   * @param workspaceSlug 워크스페이스의 지금 주소(링크를 만들 때 쓴다). 워크스페이스 밖의 알림이면 null
   * @param createdAt     처음 생긴 때
   * @param updatedAt     마지막으로 묶인 때 — 알림함은 이 순서다
   * @param readAt        읽은 때. 읽지 않았으면 null
   */
  public record Item(UUID id, String kind, String subjectType, UUID subjectId, Map<String, Object> params,
      String actorName, int count, String workspaceSlug, String workspaceName, Instant createdAt, Instant updatedAt,
      Instant readAt) {
  }

  /**
   * 알림함 한 쪽.
   *
   * @param unread 읽지 않은 알림의 수(전체)
   * @param next   다음 쪽을 받을 때 before 로 넘길 값(없으면 끝)
   */
  public record Inbox(List<Item> items, long unread, String next) {
  }

  public record UnreadCount(long unread) {
  }

  public record ReadAll(int read) {
  }

  /**
   * 알림 받기 설정 한 줄.
   *
   * @param group     설정 화면의 묶음 — WORKSPACE · PROJECT · ISSUE · WORK · SECURITY
   * @param enabled   알림함에 쌓는가
   * @param mandatory 알림함을 끌 수 없는 종류(늘 enabled)
   * @param email     메일로도 받기로 했는가(고른 적이 없으면 종류의 기본값 — 아침 알림만 켬). 알림함을 끈 종류는 메일도 가지 않는다
   */
  public record Preference(String kind, String group, boolean enabled, boolean mandatory, boolean email) {
  }
}
