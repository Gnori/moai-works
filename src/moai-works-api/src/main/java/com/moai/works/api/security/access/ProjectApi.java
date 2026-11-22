package com.moai.works.api.security.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.moai.works.api.workspace.Permission;

/**
 * 프로젝트 하나 안의 일 (moai-wiki 의 {@code @WikiApi} 와 같다). 경로에 {@code {workspace}}(워크스페이스 주소)와
 * {@code {project}}(프로젝트 주소 이름)가 있어야 한다(기동 때 확인).
 * <ul>
 *   <li>워크스페이스 멤버가 아니면 <b>404</b> {@code WORKSPACE_NOT_FOUND}</li>
 *   <li>그 프로젝트의 독자가 아니거나, 프로젝트가 없거나, 보관된 프로젝트인데 관리 권한이 없으면 <b>404</b>
 *       {@code PROJECT_NOT_FOUND} — 존재를 숨긴다</li>
 *   <li>독자지만 {@link #value()} 권한이 없으면 <b>403</b> {@code PERMISSION_REQUIRED}</li>
 * </ul>
 * 권한은 워크스페이스 역할의 권한과 그 프로젝트의 프로젝트 역할 권한을 합친 것이다(design/04).
 * 일감마다 달라지는 규칙(관리자 바꾸기 · 상위 일감)은 서비스가 더 본다.
 * 통과하면 컨트롤러 인자로 {@link ProjectContext} 를 받을 수 있다.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface ProjectApi {

  /** 필요한 권한 코드 */
  Permission value();
}
