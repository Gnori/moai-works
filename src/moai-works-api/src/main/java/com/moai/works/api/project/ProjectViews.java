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

  /**
   * 프로젝트 모듈 하나 (2026-11-23, 일감 #27).
   *
   * @param position   목록 순서(0 부터)
   * @param active     새 일감에서 고를 수 있는가 — false 는 쓰지 않는 모듈(이미 붙은 일감에는 남는다)
   * @param issueCount 이 모듈이 붙은 일감 수(지운 일감 빼고)
   * @param rowVersion 고칠 때 보낼 수 있는 판(다르면 409 CONCURRENT_UPDATE)
   */
  public record Module(String key, String name, String description, int position, boolean active, long issueCount,
      Instant createdAt, Instant updatedAt, int rowVersion) {
  }
}
