package com.moai.works.web.relay;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

/** BFF 가 직접 만드는 오류 — api 와 같은 problem+json 모양({@code status · code · detail}). */
public final class Problems {

  private Problems() {
  }

  public static void write(HttpServletResponse response, int status, String code, String detail) throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json;charset=UTF-8");
    response.getWriter().write(json(status, code, detail));
  }

  public static String json(int status, String code, String detail) {
    return "{\"status\":" + status + ",\"code\":\"" + code + "\",\"detail\":\"" + escape(detail) + "\"}";
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
