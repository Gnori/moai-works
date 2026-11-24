package com.moai.works.api.skill;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.security.access.UserApi;

/**
 * Agent 스킬 읽기 — 로그인한 사람 누구나(시스템 관리자 포함). 스킬은 Agent 에게 주는 지침이라 비밀이 아니다.
 * 지운 스킬은 시스템 관리자에게만 보인다(다른 사람에게는 없는 것과 같은 404) — 판정은 {@link SkillService}.
 * 쓰기는 {@link SkillAdminController}(시스템 관리자만). Agent 는 MCP 의 list_skills · get_skill 로 읽는다.
 */
@RestController
@RequestMapping(EndPointPaths.V1 + "/skills")
@UserApi(systemAdmin = true)
public class SkillController {

  /** SKILL.md 의 내용 형식 — Markdown(RFC 7763), UTF-8 */
  static final MediaType MARKDOWN = new MediaType("text", "markdown", StandardCharsets.UTF_8);

  private final SkillService skills;

  public SkillController(SkillService skills) {
    this.skills = skills;
  }

  /** 스킬 목록 — 이름 순. includeDeleted 는 시스템 관리자에게만 듣는다(다른 사람에게는 쓰는 스킬만) */
  @GetMapping
  public List<SkillViews.Summary> list(CurrentUser user, @RequestParam(defaultValue = "false") boolean includeDeleted) {
    return skills.list(includeDeleted, user.systemAdmin());
  }

  /**
   * 스킬 하나. sinceRevision(사본의 리비전)을 주면 그 뒤부터 지금 리비전까지의 바뀐 점(changes — 리비전 · 때 · 누가 · 한 일 · 요약, 옛 것부터)을
   * 함께 준다 — MCP get_skill 의 sinceRevision 과 같다(일감 #183).
   */
  @GetMapping("/{name}")
  public SkillViews.Detail get(CurrentUser user, @PathVariable String name,
      @RequestParam(required = false) Integer sinceRevision) {
    return skills.get(name, user.systemAdmin(), sinceRevision);
  }

  /** 리비전들 — 새 리비전부터. before 는 앞 쪽 응답의 next */
  @GetMapping("/{name}/revisions")
  public SkillViews.RevisionList revisions(CurrentUser user, @PathVariable String name,
      @RequestParam(required = false) Integer before, @RequestParam(defaultValue = "50") int limit) {
    return skills.revisions(name, user.systemAdmin(), before, limit);
  }

  @GetMapping("/{name}/revisions/{no}")
  public SkillViews.Revision revision(CurrentUser user, @PathVariable String name, @PathVariable int no) {
    return skills.revision(name, user.systemAdmin(), no);
  }

  /** 두 리비전의 차이 — from 이 비면 바로 앞 리비전과, 0 이면 "없음"과 견준다 */
  @GetMapping("/{name}/revisions/{no}/diff")
  public SkillViews.RevisionDiff diff(CurrentUser user, @PathVariable String name, @PathVariable int no,
      @RequestParam(required = false) Integer from) {
    return skills.diff(name, user.systemAdmin(), no, from);
  }

  /**
   * SKILL.md 내려받기 — frontmatter(name · description) + 출처 표시 줄 + 본문. revision 이 비면 지금 리비전.
   * {@code text/markdown; charset=UTF-8} 이고 파일로 받게 한다(브라우저가 그리지 않는다).
   */
  @GetMapping("/{name}/SKILL.md")
  public ResponseEntity<String> skillMd(CurrentUser user, @PathVariable String name,
      @RequestParam(required = false) Integer revision) {
    SkillService.Export export = skills.export(name, user.systemAdmin(), revision);
    return ResponseEntity.ok()
        .contentType(MARKDOWN)
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(SkillFile.FILE_NAME).build().toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(export.skillMd());
  }
}
