package com.moai.works.api.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * API 오류 — RFC 9457 problem+json 으로 나간다({@link ApiExceptionHandler}).
 *
 * <p>{@link #code} 는 화면이 분기에 쓰는 고정 문자열(예: EMAIL_TAKEN). 메시지는 사람이 읽는 한국어 문장.
 * 읽기 거부는 404(존재를 숨김), 쓰기 거부는 403 — SeedSiteKeeper 두 층 ACL 과 같은 규칙.
 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;
  private final Map<String, Object> properties = new LinkedHashMap<>();

  public ApiException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  /** 응답 본문에 함께 넣을 값 — 화면이 오류를 풀어 보여 줄 때 쓴다(예: 규칙 오류의 줄 번호, 겹친 페이지의 주소). */
  public ApiException with(String key, Object value) {
    properties.put(key, value);
    return this;
  }

  public Map<String, Object> properties() {
    return properties;
  }

  public static ApiException badRequest(String code, String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, code, message);
  }

  public static ApiException forbidden(String code, String message) {
    return new ApiException(HttpStatus.FORBIDDEN, code, message);
  }

  public static ApiException notFound(String code, String message) {
    return new ApiException(HttpStatus.NOT_FOUND, code, message);
  }

  public static ApiException conflict(String code, String message) {
    return new ApiException(HttpStatus.CONFLICT, code, message);
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }
}
