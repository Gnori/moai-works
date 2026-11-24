package com.moai.works.api.project;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 프로젝트 API 응답 모양. 엔티티를 그대로 내보내지 않는다. */
public final class ProjectViews {

  private ProjectViews() {
  }

  /**
   * 프로젝트 목록 한 줄.
   *
   * @param myActions 이 프로젝트에서 내가 할 수 있는 일 — createIssue(열려 있고 issue.create). 일감 옮기기의 프로젝트 고르기가 쓴다(판정은 api)
   */
  public record Summary(UUID id, String key, String name, String description, Project.Visibility visibility,
      Project.Status status, Instant updatedAt, List<String> myActions) {

    static Summary of(Project p, List<String> myActions) {
      return new Summary(p.getId(), p.getKey(), p.getName(), p.getDescription(), p.getVisibility(), p.getStatus(),
          p.getUpdatedAt(), myActions);
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

  /** 사람 — 아이디 · 표시 이름(일감 화면의 사람과 같은 모양) */
  public record Person(UUID id, String username, String displayName) {
  }

  /**
   * 프로젝트의 에이전트 멤버 하나 (2026-11-25, 일감 #285) — 이름표일 뿐 권한이 없다(주인의 MCP 키 · 주인의 권한으로 일한다, ADR-024).
   *
   * @param name           세션 이름(일감의 agentSession 과 대소문자 없이 같은 것)
   * @param owner          주인 — 그 에이전트를 돌리는 MCP 키의 주인. 옛 데이터에서 알 수 없으면 null
   * @param active         false = 쓰지 않음(목록에 남는다. 일감에 그 이름을 넣는 것은 막지 않고 MCP 결과가 AGENT_INACTIVE 로 알린다)
   * @param source         AUTO(일감에 이름이 처음 들어올 때 서버가 등록) · MANUAL(프로젝트 관리자가 더함)
   * @param openIssues     이 이름의 열린 일감 수(지운 일감 빼고, 완료 · 종료 · 취소가 아닌 것)
   * @param totalIssues    이 이름의 일감 수(지운 일감 빼고)
   * @param lastActivityAt 이 이름의 일감을 마지막으로 고친 때 — 일감이 없으면 null
   * @param rowVersion     고칠 때 보낼 수 있는 판(다르면 409 CONCURRENT_UPDATE)
   */
  public record Agent(UUID id, String name, Person owner, String description, boolean active, ProjectAgent.Source source,
      Instant createdAt, long openIssues, long totalIssues, Instant lastActivityAt, int rowVersion) {
  }
}
