package com.moai.works.api.notification;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 알림 메일의 제목 · 본문 (FR-NOTI-006) — 글자만(plain text).
 *
 * <p>본문은 무슨 일인지(제목 줄) · 그 화면으로 가는 링크 · 받는 까닭이다. <b>일감 설명 · 댓글 본문은 넣지 않는다</b> — 링크를 열면
 * 화면이 지금 권한으로 다시 판정한다(알림함과 같은 규칙). 값은 알림에 든 것(제목 · 이름 · 번호)과 보낼 때 읽은 지금 값뿐이다.
 * 언어는 받는 사람의 언어 설정({@code user_profile.locale})이 {@code en} 이면 영어, 아니면 한국어다.
 */
final class NotificationMailContent {

  private static final int SUBJECT_MAX = 200;
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private NotificationMailContent() {
  }

  /**
   * 메일을 만들 값.
   *
   * @param english       영어로 쓰나
   * @param params        알림의 값(본문 없음)
   * @param actor         한 사람의 지금 이름(시스템이 한 일이면 null). MCP 로 한 일이면 뒤에 "(MCP)"를 붙인다
   * @param workspaceName 워크스페이스 이름 (워크스페이스 밖의 알림이면 null)
   * @param workspaceSlug 링크를 만들 워크스페이스 주소 (링크가 없는 종류 · 워크스페이스 밖이면 null)
   * @param projectKey    프로젝트 주소 이름 (프로젝트 · 일감 알림)
   * @param projectName   프로젝트 이름
   * @param number        일감 번호 (일감 알림)
   * @param title         일감의 지금 제목 (일감 알림)
   */
  record Input(NotificationKind kind, boolean english, Map<String, Object> params, String actor, String workspaceName,
      String workspaceSlug, String projectKey, String projectName, Integer number, String title) {
  }

  record Mail(String subject, String body) {
  }

  static Mail render(Input in, String baseUrl) {
    String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    boolean en = in.english();
    String actor = in.actor() == null || in.actor().isBlank() ? (en ? "Someone" : "누군가")
        : "MCP".equals(str(in.params(), "via")) ? in.actor() + " (MCP)" : in.actor();
    String workspace = in.workspaceName() == null ? "" : in.workspaceName();
    String issue = en ? in.projectName() + " #" + in.number() + " “" + in.title() + "”"
        : in.projectName() + " #" + in.number() + " 「" + in.title() + "」";
    String key = str(in.params(), "name");
    String prefix = str(in.params(), "keyPrefix");

    String ws = in.workspaceSlug() == null ? null : base + "/w/" + segment(in.workspaceSlug());
    String issueLink = ws == null || in.projectKey() == null || in.number() == null ? null
        : ws + "/projects/" + segment(in.projectKey()) + "/issues/" + in.number();
    String myKeys = base + "/notifications?settings=mcp-keys";

    // 관리자 · 담당자에게 가는 일감 알림의 까닭
    String watch = en ? "You manage or are assigned to this issue." : "내가 관리자 · 담당자인 일감입니다.";
    Text t = switch (in.kind()) {
      case JOIN_REQUESTED -> new Text(
          en ? str(in.params(), "user") + " asked to join " + workspace : str(in.params(), "user") + " 님이 " + workspace + "에 가입을 신청했습니다",
          en ? "You can review join requests in this workspace." : "이 워크스페이스의 가입 신청을 처리할 수 있습니다.",
          ws == null ? null : ws + "/requests");
      case JOIN_APPROVED -> new Text(
          en ? "Your request to join " + workspace + " was approved" : workspace + " 가입 신청이 받아들여졌습니다",
          en ? "You asked to join this workspace." : "내가 낸 가입 신청입니다.", ws);
      case JOIN_REJECTED -> new Text(
          en ? "Your request to join " + workspace + " was declined" : workspace + " 가입 신청이 거절되었습니다",
          en ? "You asked to join this workspace." : "내가 낸 가입 신청입니다.", null);
      case MEMBER_ROLES_CHANGED -> new Text(
          en ? "Your roles in " + workspace + " changed" : workspace + "에서 내 역할이 바뀌었습니다",
          en ? "You are a member of this workspace." : "이 워크스페이스의 멤버입니다.", ws);
      case MEMBER_REMOVED -> new Text(
          en ? "You were removed from " + workspace : workspace + "에서 내보내졌습니다",
          en ? "You were a member of this workspace." : "이 워크스페이스의 멤버였습니다.", null);
      case PROJECT_ROLES_CHANGED -> new Text(
          en ? actor + " changed your roles in project " + in.projectName()
              : actor + " 님이 " + in.projectName() + " 프로젝트의 내 역할을 바꿨습니다",
          en ? "It is about your roles in this project." : "이 프로젝트의 내 역할에 관한 일입니다.",
          ws == null || in.projectKey() == null ? null : ws + "/projects/" + segment(in.projectKey()));
      case ISSUE_ASSIGNED -> new Text(
          en ? actor + " assigned you to " + issue : actor + " 님이 나를 " + issue + "의 담당자로 정했습니다",
          en ? "You were made assignee of this issue." : "이 일감의 담당자로 정해졌습니다.", issueLink);
      case ISSUE_UNASSIGNED -> new Text(
          en ? actor + " removed you as assignee of " + issue : actor + " 님이 " + issue + "의 담당자에서 나를 뺐습니다",
          en ? "You were the assignee of this issue." : "이 일감의 담당자였습니다.", issueLink);
      case ISSUE_MANAGER_ASSIGNED -> new Text(
          en ? actor + " made you manager of " + issue : actor + " 님이 나를 " + issue + "의 관리자로 정했습니다",
          en ? "You were made manager of this issue." : "이 일감의 관리자로 정해졌습니다.", issueLink);
      case ISSUE_STATUS_CHANGED -> new Text(
          (en ? actor + " changed the status of " + issue : actor + " 님이 " + issue + "의 상태를 바꿨습니다") + ": "
              + status(str(in.params(), "from"), en) + " → " + status(str(in.params(), "to"), en),
          watch, issueLink);
      case ISSUE_UPDATED -> new Text(en ? actor + " updated " + issue : actor + " 님이 " + issue + "을(를) 고쳤습니다",
          watch, issueLink);
      case ISSUE_COMMENTED -> new Text(en ? actor + " commented on " + issue : actor + " 님이 " + issue + "에 댓글을 남겼습니다",
          en ? "You manage, are assigned to, or commented on this issue." : "내가 관리자 · 담당자이거나 댓글을 단 일감입니다.",
          issueLink);
      case ISSUE_CHILD_ADDED -> new Text(
          en ? actor + " added child issue #" + str(in.params(), "childNumber") + " under " + issue
              : actor + " 님이 " + issue + " 아래에 하위 일감 #" + str(in.params(), "childNumber") + "을(를) 두었습니다",
          watch, issueLink);
      case ISSUE_LINKED -> new Text(
          en ? actor + " linked #" + str(in.params(), "otherNumber") + " to " + issue
              : actor + " 님이 " + issue + "에 #" + str(in.params(), "otherNumber") + "을(를) 연결했습니다",
          watch, issueLink);
      // 지운 일감은 열 수 없다 — 링크 없음
      case ISSUE_DELETED -> new Text(en ? actor + " deleted " + issue : actor + " 님이 " + issue + "을(를) 지웠습니다", watch, null);
      case ISSUE_DUE_SOON -> new Text(
          en ? issue + " is due tomorrow (" + str(in.params(), "dueDate") + ")"
              : issue + "의 완료 기한이 내일(" + str(in.params(), "dueDate") + ")입니다",
          en ? "You are assigned to or manage this open issue." : "내가 담당자 · 관리자인 열린 일감입니다.", issueLink);
      case ISSUE_OVERDUE -> new Text(
          en ? issue + " is past its due date (" + str(in.params(), "dueDate") + ")"
              : issue + "의 완료 기한(" + str(in.params(), "dueDate") + ")이 지났습니다",
          en ? "You are assigned to or manage this open issue." : "내가 담당자 · 관리자인 열린 일감입니다.", issueLink);
      case WORK_LOGGED -> new Text(
          en ? actor + " logged work time on " + issue : actor + " 님이 " + issue + "에 작업 시간을 적었습니다",
          en ? "You manage this issue." : "내가 관리하는 일감입니다.", issueLink);
      case WORK_OVER_ESTIMATE -> new Text(
          en ? "Time spent on " + issue + " is over the estimate" : issue + "의 쓴 시간이 예상 공수를 넘었습니다", watch, issueLink);
      case MCP_KEY_EXPIRED -> new Text(
          en ? actor + " expired your MCP key “" + key + "” (" + prefix + "…)"
              : actor + " 님이 내 MCP 키 「" + key + "」(" + prefix + "…)를 만료시켰습니다",
          en ? "It is your MCP key." : "내 MCP 키입니다.", myKeys);
      case MCP_KEY_EXPIRING -> new Text(
          en ? "Your MCP key “" + key + "” (" + prefix + "…) expires at " + seoul(str(in.params(), "expiresAt"))
              : "내 MCP 키 「" + key + "」(" + prefix + "…)가 " + seoul(str(in.params(), "expiresAt")) + "에 만료됩니다",
          en ? "It is your MCP key." : "내 MCP 키입니다.", myKeys);
    };

    String headline = oneLine(t.headline());
    String subject = "[moai-works] " + headline;
    if (subject.length() > SUBJECT_MAX) {
      subject = subject.substring(0, SUBJECT_MAX - 1) + "…";
    }
    StringBuilder body = new StringBuilder(headline).append("\n\n");
    if (!workspace.isBlank()) {
      body.append(en ? "Workspace: " : "워크스페이스: ").append(oneLine(workspace)).append('\n');
    }
    if (t.link() != null) {
      body.append(en ? "Open: " : "열기: ").append(t.link()).append('\n');
    }
    body.append('\n').append(en ? "Why you got this: " : "받는 까닭: ").append(t.reason()).append('\n');
    if ("issue".equals(in.kind().subjectType())) {
      body.append(en ? "Issue descriptions and comments are not included in mail. Open the link to see them with your current access.\n"
          : "일감 설명 · 댓글은 메일에 싣지 않습니다. 링크를 열면 지금 권한으로 다시 확인합니다.\n");
    }
    body.append('\n')
        .append(en ? "Turn mail on or off for each kind in notification settings: " : "메일 받기는 알림 설정에서 종류마다 켜고 끕니다: ")
        .append(base).append("/notifications?settings=notifications\n")
        .append("— moai-works\n");
    return new Mail(subject, body.toString());
  }

  private record Text(String headline, String reason, String link) {
  }

  private static String str(Map<String, Object> params, String key) {
    Object value = params == null ? null : params.get(key);
    return value == null ? "" : String.valueOf(value);
  }

  private static String status(String code, boolean en) {
    return switch (code) {
      case "NEW" -> en ? "Registered" : "등록";
      case "IN_PROGRESS" -> en ? "In progress" : "진행";
      case "TESTING" -> en ? "Testing" : "테스트";
      case "DONE" -> en ? "Done" : "완료";
      case "CLOSED" -> en ? "Closed" : "종료";
      default -> code;
    };
  }

  /** 만료 시각 — 아침 알림과 같은 Asia/Seoul 로 분까지 */
  private static String seoul(String instant) {
    try {
      return MINUTE.format(Instant.parse(instant).atZone(SEOUL)) + " KST";
    } catch (RuntimeException e) {
      return instant;
    }
  }

  private static String segment(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  /** 제목 줄은 한 줄 — 일감 제목에 든 줄바꿈 · 제어 문자를 띄어쓰기로 */
  private static String oneLine(String value) {
    return value == null ? "" : value.replaceAll("\\p{Cntrl}+", " ").strip();
  }
}
