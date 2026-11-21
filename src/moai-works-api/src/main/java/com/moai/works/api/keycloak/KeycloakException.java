package com.moai.works.api.keycloak;

import org.springframework.web.client.HttpStatusCodeException;

/** Keycloak 관리 API 실패. {@link #status()} 는 Keycloak 이 돌려준 HTTP 상태(연결 실패는 503). */
public class KeycloakException extends RuntimeException {

  private final int status;
  private final String body;

  public KeycloakException(int status, String message) {
    this(status, message, null);
  }

  private KeycloakException(int status, String message, String body) {
    super(message);
    this.status = status;
    this.body = body;
  }

  static KeycloakException of(HttpStatusCodeException e) {
    String body = e.getResponseBodyAsString();
    String shown = body.length() > 300 ? body.substring(0, 300) : body;
    return new KeycloakException(e.getStatusCode().value(),
        "Keycloak " + e.getStatusCode().value() + ": " + shown, body);
  }

  public int status() {
    return status;
  }

  /** Keycloak 응답 본문(오류 설명). 없으면 빈 문자열. */
  public String body() {
    return body == null ? "" : body;
  }

  public boolean conflict() {
    return status == 409;
  }

  public boolean notFound() {
    return status == 404;
  }
}
