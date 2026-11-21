package com.moai.works.api.security.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 사람 계정 본인 — 서비스 계정은 403. 계정(user_profile)이 ACTIVE 여야 한다(처음 보는 Keycloak 계정은 이때 사본을 만든다).
 * 워크스페이스 권한은 따지지 않는다(내 정보 · 내 워크스페이스 목록 · 가입 신청 등).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface UserApi {
}
