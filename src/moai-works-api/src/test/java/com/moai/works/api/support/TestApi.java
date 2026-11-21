package com.moai.works.api.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.moai.works.api.config.EndPointPaths;

import tools.jackson.databind.json.JsonMapper;

/**
 * 테스트가 api 를 부르는 손잡이. 경로는 {@code /moai-works-api/v1} 아래를 적는다(예: {@code "/workspaces/{ws}"}).
 *
 * <p>사람과 워크스페이스는 진짜 API 로 만든다 — 사람은 로그인({@code POST /me/login})하면 계정 사본과 개인 워크스페이스가 생기고,
 * 멤버는 가입 신청 → 승인으로 들어온다. 그래서 준비 단계도 운영과 같은 길을 지난다.
 */
public final class TestApi {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final MockMvc mvc;
  private final TestTokens tokens;
  private final FakeKeycloak keycloak;
  /** 팀 워크스페이스 만들기를 허용해 주는 시스템 관리자 — 처음 필요할 때 만든다 */
  private TestUser platformAdmin;

  public TestApi(MockMvc mvc, TestTokens tokens, FakeKeycloak keycloak) {
    this.mvc = mvc;
    this.tokens = tokens;
    this.keycloak = keycloak;
  }

  public FakeKeycloak keycloak() {
    return keycloak;
  }

  public TestTokens tokens() {
    return tokens;
  }

  // ── 요청 ─────────────────────────────────────────────────────────────

  public ResultActions get(TestUser as, String path, Object... vars) throws Exception {
    return call(as, HttpMethod.GET, path, null, vars);
  }

  public ResultActions post(TestUser as, String path, Object body, Object... vars) throws Exception {
    return call(as, HttpMethod.POST, path, body, vars);
  }

  public ResultActions put(TestUser as, String path, Object body, Object... vars) throws Exception {
    return call(as, HttpMethod.PUT, path, body, vars);
  }

  public ResultActions patch(TestUser as, String path, Object body, Object... vars) throws Exception {
    return call(as, HttpMethod.PATCH, path, body, vars);
  }

  public ResultActions delete(TestUser as, String path, Object... vars) throws Exception {
    return call(as, HttpMethod.DELETE, path, null, vars);
  }

  /** @param as null 이면 토큰 없이 부른다 */
  public ResultActions call(TestUser as, HttpMethod method, String path, Object body, Object... vars) throws Exception {
    return callWithToken(as == null ? null : tokens.of(as), method, path, body, vars);
  }

  /** 토큰 문자열을 직접 넣는다 (만료 · 다른 audience · 서비스 계정 시험) */
  public ResultActions callWithToken(String token, HttpMethod method, String path, Object body, Object... vars)
      throws Exception {
    MockHttpServletRequestBuilder builder = request(method, EndPointPaths.V1 + path, vars);
    if (token != null) {
      builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
    if (body != null) {
      builder.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
    }
    return mvc.perform(builder);
  }

  /** 파일 하나를 올린다(multipart 의 file 칸). params 는 함께 보내는 값(path 등) */
  public ResultActions upload(TestUser as, String path, String filename, String contentType, byte[] content,
      Map<String, String> params, Object... vars) throws Exception {
    var builder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .multipart(EndPointPaths.V1 + path, vars)
        .file(new org.springframework.mock.web.MockMultipartFile("file", filename, contentType, content));
    params.forEach(builder::param);
    builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.of(as));
    return mvc.perform(builder);
  }

  /** 서비스 계정(Keycloak client_credentials)으로 부른다 — 바깥 자동화의 입구를 시험할 때 */
  public ResultActions service(String clientId, String path, Object body, Object... vars) throws Exception {
    return callWithToken(tokens.ofService(clientId), HttpMethod.POST, path, body, vars);
  }

  /** 응답 본문에서 값 하나를 꺼낸다 (JsonPath) */
  public static <T> T read(ResultActions result, String jsonPath) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
        jsonPath);
  }

  // ── 준비: 사람 · 워크스페이스 · 멤버 ─────────────────────────────────

  /** 새 사람을 만들고 로그인시킨다 → 계정 사본(ACTIVE)과 개인 워크스페이스가 생긴다. */
  public TestUser user(String label, String... platformRoles) throws Exception {
    TestUser user = TestUser.create(label, platformRoles);
    keycloak.register(user);
    post(user, "/me/login", null).andExpect(status().isOk());
    return user;
  }

  /** 그 사람의 개인 워크스페이스 주소 */
  public String personalWorkspace(TestUser user) throws Exception {
    return read(get(user, "/me").andExpect(status().isOk()), "$.personalWorkspace");
  }

  /** 시스템 관리자가 그 계정이 만들 수 있는 팀 워크스페이스 수를 정한다(가입한 계정은 0 이라 못 만든다). */
  public void allowTeamWorkspaces(TestUser account, int limit) throws Exception {
    if (platformAdmin == null) {
      platformAdmin = user("platform-admin", "system-admin");
    }
    put(platformAdmin, "/admin/accounts/{id}/team-workspace-limit", Map.of("limit", limit), account.id())
        .andExpect(status().isOk());
  }

  /** 팀 워크스페이스를 만든다(공개 · 승인 가입). 만든 사람이 소유자다. 먼저 넉넉히 허용해 둔다. @return 주소(slug) */
  public String teamWorkspace(TestUser owner, String name) throws Exception {
    allowTeamWorkspaces(owner, 20);
    return read(post(owner, "/workspaces",
        Map.of("name", name, "visibility", "PUBLIC", "joinPolicy", "APPROVAL")).andExpect(status().isCreated()),
        "$.slug");
  }

  /** 가입 신청 → 승인으로 멤버를 넣는다. approver 는 그 역할을 줄 수 있는 사람이어야 한다. */
  public void join(String workspace, TestUser approver, TestUser newcomer, String roleKey) throws Exception {
    String requestId = read(post(newcomer, "/workspaces/{ws}/join-requests", Map.of("message", "테스트"), workspace)
        .andExpect(status().isCreated()), "$.id");
    post(approver, "/workspaces/{ws}/join-requests/{id}/approve", Map.of("roleKey", roleKey), workspace, requestId)
        .andExpect(status().isOk());
  }

  /** 사용자 정의 역할을 만든다. @return 역할 키 (custom-…) */
  public String customRole(String workspace, TestUser by, String name, Set<String> permissions) throws Exception {
    return read(post(by, "/workspaces/{ws}/roles", Map.of("name", name, "permissions", permissions), workspace)
        .andExpect(status().isCreated()), "$.key");
  }

  public void setRoles(String workspace, TestUser by, TestUser target, String... roles) throws Exception {
    put(by, "/workspaces/{ws}/members/{user}/roles", Map.of("roles", List.of(roles)), workspace, target.id())
        .andExpect(status().isOk());
  }
}
