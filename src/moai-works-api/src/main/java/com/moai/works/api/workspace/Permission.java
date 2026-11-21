package com.moai.works.api.workspace;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 워크스페이스 권한 코드 카탈로그 (design/03 §4).
 *
 * <p>지금은 토대(워크스페이스 · 멤버 · 역할 · MCP · 감사) 코드만 있다. 프로젝트 · 일감 코드는 그 기능을 만들 때 더한다
 * (계획한 코드: design/04 §3).
 *
 * <p>코드 하나 = Keycloak 클라이언트 {@code moai-works-api} 의 클라이언트 역할 하나.
 * Keycloak 쪽은 infra/keycloak/permission-catalog.json 으로 만들고, api 는 기동 때 둘이 같은지 확인한다.
 * 워크스페이스는 코드를 새로 만들 수 없고 골라서 역할을 만든다.
 */
public enum Permission {
  WORKSPACE_READ("workspace.read"),
  WORKSPACE_SETTINGS_MANAGE("workspace.settings.manage"),
  WORKSPACE_OWNERSHIP_TRANSFER("workspace.ownership.transfer", true),
  WORKSPACE_DELETE("workspace.delete", true),
  MEMBER_READ("member.read"),
  MEMBER_INVITE("member.invite"),
  MEMBER_REQUEST_REVIEW("member.request.review"),
  MEMBER_REMOVE("member.remove"),
  MEMBER_ROLE_ASSIGN("member.role.assign"),
  ROLE_MANAGE("role.manage"),
  MCP_ENDPOINT_MANAGE("mcp.endpoint.manage"),
  MCP_KEY_ISSUE_PERSONAL("mcp.key.issue.personal"),
  MCP_KEY_ISSUE_SERVICE("mcp.key.issue.service"),
  MCP_KEY_MANAGE("mcp.key.manage"),
  AUDIT_READ("audit.read"),
  USAGE_READ("usage.read");

  private static final Map<String, Permission> BY_CODE = Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(Permission::code, Function.identity()));

  private final String code;
  private final boolean ownerOnly;

  Permission(String code) {
    this(code, false);
  }

  Permission(String code, boolean ownerOnly) {
    this.code = code;
    this.ownerOnly = ownerOnly;
  }

  /** Keycloak 클라이언트 역할 이름이자 API · 화면에 쓰는 코드 */
  public String code() {
    return code;
  }

  /** 소유자 역할만 가질 수 있는 코드 — 사용자 정의 역할에 넣을 수 없다 */
  public boolean ownerOnly() {
    return ownerOnly;
  }

  public static Optional<Permission> ofCode(String code) {
    return Optional.ofNullable(BY_CODE.get(code));
  }

  public static Set<Permission> all() {
    return EnumSet.allOf(Permission.class);
  }
}
