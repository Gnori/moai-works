package com.moai.works.api.mcpkey;

import java.util.UUID;

import com.moai.works.api.config.McpEndpoint;

/**
 * MCP 키로 인증한 호출자. {@link com.moai.works.api.security.McpKeyAuthenticationFilter} 가 request attribute 에 두고,
 * MCP 전송 문맥 추출기가 도구까지 옮긴다.
 */
public record McpKeyPrincipal(UUID keyId, String keyName, McpApiKey.KeyType keyType, McpEndpoint endpoint,
    String ownerSubject, String ownerName) {

  public static final String REQUEST_ATTRIBUTE = McpKeyPrincipal.class.getName();
}
