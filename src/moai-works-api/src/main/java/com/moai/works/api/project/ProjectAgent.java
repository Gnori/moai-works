package com.moai.works.api.project;

import java.time.Instant;
import java.util.UUID;

import com.moai.works.api.common.ApiException;
import com.moai.works.api.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 프로젝트의 에이전트 멤버 하나 — 계정이 아니라 이름표다: 일감의 agentSession 이름과 그 주인(그 에이전트를 돌리는 MCP 키의 주인)
 * (2026-11-25 사용자, 일감 #285: "프로젝트 멤버에 에이전트 세션네임도 추가해줘" · "멤버는 사람, 에이전트 2가지로 나눠 지는거지").
 *
 * <p><b>자기 권한이 없다.</b> 에이전트는 주인의 MCP 키로, 주인의 권한으로만 일한다(ADR-024 — 키는 모두 사람의 것). 이 행은 판정에 쓰이지 않는다.
 * 지우지 않는다 — 쓰지 않음({@code active = false})으로 둔다. 이름 · 프로젝트는 바꾸지 않는다(일감의 agentSession 과 잇는 열쇠).
 * DB 트리거가 지우기와 이름 바꾸기를 막는다(V29).
 */
@Entity
@Table(name = "project_agent")
public class ProjectAgent {

  /** 이름 상한 — 일감 agent_session(V20)과 같다 */
  public static final int MAX_NAME = 100;
  /** 설명 상한 */
  public static final int MAX_DESCRIPTION = 200;

  /** 어떻게 등록됐나 — AUTO 는 일감에 그 이름이 처음 들어올 때 서버가, MANUAL 은 프로젝트 관리자가 손으로 */
  public enum Source {
    AUTO,
    MANUAL
  }

  @Id
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(nullable = false, length = MAX_NAME)
  private String name;

  @Column(name = "owner_user_id")
  private UUID ownerUserId;

  @Column(length = MAX_DESCRIPTION)
  private String description;

  @Column(nullable = false)
  private boolean active;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Source source;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "updated_by")
  private UUID updatedBy;

  @Version
  @Column(name = "row_version", nullable = false)
  private int rowVersion;

  protected ProjectAgent() {
  }

  ProjectAgent(UUID projectId, String name, UUID ownerUserId, String description, Source source, UUID by, Instant now) {
    this.id = Ids.next();
    this.projectId = projectId;
    this.name = name;
    this.ownerUserId = ownerUserId;
    this.description = description;
    this.active = true;
    this.source = source;
    this.createdAt = now;
    this.createdBy = by;
    this.updatedAt = now;
    this.updatedBy = by;
  }

  /**
   * 세션 이름을 다듬는다 — 앞뒤 공백을 빼고 비면 없음(null). 100자를 넘거나 줄바꿈 같은 제어 문자가 있으면 400 INVALID_AGENT_SESSION.
   * 일감의 agentSession(REST · MCP)과 에이전트 멤버의 이름이 같이 쓰는 규칙이다(V20 · V29 의 CHECK 와 같다).
   */
  public static String normalizeName(String value) {
    if (value == null) {
      return null;
    }
    String v = value.trim();
    if (v.isEmpty()) {
      return null;
    }
    if (v.length() > MAX_NAME) {
      throw ApiException.badRequest("INVALID_AGENT_SESSION", "에이전트 세션 이름은 " + MAX_NAME + "자를 넘을 수 없습니다.");
    }
    if (v.chars().anyMatch(Character::isISOControl)) {
      throw ApiException.badRequest("INVALID_AGENT_SESSION", "에이전트 세션 이름에 줄바꿈 같은 제어 문자를 넣을 수 없습니다.");
    }
    return v;
  }

  void describe(String description) {
    this.description = description;
  }

  void activate(boolean active) {
    this.active = active;
  }

  void ownBy(UUID ownerUserId) {
    this.ownerUserId = ownerUserId;
  }

  void touch(UUID by, Instant now) {
    this.updatedBy = by;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getName() {
    return name;
  }

  public UUID getOwnerUserId() {
    return ownerUserId;
  }

  public String getDescription() {
    return description;
  }

  public boolean isActive() {
    return active;
  }

  public Source getSource() {
    return source;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public int getRowVersion() {
    return rowVersion;
  }
}
