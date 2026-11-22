package com.moai.works.api.project;

import static com.moai.works.api.workspace.Permission.*;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import com.moai.works.api.workspace.Permission;

/**
 * 프로젝트 역할 4개 — 고정이고 위가 아래를 모두 담는다 (design/04). moai-wiki 의 위키 역할(wiki-admin ⊃ wiki-curator ⊃
 * wiki-editor ⊃ wiki-reader) 자리다.
 *
 * <p>정본은 Keycloak 조직 그룹 {@code /projects/<project id>/<role>}, api 는 {@code project_role_binding} 사본으로 판정한다.
 * 그룹에는 클라이언트 역할을 매핑하지 않는다 — 역할이 고정이라 담는 권한 코드는 여기(코드)가 정한다.
 */
public enum ProjectRole {

  READER("project-reader", "독자", EnumSet.of(ISSUE_READ, ISSUE_COMMENT)),
  EDITOR("project-editor", "편집자", EnumSet.of(ISSUE_READ, ISSUE_COMMENT, ISSUE_CREATE, ISSUE_EDIT)),
  CURATOR("project-curator", "큐레이터",
      EnumSet.of(ISSUE_READ, ISSUE_COMMENT, ISSUE_CREATE, ISSUE_EDIT, ISSUE_MANAGER_ASSIGN)),
  /** 그 프로젝트의 설정과 프로젝트 역할을 관리하고 일감을 지운다. 프로젝트 보관 · 삭제는 못 한다 */
  ADMIN("project-admin", "프로젝트 관리자",
      EnumSet.of(ISSUE_READ, ISSUE_COMMENT, ISSUE_CREATE, ISSUE_EDIT, ISSUE_MANAGER_ASSIGN, ISSUE_DELETE, PROJECT_MANAGE));

  private final String key;
  private final String displayName;
  private final Set<Permission> permissions;

  ProjectRole(String key, String displayName, Set<Permission> permissions) {
    this.key = key;
    this.displayName = displayName;
    this.permissions = Set.copyOf(permissions);
  }

  public String key() {
    return key;
  }

  public String displayName() {
    return displayName;
  }

  public Set<Permission> permissions() {
    return permissions;
  }

  public static Optional<ProjectRole> ofKey(String key) {
    return Arrays.stream(values()).filter(r -> r.key.equals(key)).findFirst();
  }
}
