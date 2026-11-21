package com.moai.works.api.workspace;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 워크스페이스 = 테넌트. Keycloak 조직 하나와 짝이다({@link #keycloakOrgId}).
 *
 * <p>설정(이름 · 공개 범위 · 가입 방식 …)은 DB 가 정본이고, 멤버십 · 역할은 Keycloak 이 정본이다(design/03 §6).
 */
@Entity
@Table(name = "workspace")
public class Workspace {

  public enum Kind {
    /** 가입하면 생기는 개인 워크스페이스. 계정당 1개, 소유자 고정 */
    PERSONAL,
    /** 사용자가 만든 팀 워크스페이스 */
    TEAM
  }

  public enum Visibility {
    /** 퍼블릭 — 찾기에 보이고 가입할 수 있다(가입 방식은 {@link JoinPolicy}) */
    PUBLIC,
    /** 프라이빗 — 다른 사람에게 보이지 않고 초대로만 들어온다 */
    PRIVATE
  }

  /** 퍼블릭 워크스페이스에 가입하는 방식. 프라이빗 · 개인에서는 쓰이지 않는다(APPROVAL 로 둔다) */
  public enum JoinPolicy {
    /** 관리자가 승인하면 가입된다 */
    APPROVAL,
    /** 신청하면 바로 가입된다 */
    AUTO
  }

  /** 프라이빗은 가입을 받지 않으므로 가입 방식을 따로 두지 않는다 — 저장하는 값을 하나로 맞춘다 */
  public static JoinPolicy joinPolicyFor(Visibility visibility, JoinPolicy requested) {
    return visibility == Visibility.PUBLIC ? requested : JoinPolicy.APPROVAL;
  }

  public enum Status {
    /** Keycloak 조직 · 역할을 만드는 중 (끊기면 다음 요청에서 이어 만든다) */
    PROVISIONING,
    ACTIVE,
    SUSPENDED,
    DELETION_PENDING,
    DELETED
  }

  @Id
  private UUID id;

  @Column(nullable = false, unique = true, length = 63)
  private String slug;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 1000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Kind kind;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Visibility visibility;

  @Enumerated(EnumType.STRING)
  @Column(name = "join_policy", nullable = false, length = 20)
  private JoinPolicy joinPolicy;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "allowed_join_domains", nullable = false, columnDefinition = "text[]")
  private List<String> allowedJoinDomains = new ArrayList<>();

  @Column(name = "default_role_key", nullable = false, length = 63)
  private String defaultRoleKey = BuiltinRole.MEMBER.key();

  @Column(name = "rejoin_wait_days", nullable = false)
  private int rejoinWaitDays = 7;

  @Column(name = "keycloak_org_id", unique = true, length = 64)
  private String keycloakOrgId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private Status status;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deletion_requested_at")
  private Instant deletionRequestedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private int rowVersion;

  protected Workspace() {
  }

  private Workspace(String slug, String name, String description, Kind kind, Visibility visibility,
      JoinPolicy joinPolicy, UUID createdBy, Instant now) {
    this.id = UUID.randomUUID();
    this.slug = slug;
    this.name = name;
    this.description = description;
    this.kind = kind;
    this.visibility = visibility;
    this.joinPolicy = joinPolicy;
    this.status = Status.PROVISIONING;
    this.createdBy = createdBy;
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** 개인 워크스페이스 — 보이지 않고, 가입 신청을 받지 않는다. */
  public static Workspace personal(String slug, String name, UUID owner, Instant now) {
    return new Workspace(slug, name, null, Kind.PERSONAL, Visibility.PRIVATE, JoinPolicy.APPROVAL, owner, now);
  }

  public static Workspace team(String slug, String name, String description, Visibility visibility,
      JoinPolicy joinPolicy, UUID creator, Instant now) {
    return new Workspace(slug, name, description, Kind.TEAM, visibility, joinPolicy, creator, now);
  }

  public void linkKeycloakOrganization(String orgId) {
    this.keycloakOrgId = orgId;
  }

  public void activate(Instant now) {
    this.status = Status.ACTIVE;
    this.updatedAt = now;
  }

  public void updateSettings(String name, String description, Visibility visibility, JoinPolicy joinPolicy,
      List<String> allowedJoinDomains, String defaultRoleKey, Instant now) {
    if (kind == Kind.PERSONAL && visibility == Visibility.PUBLIC) {
      throw new IllegalArgumentException("개인 워크스페이스는 공개하거나 가입 신청을 받을 수 없습니다");
    }
    this.name = name;
    this.description = description;
    this.visibility = visibility;
    this.joinPolicy = joinPolicyFor(visibility, joinPolicy);
    this.allowedJoinDomains = new ArrayList<>(allowedJoinDomains);
    this.defaultRoleKey = defaultRoleKey;
    this.updatedAt = now;
  }

  public boolean isActive() {
    return status == Status.ACTIVE;
  }

  /** 이 이메일이 가입 신청 허용 도메인에 드는가 (비어 있으면 누구나). */
  public boolean allowsJoinFrom(String email) {
    if (allowedJoinDomains.isEmpty()) {
      return true;
    }
    String domain = email.substring(email.indexOf('@') + 1).toLowerCase();
    return allowedJoinDomains.stream().anyMatch(d -> d.equalsIgnoreCase(domain));
  }

  public UUID getId() {
    return id;
  }

  public String getSlug() {
    return slug;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public Kind getKind() {
    return kind;
  }

  public Visibility getVisibility() {
    return visibility;
  }

  public JoinPolicy getJoinPolicy() {
    return joinPolicy;
  }

  public List<String> getAllowedJoinDomains() {
    return List.copyOf(allowedJoinDomains);
  }

  public String getDefaultRoleKey() {
    return defaultRoleKey;
  }

  public int getRejoinWaitDays() {
    return rejoinWaitDays;
  }

  public String getKeycloakOrgId() {
    return keycloakOrgId;
  }

  public Status getStatus() {
    return status;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
