package com.moai.works.api.security.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 사람 계정 본인 — 서비스 계정은 403. 계정(user_profile)이 ACTIVE 여야 한다(처음 보는 Keycloak 계정은 이때 사본을 만든다).
 * 워크스페이스 권한은 따지지 않는다(내 정보 · 내 워크스페이스 목록 · 가입 신청 등).
 *
 * <p><b>시스템 관리자 계정은 기본으로 막는다</b>(403 {@code SYSTEM_ADMIN_NOT_ALLOWED}). 이 어노테이션이 붙는 곳은 대부분
 * 워크스페이스 영역의 입구(찾기 · 만들기 · 가입 신청 · 초대)이고, 시스템 관리자는 그 영역에 들어가지 못해서다.
 * 시스템 관리자도 써야 하는 곳(내 정보 · 로그인 기록)에만 {@link #systemAdmin()} 을 켠다.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface UserApi {

  /** 시스템 관리자 계정도 부를 수 있나. 기본은 막는다 */
  boolean systemAdmin() default false;
}
