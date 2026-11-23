package com.moai.works.api.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** 알림 메일 글 — 종류 21 × 두 언어, 스프링 없이 */
class NotificationMailContentTest {

  private static final String BASE = "https://works.example.com/";

  private static NotificationMailContent.Input input(NotificationKind kind, boolean english, String title) {
    Map<String, Object> params = new HashMap<>(Map.of("user", "김가입", "from", "NEW", "to", "DONE", "childNumber", 7,
        "otherNumber", 8, "dueDate", "2026-11-24", "name", "노트북", "keyPrefix", "mai-wks-ab", "expiresAt",
        "2026-11-30T00:00:00Z", "via", "MCP"));
    return new NotificationMailContent.Input(kind, english, params, "김철수", "팀 A", "team-a", "web", "웹", 12, title);
  }

  @Test
  void 모든_종류가_두_언어로_한_줄_제목과_링크_까닭을_가진다() {
    for (NotificationKind kind : NotificationKind.values()) {
      for (boolean english : new boolean[] {false, true}) {
        NotificationMailContent.Mail mail = NotificationMailContent.render(input(kind, english, "로그인 실패"), BASE);
        assertThat(mail.subject()).as(kind + " " + english).startsWith("[moai-works] ").doesNotContain("\n").doesNotContain("null");
        assertThat(mail.body()).as(kind + " " + english).contains(english ? "Why you got this: " : "받는 까닭: ")
            .contains("https://works.example.com/notifications?settings=notifications").doesNotContain("null");
      }
    }
  }

  @Test
  void 일감_링크_지운_일감과_내보내짐은_링크가_없고_MCP_는_표시한다() {
    NotificationMailContent.Mail assigned = NotificationMailContent.render(input(NotificationKind.ISSUE_ASSIGNED, false, "로그인 실패"), BASE);
    assertThat(assigned.subject()).isEqualTo("[moai-works] 김철수 (MCP) 님이 나를 웹 #12 「로그인 실패」의 담당자로 정했습니다");
    assertThat(assigned.body()).contains("열기: https://works.example.com/w/team-a/projects/web/issues/12")
        .contains("일감 설명 · 댓글은 메일에 싣지 않습니다");
    assertThat(NotificationMailContent.render(input(NotificationKind.ISSUE_DELETED, false, "x"), BASE).body()).doesNotContain("열기:");
    assertThat(NotificationMailContent.render(input(NotificationKind.MEMBER_REMOVED, true, "x"), BASE).body()).doesNotContain("Open:");
    assertThat(NotificationMailContent.render(input(NotificationKind.ISSUE_STATUS_CHANGED, true, "x"), BASE).subject())
        .endsWith(": Registered → Done");
    assertThat(NotificationMailContent.render(input(NotificationKind.MCP_KEY_EXPIRING, false, "x"), BASE).subject())
        .contains("2026-11-30 09:00 KST");
  }

  @Test
  void 제목의_줄바꿈은_띄어쓰기로_긴_제목은_자른다() {
    NotificationMailContent.Mail mail = NotificationMailContent.render(
        input(NotificationKind.ISSUE_UPDATED, false, "첫 줄\r\nBcc: someone@example.com\n" + "가".repeat(300)), BASE);
    assertThat(mail.subject()).doesNotContain("\r").doesNotContain("\n").hasSize(200).endsWith("…");
    assertThat(mail.body().lines().findFirst().orElseThrow()).contains("첫 줄 Bcc: someone@example.com 가");
  }
}
