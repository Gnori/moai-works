package com.moai.works.api.security.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.moai.works.api.workspace.Permission;

/**
 * 일감 하나의 일 — 일감은 워크스페이스 아래에 있고 번호는 워크스페이스 안에서 하나다(ADR-034, Redmine 의 {@code /issues/:id}).
 * 경로에 {@code {workspace}}(워크스페이스 주소)와 {@code {number}}(일감 번호)가 있어야 한다(기동 때 확인).
 * <p>권한 경계는 그대로 <b>그 일감의 지금 프로젝트</b>다 — {@link ProjectApi} 와 같은 판정을 그 프로젝트로 한다:
 * <ul>
 *   <li>워크스페이스 멤버가 아니면 <b>404</b> {@code WORKSPACE_NOT_FOUND}</li>
 *   <li>일감이 없거나(지운 것 포함) · 그 일감의 프로젝트의 독자가 아니거나 · 보관된 프로젝트인데 관리 권한이 없으면 <b>404</b>
 *       {@code ISSUE_NOT_FOUND} — 일감이 있는지도, 어느 프로젝트인지도 숨긴다</li>
 *   <li>독자지만 {@link #value()} 권한이 없으면 <b>403</b> {@code PERMISSION_REQUIRED}</li>
 * </ul>
 * 통과하면 컨트롤러 인자로 그 일감의 프로젝트 {@link ProjectContext} 를 받는다(일감마다의 규칙은 서비스가 더 본다).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface IssueApi {

  /** 그 일감의 프로젝트에서 필요한 권한 코드 */
  Permission value();
}
