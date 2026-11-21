package com.moai.works.web.auth;

import java.io.Serial;

import org.springframework.security.core.AuthenticationException;

/** 로그인 실패 — 화면이 분기에 쓰는 code 와 사람이 읽는 메시지. Spring Security 의 인증 실패로 던진다. */
public class SignInException extends AuthenticationException {

  @Serial
  private static final long serialVersionUID = 1L;

  private final int status;
  private final String code;

  public SignInException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }
}
