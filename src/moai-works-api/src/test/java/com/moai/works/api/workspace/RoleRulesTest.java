package com.moai.works.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/** 사용자 정의 역할 — 가진 권한 안에서만 만들고 고친다. 기본 역할은 못 건드린다. (SECURITY.md §6.5) */
@ApiTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoleRulesTest {

  @Autowired
  TestApi api;

  TestUser owner;
  TestUser roleKeeper;
  String ws;

  @BeforeAll
  void setUp() throws Exception {
    owner = api.user("owner");
    roleKeeper = api.user("role-keeper");
    ws = api.teamWorkspace(owner, "역할 규칙 시험");
    api.join(ws, owner, roleKeeper, "member");
    // 역할은 만들 수 있지만(role.manage) 가진 권한이 적은 사람
    String keeper = api.customRole(ws, owner, "역할 담당", Set.of("workspace.read", "member.read", "role.manage"));
    api.setRoles(ws, owner, roleKeeper, "member", keeper);
  }

  private Map<String, Object> role(String name, String... permissions) {
    return Map.of("name", name, "permissions", List.of(permissions));
  }

  @Test
  void 역할을_만들면_목록에_나오고_권한이_그대로다() throws Exception {
    String key = api.customRole(ws, owner, "편집 도우미", Set.of("workspace.read", "member.read", "mcp.key.issue.personal"));
    assertThat(key).startsWith("custom-");
    List<String> permissions = TestApi.read(api.get(owner, "/workspaces/{ws}/roles", ws),
        "$[?(@.key=='" + key + "')].permissions[*]");
    assertThat(permissions).containsExactlyInAnyOrder("workspace.read", "member.read", "mcp.key.issue.personal");
    // Keycloak 그룹에도 같은 권한이 매핑된다
    api.get(owner, "/workspaces/{ws}/roles", ws).andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.key=='owner')].builtin").value(true));
  }

  @Test
  void 소유자_전용_권한과_없는_코드는_넣을_수_없다() throws Exception {
    api.post(owner, "/workspaces/{ws}/roles", role("위험", "workspace.read", "workspace.delete"), ws)
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OWNER_ONLY_PERMISSION"));
    api.post(owner, "/workspaces/{ws}/roles", role("이상", "workspace.read", "no.such.code"), ws)
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_PERMISSION"));
    api.post(owner, "/workspaces/{ws}/roles", Map.of("name", "빈 역할", "permissions", List.of()), ws)
        .andExpect(status().isBadRequest());
  }

  @Test
  void 자기에게_없는_권한은_역할에_넣을_수_없다() throws Exception {
    api.post(roleKeeper, "/workspaces/{ws}/roles", role("감사 보기", "workspace.read", "audit.read"), ws)
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ESCALATION_DENIED"));
    api.post(roleKeeper, "/workspaces/{ws}/roles", role("읽기만", "workspace.read", "member.read"), ws)
        .andExpect(status().isCreated());
  }

  @Test
  void 자기보다_큰_역할은_고치거나_지울_수_없다() throws Exception {
    String big = api.customRole(ws, owner, "큰 역할", Set.of("workspace.read", "audit.read", "member.invite"));
    api.patch(roleKeeper, "/workspaces/{ws}/roles/{key}", role("줄이기", "workspace.read"), ws, big)
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ESCALATION_DENIED"));
    api.delete(roleKeeper, "/workspaces/{ws}/roles/{key}", ws, big)
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ESCALATION_DENIED"));
    // 소유자는 고친다
    api.patch(owner, "/workspaces/{ws}/roles/{key}", role("큰 역할 (고침)", "workspace.read", "audit.read"), ws, big)
        .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("큰 역할 (고침)"));
  }

  @Test
  void 기본_역할은_고치거나_지울_수_없다() throws Exception {
    for (String builtin : List.of("owner", "admin", "member")) {
      api.patch(owner, "/workspaces/{ws}/roles/{key}", role("바꿈", "workspace.read"), ws, builtin)
          .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BUILTIN_ROLE"));
      api.delete(owner, "/workspaces/{ws}/roles/{key}", ws, builtin)
          .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BUILTIN_ROLE"));
    }
  }

  @Test
  void 쓰는_멤버가_있거나_기본_역할로_지정된_역할은_지울_수_없다() throws Exception {
    String used = api.customRole(ws, owner, "쓰는 역할", Set.of("workspace.read"));
    TestUser holder = api.user("holder");
    api.join(ws, owner, holder, used);
    api.delete(owner, "/workspaces/{ws}/roles/{key}", ws, used).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROLE_IN_USE"));

    String defaulted = api.customRole(ws, owner, "새 멤버 기본", Set.of("workspace.read", "member.read"));
    api.patch(owner, "/workspaces/{ws}", Map.of("name", "역할 규칙 시험", "visibility", "PUBLIC", "joinPolicy",
        "APPROVAL", "allowedJoinDomains", List.of(), "defaultRoleKey", defaulted), ws).andExpect(status().isOk());
    api.delete(owner, "/workspaces/{ws}/roles/{key}", ws, defaulted).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROLE_IS_DEFAULT"));

    // 되돌리면 지워진다
    api.patch(owner, "/workspaces/{ws}", Map.of("name", "역할 규칙 시험", "visibility", "PUBLIC", "joinPolicy",
        "APPROVAL", "allowedJoinDomains", List.of(), "defaultRoleKey", "member"), ws).andExpect(status().isOk());
    api.delete(owner, "/workspaces/{ws}/roles/{key}", ws, defaulted).andExpect(status().isNoContent());
  }

  @Test
  void 소유자_역할은_새_멤버_기본_역할이_될_수_없다() throws Exception {
    api.patch(owner, "/workspaces/{ws}", Map.of("name", "역할 규칙 시험", "visibility", "PUBLIC", "joinPolicy",
        "APPROVAL", "allowedJoinDomains", List.of(), "defaultRoleKey", "owner"), ws)
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DEFAULT_ROLE"));
  }

  @Test
  void 없는_역할은_404() throws Exception {
    api.patch(owner, "/workspaces/{ws}/roles/{key}", role("없음", "workspace.read"), ws, "custom-nothing")
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ROLE_NOT_FOUND"));
  }
}
