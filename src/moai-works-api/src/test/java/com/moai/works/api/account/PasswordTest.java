package com.moai.works.api.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.FakeKeycloak;
import com.moai.works.api.support.RecordingMailer;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestTokens;
import com.moai.works.api.support.TestUser;

import tools.jackson.databind.json.JsonMapper;

/**
 * 비밀번호 변경 · 재설정 (FR-WSP-006, SECURITY.md §7.5) — Keycloak 은 가짜(비밀번호 · 로그인 세션을 흉내 낸다), 나머지는 진짜다.
 *
 * <ul>
 *   <li>변경: 현재 비밀번호 확인(틀리면 400 — 401 아님) · 정책 오류 · 같은 비밀번호 · 틀린 횟수 제한 · 다른 로그인 끊기(이 로그인은 남김) ·
 *       감사(비밀번호 없음) · 메일 · 시스템 관리자</li>
 *   <li>재설정: 있는 · 없는 이메일의 응답이 같다 · 받을 수 없는 계정 · 코드 만료 · 횟수 · 한 번만 · 새 코드가 이전 코드를 무효로 ·
 *       로그인 모두 끊기 · 감사 · 속도 제한(IP · 이메일)</li>
 * </ul>
 */
@ApiTest
class PasswordTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Pattern CODE = Pattern.compile("코드: (\\d{6})");
  private static final AtomicInteger IP = new AtomicInteger();

  @Autowired
  TestApi api;

  @Autowired
  MockMvc mvc;

  @Autowired
  RecordingMailer mailer;

  @Autowired
  JdbcTemplate jdbc;

  private FakeKeycloak keycloak() {
    return api.keycloak();
  }

  private ResultActions change(TestUser user, String current, String next, Boolean signOutOthers) throws Exception {
    return changeWith(api.tokens().of(user), current, next, signOutOthers);
  }

  private ResultActions changeWith(String token, String current, String next, Boolean signOutOthers) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("currentPassword", current);
    body.put("newPassword", next);
    if (signOutOthers != null) {
      body.put("signOutOtherSessions", signOutOthers);
    }
    return api.callWithToken(token, HttpMethod.POST, "/me/password", body);
  }

  /** 공개 경로를 정한 IP 에서 부른다 — 속도 제한이 다른 시험과 섞이지 않게 */
  private ResultActions publicPost(String path, Object body, String ip) throws Exception {
    return mvc.perform(post(EndPointPaths.V1 + path).with(r -> {
      r.setRemoteAddr(ip);
      return r;
    }).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)));
  }

  private static String newIp() {
    int n = IP.incrementAndGet();
    return "10.28." + (n / 250) + "." + (n % 250 + 1);
  }

  private List<RecordingMailer.AccountMail> resetMails(TestUser user) {
    return mailer.accountMailsTo(user.email()).stream().filter(m -> m.subject().contains("비밀번호 재설정 코드")).toList();
  }

  private List<RecordingMailer.AccountMail> changedMails(TestUser user) {
    return mailer.accountMailsTo(user.email()).stream().filter(m -> m.subject().contains("비밀번호가 바뀌었습니다")).toList();
  }

  private String lastResetCode(TestUser user) {
    List<RecordingMailer.AccountMail> mails = resetMails(user);
    assertThat(mails).isNotEmpty();
    Matcher m = CODE.matcher(mails.get(mails.size() - 1).body());
    assertThat(m.find()).isTrue();
    return m.group(1);
  }

  private List<Map<String, Object>> audits(TestUser user, String action) {
    return jdbc.queryForList("select actor_id, details::text as details, workspace_id from audit_log"
        + " where action = ? and target_id = ?", action, user.id().toString());
  }

  /** 끊긴 뒤 새로 로그인한 토큰 — 다른 세션, 로그인 시작이 지금보다 뒤 */
  private String freshLogin(TestUser user, String sessionId) {
    long later = Instant.now().getEpochSecond() + 2;
    return api.tokens().of(user, c -> c.claim("sid", sessionId).claim("auth_time", later));
  }

  // ── 변경 ─────────────────────────────────────────────────────────────

  @Test
  void 맞는_현재_비밀번호면_바뀌고_감사와_메일이_남는다() throws Exception {
    TestUser user = api.user("pw-change");
    change(user, FakeKeycloak.DEFAULT_PASSWORD, "second-password-2", false).andExpect(status().isOk())
        .andExpect(jsonPath("$.otherSessionsEnded").value(false))
        .andExpect(jsonPath("$.currentSessionKept").value(true));

    assertThat(keycloak().passwordIs(user.id(), "second-password-2")).isTrue();
    List<Map<String, Object>> rows = audits(user, "account.password.change");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("actor_id")).hasToString(user.id().toString());
    assertThat(rows.get(0).get("workspace_id")).isNull();
    assertThat((String) rows.get(0).get("details")).doesNotContain("second-password-2").doesNotContain(FakeKeycloak.DEFAULT_PASSWORD);
    List<RecordingMailer.AccountMail> mails = changedMails(user);
    assertThat(mails).hasSize(1);
    assertThat(mails.get(0).body()).contains("개인 설정").doesNotContain("second-password-2")
        // MCP 키는 그대로라 남이 바꿨으면 키도 만료하라고(일감 #157)
        .contains("MCP 키는 비밀번호와 따로라 그대로입니다").contains("내 MCP 키에서 만료하십시오");
    // 이 로그인은 그대로
    api.get(user, "/me").andExpect(status().isOk());
  }

  @Test
  void 틀린_현재_비밀번호는_400_이고_로그인은_그대로다() throws Exception {
    TestUser user = api.user("pw-wrong");
    change(user, "not-my-password", "second-password-2", true).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"))
        .andExpect(jsonPath("$.attemptsLeft").value(PasswordAttemptLimiter.MAX_FAILURES - 1));

    assertThat(keycloak().passwordIs(user.id(), FakeKeycloak.DEFAULT_PASSWORD)).isTrue();
    assertThat(audits(user, "account.password.change")).isEmpty();
    assertThat(changedMails(user)).isEmpty();
    api.get(user, "/me").andExpect(status().isOk());
  }

  @Test
  void 정책에_맞지_않으면_PASSWORD_POLICY_와_까닭() throws Exception {
    TestUser user = api.user("pw-policy");
    keycloak().requireLength(user.id(), 12);
    change(user, FakeKeycloak.DEFAULT_PASSWORD, "eleven-char", false).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PASSWORD_POLICY"))
        .andExpect(jsonPath("$.reason").value("MIN_LENGTH"))
        .andExpect(jsonPath("$.value").value(12));
    assertThat(keycloak().passwordIs(user.id(), FakeKeycloak.DEFAULT_PASSWORD)).isTrue();

    // 8자보다 짧으면 Keycloak 까지 가지 않고 입력 검사에서 막힌다
    change(user, FakeKeycloak.DEFAULT_PASSWORD, "short", false).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void 새_비밀번호가_지금과_같으면_거절한다() throws Exception {
    TestUser user = api.user("pw-same");
    change(user, FakeKeycloak.DEFAULT_PASSWORD, FakeKeycloak.DEFAULT_PASSWORD, false).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PASSWORD_UNCHANGED"));
  }

  @Test
  void 현재_비밀번호를_5번_틀리면_맞아도_잠시_막힌다() throws Exception {
    TestUser user = api.user("pw-limit");
    for (int i = 0; i < PasswordAttemptLimiter.MAX_FAILURES; i++) {
      change(user, "wrong-" + i + "-password", "second-password-2", false).andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));
    }
    change(user, FakeKeycloak.DEFAULT_PASSWORD, "second-password-2", false).andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    assertThat(keycloak().passwordIs(user.id(), FakeKeycloak.DEFAULT_PASSWORD)).isTrue();
    // 다른 사람은 막히지 않는다
    TestUser other = api.user("pw-limit-other");
    change(other, FakeKeycloak.DEFAULT_PASSWORD, "second-password-2", false).andExpect(status().isOk());
  }

  @Test
  void Keycloak_이_잠근_계정은_423() throws Exception {
    TestUser user = api.user("pw-locked");
    keycloak().lock(user.id());
    change(user, FakeKeycloak.DEFAULT_PASSWORD, "second-password-2", false).andExpect(status().isLocked())
        .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
  }

  @Test
  void 다른_기기의_로그인을_끊으면_이_로그인만_남는다() throws Exception {
    TestUser user = api.user("pw-others");
    keycloak().startSession(user.id(), TestTokens.sessionOf(user));
    keycloak().startSession(user.id(), "other-device");
    String other = api.tokens().of(user, c -> c.claim("sid", "other-device"));
    api.callWithToken(other, HttpMethod.GET, "/me", null).andExpect(status().isOk());

    change(user, FakeKeycloak.DEFAULT_PASSWORD, "second-password-2", null).andExpect(status().isOk())
        .andExpect(jsonPath("$.otherSessionsEnded").value(true))
        .andExpect(jsonPath("$.currentSessionKept").value(true));

    // 다른 기기: 토큰이 아직 만료 전이어도 다음 요청부터 401 — Keycloak 세션도 지워져 갱신도 못 한다
    api.callWithToken(other, HttpMethod.GET, "/me", null).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    assertThat(keycloak().sessionsOf(user.id())).containsExactly(TestTokens.sessionOf(user));
    // 이 브라우저(같은 sid — 갱신한 토큰 포함)는 그대로
    api.get(user, "/me").andExpect(status().isOk());
    // 새로 로그인하면 된다
    api.callWithToken(freshLogin(user, "new-device"), HttpMethod.GET, "/me", null).andExpect(status().isOk());
    assertThat((String) audits(user, "account.password.change").get(0).get("details")).contains("\"otherSessionsEnded\": true");
    assertThat(changedMails(user).get(0).body()).contains("다른 기기");
  }

  @Test
  void 끊기를_고르지_않으면_다른_로그인도_그대로다() throws Exception {
    TestUser user = api.user("pw-keep");
    keycloak().startSession(user.id(), "other-device");
    String other = api.tokens().of(user, c -> c.claim("sid", "other-device"));
    change(user, FakeKeycloak.DEFAULT_PASSWORD, "second-password-2", false).andExpect(status().isOk());
    api.callWithToken(other, HttpMethod.GET, "/me", null).andExpect(status().isOk());
    assertThat(keycloak().sessionsOf(user.id())).contains("other-device");
  }

  @Test
  void 토큰에_세션_id_가_없으면_모두_끊고_다시_로그인하게_한다() throws Exception {
    TestUser user = api.user("pw-nosid");
    keycloak().startSession(user.id(), "some-session");
    String noSid = api.tokens().of(user, c -> c.claim("sid", null));
    changeWith(noSid, FakeKeycloak.DEFAULT_PASSWORD, "second-password-2", true).andExpect(status().isOk())
        .andExpect(jsonPath("$.otherSessionsEnded").value(true))
        .andExpect(jsonPath("$.currentSessionKept").value(false));
    api.callWithToken(noSid, HttpMethod.GET, "/me", null).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    assertThat(keycloak().sessionsOf(user.id())).isEmpty();
  }

  @Test
  void 시스템_관리자도_자기_비밀번호를_바꾼다() throws Exception {
    TestUser admin = api.user("pw-sysadmin", "system-admin");
    change(admin, FakeKeycloak.DEFAULT_PASSWORD, "admin-password-2", null).andExpect(status().isOk())
        .andExpect(jsonPath("$.currentSessionKept").value(true));
    assertThat(keycloak().passwordIs(admin.id(), "admin-password-2")).isTrue();
    assertThat(audits(admin, "account.password.change")).hasSize(1);
  }

  @Test
  void 서비스_계정은_비밀번호를_바꿀_수_없다() throws Exception {
    api.service("moai-works-automation", "/me/password",
        Map.of("currentPassword", "whatever-1", "newPassword", "whatever-2")).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("USER_ONLY"));
  }

  // ── 재설정 (비밀번호 찾기) ───────────────────────────────────────────

  @Test
  void 있는_이메일과_없는_이메일의_응답이_같다() throws Exception {
    TestUser user = api.user("reset-same");
    MockHttpServletResponse known = publicPost("/password-reset/request", Map.of("email", user.email()), newIp())
        .andExpect(status().isAccepted()).andReturn().getResponse();
    MockHttpServletResponse unknown = publicPost("/password-reset/request",
        Map.of("email", "nobody-" + user.id() + "@test.moai-works.local"), newIp())
        .andExpect(status().isAccepted()).andReturn().getResponse();

    assertThat(unknown.getStatus()).isEqualTo(known.getStatus());
    assertThat(unknown.getContentAsString()).isEqualTo(known.getContentAsString()).isEmpty();
    assertThat(Set.copyOf(unknown.getHeaderNames())).isEqualTo(Set.copyOf(known.getHeaderNames()));
    assertThat(resetMails(user)).hasSize(1);
    assertThat(mailer.accountMailsTo("nobody-" + user.id() + "@test.moai-works.local")).isEmpty();
  }

  @Test
  void 코드를_받을_수_없는_계정에는_보내지_않고_같은_응답이다() throws Exception {
    TestUser admin = api.user("reset-sysadmin", "system-admin");
    publicPost("/password-reset/request", Map.of("email", admin.email()), newIp()).andExpect(status().isAccepted());
    assertThat(resetMails(admin)).isEmpty();

    // 이메일 인증을 기다리는 계정(가입만 한 계정)
    String pending = "reset-pending-" + IP.incrementAndGet() + "@test.moai-works.local";
    publicPost("/signup", Map.of("email", pending, "password", "pending-password-1", "displayName", "대기",
        "agreeTerms", true), newIp()).andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));
    publicPost("/password-reset/request", Map.of("email", pending), newIp()).andExpect(status().isAccepted());
    assertThat(mailer.accountMailsTo(pending)).extracting(RecordingMailer.AccountMail::subject)
        .noneMatch(s -> s.contains("비밀번호 재설정"));
    // 그 계정으로 코드를 맞혀 보려 해도 같은 INVALID_CODE
    publicPost("/password-reset/confirm", Map.of("email", pending, "code", "123456", "newPassword", "taken-over-1"),
        newIp()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CODE"));
  }

  @Test
  void 코드로_다시_정하면_로그인이_모두_끊기고_감사와_메일이_남는다() throws Exception {
    TestUser user = api.user("reset-ok");
    keycloak().startSession(user.id(), TestTokens.sessionOf(user));
    keycloak().startSession(user.id(), "other-device");
    String old = api.tokens().of(user);
    publicPost("/password-reset/request", Map.of("email", user.email()), newIp()).andExpect(status().isAccepted());
    String code = lastResetCode(user);

    publicPost("/password-reset/confirm", Map.of("email", user.email(), "code", code, "newPassword", "reset-password-3"),
        newIp()).andExpect(status().isNoContent());

    assertThat(keycloak().passwordIs(user.id(), "reset-password-3")).isTrue();
    assertThat(keycloak().sessionsOf(user.id())).isEmpty();
    assertThat(keycloak().loginFailuresCleared(user.id())).isTrue();
    // 이미 나간 토큰도 다음 요청부터 막힌다(이 브라우저였던 것 포함)
    api.callWithToken(old, HttpMethod.GET, "/me", null).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    api.callWithToken(freshLogin(user, "after-reset"), HttpMethod.GET, "/me", null).andExpect(status().isOk());

    List<Map<String, Object>> rows = audits(user, "account.password.reset");
    assertThat(rows).hasSize(1);
    assertThat((String) rows.get(0).get("details")).doesNotContain(code).doesNotContain("reset-password-3");
    assertThat(changedMails(user)).hasSize(1);
    assertThat(changedMails(user).get(0).body()).contains("메일로 받은 코드").doesNotContain("reset-password-3")
        .contains("MCP 키는 비밀번호와 따로라 그대로입니다");
    // DB 에는 코드 원문이 없다(HMAC 만)
    assertThat(jdbc.queryForList("select code_hmac from email_verification where user_id = ?", String.class, user.id()))
        .isNotEmpty().allSatisfy(h -> assertThat(h).isNotEqualTo(code).hasSize(64));
  }

  @Test
  void 코드는_한_번만_쓴다() throws Exception {
    TestUser user = api.user("reset-once");
    publicPost("/password-reset/request", Map.of("email", user.email()), newIp()).andExpect(status().isAccepted());
    String code = lastResetCode(user);
    publicPost("/password-reset/confirm", Map.of("email", user.email(), "code", code, "newPassword", "reset-password-3"),
        newIp()).andExpect(status().isNoContent());
    publicPost("/password-reset/confirm", Map.of("email", user.email(), "code", code, "newPassword", "reset-password-4"),
        newIp()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CODE"));
    assertThat(keycloak().passwordIs(user.id(), "reset-password-3")).isTrue();
  }

  @Test
  void 코드를_5번_틀리면_맞는_코드도_거절한다() throws Exception {
    TestUser user = api.user("reset-attempts");
    publicPost("/password-reset/request", Map.of("email", user.email()), newIp()).andExpect(status().isAccepted());
    String code = lastResetCode(user);
    String wrong = String.format("%06d", (Integer.parseInt(code) + 1) % 1_000_000);
    String ip = newIp();
    for (int i = 0; i < EmailVerification.MAX_ATTEMPTS; i++) {
      publicPost("/password-reset/confirm", Map.of("email", user.email(), "code", wrong, "newPassword", "reset-password-3"),
          ip).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CODE"));
    }
    publicPost("/password-reset/confirm", Map.of("email", user.email(), "code", code, "newPassword", "reset-password-3"),
        ip).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CODE"));
    assertThat(keycloak().passwordIs(user.id(), FakeKeycloak.DEFAULT_PASSWORD)).isTrue();
  }

  @Test
  void 만료된_코드는_거절한다() throws Exception {
    TestUser user = api.user("reset-expired");
    publicPost("/password-reset/request", Map.of("email", user.email()), newIp()).andExpect(status().isAccepted());
    String code = lastResetCode(user);
    jdbc.update("update email_verification set expires_at = now() - interval '1 minute'"
        + " where user_id = ? and purpose = 'PASSWORD_RESET'", user.id());
    publicPost("/password-reset/confirm", Map.of("email", user.email(), "code", code, "newPassword", "reset-password-3"),
        newIp()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CODE"));
    assertThat(keycloak().passwordIs(user.id(), FakeKeycloak.DEFAULT_PASSWORD)).isTrue();
  }

  @Test
  void 새_코드를_받으면_이전_코드는_못_쓴다() throws Exception {
    TestUser user = api.user("reset-newer");
    publicPost("/password-reset/request", Map.of("email", user.email()), newIp()).andExpect(status().isAccepted());
    String first = lastResetCode(user);
    publicPost("/password-reset/request", Map.of("email", user.email()), newIp()).andExpect(status().isAccepted());
    String second = lastResetCode(user);
    if (!first.equals(second)) {
      publicPost("/password-reset/confirm", Map.of("email", user.email(), "code", first, "newPassword", "reset-password-3"),
          newIp()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CODE"));
    }
    publicPost("/password-reset/confirm", Map.of("email", user.email(), "code", second, "newPassword", "reset-password-3"),
        newIp()).andExpect(status().isNoContent());
  }

  @Test
  void 정책에_맞지_않으면_코드를_남겨_다시_해_볼_수_있다() throws Exception {
    TestUser user = api.user("reset-policy");
    keycloak().requireLength(user.id(), 12);
    publicPost("/password-reset/request", Map.of("email", user.email()), newIp()).andExpect(status().isAccepted());
    String code = lastResetCode(user);
    publicPost("/password-reset/confirm", Map.of("email", user.email(), "code", code, "newPassword", "eleven-char"),
        newIp()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_POLICY"))
        .andExpect(jsonPath("$.reason").value("MIN_LENGTH"));
    publicPost("/password-reset/confirm", Map.of("email", user.email(), "code", code, "newPassword", "twelve-chars-ok"),
        newIp()).andExpect(status().isNoContent());
    assertThat(keycloak().passwordIs(user.id(), "twelve-chars-ok")).isTrue();
  }

  @Test
  void 재설정_요청은_IP_와_이메일마다_속도를_제한한다() throws Exception {
    TestUser user = api.user("reset-limit");
    for (int i = 0; i < PasswordResetService.REQUESTS_PER_EMAIL_HOUR; i++) {
      publicPost("/password-reset/request", Map.of("email", user.email()), newIp()).andExpect(status().isAccepted());
    }
    publicPost("/password-reset/request", Map.of("email", user.email()), newIp()).andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    // 없는 이메일도 같은 수에서 막힌다 — 막히는지로 계정이 있는지 알 수 없다
    String nobody = "nobody-limit-" + user.id() + "@test.moai-works.local";
    for (int i = 0; i < PasswordResetService.REQUESTS_PER_EMAIL_HOUR; i++) {
      publicPost("/password-reset/request", Map.of("email", nobody), newIp()).andExpect(status().isAccepted());
    }
    publicPost("/password-reset/request", Map.of("email", nobody), newIp()).andExpect(status().isTooManyRequests());

    String ip = newIp();
    for (int i = 0; i < PasswordResetService.REQUESTS_PER_IP_HOUR; i++) {
      publicPost("/password-reset/request", Map.of("email", "ip-" + i + "-" + user.id() + "@test.moai-works.local"), ip)
          .andExpect(status().isAccepted());
    }
    publicPost("/password-reset/request", Map.of("email", "ip-x-" + user.id() + "@test.moai-works.local"), ip)
        .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("RATE_LIMITED"));
  }

  @Test
  void 비밀번호_찾기는_토큰_없이_부른다() throws Exception {
    api.call(null, HttpMethod.POST, "/password-reset/request", Map.of("email", "anyone-" + IP.incrementAndGet() + "@test.moai-works.local"))
        .andExpect(status().isAccepted());
  }

  // ── 가입 인증 코드도 같은 장치다 (VerificationCodes 로 옮긴 뒤에도 그대로) ──

  @Test
  void 가입_인증_코드는_그대로_돈다() throws Exception {
    String email = "signup-code-" + IP.incrementAndGet() + "@test.moai-works.local";
    publicPost("/signup", Map.of("email", email, "password", "signup-password-1", "displayName", "가입",
        "agreeTerms", true), newIp()).andExpect(status().isCreated());
    List<RecordingMailer.AccountMail> mails = mailer.accountMailsTo(email);
    assertThat(mails).hasSize(1);
    Matcher m = CODE.matcher(mails.get(0).body());
    assertThat(m.find()).isTrue();
    publicPost("/signup/verify", Map.of("email", email, "code", m.group(1)), newIp()).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }
}
