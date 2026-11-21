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

  List<McpApiKey> findByOwnerSubjectOrderByCreatedAtDesc(String ownerSubject);

  /** 마지막 사용 시각 — {@code before} 보다 오래됐을 때만 쓴다(요청마다 쓰지 않는다). */
  @Modifying
  @Query("update McpApiKey k set k.lastUsedAt = :now where k.id = :id and (k.lastUsedAt is null or k.lastUsedAt < :before)")
  int touchLastUsed(@Param("id") UUID id, @Param("now") Instant now, @Param("before") Instant before);
}
