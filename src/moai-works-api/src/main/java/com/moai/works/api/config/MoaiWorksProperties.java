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
   * @param maxWorkspaceGb   워크스페이스 하나의 첨부 총 용량(GB, 0 이하면 제한 없음)
   * @param purgeAfterDays   지운 첨부의 객체를 저장소에 두는 날 수 — 지나면 정리 작업이 지운다(0 이하면 정리하지 않는다)
   */
  public record Storage(String endpoint, String attachmentBucket, String accessKey, String secretKey, int maxFileMb,
      int maxWorkspaceGb, int purgeAfterDays) {
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
   * @param issueEndpoint   일감 MCP 입구 (바깥 AI Agent — 일감 찾기 · 읽기 · 만들기 · 고치기 · 댓글 · 작업 시간 · 첨부)
   * @param projectEndpoint 프로젝트 관리 MCP 입구 (프로젝트 만들기 · 지우기 같은 관리 기능 자리. 키는 두 입구 모두에 쓴다)
   * @param keyDefaultDays  MCP 키 기본 유효 기간(일)
   * @param keyMaxDays      MCP 키 최대 유효 기간(일)
   */
  public record Mcp(String issueEndpoint, String projectEndpoint, int keyDefaultDays, int keyMaxDays, String publicBaseUrl) {

    /** 바깥 클라이언트가 붙는 web-backend 주소(첨부 티켓의 올리기 · 받기 주소를 만든다). 비면 https://localhost:11002 */
    public String baseUrl() {
      String u = publicBaseUrl == null || publicBaseUrl.isBlank() ? "https://localhost:11002" : publicBaseUrl.strip();
      return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    /** 이 경로가 MCP 입구인가 — 입구는 Keycloak 이 아니라 MCP 키로 인증한다. */
    public boolean isEntry(String path) {
      return endpointOf(path) != null;
    }

    /** 경로가 가리키는 MCP 입구. 입구가 아니면 null. */
    public McpEndpoint endpointOf(String path) {
      if (matches(path, issueEndpoint)) {
        return McpEndpoint.ISSUE;
      }
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
   * @param mode       {@code log}(기본) — 메일을 보내지 않고 로그에 남김(로컬 개발, SMTP 없음). {@code smtp} — 아래 SMTP 서버로 보냄
   * @param from       보내는 주소
   * @param smtp       smtp 모드의 서버 · 계정 (env 로만)
   * @param outboxCron 알림 메일 보낼 통(notification_mail)을 살피는 주기(cron). {@code -} 면 돌지 않는다(시험)
   */
  public record Mail(String mode, String from, Smtp smtp, String outboxCron) {

    public boolean smtpMode() {
      return "smtp".equals(mode);
    }
  }

  /**
   * SMTP 서버 — 아직 정하지 않았다(WBS D-06). 값은 env({@code MOAI_WORKS_SMTP_*})로만 넣는다.
   * 평문 SMTP 는 두지 않는다 — {@code starttls}(기본, 587) 또는 {@code ssl}(465) 이고 서버 인증서를 확인한다.
   *
   * @param host     서버 주소. smtp 모드인데 비면 기동하지 않는다
   * @param port     포트
   * @param username 계정 (비면 인증 없이)
   * @param password 비밀값
   * @param tls      {@code starttls} · {@code ssl}
   */
  public record Smtp(String host, int port, String username, String password, String tls) {
  }

  /** @param baseUrl 사용자가 여는 웹 주소 (web-backend). 메일 속 링크에 쓴다 */
  public record Web(String baseUrl) {
  }
}
