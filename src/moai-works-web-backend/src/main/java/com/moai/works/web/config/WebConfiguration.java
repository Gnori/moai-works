package com.moai.works.web.config;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * React 정적 파일 서비스 (classpath:static — moai-works-web-frontend 빌드 결과).
 * <ul>
 *   <li>{@code /assets/**}: 파일 이름에 해시가 있어 1년 캐시</li>
 *   <li>그 밖에 파일이 없는 경로(예: /w/workspace-a/members)는 index.html — 화면 주소는 React Router 가 처리</li>
 *   <li>{@code /api/**} · {@code /actuator/**} 는 index.html 로 돌리지 않는다(없는 API 는 404)</li>
 * </ul>
 */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {

  /** 정적 파일 위치. 기본은 jar 안(classpath:/static/). 로컬 개발은 빌드 폴더를 직접 읽어 다시 띄우지 않고 반영한다. */
  private final String staticLocation;

  public WebConfiguration(@Value("${moai-works-web.static-location:classpath:/static/}") String staticLocation) {
    this.staticLocation = staticLocation.endsWith("/") ? staticLocation : staticLocation + "/";
  }

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/assets/**")
        .addResourceLocations(staticLocation + "assets/")
        .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    registry.addResourceHandler("/**")
        .addResourceLocations(staticLocation)
        .setCacheControl(CacheControl.noCache())
        .resourceChain(false)
        .addResolver(new SpaFallbackResolver(staticLocation));
  }

  /** 파일이 있으면 그 파일, 확장자 없는 화면 경로면 index.html, API 경로면 없음(404). */
  static final class SpaFallbackResolver extends PathResourceResolver {

    private final String root;

    SpaFallbackResolver(String root) {
      this.root = root;
    }

    @Override
    protected Resource getResource(String resourcePath, Resource location) throws IOException {
      if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) {
        return null;
      }
      Resource requested = location.createRelative(resourcePath);
      if (requested.exists() && requested.isReadable()) {
        return requested;
      }
      String last = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
      if (last.contains(".")) {
        return null;
      }
      Resource index = root.startsWith("classpath:")
          ? new ClassPathResource(root.substring("classpath:".length()) + "index.html")
          : new org.springframework.core.io.FileSystemResource(root.substring("file:".length()) + "index.html");
      return index.exists() ? index : null;
    }
  }
}
