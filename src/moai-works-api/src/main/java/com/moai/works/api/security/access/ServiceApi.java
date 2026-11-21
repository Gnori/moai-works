package com.moai.works.api.security.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 지정한 Keycloak 클라이언트의 서비스 계정만 (바깥 자동화 — 아직 쓰는 엔드포인트 없음, SECURITY.md §8.1). 사람 토큰은 403. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ServiceApi {

  /** 허용할 클라이언트 id (토큰 azp) */
  String[] clients();
}
