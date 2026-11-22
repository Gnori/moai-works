package com.moai.works.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code moai-works-web.*} 설정.
 *
 * @param keycloak 로그인(패스워드 그랜트) · 토큰 갱신 · 로그아웃에 쓰는 web 클라이언트
 * @param api      moai-works-api 주소
 * @param signIn   로그인 시도 제한
 * @param tls      다른 서비스를 부를 때의 TLS 신뢰
 */
@ConfigurationProperties(prefix = "moai-works-web")
public record WebProperties(Keycloak keycloak, Api api, SignIn signIn, Mcp mcp, Tls tls) {

  /**
   * @param url          Keycloak 주소 (KC_HOSTNAME 과 같게)
   * @param clientId     web 클라이언트 — 패스워드 그랜트만 한다. 관리 권한 없음
   * @param clientSecret env 로만
   */
  public record Keycloak(String url, String realm, String clientId, String clientSecret) {

    public String tokenUri() {
      return url + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    public String logoutUri() {
      return url + "/realms/" + realm + "/protocol/openid-connect/logout";
    }
  }

  /**
   * @param baseUrl api v1 주소 (예: https://localhost:11001/moai-works-api/v1)
   * @param mcpUrl  api 의 MCP 입구 앞부분 (예: https://localhost:11001/mcp) — /mcp/** 를 그대로 여기로 중계한다
   */
  public record Api(String baseUrl, String mcpUrl) {
  }

  /** @param perIpPerMinute /mcp 중계의 IP 별 분당 요청 수 — 키 검사 전에 막무가내 요청을 막는다. 키 · 도구별 제한은 api */
  public record Mcp(int perIpPerMinute) {
  }

  /** @param caFile 더 믿을 인증 기관의 PEM 파일 경로. 비면 JVM 이 원래 믿는 것만 쓴다 */
  public record Tls(String caFile) {
  }

  public record SignIn(int perIpPerMinute, int perUsernamePer15Minutes) {
  }
}
