package com.moai.works.api.support;

import java.util.List;
import java.util.UUID;

/**
 * 테스트에 쓰는 사람 (Keycloak 계정 하나에 해당).
 *
 * @param id            Keycloak 사용자 id (토큰의 sub)
 * @param username      username
 * @param email         이메일
 * @param displayName   표시 이름
 * @param platformRoles 렐름 역할 (system-admin 등)
 */
public record TestUser(UUID id, String username, String email, String displayName, List<String> platformRoles) {

  /** 서로 겹치지 않는 새 사람. label 은 읽기 좋으라고 붙인다. */
  public static TestUser create(String label, String... platformRoles) {
    UUID id = UUID.randomUUID();
    String unique = label + "-" + id.toString().substring(0, 8);
    return new TestUser(id, unique, unique + "@test.moai-works.local", label, List.of(platformRoles));
  }
}
