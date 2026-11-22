package com.moai.works.api.project;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 프로젝트 API 응답 모양. 엔티티를 그대로 내보내지 않는다. */
public final class ProjectViews {

  private ProjectViews() {
  }

  /** 프로젝트 목록 한 줄 */
  public record Summary(UUID id, String key, String name, String description, Project.Visibility visibility,
      Project.Status status, Instant updatedAt) {

    static Summary of(Project p) {
      return new Summary(p.getId(), p.getKey(), p.getName(), p.getDescription(), p.getVisibility(), p.getStatus(),
          p.getUpdatedAt());
    }
  }

  /**
   * 프로젝트 상세 (독자에게).
   *
   * @param issueCount    지우지 않은 일감 수
   * @param myRoles       이 프로젝트에서 내가 받은 프로젝트 역할 key
   * @param myPermissions 이 프로젝트에서 내가 가진 권한 코드 — 화면은 이것으로 단추를 숨기기만 한다(판정은 api)
   * @param myActions     권한 코드만으로는 알 수 없는 것 — createIssue(프로젝트가 열려 있고 issue.create 가 있다)
   */
  public record Detail(UUID id, String key, String name, String description, Project.Visibility visibility,
      Project.Status status, Instant createdAt, Instant updatedAt, Instant archivedAt, long issueCount,
      List<String> myRoles,
      List<String> myPermissions, List<String> myActions) {
  }
}
