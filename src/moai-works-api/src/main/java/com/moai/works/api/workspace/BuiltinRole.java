package com.moai.works.api.workspace;

import static com.moai.works.api.workspace.Permission.*;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * 모든 워크스페이스에 자동으로 생기는 기본 역할 3개 (design/03 §4.1). 고치거나 지울 수 없다.
 *
 * <p>역할 = Keycloak 조직 그룹 {@code /roles/<key>}, 담는 권한 = 그 그룹에 매핑한 클라이언트 역할.
 */
public enum BuiltinRole {

  /** 소유자 — 모든 권한 코드 (자기 워크스페이스의 총관리자). 항상 1명 이상 */
  OWNER("owner", "소유자", "워크스페이스의 모든 권한", Permission.all()),

  /** 관리자 — 소유자 전용 권한(소유권 이전 · 삭제)을 뺀 전부. 소유자는 건드릴 수 없다 */
  ADMIN("admin", "관리자", "멤버 · 역할 · 프로젝트 · MCP 관리 (소유권 이전 · 삭제 제외)",
      EnumSet.complementOf(EnumSet.of(WORKSPACE_OWNERSHIP_TRANSFER, WORKSPACE_DELETE))),

  /** 멤버 — 워크스페이스와 멤버를 본다. 프로젝트 안에서 할 수 있는 일은 프로젝트 역할이 정한다 (design/04) */
  MEMBER("member", "멤버", "읽기 · 개인 MCP 키",
      EnumSet.of(WORKSPACE_READ, MEMBER_READ, MCP_KEY_ISSUE_PERSONAL));

  private final String key;
  private final String displayName;
  private final String description;
  private final Set<Permission> permissions;

  BuiltinRole(String key, String displayName, String description, Set<Permission> permissions) {
    this.key = key;
    this.displayName = displayName;
    this.description = description;
    this.permissions = Set.copyOf(permissions);
  }

  public String key() {
    return key;
  }

  public String displayName() {
    return displayName;
  }

  public String description() {
    return description;
  }

  public Set<Permission> permissions() {
    return permissions;
  }

  public static Optional<BuiltinRole> ofKey(String key) {
    return Arrays.stream(values()).filter(r -> r.key.equals(key)).findFirst();
  }
}
