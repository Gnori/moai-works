package com.moai.works.api.config;

/**
 * MCP 입구. 키는 입구 하나에만 쓸 수 있다.
 *
 * <p>moai-works 는 AI Agent 를 내장하지 않는다. 바깥 AI Agent(Claude Code 등)가 이 입구로 붙어 프로젝트를 관리한다.
 * 지금은 고정 입구 하나다. 워크스페이스마다 여러 입구를 만드는 구조는 design/07 에 있다.
 */
public enum McpEndpoint {
  /** 프로젝트 관리용 — 바깥 AI Agent 가 프로젝트 · 일감을 보고 고친다. */
  PROJECT
}
