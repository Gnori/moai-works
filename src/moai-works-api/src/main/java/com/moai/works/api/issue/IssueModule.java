package com.moai.works.api.issue;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * 일감의 관련 모듈 하나 — 일감 하나에 여럿(없어도 된다). 일감과 모듈은 같은 프로젝트여야 한다(V21 복합 외래 키).
 * 바꾸기는 {@link IssueService} 가 한다(넣고 빼기, 이력 · 판 · 감사와 함께).
 */
@Entity
@Table(name = "issue_module")
@IdClass(IssueModule.Key.class)
public class IssueModule {

  @Id
  @Column(name = "issue_id", nullable = false)
  private UUID issueId;

  @Id
  @Column(name = "module_id", nullable = false)
  private UUID moduleId;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  protected IssueModule() {
  }

  IssueModule(UUID projectId, UUID issueId, UUID moduleId) {
    this.projectId = projectId;
    this.issueId = issueId;
    this.moduleId = moduleId;
  }

  public UUID getIssueId() {
    return issueId;
  }

  public UUID getModuleId() {
    return moduleId;
  }

  public UUID getProjectId() {
    return projectId;
  }

  /** (일감, 모듈) */
  public static class Key implements Serializable {
    private UUID issueId;
    private UUID moduleId;

    public Key() {
    }

    public Key(UUID issueId, UUID moduleId) {
      this.issueId = issueId;
      this.moduleId = moduleId;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Key k && Objects.equals(issueId, k.issueId) && Objects.equals(moduleId, k.moduleId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(issueId, moduleId);
    }
  }
}
