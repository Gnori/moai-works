package com.moai.works.api.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** 알림 메일 글 — 종류 24 × 두 언어, 스프링 없이 */
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
    assertThat(assigned.body()).contains("열기: https://works.example.com/w/team-a/issues/12")
        .contains("일감 설명 · 댓글은 메일에 싣지 않습니다");
    assertThat(NotificationMailContent.render(input(NotificationKind.ISSUE_DELETED, false, "x"), BASE).body()).doesNotContain("열기:");
    assertThat(NotificationMailContent.render(input(NotificationKind.MEMBER_REMOVED, true, "x"), BASE).body()).doesNotContain("Open:");
    assertThat(NotificationMailContent.render(input(NotificationKind.ISSUE_STATUS_CHANGED, true, "x"), BASE).subject())
        .endsWith(": Registered → Done");
    assertThat(NotificationMailContent.render(input(NotificationKind.MCP_KEY_EXPIRING, false, "x"), BASE).subject())
        .contains("2026-11-30 09:00 KST");
  }

  @Test
  void 상태_바뀜_메일은_상태_일곱을_두_언어의_말로_적는다() {
    // 2026-11-24 보류 · 취소 더함(일감 #152) — 코드값이 그대로 메일에 나가지 않는다
    Map<String, String[]> names = new java.util.LinkedHashMap<>();
    names.put("NEW", new String[] {"등록", "Registered"});
    names.put("REQUIREMENT_REVIEW", new String[] {"요구사항 확인요청", "Requirement review"});
    names.put("REQUIREMENT_CONFIRMED", new String[] {"요구사항 확인완료", "Requirement confirmed"});
    names.put("IN_PROGRESS", new String[] {"진행", "In progress"});
    names.put("TESTING", new String[] {"테스트", "Testing"});
    names.put("REVIEW", new String[] {"확인요청", "Review"});
    names.put("CONFIRMED", new String[] {"확인완료", "Confirmed"});
    names.put("ON_HOLD", new String[] {"보류", "On hold"});
    names.put("DONE", new String[] {"완료", "Done"});
    names.put("CLOSED", new String[] {"종료", "Closed"});
    names.put("CANCELLED", new String[] {"취소", "Cancelled"});
    assertThat(names.keySet()).containsExactly(java.util.Arrays.stream(com.moai.works.api.issue.Issue.Status.values())
        .map(Enum::name).toArray(String[]::new));
    names.forEach((code, label) -> {
      for (int i = 0; i < 2; i++) {
        NotificationMailContent.Input in = input(NotificationKind.ISSUE_STATUS_CHANGED, i == 1, "x");
        in.params().put("to", code);
        assertThat(NotificationMailContent.render(in, BASE).subject()).as(code).endsWith(" → " + label[i]);
      }
    });
  }

  @Test
  void 관리자_확인_메일은_요청_확인_되돌림을_상태_이름과_함께_적는다() {
    // 2026-11-25, 일감 #272
    NotificationMailContent.Input requested = input(NotificationKind.ISSUE_REVIEW_REQUESTED, false, "로그인 실패");
    requested.params().put("to", "REQUIREMENT_REVIEW");
    NotificationMailContent.Mail mail = NotificationMailContent.render(requested, BASE);
    assertThat(mail.subject()).isEqualTo("[moai-works] 김철수 (MCP) 님이 웹 #12 「로그인 실패」의 확인을 요청했습니다: 요구사항 확인요청");
    assertThat(mail.body()).contains("웹에서 확인하거나 반려합니다").contains("열기: https://works.example.com/w/team-a/issues/12");
    NotificationMailContent.Input confirmed = input(NotificationKind.ISSUE_REVIEW_CONFIRMED, true, "x");
    confirmed.params().put("to", "CONFIRMED");
    assertThat(NotificationMailContent.render(confirmed, BASE).subject()).endsWith(": Confirmed");
    NotificationMailContent.Input returned = input(NotificationKind.ISSUE_REVIEW_RETURNED, false, "x");
    returned.params().put("from", "REVIEW");
    returned.params().put("to", "IN_PROGRESS");
    assertThat(NotificationMailContent.render(returned, BASE).subject()).endsWith("확인요청을 되돌렸습니다: 확인요청 → 진행");
  }

  @Test
  void 긴급_메일은_두_언어로_긴급이라_적는다() {
    // 2026-11-26, 일감 #302
    NotificationMailContent.Input raised = input(NotificationKind.ISSUE_PRIORITY_URGENT, false, "로그인 실패");
    raised.params().put("from", "HIGH");
    NotificationMailContent.Mail ko = NotificationMailContent.render(raised, BASE);
    assertThat(ko.subject()).isEqualTo("[moai-works] 김철수 (MCP) 님이 웹 #12 「로그인 실패」을(를) 긴급으로 올렸습니다");
    assertThat(ko.body()).contains("내가 관리자 · 담당자인 일감입니다").contains("열기: https://works.example.com/w/team-a/issues/12");
    // 만들 때부터 긴급(from 없음)
    NotificationMailContent.Input created = input(NotificationKind.ISSUE_PRIORITY_URGENT, false, "x");
    created.params().remove("from");
    assertThat(NotificationMailContent.render(created, BASE).subject()).endsWith("긴급으로 만들었습니다");
    NotificationMailContent.Input createdEn = input(NotificationKind.ISSUE_PRIORITY_URGENT, true, "x");
    createdEn.params().remove("from");
    assertThat(NotificationMailContent.render(createdEn, BASE).subject()).contains(" created ").endsWith(" as urgent");
  }

  @Test
  void 제목의_줄바꿈은_띄어쓰기로_긴_제목은_자른다() {
    NotificationMailContent.Mail mail = NotificationMailContent.render(
        input(NotificationKind.ISSUE_UPDATED, false, "첫 줄\r\nBcc: someone@example.com\n" + "가".repeat(300)), BASE);
    assertThat(mail.subject()).doesNotContain("\r").doesNotContain("\n").hasSize(200).endsWith("…");
    assertThat(mail.body().lines().findFirst().orElseThrow()).contains("첫 줄 Bcc: someone@example.com 가");
  }
}
