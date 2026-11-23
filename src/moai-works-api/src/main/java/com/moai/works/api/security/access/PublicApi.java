package com.moai.works.api.security.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 인증 없이 부를 수 있다 — 가입 · 인증 코드, 비밀번호 찾기(메일 코드)뿐. 경로가 SecurityConfiguration.PUBLIC_PREFIXES 아래여야 한다(기동 때 확인).
 * 속도 제한과 "계정이 있는지 드러내지 않기"는 서비스가 맡는다.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface PublicApi {
}
