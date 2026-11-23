package com.moai.works.api.workspace;

import static com.moai.works.api.workspace.Permission.*;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * 모든 워크스페이스에 자동으로 생기는 기본 역할 4개 (design/03 §4.1) — moai-wiki 와 같다. 고치거나 지울 수 없다.
 *
 * <p>역할 = Keycloak 조직 그룹 {@code /roles/<key>}, 담는 권한 = 그 그룹에 매핑한 클라이언트 역할.
 */
public enum BuiltinRole {

  /** 소유자 — 모든 권한 코드 (자기 워크스페이스의 총관리자). 항상 1명 이상 */
  OWNER("owner", "소유자", "워크스페이스의 모든 권한", Permission.all()),

  /** 관리자 — 소유자 전용 권한(소유권 이전 · 삭제)을 뺀 전부. 소유자는 건드릴 수 없다 */
  ADMIN("admin", "관리자", "멤버 · 역할 · 프로젝트 · MCP 관리 (소유권 이전 · 삭제 제외)",
      EnumSet.complementOf(EnumSet.of(WORKSPACE_OWNERSHIP_TRANSFER, WORKSPACE_DELETE))),

  /** 큐레이터 — 모든 프로젝트에서 일감을 만들고 고친다. 일감 삭제와 프로젝트 관리는 못 한다 (design/04) */
  CURATOR("curator", "큐레이터", "일감 만들기 · 편집 · 댓글 · 관리자 지정",
      EnumSet.of(WORKSPACE_READ, MEMBER_READ, ISSUE_READ, ISSUE_CREATE, ISSUE_EDIT, ISSUE_COMMENT, ISSUE_MANAGER_ASSIGN,
          MCP_KEY_ISSUE_PERSONAL)),

  /** 멤버 — 읽고 댓글을 단다. 특정 프로젝트에서 일감을 쓰려면 그 프로젝트의 프로젝트 역할을 받는다 (design/04) */
  MEMBER("member", "멤버", "읽기 · 댓글 · MCP 키로 일하기",
      EnumSet.of(WORKSPACE_READ, MEMBER_READ, ISSUE_READ, ISSUE_COMMENT, MCP_KEY_ISSUE_PERSONAL));

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
