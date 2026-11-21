package com.moai.works.api.security.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 플랫폼 역할(Keycloak 렐름 역할)을 가진 사람 — 시스템 관리자 화면. 워크스페이스 역할과 다른 층이다. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface PlatformApi {

  /** 필요한 렐름 역할 (PlatformRole 상수) */
  String role();
}
