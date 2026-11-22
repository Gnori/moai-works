package com.moai.works.api.mcpkey;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface McpApiKeyRepository extends JpaRepository<McpApiKey, UUID> {

  Optional<McpApiKey> findByKeyHash(String keyHash);

  /** 워크스페이스의 키 전부 — 발급 이력(만료된 것 포함), 새것부터 */
  List<McpApiKey> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

  List<McpApiKey> findByWorkspaceIdAndOwnerUserIdOrderByCreatedAtDesc(UUID workspaceId, UUID ownerUserId);

  /** 내 키 — 모든 워크스페이스 */
  List<McpApiKey> findByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);

  /** 그 사람의 그 워크스페이스 키 가운데 아직 유효한 것 (떠날 때 만료시킨다) */
  @Query("select k from McpApiKey k where k.workspaceId = ?1 and k.ownerUserId = ?2 and k.expiresAt > ?3")
  List<McpApiKey> findActive(UUID workspaceId, UUID ownerUserId, Instant now);

  /** 마지막 사용 시각 — {@code before} 보다 오래됐을 때만 쓴다(요청마다 쓰지 않는다). */
  @Modifying
  @Query("update McpApiKey k set k.lastUsedAt = :now where k.id = :id and (k.lastUsedAt is null or k.lastUsedAt < :before)")
  int touchLastUsed(@Param("id") UUID id, @Param("now") Instant now, @Param("before") Instant before);
}
