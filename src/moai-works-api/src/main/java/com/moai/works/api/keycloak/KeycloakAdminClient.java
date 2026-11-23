package com.moai.works.api.keycloak;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import com.moai.works.api.config.MoaiWorksProperties;

/**
 * Keycloak 관리 API — api 서비스 계정(client_credentials)으로 부른다.
 *
 * <p>쓰는 일: 계정 만들기 · 켜기/끄기, 조직(워크스페이스) · 조직 그룹(역할) · 멤버 · 그룹 가입, 그룹에 권한 코드(클라이언트 역할) 매핑.
 * 필요한 권한은 infra/keycloak/ensure-realm.py 가 서비스 계정에 준다(manage-users · manage-realm · view-clients …).
 *
 * <p>토큰은 만료 30초 전까지 재사용하고, 401 이면 한 번 새로 받아 다시 부른다.
 * Keycloak 이 실패하면 {@link KeycloakException}(상태 코드 포함). 409 는 {@link KeycloakException#conflict()}.
 */
@Component
public class KeycloakAdminClient {

  private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
  };
  private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
  };

  private final MoaiWorksProperties.Keycloak keycloak;
  private final RestClient rest;
  private final Map<String, String> clientUuids = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Object>> clientRoles = new ConcurrentHashMap<>();
  private volatile CachedToken token;

  public KeycloakAdminClient(MoaiWorksProperties properties) {
    this.keycloak = properties.keycloak();
    this.rest = RestClient.builder().baseUrl(keycloak.url() + "/admin/realms/" + keycloak.realm()).build();
  }

  // ── 사용자 ───────────────────────────────────────────────────────────

  /** 사용자를 만들고 id 를 돌려준다. 같은 username · email 이 있으면 409. 비밀번호 정책 위반이면 400. */
  public String createUser(NewUser user) {
    Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("username", user.username());
    body.put("email", user.email());
    body.put("emailVerified", user.emailVerified());
    body.put("enabled", user.enabled());
    body.put("firstName", user.displayName());
    body.put("attributes", Map.of("moai.origin", List.of(user.origin())));
    if (user.password() != null) {
      body.put("credentials", List.of(Map.of("type", "password", "value", user.password(), "temporary", false)));
    }
    ResponseEntity<Void> response = call(c -> c.post().uri("/users").contentType(MediaType.APPLICATION_JSON)
        .body(body).retrieve().toBodilessEntity());
    return idFromLocation(response.getHeaders().getLocation());
  }

  public Optional<Map<String, Object>> findUser(String userId) {
    try {
      return Optional.ofNullable(call(c -> c.get().uri("/users/{id}", userId).retrieve().body(MAP)));
    } catch (KeycloakException e) {
      if (e.notFound()) {
        return Optional.empty();
      }
      throw e;
    }
  }

  /** 사용자 일부 속성만 바꾼다(enabled · emailVerified · firstName 등). */
  public void updateUser(String userId, Map<String, Object> changes) {
    Map<String, Object> current = findUser(userId).orElseThrow(() -> new KeycloakException(404, "사용자가 없습니다: " + userId));
    current.putAll(changes);
    call(c -> c.put().uri("/users/{id}", userId).contentType(MediaType.APPLICATION_JSON).body(current)
        .retrieve().toBodilessEntity());
  }

  public void deleteUser(String userId) {
    try {
      call(c -> c.delete().uri("/users/{id}", userId).retrieve().toBodilessEntity());
    } catch (KeycloakException e) {
      if (!e.notFound()) {
        throw e;
      }
    }
  }

  // ── 조직 = 워크스페이스 ───────────────────────────────────────────────

  /** 조직을 만들고 id 를 돌려준다. alias 가 겹치면 409. */
  public String createOrganization(String name, String alias, Map<String, List<String>> attributes) {
    Map<String, Object> body = Map.of("name", name, "alias", alias, "enabled", true, "attributes", attributes);
    ResponseEntity<Void> response = call(c -> c.post().uri("/organizations").contentType(MediaType.APPLICATION_JSON)
        .body(body).retrieve().toBodilessEntity());
    return idFromLocation(response.getHeaders().getLocation());
  }

  /** 속성으로 조직 찾기 — 만들다가 끊긴 단계를 이어 갈 때 쓴다. */
  public Optional<String> findOrganizationByAttribute(String key, String value) {
    List<Map<String, Object>> found = call(c -> c.get()
        .uri(b -> b.path("/organizations").queryParam("q", key + ":" + value).queryParam("briefRepresentation", true)
            .build())
        .retrieve().body(LIST));
    return found == null || found.isEmpty() ? Optional.empty() : Optional.of(String.valueOf(found.get(0).get("id")));
  }

  /** 조직 멤버로 넣는다. 이미 멤버면 아무 일도 하지 않는다. */
  public void addOrganizationMember(String orgId, String userId) {
    try {
      call(c -> c.post().uri("/organizations/{org}/members", orgId).contentType(MediaType.APPLICATION_JSON)
          .body("\"" + userId + "\"").retrieve().toBodilessEntity());
    } catch (KeycloakException e) {
      if (!e.conflict()) {
        throw e;
      }
    }
  }

  public void removeOrganizationMember(String orgId, String userId) {
    try {
      call(c -> c.delete().uri("/organizations/{org}/members/{user}", orgId, userId).retrieve().toBodilessEntity());
    } catch (KeycloakException e) {
      if (!e.notFound()) {
        throw e;
      }
    }
  }

  // ── 조직 그룹 = 역할 ─────────────────────────────────────────────────

  /** 조직의 최상위 그룹을 찾거나 만든다. */
  public String ensureOrganizationGroup(String orgId, String name) {
    return findGroup(call(c -> c.get().uri("/organizations/{org}/groups", orgId).retrieve().body(LIST)), name)
        .orElseGet(() -> {
          ResponseEntity<Void> r = call(c -> c.post().uri("/organizations/{org}/groups", orgId)
              .contentType(MediaType.APPLICATION_JSON).body(Map.of("name", name)).retrieve().toBodilessEntity());
          return idOrLookup(r, () -> ensureOrganizationGroup(orgId, name));
        });
  }

  /** 조직 그룹 아래 하위 그룹을 찾거나 만든다. */
  public String ensureOrganizationSubGroup(String orgId, String parentId, String name) {
    return findGroup(call(c -> c.get().uri("/organizations/{org}/groups/{g}/children", orgId, parentId)
        .retrieve().body(LIST)), name)
        .orElseGet(() -> {
          ResponseEntity<Void> r = call(c -> c.post().uri("/organizations/{org}/groups/{g}/children", orgId, parentId)
              .contentType(MediaType.APPLICATION_JSON).body(Map.of("name", name)).retrieve().toBodilessEntity());
          return idOrLookup(r, () -> ensureOrganizationSubGroup(orgId, parentId, name));
        });
  }

  /** 조직 그룹을 지운다(사용자 정의 역할 삭제). 없으면 아무 일도 하지 않는다. */
  public void deleteOrganizationGroup(String orgId, String groupId) {
    try {
      call(c -> c.delete().uri("/organizations/{org}/groups/{g}", orgId, groupId).retrieve().toBodilessEntity());
    } catch (KeycloakException e) {
      if (!e.notFound()) {
        throw e;
      }
    }
  }

  /**
   * 조직을 켜고 끈다(시스템 관리자의 워크스페이스 사용 중지 · 다시 사용). 꺼진 조직은 토큰의 organization 클레임에 실리지 않는다.
   * 멤버는 UNMANAGED 라 로그인은 그대로다 — 막는 것은 api 의 판정(DB 사본의 워크스페이스 상태)이다.
   * Keycloak 의 조직 고치기(PUT)는 받은 표현 전체로 바꾸므로, 지금 표현을 읽어 enabled 만 바꿔 다시 보낸다(이름 · 별칭 · 속성 · 도메인 보존).
   */
  public void setOrganizationEnabled(String orgId, boolean enabled) {
    Map<String, Object> current = call(c -> c.get().uri("/organizations/{org}", orgId).retrieve().body(MAP));
    if (current == null) {
      throw new KeycloakException(404, "조직이 없습니다: " + orgId);
    }
    if (Boolean.valueOf(enabled).equals(current.get("enabled"))) {
      return;
    }
    Map<String, Object> body = new java.util.LinkedHashMap<>(current);
    body.put("enabled", enabled);
    call(c -> c.put().uri("/organizations/{org}", orgId).contentType(MediaType.APPLICATION_JSON).body(body)
        .retrieve().toBodilessEntity());
  }

  /** 조직을 지운다(개발용 초기화 · 워크스페이스 삭제). 없으면 아무 일도 하지 않는다. */
  public void deleteOrganization(String orgId) {
    try {
      call(c -> c.delete().uri("/organizations/{org}", orgId).retrieve().toBodilessEntity());
    } catch (KeycloakException e) {
      if (!e.notFound()) {
        throw e;
      }
    }
  }

  public void joinOrganizationGroup(String orgId, String groupId, String userId) {
    call(c -> c.put().uri("/organizations/{org}/groups/{g}/members/{u}", orgId, groupId, userId)
        .retrieve().toBodilessEntity());
  }

  public void leaveOrganizationGroup(String orgId, String groupId, String userId) {
    try {
      call(c -> c.delete().uri("/organizations/{org}/groups/{g}/members/{u}", orgId, groupId, userId)
          .retrieve().toBodilessEntity());
    } catch (KeycloakException e) {
      if (!e.notFound()) {
        throw e;
      }
    }
  }

  /** 조직 그룹에 매핑된 이 API 클라이언트 역할(권한 코드) 이름들. */
  public List<String> groupPermissions(String orgId, String groupId) {
    String api = clientUuid(keycloak.clientId());
    List<Map<String, Object>> roles = call(c -> c.get()
        .uri("/organizations/{org}/groups/{g}/role-mappings/clients/{client}", orgId, groupId, api).retrieve().body(LIST));
    return roles == null ? List.of() : roles.stream().map(r -> String.valueOf(r.get("name"))).sorted().toList();
  }

  /** 조직 그룹의 권한 코드를 원하는 집합으로 맞춘다(더하고 뺀다). */
  public void setGroupPermissions(String orgId, String groupId, java.util.Collection<String> permissions) {
    String api = clientUuid(keycloak.clientId());
    List<String> current = groupPermissions(orgId, groupId);
    List<Map<String, Object>> add = permissions.stream().filter(p -> !current.contains(p)).map(this::apiRole).toList();
    List<Map<String, Object>> remove = current.stream().filter(p -> !permissions.contains(p)).map(this::apiRole).toList();
    if (!add.isEmpty()) {
      call(c -> c.post().uri("/organizations/{org}/groups/{g}/role-mappings/clients/{client}", orgId, groupId, api)
          .contentType(MediaType.APPLICATION_JSON).body(add).retrieve().toBodilessEntity());
    }
    if (!remove.isEmpty()) {
      call(c -> c.method(org.springframework.http.HttpMethod.DELETE)
          .uri("/organizations/{org}/groups/{g}/role-mappings/clients/{client}", orgId, groupId, api)
          .contentType(MediaType.APPLICATION_JSON).body(remove).retrieve().toBodilessEntity());
    }
  }

  /** 이 API 클라이언트에 정의된 권한 코드(클라이언트 역할) 이름 — 기동 때 카탈로그와 대조한다. */
  public List<String> definedPermissions() {
    String api = clientUuid(keycloak.clientId());
    List<Map<String, Object>> roles = call(c -> c.get().uri("/clients/{c}/roles", api).retrieve().body(LIST));
    return roles == null ? List.of() : roles.stream().map(r -> String.valueOf(r.get("name"))).sorted().toList();
  }

  // ── 내부 ─────────────────────────────────────────────────────────────

  private Map<String, Object> apiRole(String name) {
    return clientRoles.computeIfAbsent(name, n -> {
      String api = clientUuid(keycloak.clientId());
      Map<String, Object> role = call(c -> c.get().uri("/clients/{c}/roles/{r}", api, n).retrieve().body(MAP));
      if (role == null) {
        throw new KeycloakException(404, "권한 코드가 Keycloak 에 없습니다: " + n);
      }
      return Map.of("id", role.get("id"), "name", role.get("name"));
    });
  }

  private String clientUuid(String clientId) {
    return clientUuids.computeIfAbsent(clientId, id -> {
      List<Map<String, Object>> found = call(c -> c.get()
          .uri((UriBuilder b) -> b.path("/clients").queryParam("clientId", id).build()).retrieve().body(LIST));
      if (found == null || found.isEmpty()) {
        throw new KeycloakException(404, "클라이언트가 없습니다: " + id);
      }
      return String.valueOf(found.get(0).get("id"));
    });
  }

  private static Optional<String> findGroup(List<Map<String, Object>> groups, String name) {
    if (groups == null) {
      return Optional.empty();
    }
    return groups.stream().filter(g -> name.equals(g.get("name"))).map(g -> String.valueOf(g.get("id"))).findFirst();
  }

  private static String idOrLookup(ResponseEntity<Void> response, java.util.function.Supplier<String> lookup) {
    URI location = response.getHeaders().getLocation();
    return location != null ? idFromLocation(location) : lookup.get();
  }

  private static String idFromLocation(URI location) {
    if (location == null) {
      throw new KeycloakException(500, "Keycloak 응답에 Location 이 없습니다");
    }
    String path = location.getPath();
    return path.substring(path.lastIndexOf('/') + 1);
  }

  /** 토큰을 붙여 부른다. 401 이면 토큰을 새로 받아 한 번만 다시 부른다. */
  private <T> T call(Function<RestClient, T> request) {
    try {
      return withToken(request, false);
    } catch (HttpStatusCodeException e) {
      if (e.getStatusCode().value() == 401) {
        try {
          return withToken(request, true);
        } catch (HttpStatusCodeException retry) {
          throw KeycloakException.of(retry);
        }
      }
      throw KeycloakException.of(e);
    } catch (RestClientException e) {
      throw new KeycloakException(503, "Keycloak 에 연결할 수 없습니다: " + e.getMessage());
    }
  }

  private <T> T withToken(Function<RestClient, T> request, boolean refresh) {
    String bearer = accessToken(refresh);
    RestClient authed = rest.mutate().defaultHeaders(h -> h.setBearerAuth(bearer)).build();
    return request.apply(authed);
  }

  private String accessToken(boolean refresh) {
    CachedToken current = token;
    if (!refresh && current != null && Instant.now().isBefore(current.expiresAt())) {
      return current.value();
    }
    synchronized (this) {
      current = token;
      if (!refresh && current != null && Instant.now().isBefore(current.expiresAt())) {
        return current.value();
      }
      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("grant_type", "client_credentials");
      Map<String, Object> body;
      try {
        body = RestClient.create().post()
            .uri(keycloak.url() + "/realms/" + keycloak.realm() + "/protocol/openid-connect/token")
            .headers(h -> h.setBasicAuth(keycloak.clientId(), keycloak.clientSecret()))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(MAP);
      } catch (HttpStatusCodeException e) {
        throw new KeycloakException(e.getStatusCode().value(), "api 서비스 계정 토큰을 받지 못했습니다");
      } catch (RestClientException e) {
        throw new KeycloakException(503, "Keycloak 에 연결할 수 없습니다: " + e.getMessage());
      }
      if (body == null || body.get("access_token") == null) {
        throw new KeycloakException(502, "Keycloak 토큰 응답이 비었습니다");
      }
      long ttl = body.get("expires_in") instanceof Number n ? n.longValue() : 60;
      token = new CachedToken(String.valueOf(body.get("access_token")),
          Instant.now().plus(Duration.ofSeconds(Math.max(5, ttl - 30))));
      return token.value();
    }
  }

  private record CachedToken(String value, Instant expiresAt) {
  }

  /**
   * 새 사용자.
   *
   * @param origin Keycloak 사용자 속성 moai.origin — 누가 만든 계정인지 (SIGNUP · SEED …)
   */
  public record NewUser(String username, String email, String displayName, String password, boolean enabled,
      boolean emailVerified, String origin) {
  }
}
