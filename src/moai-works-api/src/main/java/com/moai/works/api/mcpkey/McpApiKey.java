package com.moai.works.api.mcpkey;

import java.time.Instant;
import java.util.UUID;

import com.moai.works.api.common.Ids;
import com.moai.works.api.config.McpEndpoint;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * MCP 키 — 워크스페이스 하나와 주인 계정 하나에 묶이고, 주인의 권한 안에서만 닿는다(2026-11-22 사용자 결정, ADR-024).
 * 원문은 저장하지 않고 SHA-256 만 둔다. <b>지우지 않는다</b> — 일찍 만료시킬 뿐이다(DB 트리거가 지우기 · 발급 정보 변경을 막는다).
 */
@Entity
@Table(name = "mcp_api_key")
public class McpApiKey {

  public enum ExpireReason {
    /** 주인이나 관리자가 만료시킴 */
    MANUAL,
    /** 주인이 워크스페이스를 떠남 */
    MEMBER_LEFT,
    /** 엔드포인트를 지움 (엔드포인트가 생기면) */
    ENDPOINT_DELETED
  }

  @Id
  private UUID id;

  @Column(nullable = false, length = 100)
  private String name;

  /** 화면 표시용 앞부분(예: mai-wks-Ab12…). 검증에는 쓰지 않는다. */
  @Column(name = "key_prefix", nullable = false, length = 16)
  private String keyPrefix;

  @Column(name = "key_hash", nullable = false, unique = true, length = 64)
  private String keyHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private McpEndpoint endpoint;

  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;

  /** 주인 = 발급한 사람 */
  @Column(name = "owner_user_id", nullable = false)
  private UUID ownerUserId;

  @Column(name = "owner_name", nullable = false)
  private String ownerName;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  @Column(name = "issued_ip", length = 64)
  private String issuedIp;

  @Column(name = "issued_user_agent", length = 500)
  private String issuedUserAgent;

  @Column(name = "expired_by")
  private UUID expiredBy;

  @Enumerated(EnumType.STRING)
  @Column(name = "expire_reason", length = 20)
  private ExpireReason expireReason;

  @Column(name = "expire_note", length = 500)
  private String expireNote;

  protected McpApiKey() {
  }

  public McpApiKey(UUID workspaceId, UUID ownerUserId, String ownerName, String name, String keyPrefix, String keyHash,
      McpEndpoint endpoint, Instant createdAt, Instant expiresAt, String issuedIp, String issuedUserAgent) {
    this.id = Ids.next();
    this.workspaceId = workspaceId;
    this.ownerUserId = ownerUserId;
    this.ownerName = ownerName;
    this.name = name;
    this.keyPrefix = keyPrefix;
    this.keyHash = keyHash;
    this.endpoint = endpoint;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
    this.issuedIp = issuedIp;
    this.issuedUserAgent = issuedUserAgent;
  }

  /** 지금 만료시킨다 — 기간을 지금으로 앞당기고 누가 · 왜를 남긴다 */
  public void expireNow(UUID by, ExpireReason reason, String note, Instant now) {
    if (!isActive(now)) {
      throw new IllegalStateException("이미 만료된 키입니다");
    }
    this.expiresAt = now;
    this.expiredBy = by;
    this.expireReason = reason;
    this.expireNote = note;
  }

  public boolean isActive(Instant now) {
    return expiresAt.isAfter(now);
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

  public McpEndpoint getEndpoint() {
    return endpoint;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public UUID getOwnerUserId() {
    return ownerUserId;
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

  public Instant getLastUsedAt() {
    return lastUsedAt;
  }

  public String getIssuedIp() {
    return issuedIp;
  }

  public String getIssuedUserAgent() {
    return issuedUserAgent;
  }

  public UUID getExpiredBy() {
    return expiredBy;
  }

  public ExpireReason getExpireReason() {
    return expireReason;
  }

  public String getExpireNote() {
    return expireNote;
  }
}
