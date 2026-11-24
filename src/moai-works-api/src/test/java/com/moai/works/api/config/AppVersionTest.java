package com.moai.works.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;

/** 실제 판(일감 #183) — Maven 판 + 짧은 커밋, 빌드 정보가 없을 때 */
class AppVersionTest {

  private static BuildProperties build(String version) {
    Properties p = new Properties();
    p.setProperty("version", version);
    return new BuildProperties(p);
  }

  private static GitProperties git(String commit, boolean dirty) {
    Properties p = new Properties();
    p.setProperty("commit.id", commit);
    p.setProperty("dirty", String.valueOf(dirty));
    return new GitProperties(p);
  }

  @Test
  void Maven_판에_짧은_커밋을_붙이고_커밋하지_않은_변경이_있으면_dirty() {
    assertThat(AppVersion.compose(build("0.1.0-SNAPSHOT"), git("4b5e2149320e756987245d7b115b5966c222f7da", false)))
        .isEqualTo("0.1.0-SNAPSHOT+4b5e214");
    assertThat(AppVersion.compose(build("0.2.0"), git("c65c8b82f76e11cdb3936cf68241159b14a7bdcc", true)))
        .isEqualTo("0.2.0+c65c8b8.dirty");
  }

  @Test
  void git_정보가_없으면_Maven_판만_빌드_정보도_없으면_dev() {
    assertThat(AppVersion.compose(build("0.1.0-SNAPSHOT"), null)).isEqualTo("0.1.0-SNAPSHOT");
    assertThat(AppVersion.compose(null, null)).isEqualTo(AppVersion.UNKNOWN);
  }
}
