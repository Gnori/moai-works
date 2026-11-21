package com.moai.works.api.security.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 인증 없이 부를 수 있다 — 가입 · 인증 코드뿐. 경로가 SecurityConfiguration.PUBLIC_API 아래여야 한다(기동 때 확인). */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface PublicApi {
}
