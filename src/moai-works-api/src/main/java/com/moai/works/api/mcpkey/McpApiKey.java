package com.moai.works.api.mcpkey;

import java.time.Instant;
import java.util.UUID;

import com.moai.works.api.config.McpEndpoint;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * MCP 키 — 불투명 키(원문은 발급 응답에 한 번만 나간다). DB 에는 원문의 SHA-256 만 둔다.
 * seed-site-keeper-api {@code McpApiKeyEntity} 와 같은 원칙.
 */
@Entity
@Table(name = "mcp_api_key")
public class McpApiKey {

  /** 키 종류 */
  public enum KeyType {
    /** 사람 본인의 키 */
    PERSONAL,
    /** 서비스(사람이 아닌 자동화 · AI Agent)가 쓰는 키 */
    SERVICE
  }

  @Id
  private UUID id;

  @Column(nullable = false, length = 100)
  private String name;

  /** 화면 표시용 앞부분(예: mwk_mcp_Ab12…). 검증에는 쓰지 않는다. */
  @Column(name = "key_prefix", nullable = false, length = 16)
  private String keyPrefix;

  @Column(name = "key_hash", nullable = false, unique = true, length = 64)
  private String keyHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "key_type", nullable = false, length = 20)
  private KeyType keyType;

  /** 이 키로 들어갈 수 있는 MCP 입구 하나 */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private McpEndpoint endpoint;

  /** 발급한 사람 · 서비스 계정의 Keycloak sub */
  @Column(name = "owner_subject", nullable = false)
  private String ownerSubject;

  @Column(name = "owner_name", nullable = false)
  private String ownerName;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  protected McpApiKey() {
  }

  McpApiKey(String name, String keyPrefix, String keyHash, KeyType keyType, McpEndpoint endpoint,
      String ownerSubject, String ownerName, Instant createdAt, Instant expiresAt) {
    this.id = UUID.randomUUID();
    this.name = name;
    this.keyPrefix = keyPrefix;
    this.keyHash = keyHash;
    this.keyType = keyType;
    this.endpoint = endpoint;
    this.ownerSubject = ownerSubject;
    this.ownerName = ownerName;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  void revoke(Instant when) {
    if (revokedAt == null) {
      revokedAt = when;
    }
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getKeyPrefix() {
    return keyPrefix;
  }

  public KeyType getKeyType() {
    return keyType;
  }

  public McpEndpoint getEndpoint() {
    return endpoint;
  }

  public String getOwnerSubject() {
    return ownerSubject;
  }

  public String getOwnerName() {
    return ownerName;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public Instant getLastUsedAt() {
    return lastUsedAt;
  }
}
