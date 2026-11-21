package com.moai.works.api.security;

import jakarta.servlet.http.HttpServletRequest;

/** Authorization 헤더의 Bearer 값. 스킴 「Bearer」는 대소문자를 가리지 않는다(RFC 7235 2.1). */
final class BearerTokens {

  private BearerTokens() {
  }

  /** 값이 없으면 null. */
  static String read(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return null;
    }
    String token = header.substring(7).trim();
    return token.isEmpty() ? null : token;
  }
}
