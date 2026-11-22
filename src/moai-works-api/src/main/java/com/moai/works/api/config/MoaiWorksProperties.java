package com.moai.works.api.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code moai-works.*} 설정.
 *
 * @param keycloak REST 토큰 검증 · 관리 API
 * @param mcp      MCP 엔드포인트 · MCP 키
 * @param security 인증 코드 HMAC 등
 * @param mail     메일 보내기
 * @param web      웹 화면 주소 (초대 링크 등)
 * @param storage  객체 저장소 — 첨부 파일
 */
@ConfigurationProperties(prefix = "moai-works")
public record MoaiWorksProperties(Keycloak keycloak, Mcp mcp, Security security, Mail mail, Web web, Storage storage) {

  /**
   * 객체 저장소(MinIO · S3 호환). 열쇠는 env 로만 넣는다. 객체를 지우는 것은 api 뿐이다.
   *
   * @param endpoint         저장소 주소
   * @param attachmentBucket 첨부 파일 (일감 · 위키 · 문서)
   * @param accessKey        api 용 사용자
   * @param secretKey        api 용 비밀값
   * @param maxFileMb        파일 하나의 크기 상한(MB)
   */
  public record Storage(String endpoint, String attachmentBucket, String accessKey, String secretKey, int maxFileMb) {
    public long maxFileBytes() {
      return maxFileMb * 1024L * 1024L;
    }
  }

  /**
   * @param url          Keycloak 주소. Keycloak 의 KC_HOSTNAME 과 같아야 한다(발급자 · 관리 API audience 가 이 값으로 정해진다).
   * @param realm        렐름
   * @param clientId     이 API 의 클라이언트 — 토큰 audience 에 이 값이 있어야 받는다. 관리 API 는 이 클라이언트의 서비스 계정으로 부른다.
   * @param clientSecret 클라이언트 비밀값 (env 로만 주입)
   */
  public record Keycloak(String url, String realm, String clientId, String clientSecret) {

    public String issuer() {
      return url + "/realms/" + realm;
    }

    public String jwksUri() {
      return issuer() + "/protocol/openid-connect/certs";
    }
  }

  /**
   * @param projectEndpoint 프로젝트 관리용 MCP (바깥 AI Agent)
   * @param keyDefaultDays  MCP 키 기본 유효 기간(일)
   * @param keyMaxDays      MCP 키 최대 유효 기간(일)
   */
  public record Mcp(String projectEndpoint, int keyDefaultDays, int keyMaxDays) {

    /** 이 경로가 MCP 입구인가 — 입구는 Keycloak 이 아니라 MCP 키로 인증한다. */
    public boolean isEntry(String path) {
      return endpointOf(path) != null;
    }

    /** 경로가 가리키는 MCP 입구. 입구가 아니면 null. */
    public McpEndpoint endpointOf(String path) {
      if (matches(path, projectEndpoint)) {
        return McpEndpoint.PROJECT;
      }
      return null;
    }

    private static boolean matches(String path, String endpoint) {
      return path != null && (path.equals(endpoint) || path.startsWith(endpoint + "/"));
    }
  }

  /**
   * @param codeSecret     인증 코드 HMAC 비밀값 (env 로만, 32자 이상)
   * @param trustedProxies X-Forwarded-For 를 믿는 중계(web-backend)의 접속 주소. 여기서 오지 않은 요청의 X-Forwarded-For 는
   *                       버리고 접속 주소를 클라이언트 IP 로 쓴다 — api 에 직접 붙은 클라이언트가 IP 를 꾸미지 못하게(MCP 설계 「중계」)
   */
  public record Security(String codeSecret, List<String> trustedProxies) {

    public Security {
      trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
    }
  }

  /**
   * @param mode {@code log} — 메일을 보내지 않고 로그에 남김(로컬 개발, SMTP 없음). {@code smtp} — spring.mail 로 보냄
   * @param from 보내는 주소
   */
  public record Mail(String mode, String from) {
  }

  /** @param baseUrl 사용자가 여는 웹 주소 (web-backend). 메일 속 링크에 쓴다 */
  public record Web(String baseUrl) {
  }
}
