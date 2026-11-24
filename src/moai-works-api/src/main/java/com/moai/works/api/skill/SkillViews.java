package com.moai.works.api.skill;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.moai.works.api.issue.LineDiff;

/** 스킬 응답 모양 — 화면(shared/api/types.ts 의 Skill*)과 같다 */
public final class SkillViews {

  private SkillViews() {
  }

  /** 사람 — 지금 이름(없으면 username) */
  public record Person(UUID id, String name) {
  }

  /**
   * 목록 한 줄. 지운 스킬은 시스템 관리자에게만 보인다.
   *
   * @param deleted      지움 표시가 있다(되살릴 수 있다)
   * @param latestChange 지금 리비전의 바뀐 점(요약이 없는 옛 리비전이면 summary 가 null)
   */
  public record Summary(String name, String description, int revisionNo, Instant updatedAt, Person updatedBy,
      boolean deleted, Instant deletedAt, Person deletedBy, Change latestChange) {
  }

  /**
   * 스킬 하나 — 지금 리비전의 전체. 고칠 때는 revisionNo 를 baseRevisionNo 로 보낸다(낡았으면 409 SKILL_CHANGED).
   *
   * @param changes        {@code sinceRevision} 을 주고 읽을 때만 — 그 뒤부터 지금 리비전까지의 바뀐 점(옛 것부터). 아니면 null
   * @param changesOmitted changes 에 담지 못한 더 옛 리비전 수(한 번에 {@code SkillService.CHANGES_MAX} 개까지). 아니면 null
   */
  public record Detail(String name, String description, String body, int revisionNo, Instant createdAt,
      Person createdBy, Instant updatedAt, Person updatedBy, boolean deleted, Instant deletedAt, Person deletedBy,
      List<Change> changes, Integer changesOmitted) {

    Detail withChanges(Changes c) {
      return new Detail(name, description, body, revisionNo, createdAt, createdBy, updatedAt, updatedBy, deleted,
          deletedAt, deletedBy, c.items(), c.omitted());
    }
  }

  /**
   * 리비전 하나의 바뀐 점 — Agent 가 사본을 새로 받을 때 보는 줄(일감 #183, 2026-11-24 사용자: "버전 정보만 보고 업데이트하는 건 안 맞잖아?
   * 간략한 설명도?"). summary 는 시스템 관리자가 쓴 "바뀐 점 요약"(리비전의 change_note 칸)이다 — 요약을 받기 전의 옛 리비전이면 null.
   *
   * @param by           바꾼 사람(그때의 이름)
   * @param kind         CREATE · UPDATE · IMPORT · RESTORE
   * @param restoredFrom RESTORE 면 되돌린 리비전 번호
   */
  public record Change(int revisionNo, Instant at, String by, String kind, Integer restoredFrom, String summary) {
  }

  /** 바뀐 점 여럿 — 옛 것부터. omitted 는 담지 못한 더 옛 리비전 수 */
  public record Changes(List<Change> items, int omitted) {
  }

  /**
   * 리비전 목록 한 줄 — 누가 · 어느 길로 · 언제 · 무엇을 · 왜.
   *
   * @param kind          CREATE · UPDATE · IMPORT · RESTORE
   * @param restoredFrom  RESTORE 면 되돌린 리비전 번호
   * @param changedFields 바로 앞 리비전과 견줘 바뀐 것(description · body)
   * @param current       지금 리비전인가
   */
  public record RevisionItem(int no, String kind, Integer restoredFrom, List<String> changedFields, String changeNote,
      String actorType, UUID actorId, String actorName, String via, Instant createdAt, boolean current) {
  }

  /** @param next 다음 쪽을 부를 때 before 에 넣는 리비전 번호. 마지막 쪽이면 null */
  public record RevisionList(List<RevisionItem> items, Integer next) {
  }

  /** 한 리비전의 전체 */
  public record Revision(int no, String kind, Integer restoredFrom, List<String> changedFields, String changeNote,
      String actorType, UUID actorId, String actorName, String via, Instant createdAt, boolean current, String name,
      String description, String body) {
  }

  /**
   * 두 리비전의 차이 — 설명은 전 · 후(fields), 본문은 git 처럼 줄 단위(LineDiff).
   *
   * @param from 옛 쪽 리비전 번호 (1리비전과 "없음"을 견주면 null)
   */
  public record RevisionDiff(Integer from, int to, RevisionItem fromInfo, RevisionItem toInfo,
      List<Map<String, Object>> fields, List<LineDiff.Line> bodyLines, int added, int removed, boolean coarse) {
  }

  /** 내보내는 파일 하나 — get_skill 의 files[] 와 같은 모양(지금은 SKILL.md 하나, 딸린 파일은 나중) */
  public record File(String path, String content) {
  }
}
