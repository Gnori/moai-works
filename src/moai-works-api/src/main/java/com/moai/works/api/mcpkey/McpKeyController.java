package com.moai.works.api.mcpkey;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.config.McpEndpoint;
import com.moai.works.api.security.KeycloakPrincipal;
import com.moai.works.api.security.access.AuthenticatedApi;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * MCP 키 — Keycloak 로그인한 호출자 본인 명의로 발급 · 목록 · 폐기.
 * 지금은 고정 입구(/mcp/project)용. 워크스페이스 MCP 엔드포인트로 옮기면(design/07, WBS 3단계)
 * {@code @WorkspaceApi(MCP_KEY_ISSUE_*)} 로 바뀐다.
 */
@RestController
@AuthenticatedApi
@RequestMapping(EndPointPaths.BASE_PATH + "/mcp-keys")
public class McpKeyController {

  private final McpKeyService service;

  public McpKeyController(McpKeyService service) {
    this.service = service;
  }

  public record IssueRequest(@NotBlank @Size(max = 100) String name, @NotNull McpEndpoint endpoint,
      McpApiKey.KeyType keyType, Integer validDays) {
  }

  /** {@code key} 원문은 이 응답에만 있다 — 다시 볼 수 없다. */
  public record IssueResponse(UUID id, String name, McpEndpoint endpoint, McpApiKey.KeyType keyType,
      Instant expiresAt, String key) {
  }

  public record KeyView(UUID id, String name, String keyPrefix, McpEndpoint endpoint, McpApiKey.KeyType keyType,
      Instant createdAt, Instant expiresAt, Instant revokedAt, Instant lastUsedAt) {

    static KeyView of(McpApiKey k) {
      return new KeyView(k.getId(), k.getName(), k.getKeyPrefix(), k.getEndpoint(), k.getKeyType(),
          k.getCreatedAt(), k.getExpiresAt(), k.getRevokedAt(), k.getLastUsedAt());
    }
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public IssueResponse issue(@AuthenticationPrincipal KeycloakPrincipal me, @Valid @RequestBody IssueRequest request) {
    McpKeyService.Issued issued = service.issue(me, request.name(), request.endpoint(), request.keyType(),
        request.validDays());
    McpApiKey k = issued.key();
    return new IssueResponse(k.getId(), k.getName(), k.getEndpoint(), k.getKeyType(), k.getExpiresAt(),
        issued.token());
  }

  @GetMapping
  public List<KeyView> list(@AuthenticationPrincipal KeycloakPrincipal me) {
    return service.listOwned(me).stream().map(KeyView::of).toList();
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(@AuthenticationPrincipal KeycloakPrincipal me, @PathVariable UUID id) {
    service.revoke(me, id);
  }
}
