package com.moai.works.api.config;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.stereotype.Component;

/**
 * 이 api 의 실제 판 — Maven 프로젝트 판 + 짧은 커밋(예 {@code 0.1.0-SNAPSHOT+4b5e214}, 커밋하지 않은 변경이 있던 빌드면 끝에 {@code .dirty}).
 * MCP {@code initialize} 의 serverInfo.version 과 {@code /actuator/info} 의 {@code app.version} 이 이 값이다(일감 #183 — 전에는 "0.1.0" 고정).
 *
 * <p>판은 빌드가 만든 {@code META-INF/build-info.properties}(spring-boot-maven-plugin {@code build-info}), 커밋은 {@code git.properties}
 * (git-commit-id-maven-plugin — git 이 없는 곳에서 빌드하면 만들지 않고 빌드는 그대로 된다)에서 읽는다. 둘 다 없으면(IDE 에서 바로 띄움) {@code dev}.
 */
@Component
public class AppVersion implements InfoContributor {

  /** 빌드 정보가 없을 때 */
  public static final String UNKNOWN = "dev";

  private final String value;

  public AppVersion(ObjectProvider<BuildProperties> build, ObjectProvider<GitProperties> git) {
    this.value = compose(build.getIfAvailable(), git.getIfAvailable());
  }

  static String compose(BuildProperties build, GitProperties git) {
    String version = build == null || build.getVersion() == null || build.getVersion().isBlank() ? UNKNOWN : build.getVersion();
    String commit = git == null ? null : git.getShortCommitId();
    if (commit == null || commit.isBlank()) {
      return version;
    }
    // semver 의 빌드 메타데이터(+) — 판을 견줄 때는 무시되고, 사람은 어느 커밋인지 본다
    return version + "+" + commit + ("true".equals(git.get("dirty")) ? ".dirty" : "");
  }

  /** 판 — 예 0.1.0-SNAPSHOT+4b5e214 */
  public String value() {
    return value;
  }

  /** {@code /actuator/info} 에 {@code app.version} 으로 — build · git 항목은 Spring Boot 가 따로 싣는다 */
  @Override
  public void contribute(Info.Builder builder) {
    builder.withDetail("app", Map.of("version", value));
  }
}
