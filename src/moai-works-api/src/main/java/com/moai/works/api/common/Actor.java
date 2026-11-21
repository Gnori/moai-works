package com.moai.works.api.common;

import java.util.UUID;

/**
 * 요청을 한 주체 — 감사 기록과 권한 판정에 쓴다.
 *
 * @param type USER · SERVICE · SYSTEM
 * @param id   사람이면 Keycloak 사용자 id, 서비스 계정이면 그 서비스 계정 id, SYSTEM 이면 null
 * @param name 표시 이름(감사 화면용)
 * @param ip   요청 IP (없으면 null)
 */
public record Actor(Type type, UUID id, String name, String ip) {

  public enum Type {
    USER,
    SERVICE,
    SYSTEM
  }

  public static Actor system(String name) {
    return new Actor(Type.SYSTEM, null, name, null);
  }

  public static Actor user(UUID id, String name, String ip) {
    return new Actor(Type.USER, id, name, ip);
  }
}
