package com.moai.works.api.skill;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.api.account.PlatformRole;
import com.moai.works.api.common.Actor;
import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.security.access.PlatformApi;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Agent 스킬 쓰기 — 시스템 관리자만(렐름 역할 system-admin). 스킬은 바깥 Agent 가 지시로 읽는 글이라(프롬프트 주입 통로) 쓰는 사람을 좁히고,
 * 바꿀 때마다 리비전(전체 · 누가 · 언제 · 바꾼 까닭)과 감사({@code admin.skill.*})를 남긴다. 읽기는 {@link SkillController}.
 * 길이 · 형식 검사는 {@link SkillFile} 한 곳(여기 {@code @Size} 는 너무 큰 요청을 일찍 막는 것뿐이다).
 */
@RestController
@RequestMapping(EndPointPaths.V1 + "/admin/skills")
@PlatformApi(role = PlatformRole.SYSTEM_ADMIN)
public class SkillAdminController {

  private final SkillService skills;

  public SkillAdminController(SkillService skills) {
    this.skills = skills;
  }

  public record CreateRequest(@Size(max = 200) String name, @Size(max = 4_000) String description,
      @Size(max = 200_000) String body, @Size(max = 2_000) String changeNote) {
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SkillViews.Detail create(CurrentUser user, @Valid @RequestBody CreateRequest r) {
    return skills.create(r.name(), r.description(), r.body(), r.changeNote(), actor(user), SkillRevision.Via.WEB);
  }

  /**
   * 수정 — 이름은 바꾸지 않는다(주소의 것). baseRevisionNo 는 읽은 리비전(다르면 409 SKILL_CHANGED — moai-wiki 와 같다).
   * 설명 · 본문이 그대로면 리비전 없이 200.
   */
  public record UpdateRequest(@Size(max = 4_000) String description, @Size(max = 200_000) String body,
      @NotNull Integer baseRevisionNo, @Size(max = 2_000) String changeNote) {
  }

  @PutMapping("/{name}")
  public SkillViews.Detail update(CurrentUser user, @PathVariable String name, @Valid @RequestBody UpdateRequest r) {
    return skills.update(name, r.description(), r.body(), r.baseRevisionNo(), r.changeNote(), actor(user),
        SkillRevision.Via.WEB);
  }

  /** 삭제 — 지움 표시만(목록 · MCP 에서 숨고, 리비전은 남고, 되살릴 수 있다) */
  @DeleteMapping("/{name}")
  public SkillViews.Detail delete(CurrentUser user, @PathVariable String name) {
    return skills.delete(name, actor(user));
  }

  /** 되살리기(undelete) — 지움 표시를 걷는다(내용 · 리비전 그대로) */
  @PostMapping("/{name}/undelete")
  public SkillViews.Detail undelete(CurrentUser user, @PathVariable String name) {
    return skills.undelete(name, actor(user));
  }

  public record RestoreRequest(@NotNull Integer baseRevisionNo, @Size(max = 2_000) String changeNote) {
  }

  /** 이 리비전으로 되돌리기(restore) — 그 내용으로 새 리비전을 만든다. 지금 내용과 같으면 리비전 없이 200 */
  @PostMapping("/{name}/revisions/{no}/restore")
  public SkillViews.Detail restoreRevision(CurrentUser user, @PathVariable String name, @PathVariable int no,
      @Valid @RequestBody RestoreRequest r) {
    return skills.restoreRevision(name, no, r.baseRevisionNo(), r.changeNote(), actor(user), SkillRevision.Via.WEB);
  }

  /**
   * SKILL.md 가져오기(올린 파일 · 붙여 넣은 글) — frontmatter 의 name 으로 새로 만들거나(201) 있던 스킬을 고친다(200, baseRevisionNo 필요).
   * 있던 스킬인데 baseRevisionNo 가 없으면 409 SKILL_EXISTS(그 스킬의 revisionNo 를 함께 준다).
   */
  public record ImportRequest(@NotNull @Size(max = SkillFile.FILE_MAX) String content, Integer baseRevisionNo,
      @Size(max = 2_000) String changeNote) {
  }

  @PostMapping("/import")
  public ResponseEntity<SkillService.ImportResult> importFile(CurrentUser user, @Valid @RequestBody ImportRequest r) {
    SkillService.ImportResult result = skills.importFile(r.content(), r.baseRevisionNo(), r.changeNote(), actor(user),
        SkillRevision.Via.WEB);
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result);
  }

  /** 리비전 · 감사에 남는 이름 — 표시 이름이 없으면 username */
  private static Actor actor(CurrentUser user) {
    String name = user.displayName() == null || user.displayName().isBlank() ? user.username() : user.displayName();
    return Actor.user(user.id(), name, user.ip());
  }
}
