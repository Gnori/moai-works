package com.moai.works.api.security.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.moai.works.api.workspace.Permission;

/**
 * 워크스페이스 안의 일. 경로에 {@code {workspace}}(슬러그)가 있어야 한다(기동 때 확인).
 * <ul>
 *   <li>멤버가 아니거나(정지 포함) 워크스페이스가 활성이 아니면 <b>404</b> — 존재를 숨긴다</li>
 *   <li>멤버지만 {@link #value()} 권한이 없으면 <b>403</b></li>
 * </ul>
 * 통과하면 컨트롤러 인자로 {@link WorkspaceContext} 를 받을 수 있다.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface WorkspaceApi {

  /** 필요한 권한 코드 */
  Permission value();
}
