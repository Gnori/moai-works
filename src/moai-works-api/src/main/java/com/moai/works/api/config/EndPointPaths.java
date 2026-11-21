package com.moai.works.api.config;

/** REST 경로 상수. MCP 입구 경로는 설정(moai-works.mcp.*)이 정한다. */
public final class EndPointPaths {

  /** REST API 인바운드 prefix */
  public static final String BASE_PATH = "/moai-works-api";

  /** 버전 1 — 계정 · 워크스페이스 · 관리 API */
  public static final String V1 = BASE_PATH + "/v1";

  private EndPointPaths() {
  }
}
