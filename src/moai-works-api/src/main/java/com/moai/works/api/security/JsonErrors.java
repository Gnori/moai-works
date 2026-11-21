package com.moai.works.api.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** 필터 단계 오류 응답 — {@code {"code","message"}} JSON. */
final class JsonErrors {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private JsonErrors() {
  }

  static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
    response.setStatus(status);
    if (status == HttpServletResponse.SC_UNAUTHORIZED) {
      response.setHeader("WWW-Authenticate", "Bearer");
    }
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    Map<String, String> body = new LinkedHashMap<>();
    body.put("code", code);
    body.put("message", message);
    response.getWriter().write(JSON.writeValueAsString(body));
  }
}
