package com.moai.works.api.issue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 일감 유형 — 공통 시드(요구사항 · 기능 · 버그 · 작업). moai-wiki 의 페이지 유형(page_type) 자리다. 프로젝트마다 바꾸기는 나중 */
@Entity
@Table(name = "issue_type")
public class IssueType {

  @Id
  @Column(length = 30)
  private String key;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, length = 300)
  private String description;

  @Column(nullable = false)
  private int position;

  protected IssueType() {
  }

  public String getKey() {
    return key;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public int getPosition() {
    return position;
  }
}
