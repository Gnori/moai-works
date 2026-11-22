package com.moai.works.api.mcpkey;

import java.util.UUID;

import com.moai.works.api.config.McpEndpoint;

/**
 * MCP 키 검사를 통과한 호출자 — 키 하나 = 워크스페이스 하나 · 주인 계정 하나. 도구의 범위는 이 주인의 권한이다
 * (모델이 넘긴 인자가 아니라 이 문맥으로 정한다).
 */
public record McpKeyPrincipal(UUID keyId, String keyName, McpEndpoint endpoint, UUID workspaceId, UUID ownerUserId,
    String ownerName) {

  public static final String REQUEST_ATTRIBUTE = McpKeyPrincipal.class.getName();
}
