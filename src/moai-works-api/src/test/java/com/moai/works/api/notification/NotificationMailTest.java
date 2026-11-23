package com.moai.works.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.moai.works.api.account.Mailer;
import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.RecordingMailer;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * 알림 메일 (FR-NOTI-006, design/07 §8) — 메일을 켠 종류만 · 보낼 때 다시 판정 · 한 번만 · 로그 모드 한 줄 · 다시 보내기.
 * 시험에서는 정해진 주기로 돌지 않는다(outbox-cron "-") — {@link NotificationMailSender#sendDue()} 를 직접 부른다.
 */
@ApiTest
class NotificationMailTest {

  private static final String BASE = "/workspaces/{ws}/projects/{project}";
  private static final String P = "mail";
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  @Autowired
  TestApi api;
  @Autowired
  NotificationMailSender sender;
  @Autowired
  NotificationReminders reminders;
  @Autowired
  RecordingMailer mailer;
  @Autowired
  JdbcTemplate jdbc;

  private final ListAppender<ILoggingEvent> mailLog = new ListAppender<>();

  @BeforeEach
  void captureMailLog() {
    mailLog.start();
    ((Logger) LoggerFactory.getLogger(Mailer.class)).addAppender(mailLog);
  }

  @AfterEach
  void releaseMailLog() {
    ((Logger) LoggerFactory.getLogger(Mailer.class)).detachAppender(mailLog);
  }

  private record Space(String ws, TestUser owner, TestUser alice, TestUser bob, TestUser carol) {
  }

  /** 소유자 · 큐레이터 셋(일감을 만들고 고칠 수 있다) */
  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "메일 시험");
    TestUser alice = api.user("alice");
    TestUser bob = api.user("bob");
    TestUser carol = api.user("carol");
    for (TestUser u : List.of(alice, bob, carol)) {
      api.join(ws, owner, u, "curator");
    }
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "메일", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    return new Space(ws, owner, alice, bob, carol);
  }

  private int issue(Space s, TestUser by, Map<String, Object> body) throws Exception {
    Map<String, Object> b = new HashMap<>(Map.of("type", "feature", "title", "메일 일감"));
    b.putAll(body);
    return TestApi.<Integer>read(api.post(by, BASE + "/issues", b, s.ws(), P).andExpect(status().isCreated()),
        "$.number");
  }

  private int assign(Space s, TestUser to) throws Exception {
    return issue(s, s.alice(), Map.of("assigneeId", to.id().toString()));
  }

  private void email(TestUser who, String kind, boolean on) throws Exception {
    api.put(who, "/me/notifications/preferences/{k}", Map.of("email", on), kind).andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.kind == '" + kind + "')].email").value(on));
  }

  /** 그 사람의 보낼 통 줄들 (넣은 순서) */
  private List<Map<String, Object>> mails(TestUser who) {
    return jdbc.queryForList("SELECT m.*, n.kind AS notification_kind FROM notification_mail m"
        + " JOIN notification n ON n.id = m.notification_id WHERE m.recipient_id = ?::uuid ORDER BY m.created_at, m.id",
        who.id().toString());
  }

  /** 보낼 때가 된 것을 모두 보낸다 — 다른 시험이 남긴 줄도 함께 나간다(받는 사람으로 거른다) */
  private void drain() {
    for (int i = 0; i < 20; i++) {
      NotificationMailSender.Result r = sender.sendDue();
      if (r.sent() + r.skipped() + r.retrying() + r.failed() == 0) {
        return;
      }
    }
  }

  /** 다시 보낼 때를 지금으로 당긴다 */
  private void due(TestUser who) {
    jdbc.update("UPDATE notification_mail SET next_attempt_at = now() - interval '1 second'"
        + " WHERE recipient_id = ?::uuid AND status = 'PENDING'", who.id().toString());
  }

  @Test
  void 메일을_켠_종류만_한_통씩_가고_기본은_아침_알림만_켜져_있다() throws Exception {
    Space s = space();
    // 기본값: 아침 알림(기한 임박 · 기한 지남 · MCP 키 만료 예정)만 메일을 켠다
    api.get(s.bob(), "/me/notifications/preferences")
        .andExpect(jsonPath("$[?(@.kind == 'issue.assigned')].email").value(false))
        .andExpect(jsonPath("$[?(@.kind == 'issue.commented')].email").value(false))
        .andExpect(jsonPath("$[?(@.kind == 'issue.due_soon')].email").value(true))
        .andExpect(jsonPath("$[?(@.kind == 'issue.overdue')].email").value(true))
        .andExpect(jsonPath("$[?(@.kind == 'mcp_key.expiring')].email").value(true));

    // 꺼진 종류 — 알림함에는 오고 메일은 없다
    assign(s, s.bob());
    drain();
    assertThat(mails(s.bob())).isEmpty();
    assertThat(mailer.sentTo(s.bob().email())).isEmpty();

    // 켜면 다음 알림부터 한 통. 알림함 설정은 그대로다
    api.put(s.bob(), "/me/notifications/preferences/{k}", Map.of("email", true), "issue.assigned").andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.kind == 'issue.assigned')].enabled").value(true))
        .andExpect(jsonPath("$[?(@.kind == 'issue.assigned')].email").value(true));
    int n = assign(s, s.bob());
    drain();
    assertThat(mailer.sentTo(s.bob().email())).singleElement().satisfies(m -> {
      assertThat(m.kind()).isEqualTo("issue.assigned");
      assertThat(m.body()).contains("/w/" + s.ws() + "/issues/" + n);
    });

    // 알림함을 끄면 메일도 가지 않는다(메일 설정은 남아 있다)
    api.put(s.bob(), "/me/notifications/preferences/{k}", Map.of("enabled", false), "issue.assigned")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.kind == 'issue.assigned')].enabled").value(false))
        .andExpect(jsonPath("$[?(@.kind == 'issue.assigned')].email").value(true));
    assign(s, s.bob());
    drain();
    assertThat(mails(s.bob())).hasSize(1);
    assertThat(mailer.sentTo(s.bob().email())).hasSize(1);

    // 아침 알림은 기본으로 메일이 간다
    issue(s, s.alice(), Map.of("assigneeId", s.carol().id().toString(),
        "dueDate", LocalDate.now(SEOUL).plusDays(1).toString()));
    reminders.run();
    drain();
    assertThat(mailer.sentTo(s.carol().email())).extracting(RecordingMailer.Sent::kind).containsExactly("issue.due_soon");

    // 메일은 끌 수 없는 종류도 끌 수 있다. 알림함은 여전히 끌 수 없다. 둘 다 없으면 400
    email(s.bob(), "mcp_key.expiring", false);
    api.put(s.bob(), "/me/notifications/preferences/{k}", Map.of("enabled", false), "mcp_key.expiring")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOTIFICATION_MANDATORY"));
    api.put(s.bob(), "/me/notifications/preferences/{k}", Map.of(), "issue.assigned")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PREFERENCE"));
  }

  @Test
  void 보낼_때_다시_판정해서_볼_수_없게_된_사람에게는_보내지_않는다() throws Exception {
    Space s = space();
    TestUser dave = api.user("dave");
    api.join(s.ws(), s.owner(), dave, "curator");
    for (TestUser u : List.of(s.alice(), s.bob(), s.carol(), dave)) {
      email(u, "issue.assigned", true);
    }
    assign(s, s.bob());      // 보내기 전에 워크스페이스에서 내보내진다
    assign(s, s.carol());    // 보내기 전에 메일을 끈다
    int gone = assign(s, dave);  // 보내기 전에 일감이 지워진다
    TestUser stays = s.owner();
    email(stays, "issue.assigned", true);
    assign(s, stays);        // 그대로 — 보낸다

    api.delete(s.owner(), "/workspaces/{ws}/members/{user}", s.ws(), s.bob().id()).andExpect(status().is2xxSuccessful());
    email(s.carol(), "issue.assigned", false);
    api.delete(s.owner(), "/workspaces/{ws}/issues/{n}", s.ws(), gone).andExpect(status().is2xxSuccessful());
    drain();

    assertThat(mailer.sentTo(s.bob().email())).isEmpty();
    assertThat(mailer.sentTo(s.carol().email())).isEmpty();
    assertThat(mailer.sentTo(dave.email())).isEmpty();
    assertThat(mails(s.bob())).extracting(m -> m.get("status") + "/" + m.get("last_error")).containsExactly("SKIPPED/NOT_MEMBER");
    assertThat(mails(s.carol())).extracting(m -> m.get("status") + "/" + m.get("last_error")).containsExactly("SKIPPED/EMAIL_OFF");
    assertThat(mails(dave)).extracting(m -> m.get("status") + "/" + m.get("last_error")).containsExactly("SKIPPED/GONE");
    assertThat(mailer.sentTo(stays.email())).hasSize(1);
    assertThat(mails(stays)).extracting(m -> m.get("status")).containsExactly("SENT");
  }

  @Test
  void 알림_하나에_메일은_한_번만_가고_여럿이_함께_보내도_겹치지_않는다() throws Exception {
    Space s = space();
    email(s.bob(), "issue.updated", true);
    int n = assign(s, s.bob());
    // 같은 일감을 두 번 고쳐도 읽지 않은 알림은 한 줄 — 메일도 한 통
    for (String title : List.of("고친 제목 하나", "고친 제목 둘")) {
      Map<String, Object> body = new HashMap<>(Map.of("title", title));
      body.put("rowVersion", TestApi.<Integer>read(api.get(s.owner(), "/workspaces/{ws}/issues/{n}", s.ws(), n), "$.rowVersion"));
      api.patch(s.owner(), "/workspaces/{ws}/issues/{n}", body, s.ws(), n).andExpect(status().isOk());
    }
    assertThat(mails(s.bob())).hasSize(1);
    drain();
    drain();
    assertThat(mailer.sentTo(s.bob().email())).singleElement()
        .satisfies(m -> assertThat(m.subject()).contains("고친 제목 둘"));   // 보낼 때의 값으로 만든다
    assertThat(mails(s.bob())).singleElement().satisfies(m -> {
      assertThat(m.get("status")).isEqualTo("SENT");
      assertThat(m.get("attempts")).isEqualTo(1);
      assertThat(m.get("sent_at")).isNotNull();
    });

    // 두 곳이 함께 보내도 한 줄은 한 곳만 보낸다(FOR UPDATE SKIP LOCKED)
    email(s.carol(), "issue.assigned", true);
    for (int i = 0; i < 3; i++) {
      assign(s, s.carol());
    }
    mailer.slow(s.carol().email(), Duration.ofMillis(300));
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      CountDownLatch start = new CountDownLatch(1);
      List<CompletableFuture<Void>> runs = List.of(1, 2).stream().map(i -> CompletableFuture.runAsync(() -> {
        try {
          start.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        sender.sendDue();
      }, pool)).toList();
      start.countDown();
      CompletableFuture.allOf(runs.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }
    drain();
    List<RecordingMailer.Sent> toCarol = mailer.sentTo(s.carol().email());
    assertThat(toCarol).hasSize(3);
    assertThat(toCarol).extracting(RecordingMailer.Sent::mailId).doesNotHaveDuplicates();
    assertThat(mails(s.carol())).extracting(m -> m.get("status") + "/" + m.get("attempts"))
        .containsExactly("SENT/1", "SENT/1", "SENT/1");
  }

  @Test
  void 로그_모드는_가린_주소와_종류와_제목_한_줄만_남기고_본문에는_댓글을_싣지_않는다() throws Exception {
    Space s = space();
    email(s.bob(), "issue.commented", true);
    int n = issue(s, s.alice(), Map.of("assigneeId", s.bob().id().toString(), "title", "로그 모드 일감"));
    api.post(s.carol(), "/workspaces/{ws}/issues/{n}/comments", Map.of("notes", "비밀 댓글 본문 XYZ-123"), s.ws(), n)
        .andExpect(status().is2xxSuccessful());
    drain();

    // 다른 시험이 남긴 메일도 함께 나가므로 이 일감의 줄만 본다
    List<String> lines = mailLog.list.stream().map(ILoggingEvent::getFormattedMessage)
        .filter(m -> m.contains("로그 모드 일감")).toList();
    assertThat(lines).singleElement().satisfies(line -> {
      assertThat(line).startsWith("[메일 · 로그 모드] 알림 메일").contains("받는 사람=" + Mailer.mask(s.bob().email()))
          .contains("종류=issue.commented").contains("제목=[moai-works] ");
      assertThat(line).doesNotContain(s.bob().email()).doesNotContain("XYZ-123").doesNotContain("https://")
          .doesNotContain("받는 까닭").doesNotContain("\n");
    });
    assertThat(mailer.sentTo(s.bob().email())).singleElement().satisfies(m -> {
      assertThat(m.subject()).startsWith("[moai-works] ").contains("댓글").contains("로그 모드 일감");
      assertThat(m.body()).contains("/w/" + s.ws() + "/issues/" + n).contains("받는 까닭: ")
          .contains("/notifications?settings=notifications").doesNotContain("XYZ-123");
    });

    // 언어 설정이 영어면 영어로 — 새 알림이 생겨야 새 메일이다(읽음으로 한 뒤 다시 댓글)
    jdbc.update("UPDATE user_profile SET locale = 'en' WHERE id = ?::uuid", s.bob().id().toString());
    api.post(s.bob(), "/me/notifications/read-all", null).andExpect(status().isOk());
    api.post(s.carol(), "/workspaces/{ws}/issues/{n}/comments", Map.of("notes", "두 번째 댓글"), s.ws(), n)
        .andExpect(status().is2xxSuccessful());
    drain();
    assertThat(mailer.sentTo(s.bob().email())).hasSize(2).last().satisfies(m -> {
      assertThat(m.subject()).contains("commented on").contains("“로그 모드 일감”");
      assertThat(m.body()).contains("Why you got this: ").doesNotContain("두 번째 댓글");
    });
  }

  @Test
  void 보내지_못하면_나중에_다시_보내고_다_쓰면_실패로_둔다() throws Exception {
    Space s = space();
    email(s.bob(), "issue.assigned", true);
    mailer.failNext(s.bob().email(), 1);
    assign(s, s.bob());
    drain();
    assertThat(mailer.sentTo(s.bob().email())).isEmpty();
    assertThat(mails(s.bob())).singleElement().satisfies(m -> {
      assertThat(m.get("status")).isEqualTo("PENDING");
      assertThat(m.get("attempts")).isEqualTo(1);
      assertThat((String) m.get("last_error")).startsWith("MailSendException").contains("***")
          .doesNotContain(s.bob().email());
    });
    // 다시 보낼 때(1분 뒤)가 아직 아니다
    drain();
    assertThat(mailer.sentTo(s.bob().email())).isEmpty();
    due(s.bob());
    drain();
    assertThat(mailer.sentTo(s.bob().email())).hasSize(1);
    assertThat(mails(s.bob())).singleElement().satisfies(m -> {
      assertThat(m.get("status")).isEqualTo("SENT");
      assertThat(m.get("attempts")).isEqualTo(2);
      assertThat(m.get("last_error")).isNull();
    });

    // 계속 실패하면 정해진 횟수까지만 하고 FAILED — 그 뒤로는 집지 않는다
    email(s.carol(), "issue.assigned", true);
    mailer.failNext(s.carol().email(), 99);
    assign(s, s.carol());
    for (int i = 0; i < NotificationMailSender.MAX_ATTEMPTS + 2; i++) {
      due(s.carol());
      drain();
    }
    assertThat(mailer.sentTo(s.carol().email())).isEmpty();
    assertThat(mails(s.carol())).singleElement().satisfies(m -> {
      assertThat(m.get("status")).isEqualTo("FAILED");
      assertThat(m.get("attempts")).isEqualTo(NotificationMailSender.MAX_ATTEMPTS);
    });
  }
}
