package com.moai.works.api.workspace;

import java.util.Arrays;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.security.access.UserApi;

/** 권한 코드 카탈로그와 기본 역할 — 역할 화면이 그린다. */
@RestController
@UserApi
public class PermissionCatalogController {

  public record PermissionView(String code, boolean ownerOnly) {
  }

  public record BuiltinRoleView(String key, String name, String description, List<String> permissions) {
  }

  public record Catalog(List<PermissionView> permissions, List<BuiltinRoleView> builtinRoles) {
  }

  @GetMapping(EndPointPaths.V1 + "/permission-catalog")
  public Catalog catalog() {
    return new Catalog(
        Arrays.stream(Permission.values()).map(p -> new PermissionView(p.code(), p.ownerOnly())).toList(),
        Arrays.stream(BuiltinRole.values()).map(r -> new BuiltinRoleView(r.key(), r.displayName(), r.description(),
            r.permissions().stream().map(Permission::code).sorted().toList())).toList());
  }
}
