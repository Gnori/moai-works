package com.moai.works.api.security.access;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Map;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import com.moai.works.api.account.AccountService;
import com.moai.works.api.common.ApiException;
import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.security.ClientIp;
import com.moai.works.api.issue.IssueLocator;
import com.moai.works.api.project.ProjectAccessService;
import com.moai.works.api.security.KeycloakPrincipal;
import com.moai.works.api.workspace.WorkspaceAccessService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 엔드포인트의 접근 어노테이션을 판정한다 — REST 권한 판정은 모두 여기서 한다(기본 거부).
 *
 * <p>어노테이션이 없는 REST 핸들러는 {@link AccessContractVerifier} 가 기동 때 막지만, 혹시 지나쳐도 여기서 403 이다.
 * 판정 결과(호출자 · 워크스페이스 문맥)는 요청 속성에 두고, 컨트롤러는 인자로 받는다({@link AccessArgumentResolver}).
 */
@Component
public class AccessInterceptor implements HandlerInterceptor {

  static final String CURRENT_USER = AccessInterceptor.class.getName() + ".user";
  static final String WORKSPACE_CONTEXT = AccessInterceptor.class.getName() + ".workspace";
  static final String PROJECT_CONTEXT = AccessInterceptor.class.getName() + ".project";

  private final AccountService accounts;
  private final ClientIp clientIp;
  private final WorkspaceAccessService workspaceAccess;

  private final ProjectAccessService projectAccess;
  private final IssueLocator issueLocator;

  public AccessInterceptor(AccountService accounts, WorkspaceAccessService workspaceAccess,
      ProjectAccessService projectAccess, IssueLocator issueLocator, ClientIp clientIp) {
    this.accounts = accounts;
    this.clientIp = clientIp;
    this.workspaceAccess = workspaceAccess;
    this.projectAccess = projectAccess;
    this.issueLocator = issueLocator;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(handler instanceof HandlerMethod method) || !request.getRequestURI().startsWith(EndPointPaths.BASE_PATH)) {
      return true;
    }
    Annotation rule = AccessRules.find(method)
        .orElseThrow(() -> ApiException.forbidden("ACCESS_RULE_MISSING", "이 엔드포인트에는 접근 규칙이 없습니다."));

    if (rule instanceof PublicApi) {
      return true;
    }
    KeycloakPrincipal principal = principal();
    switch (rule) {
      case AuthenticatedApi a -> {
      }
      case ServiceApi s -> {
        if (!principal.serviceAccount() || Arrays.stream(s.clients()).noneMatch(c -> c.equals(principal.clientId()))) {
          throw ApiException.forbidden("SERVICE_ONLY", "이 API 는 지정된 서비스만 부를 수 있습니다.");
        }
      }
      case UserApi u -> {
        CurrentUser user = user(principal, request);
        if (user.systemAdmin() && !u.systemAdmin()) {
          throw ApiException.forbidden("SYSTEM_ADMIN_NOT_ALLOWED", "시스템 관리자 계정은 워크스페이스 영역을 쓸 수 없습니다.");
        }
        request.setAttribute(CURRENT_USER, user);
      }
      case PlatformApi p -> {
        CurrentUser user = user(principal, request);
        if (!user.platformRoles().contains(p.role())) {
          throw ApiException.forbidden("PLATFORM_ROLE_REQUIRED", "플랫폼 역할 " + p.role() + " 이(가) 필요합니다.");
        }
        request.setAttribute(CURRENT_USER, user);
      }
      case WorkspaceApi w -> {
        CurrentUser user = user(principal, request);
        WorkspaceContext context = workspace(request, user);
        if (!context.has(w.value())) {
          throw ApiException.forbidden("PERMISSION_REQUIRED", "권한 " + w.value().code() + " 이(가) 필요합니다.");
        }
        request.setAttribute(CURRENT_USER, user);
        request.setAttribute(WORKSPACE_CONTEXT, context);
      }
      case ProjectApi p -> {
        CurrentUser user = user(principal, request);
        WorkspaceContext workspace = workspace(request, user);
        // 프로젝트가 없거나 · 독자가 아니거나 · 보관됐는데 관리 권한이 없으면 모두 같은 404 — 존재를 숨긴다
        ProjectContext project = pathVariable(request, "project")
            .flatMap(key -> projectAccess.contextFor(workspace, key))
            .orElseThrow(() -> ApiException.notFound("PROJECT_NOT_FOUND", "프로젝트를 찾을 수 없습니다."));
        if (!project.has(p.value())) {
          throw ApiException.forbidden("PERMISSION_REQUIRED", "권한 " + p.value().code() + " 이(가) 필요합니다.");
        }
        request.setAttribute(CURRENT_USER, user);
        request.setAttribute(WORKSPACE_CONTEXT, workspace);
        request.setAttribute(PROJECT_CONTEXT, project);
      }
      case IssueApi i -> {
        CurrentUser user = user(principal, request);
        WorkspaceContext workspace = workspace(request, user);
        // 일감이 없거나 · 그 일감의 지금 프로젝트의 독자가 아니면 모두 같은 404 — 일감이 있는지 · 어느 프로젝트인지 숨긴다
        ProjectContext project = pathVariable(request, "number").flatMap(AccessInterceptor::number)
            .flatMap(n -> issueLocator.contextFor(workspace, n))
            .orElseThrow(() -> ApiException.notFound("ISSUE_NOT_FOUND", "일감을 찾을 수 없습니다."));
        if (!project.has(i.value())) {
          throw ApiException.forbidden("PERMISSION_REQUIRED", "권한 " + i.value().code() + " 이(가) 필요합니다.");
        }
        request.setAttribute(CURRENT_USER, user);
        request.setAttribute(WORKSPACE_CONTEXT, workspace);
        request.setAttribute(PROJECT_CONTEXT, project);
      }
      default -> throw ApiException.forbidden("ACCESS_RULE_UNKNOWN", "알 수 없는 접근 규칙입니다.");
    }
    return true;
  }

  /**
   * 경로의 워크스페이스 — 멤버가 아니면 404.
   * 시스템 관리자 계정은 어느 워크스페이스의 멤버로도 보지 않는다(예전에 생긴 멤버십이 남아 있어도 같다) — 같은 404 다.
   * 시스템 관리자가 사용 중지한 워크스페이스는 모든 입구가 막힌다 — 멤버에게는 403 {@code WORKSPACE_SUSPENDED}(화면이 사용 중지 안내를
   * 보인다), 멤버가 아니면 없는 것과 같은 404 다.
   */
  private WorkspaceContext workspace(HttpServletRequest request, CurrentUser user) {
    if (user.systemAdmin()) {
      throw ApiException.notFound("WORKSPACE_NOT_FOUND", "워크스페이스를 찾을 수 없습니다.");
    }
    java.util.Optional<String> slug = pathVariable(request, "workspace");
    return slug.flatMap(s -> workspaceAccess.contextFor(s, user.id())).orElseThrow(() -> {
      if (slug.isPresent() && workspaceAccess.suspendedFor(slug.get(), user.id())) {
        return ApiException.forbidden("WORKSPACE_SUSPENDED", "시스템 관리자가 이 워크스페이스를 사용 중지했습니다.");
      }
      return ApiException.notFound("WORKSPACE_NOT_FOUND", "워크스페이스를 찾을 수 없습니다.");
    });
  }

  private CurrentUser user(KeycloakPrincipal principal, HttpServletRequest request) {
    if (principal.serviceAccount()) {
      throw ApiException.forbidden("USER_ONLY", "사람 계정만 부를 수 있습니다.");
    }
    return accounts.requireActiveUser(principal, clientIp.of(request));
  }

  private static KeycloakPrincipal principal() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof KeycloakPrincipal p) {
      return p;
    }
    throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다.");
  }

  private static java.util.Optional<String> pathVariable(HttpServletRequest request, String name) {
    Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (vars instanceof Map<?, ?> map && map.get(name) instanceof String value) {
      return java.util.Optional.of(value);
    }
    return java.util.Optional.empty();
  }

  /** 경로의 일감 번호 — 숫자가 아니면 없는 일감과 같다 */
  private static java.util.Optional<Integer> number(String value) {
    if (value == null || !value.matches("\\d{1,9}")) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.of(Integer.parseInt(value));
  }

  /** 핸들러의 접근 어노테이션 찾기 (메서드 우선, 없으면 클래스) */
  static final class AccessRules {

    static final java.util.List<Class<? extends Annotation>> TYPES = java.util.List.of(PublicApi.class,
        AuthenticatedApi.class, UserApi.class, ServiceApi.class, PlatformApi.class, WorkspaceApi.class,
        ProjectApi.class, IssueApi.class);

    static java.util.Optional<Annotation> find(HandlerMethod method) {
      java.util.List<Annotation> onMethod = all(method.getMethod());
      if (!onMethod.isEmpty()) {
        return java.util.Optional.of(onMethod.get(0));
      }
      java.util.List<Annotation> onType = all(method.getBeanType());
      return onType.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(onType.get(0));
    }

    static java.util.List<Annotation> all(java.lang.reflect.AnnotatedElement element) {
      return TYPES.stream()
          .map(t -> (Annotation) AnnotatedElementUtils.findMergedAnnotation(element, t))
          .filter(java.util.Objects::nonNull)
          .toList();
    }
  }
}
