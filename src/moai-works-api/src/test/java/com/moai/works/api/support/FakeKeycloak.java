package com.moai.works.api.support;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.moai.works.api.config.MoaiWorksProperties;
import com.moai.works.api.keycloak.KeycloakAdminClient;
import com.moai.works.api.keycloak.KeycloakException;
import com.moai.works.api.workspace.Permission;

/**
 * 메모리에서 도는 가짜 Keycloak 관리 API. 자동 테스트는 진짜 Keycloak 을 부르지 않는다.
 *
 * <p>진짜 클라이언트와 같은 약속을 지킨다: 같은 username · email 은 409, 없는 대상을 지우면 아무 일도 없음,
 * 그룹은 이름으로 찾아 있으면 그 id. 테스트가 "Keycloak 쪽 상태가 DB 사본과 같은가"를 볼 수 있게 조회 메서드를 둔다.
 */
public class FakeKeycloak extends KeycloakAdminClient {

  private final Map<String, Map<String, Object>> users = new ConcurrentHashMap<>();
  private final Map<String, Org> orgs = new ConcurrentHashMap<>();
  /** 사용자 id → 비밀번호 (진짜 Keycloak 처럼 바깥에 내주지 않는다 — 시험은 확인 결과로만 본다) */
  private final Map<String, String> passwords = new ConcurrentHashMap<>();
  /** 사용자 id → 로그인 세션 id 들 */
  private final Map<String, Set<String>> sessions = new ConcurrentHashMap<>();
  /** 무차별 대입 잠금을 지운 사용자 id */
  private final Set<String> failuresCleared = ConcurrentHashMap.newKeySet();
  /** 잠긴 것처럼 굴 사용자 id */
  private final Set<String> locked = ConcurrentHashMap.newKeySet();
  /** 사용자마다 더 긴 최소 길이 — 렐름 정책이 화면 · api 의 8자보다 길 때를 흉내 낸다 */
  private final Map<String, Integer> minLength = new ConcurrentHashMap<>();

  public FakeKeycloak(MoaiWorksProperties properties) {
    super(properties);
  }

  private static final class Org {
    final String alias;
    final Map<String, List<String>> attributes;
    final Set<String> members = ConcurrentHashMap.newKeySet();
    final Map<String, Group> groups = new ConcurrentHashMap<>();
    volatile boolean enabled = true;

    Org(String alias, Map<String, List<String>> attributes) {
      this.alias = alias;
      this.attributes = attributes;
    }
  }

  private static final class Group {
    final String name;
    final String parentId;
    final Set<String> members = ConcurrentHashMap.newKeySet();
    final Set<String> permissions = ConcurrentHashMap.newKeySet();

    Group(String name, String parentId) {
      this.name = name;
      this.parentId = parentId;
    }
  }

  // ── 테스트가 보는 상태 ──────────────────────────────────────────────

  public boolean isOrganizationMember(String orgId, String userId) {
    return org(orgId).members.contains(userId);
  }

  public boolean organizationExists(String orgId) {
    return orgs.containsKey(orgId);
  }

  /** 조직이 켜져 있는가 (워크스페이스 사용 중지면 꺼진다) */
  public boolean organizationEnabled(String orgId) {
    return org(orgId).enabled;
  }

  /** 그 사람이 들어 있는 조직 그룹 이름들 (예: owner, member, custom-…) */
  public Set<String> groupNamesOf(String orgId, String userId) {
    Set<String> names = new TreeSet<>();
    org(orgId).groups.values().stream().filter(g -> g.members.contains(userId)).forEach(g -> names.add(g.name));
    return names;
  }

  /** 토큰으로만 만든 테스트 사람을 Keycloak 에 있는 계정으로 올린다 (정지 · 복구가 이 계정을 찾는다). */
  public void register(TestUser user) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("id", user.id().toString());
    body.put("username", user.username());
    body.put("email", user.email());
    body.put("emailVerified", true);
    body.put("enabled", true);
    body.put("firstName", user.displayName());
    users.put(user.id().toString(), body);
    passwords.putIfAbsent(user.id().toString(), DEFAULT_PASSWORD);
  }

  /** 시험용 처음 비밀번호를 정한다(register 한 사람은 {@link #DEFAULT_PASSWORD}) */
  public void setPassword(UUID userId, String password) {
    passwords.put(userId.toString(), password);
  }

  /** 그 비밀번호가 지금 비밀번호인가 */
  public boolean passwordIs(UUID userId, String password) {
    return password.equals(passwords.get(userId.toString()));
  }

  /** 로그인 세션 하나를 만든다(브라우저 하나의 로그인) — 토큰의 sid 와 같은 값을 쓴다 */
  public void startSession(UUID userId, String sessionId) {
    sessions.computeIfAbsent(userId.toString(), k -> ConcurrentHashMap.newKeySet()).add(sessionId);
  }

  /** 남아 있는 로그인 세션 id 들 */
  public Set<String> sessionsOf(UUID userId) {
    return new TreeSet<>(sessions.getOrDefault(userId.toString(), Set.of()));
  }

  public boolean loginFailuresCleared(UUID userId) {
    return failuresCleared.contains(userId.toString());
  }

  /** 그 사람의 비밀번호 최소 길이를 늘린다(렐름 정책이 바뀐 것처럼) */
  public void requireLength(UUID userId, int length) {
    minLength.put(userId.toString(), length);
  }

  /** 무차별 대입 잠금에 걸린 것처럼 — 비밀번호 확인이 LOCKED */
  public void lock(UUID userId) {
    locked.add(userId.toString());
  }

  public boolean userExists(String userId) {
    return users.containsKey(userId);
  }

  public boolean userEnabled(String userId) {
    return Boolean.TRUE.equals(users.get(userId).get("enabled"));
  }

  // ── 사용자 ───────────────────────────────────────────────────────────

  @Override
  public String createUser(NewUser user) {
    boolean taken = users.values().stream().anyMatch(u -> user.username().equalsIgnoreCase(String.valueOf(u.get("username")))
        || user.email().equalsIgnoreCase(String.valueOf(u.get("email"))));
    if (taken) {
      throw new KeycloakException(409, "Keycloak 409: User exists with same username or email");
    }
    String id = UUID.randomUUID().toString();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("id", id);
    body.put("username", user.username());
    body.put("email", user.email());
    body.put("emailVerified", user.emailVerified());
    body.put("enabled", user.enabled());
    body.put("firstName", user.displayName());
    if (user.password() != null) {
      checkPolicy(null, user.password());
      passwords.put(id, user.password());
    }
    users.put(id, body);
    return id;
  }

  /** register 한 사람의 처음 비밀번호 */
  public static final String DEFAULT_PASSWORD = "first-password-1";

  /** 정책은 렐름과 같은 length(8)(사람마다 늘릴 수 있다) — 진짜 Keycloak 의 오류 본문 모양으로 400 */
  private void checkPolicy(String userId, String password) {
    int min = userId == null ? 8 : minLength.getOrDefault(userId, 8);
    if (password.length() < min) {
      throw new KeycloakException(400, "Keycloak 400: invalid password",
          "{\"error\":\"invalidPasswordMinLengthMessage\",\"error_description\":\"Invalid password: minimum length " + min + ".\"}");
    }
  }

  @Override
  public PasswordCheck checkPassword(String username, String password) {
    Map<String, Object> user = users.values().stream()
        .filter(u -> username.equalsIgnoreCase(String.valueOf(u.get("username")))).findFirst().orElse(null);
    if (user == null) {
      return PasswordCheck.INVALID;
    }
    String id = String.valueOf(user.get("id"));
    if (locked.contains(id)) {
      return PasswordCheck.LOCKED;
    }
    if (!Boolean.TRUE.equals(user.get("enabled"))) {
      return PasswordCheck.DISABLED;
    }
    // 진짜와 같이 확인으로 생긴 세션은 바로 끝내므로 세션이 남지 않는다
    return password.equals(passwords.get(id)) ? PasswordCheck.OK : PasswordCheck.INVALID;
  }

  @Override
  public void resetPassword(String userId, String password) {
    if (!users.containsKey(userId)) {
      throw new KeycloakException(404, "사용자가 없습니다: " + userId);
    }
    checkPolicy(userId, password);
    passwords.put(userId, password);
  }

  @Override
  public List<String> userSessionIds(String userId) {
    return List.copyOf(sessions.getOrDefault(userId, Set.of()));
  }

  @Override
  public void deleteSession(String sessionId) {
    sessions.values().forEach(s -> s.remove(sessionId));
  }

  @Override
  public void logoutUser(String userId) {
    sessions.remove(userId);
  }

  @Override
  public void clearLoginFailures(String userId) {
    failuresCleared.add(userId);
    locked.remove(userId);
  }

  @Override
  public Optional<Map<String, Object>> findUser(String userId) {
    Map<String, Object> found = users.get(userId);
    return found == null ? Optional.empty() : Optional.of(new LinkedHashMap<>(found));
  }

  @Override
  public void updateUser(String userId, Map<String, Object> changes) {
    Map<String, Object> current = users.get(userId);
    if (current == null) {
      throw new KeycloakException(404, "사용자가 없습니다: " + userId);
    }
    current.putAll(changes);
  }

  @Override
  public void deleteUser(String userId) {
    users.remove(userId);
  }

  // ── 조직 ─────────────────────────────────────────────────────────────

  @Override
  public String createOrganization(String name, String alias, Map<String, List<String>> attributes) {
    if (orgs.values().stream().anyMatch(o -> o.alias.equals(alias))) {
      throw new KeycloakException(409, "Keycloak 409: organization alias exists");
    }
    String id = UUID.randomUUID().toString();
    orgs.put(id, new Org(alias, attributes));
    return id;
  }

  @Override
  public Optional<String> findOrganizationByAttribute(String key, String value) {
    return orgs.entrySet().stream()
        .filter(e -> e.getValue().attributes.getOrDefault(key, List.of()).contains(value))
        .map(Map.Entry::getKey).findFirst();
  }

  @Override
  public void addOrganizationMember(String orgId, String userId) {
    org(orgId).members.add(userId);
  }

  @Override
  public void removeOrganizationMember(String orgId, String userId) {
    Org org = org(orgId);
    org.members.remove(userId);
    org.groups.values().forEach(g -> g.members.remove(userId));
  }

  @Override
  public String ensureOrganizationGroup(String orgId, String name) {
    return ensureGroup(orgId, null, name);
  }

  @Override
  public String ensureOrganizationSubGroup(String orgId, String parentId, String name) {
    return ensureGroup(orgId, parentId, name);
  }

  private String ensureGroup(String orgId, String parentId, String name) {
    Org org = org(orgId);
    synchronized (org) {
      return org.groups.entrySet().stream()
          .filter(e -> e.getValue().name.equals(name) && java.util.Objects.equals(e.getValue().parentId, parentId))
          .map(Map.Entry::getKey).findFirst()
          .orElseGet(() -> {
            String id = UUID.randomUUID().toString();
            org.groups.put(id, new Group(name, parentId));
            return id;
          });
    }
  }

  @Override
  public void deleteOrganizationGroup(String orgId, String groupId) {
    org(orgId).groups.remove(groupId);
  }

  @Override
  public void setOrganizationEnabled(String orgId, boolean enabled) {
    org(orgId).enabled = enabled;
  }

  @Override
  public void deleteOrganization(String orgId) {
    orgs.remove(orgId);
  }

  @Override
  public void joinOrganizationGroup(String orgId, String groupId, String userId) {
    group(orgId, groupId).members.add(userId);
  }

  @Override
  public void leaveOrganizationGroup(String orgId, String groupId, String userId) {
    Group group = org(orgId).groups.get(groupId);
    if (group != null) {
      group.members.remove(userId);
    }
  }

  @Override
  public List<String> groupPermissions(String orgId, String groupId) {
    return group(orgId, groupId).permissions.stream().sorted().toList();
  }

  @Override
  public void setGroupPermissions(String orgId, String groupId, Collection<String> permissions) {
    Group group = group(orgId, groupId);
    group.permissions.clear();
    group.permissions.addAll(permissions);
  }

  @Override
  public List<String> definedPermissions() {
    return Permission.all().stream().map(Permission::code).sorted().toList();
  }

  private Org org(String orgId) {
    Org org = orgs.get(orgId);
    if (org == null) {
      throw new KeycloakException(404, "Keycloak 404: organization not found " + orgId);
    }
    return org;
  }

  private Group group(String orgId, String groupId) {
    Group group = org(orgId).groups.get(groupId);
    if (group == null) {
      throw new KeycloakException(404, "Keycloak 404: group not found " + groupId);
    }
    return group;
  }
}
