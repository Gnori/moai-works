package com.moai.works.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 화면용 보안 정책(CSP) — 같은 출처만. 첨부 보기(일감 #310)의 동영상 · 소리 blob: 주소만 media-src 에 더했고,
 * 스크립트는 여전히 같은 출처만(eval · WebAssembly · blob: worker 없음 — pdf.js 도 그대로 돈다).
 */
class ContentSecurityPolicyTest {

  private static List<String> directives() {
    return Arrays.stream(SecurityConfiguration.CSP.split(";")).map(String::trim).toList();
  }

  @Test
  void 정책_전체가_SECURITY_md_와_같다() {
    assertThat(SecurityConfiguration.CSP).isEqualTo("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
        + "img-src 'self' data: blob:; media-src 'self' blob:; font-src 'self' data:; connect-src 'self'; object-src 'none'; "
        + "base-uri 'self'; form-action 'self'; frame-ancestors 'none'");
  }

  @Test
  void 동영상_소리는_같은_출처와_blob_만() {
    assertThat(directives()).contains("media-src 'self' blob:");
  }

  @Test
  void 스크립트_worker_는_같은_출처만_eval_wasm_blob_없음() {
    assertThat(directives()).contains("script-src 'self'");
    assertThat(directives()).noneMatch(d -> d.startsWith("worker-src") || d.startsWith("child-src"));
    assertThat(SecurityConfiguration.CSP).doesNotContain("unsafe-eval").doesNotContain("wasm-unsafe-eval");
  }
}
