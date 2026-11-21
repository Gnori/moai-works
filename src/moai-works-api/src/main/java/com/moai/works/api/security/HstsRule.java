package com.moai.works.api.security;

import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;

/**
 * HSTS(Strict-Transport-Security) 를 언제 보내는가 — HTTPS 요청이고, 호스트가 이 PC(localhost)가 아닐 때만.
 *
 * <p>HSTS 는 포트가 아니라 <b>호스트 이름</b>에 걸린다. {@code localhost} 에 한 번 걸리면 브라우저가 이 PC 의 다른 포트에서
 * HTTP 로 도는 것(Keycloak 콘솔 · MinIO 콘솔 · 다른 프로젝트)까지 HTTPS 로만 열려고 해서 전부 열리지 않는다.
 * 운영 도메인에는 그대로 보낸다(Spring Security 기본값: 1년 · 하위 도메인 포함).
 */
public final class HstsRule {

  private HstsRule() {
  }

  public static boolean applies(HttpServletRequest request) {
    if (!request.isSecure()) {
      return false;
    }
    String host = request.getServerName() == null ? "" : request.getServerName().toLowerCase(Locale.ROOT);
    boolean thisMachine = host.equals("localhost") || host.endsWith(".localhost") || host.equals("127.0.0.1")
        || host.equals("::1") || host.equals("[::1]") || host.equals("0:0:0:0:0:0:0:1");
    return !thisMachine;
  }
}
