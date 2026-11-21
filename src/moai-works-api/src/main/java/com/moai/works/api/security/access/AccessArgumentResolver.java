package com.moai.works.api.security.access;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.moai.works.api.config.EndPointPaths;

/** 접근 판정 결과({@link CurrentUser} · {@link WorkspaceContext})를 컨트롤러 인자로 넘긴다. */
@Configuration
public class AccessArgumentResolver implements HandlerMethodArgumentResolver, WebMvcConfigurer {

  private final AccessInterceptor interceptor;

  public AccessArgumentResolver(AccessInterceptor interceptor) {
    this.interceptor = interceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(interceptor).addPathPatterns(EndPointPaths.BASE_PATH + "/**");
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(this);
  }

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    Class<?> type = parameter.getParameterType();
    return type == CurrentUser.class || type == WorkspaceContext.class;
  }

  @Override
  public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav, NativeWebRequest request,
      WebDataBinderFactory binder) {
    Class<?> type = parameter.getParameterType();
    String key = type == CurrentUser.class ? AccessInterceptor.CURRENT_USER : AccessInterceptor.WORKSPACE_CONTEXT;
    Object value = request.getAttribute(key, RequestAttributes.SCOPE_REQUEST);
    if (value == null) {
      throw new IllegalStateException(parameter.getParameterType().getSimpleName()
          + " 는 해당 접근 어노테이션(@UserApi · @WorkspaceApi …)이 있는 엔드포인트에서만 받을 수 있습니다");
    }
    return value;
  }
}
