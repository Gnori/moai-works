package com.moai.works.api.security.access;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.moai.works.api.config.EndPointPaths;

/**
 * 기동 때 REST 계약을 확인한다 — 하나라도 어기면 기동하지 않는다.
 * <ul>
 *   <li>{@code /moai-works-api/**} 핸들러마다 접근 어노테이션이 <b>정확히 하나</b> (메서드 또는 클래스)</li>
 *   <li>{@link PublicApi} 는 공개 경로({@code /moai-works-api/v1/signup/**}) 안에서만</li>
 *   <li>{@link WorkspaceApi} 경로에는 {@code {workspace}} 변수가 있어야 한다</li>
 *   <li>{@link ProjectApi} 경로에는 {@code {workspace}} 와 {@code {project}} 변수가 있어야 한다</li>
 * </ul>
 * SeedSiteKeeper 의 "MCP 도구에 권한이 없으면 기동 실패" 규칙을 REST 전체로 넓힌 것이다.
 */
@Component
public class AccessContractVerifier implements SmartInitializingSingleton {

  private final RequestMappingHandlerMapping mapping;

  public AccessContractVerifier(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mapping) {
    this.mapping = mapping;
  }

  @Override
  public void afterSingletonsInstantiated() {
    List<String> problems = new ArrayList<>();
    for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
      Set<String> paths = entry.getKey().getPatternValues();
      if (paths.stream().noneMatch(p -> p.startsWith(EndPointPaths.BASE_PATH))) {
        continue;
      }
      HandlerMethod method = entry.getValue();
      String where = method.getBeanType().getSimpleName() + "#" + method.getMethod().getName() + " " + paths;
      List<Annotation> onMethod = AccessInterceptor.AccessRules.all(method.getMethod());
      List<Annotation> onType = AccessInterceptor.AccessRules.all(method.getBeanType());
      List<Annotation> rules = onMethod.isEmpty() ? onType : onMethod;
      if (rules.size() != 1) {
        problems.add(where + " — 접근 어노테이션 " + rules.size() + "개 (정확히 1개여야 함)");
        continue;
      }
      Annotation rule = rules.get(0);
      if (rule instanceof PublicApi && paths.stream().anyMatch(p -> !p.startsWith(EndPointPaths.V1 + "/signup"))) {
        problems.add(where + " — @PublicApi 는 " + EndPointPaths.V1 + "/signup 아래에만");
      }
      if (rule instanceof WorkspaceApi && paths.stream().anyMatch(p -> !p.contains("{workspace}"))) {
        problems.add(where + " — @WorkspaceApi 경로에 {workspace} 가 없음");
      }
      if (rule instanceof ProjectApi
          && paths.stream().anyMatch(p -> !p.contains("{workspace}") || !p.contains("{project}"))) {
        problems.add(where + " — @ProjectApi 경로에 {workspace} 와 {project} 가 있어야 함");
      }
    }
    if (!problems.isEmpty()) {
      throw new IllegalStateException("REST 접근 규칙 위반 — 기동을 멈춥니다:\n  " + String.join("\n  ", problems));
    }
  }
}
