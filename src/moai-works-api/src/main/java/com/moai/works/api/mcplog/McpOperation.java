package com.moai.works.api.mcplog;

import java.util.Locale;

/**
 * 접근 기록 한 줄의 작업 종류 (FR-MCP-006 — "읽기, 수정, 삭제, 생성 모두").
 *
 * <ul>
 *   <li>도구 호출은 도구 이름으로 정한다 — {@link #ofTool}</li>
 *   <li>{@link #LIST} 는 도구 목록({@code tools/list}), {@link #CONNECT} 는 연결 시작({@code initialize})과 그 밖의 MCP 신호
 *       (알림 · ping · 세션 끝 · 스트림)</li>
 * </ul>
 */
public enum McpOperation {
  READ,
  CREATE,
  UPDATE,
  DELETE,
  LINK,
  LIST,
  CONNECT;

  /**
   * 도구 이름 → 작업 종류. 앞말로 정해서, 나중에 더할 도구(예: 댓글 고치기 · 지우기)도 표를 고치지 않고 맞게 들어간다.
   * <ul>
   *   <li>{@code create_} · {@code add_} · {@code log_} · {@code request_attachment_upload} → 만들기</li>
   *   <li>{@code update_} · {@code edit_} · {@code set_} → 고치기</li>
   *   <li>{@code delete_} · {@code remove_} · {@code reset_} → 지우기</li>
   *   <li>{@code link_} · {@code unlink_} → 연결</li>
   *   <li>그 밖(목록 · 찾기 · 보기 · 받기 티켓 · 모르는 도구) → 읽기</li>
   * </ul>
   */
  public static McpOperation ofTool(String tool) {
    if (tool == null || tool.isBlank()) {
      return READ;
    }
    String t = tool.trim().toLowerCase(Locale.ROOT);
    if (t.equals("request_attachment_upload") || t.startsWith("create_") || t.startsWith("add_") || t.startsWith("log_")) {
      return CREATE;
    }
    if (t.startsWith("update_") || t.startsWith("edit_") || t.startsWith("set_")) {
      return UPDATE;
    }
    if (t.startsWith("delete_") || t.startsWith("remove_") || t.startsWith("reset_")) {
      return DELETE;
    }
    if (t.startsWith("link_") || t.startsWith("unlink_")) {
      return LINK;
    }
    return READ;
  }
}
