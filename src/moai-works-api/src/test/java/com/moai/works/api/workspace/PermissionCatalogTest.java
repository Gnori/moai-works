package com.moai.works.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.jayway.jsonpath.JsonPath;
import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * 권한 코드 카탈로그 — 코드(enum) · Keycloak 설치 파일 · 기본 역할이 서로 맞는가, 그리고 카탈로그가 바뀌었을 때
 * 이미 있는 워크스페이스의 역할이 따라오는가.
 */
@ApiTest
class PermissionCatalogTest {

  @Autowired
  TestApi api;

  @Autowired
  PermissionCatalogMigrator migrator;

  @Autowired
  WorkspaceRepositories.Workspaces workspaces;

  @Autowired
  WorkspaceRepositories.Roles roles;

  @Autowired
  JdbcTemplate jdbc;

  @Test
  void Keycloak_설치_파일의_권한_코드가_enum_과_같다() throws Exception {
    // 모듈 폴더에서 돈다(surefire 기본) — 저장소의 infra/keycloak/permission-catalog.json
    String json = Files.readString(Path.of("../../infra/keycloak/permission-catalog.json"));
    List<String> inFile = JsonPath.read(json, "$.permissions[*].code");
    Set<String> inCode = Permission.all().stream().map(Permission::code).collect(Collectors.toSet());
    assertThat(inFile).containsExactlyInAnyOrderElementsOf(inCode);
  }

  @Test
  void 소유자_전용_권한은_소유자_역할에만_있다() {
    for (BuiltinRole role : BuiltinRole.values()) {
      boolean hasOwnerOnly = role.permissions().stream().anyMatch(Permission::ownerOnly);
      assertThat(hasOwnerOnly).as(role.key()).isEqualTo(role == BuiltinRole.OWNER);
    }
  }

  @Test
  void 기본_역할의_권한이_설계와_같다() {
    // design/03 §4.1 — 멤버는 보기 · 개인 MCP 키만. 관리자는 소유자 전용을 뺀 전부.
    assertThat(BuiltinRole.MEMBER.permissions()).containsExactlyInAnyOrder(Permission.WORKSPACE_READ,
        Permission.MEMBER_READ, Permission.MCP_KEY_ISSUE_PERSONAL);
    assertThat(BuiltinRole.ADMIN.permissions()).contains(Permission.MEMBER_INVITE, Permission.MEMBER_ROLE_ASSIGN,
        Permission.ROLE_MANAGE, Permission.MCP_ENDPOINT_MANAGE, Permission.AUDIT_READ)
        .doesNotContain(Permission.WORKSPACE_OWNERSHIP_TRANSFER, Permission.WORKSPACE_DELETE);
  }

  @Test
  void 카탈로그가_바뀌면_기존_워크스페이스의_역할을_Keycloak_먼저_맞춘다() throws Exception {
    TestUser owner = api.user("catalog-owner");
    String slug = api.teamWorkspace(owner, "카탈로그 시험");
    String custom = api.customRole(slug, owner, "옛 코드가 든 역할", Set.of("workspace.read", "audit.read"));
    Workspace ws = workspaces.findBySlug(slug).orElseThrow();
    WorkspaceRole memberRole = roles.findByWorkspaceIdAndRoleKey(ws.getId(), "member").orElseThrow();
    WorkspaceRole customRole = roles.findByWorkspaceIdAndRoleKey(ws.getId(), custom).orElseThrow();

    // 옛 카탈로그로 만든 워크스페이스를 흉내 낸다: 멤버 역할에 새 권한이 없고, 사용자 정의 역할에 없어진 코드가 있다
    jdbc.update("DELETE FROM role_permission WHERE role_id = ? AND permission_code IN ('member.read', 'mcp.key.issue.personal')",
        memberRole.getId());
    api.keycloak().setGroupPermissions(ws.getKeycloakOrgId(), memberRole.getKeycloakGroupId(),
        List.of("workspace.read"));
    jdbc.update("INSERT INTO permission (code, description, owner_only, retired_at) VALUES ('old.code', 'old', false, now())"
        + " ON CONFLICT (code) DO NOTHING");
    jdbc.update("INSERT INTO role_permission (role_id, permission_code) VALUES (?, 'old.code')", customRole.getId());
    jdbc.update("DELETE FROM platform_setting WHERE key = ?", PermissionCatalogMigrator.APPLIED_KEY);

    assertThat(migrator.applyIfChanged()).isGreaterThanOrEqualTo(2);

    Set<String> expected = BuiltinRole.MEMBER.permissions().stream().map(Permission::code).collect(Collectors.toSet());
    assertThat(roles.findById(memberRole.getId()).orElseThrow().getPermissions()).isEqualTo(expected);
    assertThat(api.keycloak().groupPermissions(ws.getKeycloakOrgId(), memberRole.getKeycloakGroupId()))
        .containsExactlyInAnyOrderElementsOf(expected);
    assertThat(roles.findById(customRole.getId()).orElseThrow().getPermissions())
        .containsExactlyInAnyOrder("workspace.read", "audit.read");
    assertThat(api.keycloak().groupPermissions(ws.getKeycloakOrgId(), customRole.getKeycloakGroupId()))
        .containsExactlyInAnyOrder("workspace.read", "audit.read");

    // 같은 카탈로그면 다시 돌지 않는다
    assertThat(migrator.applyIfChanged()).isEqualTo(-1);
  }
}
