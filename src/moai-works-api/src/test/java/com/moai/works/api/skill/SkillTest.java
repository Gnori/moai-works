package com.moai.works.api.skill;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.McpTestClient;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * Agent 스킬(일감 #30) — 쓰기는 시스템 관리자만 · 읽기는 누구나 · 이름 규칙과 바꾸지 않기 · 읽은 리비전(baseRevisionNo) · 리비전(만들기 · 수정 · 되돌리기) ·
 * 리비전은 추가만 · 지움 표시와 되살리기 · SKILL.md 내보내기 · 가져오기 · 감사 · MCP list_skills · get_skill.
 */
@ApiTest
class SkillTest {

  private static final String ADMIN = "/admin/skills";
  private static final String SKILLS = "/skills";

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;
  @Autowired
  JdbcTemplate jdbc;

  private record People(TestUser sysadmin, TestUser member) {
  }

  private People people() throws Exception {
    return new People(api.user("sysadmin", "system-admin"), api.user("member"));
  }

  /** 시험마다 겹치지 않는 이름 */
  private static String uniqueName(String base) {
    return base + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private Map<String, Object> createBody(String name, String description, String body) {
    Map<String, Object> m = new HashMap<>();
    m.put("name", name);
    m.put("description", description);
    m.put("body", body);
    return m;
  }

  /** 만들고 그 리비전 번호(늘 1)를 돌려준다 */
  private int create(TestUser admin, String name, String description, String body) throws Exception {
    return TestApi.read(api.post(admin, ADMIN, createBody(name, description, body)).andExpect(status().isCreated()),
        "$.revisionNo");
  }

  /** 수정 요청 — baseRevisionNo 는 읽은 리비전(moai-wiki 와 같은 동시 수정 규칙) */
  private static Map<String, Object> update(String description, String body, int baseRevisionNo, String note) {
    Map<String, Object> m = new HashMap<>();
    m.put("description", description);
    m.put("body", body);
    m.put("baseRevisionNo", baseRevisionNo);
    if (note != null) {
      m.put("changeNote", note);
    }
    return m;
  }

  private String mcpKey(TestUser who) throws Exception {
    return TestApi.read(api.post(who, "/me/mcp-keys", Map.of("name", "스킬 받는 키")).andExpect(status().isCreated()),
        "$.key");
  }

  private static Map<String, Object> args(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  private String exported(TestUser as, String name) throws Exception {
    return api.get(as, SKILLS + "/{name}/SKILL.md", name).andExpect(status().isOk()).andReturn().getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
  }

  // ── 판정 ──────────────────────────────────────────────────────────

  @Test
  void 쓰기는_시스템_관리자만이고_읽기는_로그인한_사람_누구나() throws Exception {
    People p = people();
    String name = uniqueName("perm");
    // 일반 사용자의 쓰기는 모두 403
    api.post(p.member(), ADMIN, createBody(name, "설명", "# 본문")).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PLATFORM_ROLE_REQUIRED"));
    int rv = create(p.sysadmin(), name, "설명", "# 본문");
    api.put(p.member(), ADMIN + "/{name}", update("바꿈", "# 바꿈", rv, null), name).andExpect(status().isForbidden());
    api.delete(p.member(), ADMIN + "/{name}", name).andExpect(status().isForbidden());
    api.post(p.member(), ADMIN + "/{name}/undelete", null, name).andExpect(status().isForbidden());
    api.post(p.member(), ADMIN + "/{name}/revisions/1/restore", Map.of("baseRevisionNo", rv), name).andExpect(status().isForbidden());
    api.post(p.member(), ADMIN + "/import", Map.of("content", "---\nname: x\ndescription: y\n---\nz"))
        .andExpect(status().isForbidden());

    // 읽기 — 사용자 · 시스템 관리자 모두
    for (TestUser who : List.of(p.member(), p.sysadmin())) {
      List<String> names = TestApi.read(api.get(who, SKILLS).andExpect(status().isOk()), "$[*].name");
      assertThat(names).contains(name);
      api.get(who, SKILLS + "/{name}", name).andExpect(status().isOk()).andExpect(jsonPath("$.body").value("# 본문"))
          .andExpect(jsonPath("$.revisionNo").value(1)).andExpect(jsonPath("$.deleted").value(false));
      api.get(who, SKILLS + "/{name}/revisions", name).andExpect(status().isOk());
      api.get(who, SKILLS + "/{name}/revisions/1", name).andExpect(status().isOk());
      api.get(who, SKILLS + "/{name}/revisions/1/diff", name).andExpect(status().isOk());
      api.get(who, SKILLS + "/{name}/SKILL.md", name).andExpect(status().isOk());
    }
    // 토큰이 없으면 401, 없는 스킬은 404
    api.get(null, SKILLS).andExpect(status().isUnauthorized());
    api.get(p.member(), SKILLS + "/{name}", "no-such-skill").andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SKILL_NOT_FOUND"));
  }

  // ── 이름 규칙 ──────────────────────────────────────────────────────

  @Test
  void 이름은_스킬_이름_규칙을_따르고_만든_뒤_바뀌지_않는다() throws Exception {
    People p = people();
    for (String bad : List.of("Bad", "under_score", "-front", "back-", "two--hyphens", "공백 있음", "a".repeat(65), "")) {
      api.post(p.sysadmin(), ADMIN, createBody(bad, "설명", "본문")).andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("SKILL_NAME_INVALID"));
    }
    String name = uniqueName("a1-b2");
    int rv = create(p.sysadmin(), name, "설명", "본문");
    api.post(p.sysadmin(), ADMIN, createBody(name, "또", "또")).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SKILL_NAME_TAKEN"));
    // 64자는 된다
    create(p.sysadmin(), "x".repeat(56) + "-" + UUID.randomUUID().toString().substring(0, 7), "설명", "본문");
    // 설명 · 본문 규칙
    api.post(p.sysadmin(), ADMIN, createBody(uniqueName("d"), " ", "본문")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SKILL_DESCRIPTION_REQUIRED"));
    api.post(p.sysadmin(), ADMIN, createBody(uniqueName("d"), "가".repeat(1025), "본문")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SKILL_DESCRIPTION_TOO_LONG"));
    api.post(p.sysadmin(), ADMIN, createBody(uniqueName("d"), "설명", "\n \n")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SKILL_BODY_REQUIRED"));
    api.post(p.sysadmin(), ADMIN, createBody(uniqueName("d"), "설명", "가".repeat(100_001))).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SKILL_BODY_TOO_LONG"));
    // 줄바꿈 · 탭 말고 제어 문자는 받지 않는다(moai-wiki 와 같다)
    api.post(p.sysadmin(), ADMIN, createBody(uniqueName("d"), "벨\u0007소리", "본문")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SKILL_DESCRIPTION_INVALID"));
    api.post(p.sysadmin(), ADMIN, createBody(uniqueName("d"), "설명", "널\u0000문자")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SKILL_BODY_INVALID"));
    create(p.sysadmin(), uniqueName("tab"), "탭\t과\n줄바꿈", "탭\t과\n줄바꿈");

    // 수정 요청에는 이름 칸이 없다 — 넣어도 듣지 않는다
    Map<String, Object> withName = update("새 설명", "본문", rv, null);
    withName.put("name", "renamed");
    api.put(p.sysadmin(), ADMIN + "/{name}", withName, name).andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value(name));
    api.get(p.sysadmin(), SKILLS + "/{name}", "renamed").andExpect(status().isNotFound());
    // DB 도 이름 바꾸기 · 행 지우기를 막는다
    assertThatThrownBy(() -> jdbc.update("UPDATE skill SET name = 'renamed' WHERE name = ?", name))
        .hasMessageContaining("바꿀 수 없다");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM skill WHERE name = ?", name)).hasMessageContaining("지울 수 없다");
  }

  // ── 수정 · 리비전 ─────────────────────────────────────────────────

  @Test
  void 수정은_읽은_리비전으로_겹침을_막고_만들기_수정_되돌리기마다_리비전이_쌓인다() throws Exception {
    People p = people();
    String name = uniqueName("rev");
    int base = create(p.sysadmin(), name, "첫 설명", "# 제목\n\n첫 본문\n");
    assertThat(base).isEqualTo(1);
    api.put(p.sysadmin(), ADMIN + "/{name}", update("둘째 설명", "# 제목\n\n둘째 본문", 1, "설명을 다듬음"), name)
        .andExpect(status().isOk()).andExpect(jsonPath("$.revisionNo").value(2))
        .andExpect(jsonPath("$.rowVersion").doesNotExist());
    // 낡은 리비전(1)으로 고치면 409 SKILL_CHANGED — 지금 리비전을 함께 준다
    api.put(p.sysadmin(), ADMIN + "/{name}", update("늦음", "늦음", 1, null), name).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SKILL_CHANGED")).andExpect(jsonPath("$.revisionNo").value(2));
    // 내용이 그대로면 리비전을 만들지 않는다(200, 지금 리비전)
    api.put(p.sysadmin(), ADMIN + "/{name}", update("둘째 설명", "# 제목\n\n둘째 본문\n", 2, null), name)
        .andExpect(status().isOk()).andExpect(jsonPath("$.revisionNo").value(2));

    // 1리비전으로 되돌리기(restore) → 그 내용으로 3리비전
    api.post(p.sysadmin(), ADMIN + "/{name}/revisions/1/restore", Map.of("baseRevisionNo", 2, "changeNote", "처음으로"), name)
        .andExpect(status().isOk()).andExpect(jsonPath("$.revisionNo").value(3))
        .andExpect(jsonPath("$.description").value("첫 설명")).andExpect(jsonPath("$.body").value("# 제목\n\n첫 본문"));
    // 낡은 리비전으로 되돌리면 409, 지금 내용과 같은 리비전으로 되돌리면 리비전 없이 200
    api.post(p.sysadmin(), ADMIN + "/{name}/revisions/2/restore", Map.of("baseRevisionNo", 2), name)
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SKILL_CHANGED"));
    api.post(p.sysadmin(), ADMIN + "/{name}/revisions/1/restore", Map.of("baseRevisionNo", 3), name)
        .andExpect(status().isOk()).andExpect(jsonPath("$.revisionNo").value(3));
    api.post(p.sysadmin(), ADMIN + "/{name}/revisions/9/restore", Map.of("baseRevisionNo", 3), name)
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REVISION_NOT_FOUND"));

    // 타임라인 — 새 리비전부터, 누가 · 길 · 무엇을 · 왜
    api.get(p.member(), SKILLS + "/{name}/revisions", name).andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.items[0].no").value(3)).andExpect(jsonPath("$.items[0].kind").value("RESTORE"))
        .andExpect(jsonPath("$.items[0].restoredFrom").value(1)).andExpect(jsonPath("$.items[0].current").value(true))
        .andExpect(jsonPath("$.items[0].changeNote").value("처음으로"))
        .andExpect(jsonPath("$.items[0].actorId").value(p.sysadmin().id().toString()))
        .andExpect(jsonPath("$.items[0].via").value("WEB"))
        .andExpect(jsonPath("$.items[1].kind").value("UPDATE")).andExpect(jsonPath("$.items[1].changeNote").value("설명을 다듬음"))
        .andExpect(jsonPath("$.items[1].changedFields[0]").value("description"))
        .andExpect(jsonPath("$.items[1].changedFields[1]").value("body"))
        .andExpect(jsonPath("$.items[2].kind").value("CREATE")).andExpect(jsonPath("$.items[2].current").value(false))
        .andExpect(jsonPath("$.next").doesNotExist());
    // 쪽 나누기
    api.get(p.member(), SKILLS + "/{name}/revisions?limit=2", name).andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.next").value(2));
    api.get(p.member(), SKILLS + "/{name}/revisions?before=2", name).andExpect(jsonPath("$.items[0].no").value(1));

    // 한 리비전 전체 · 차이(설명은 전 · 후, 본문은 줄)
    api.get(p.member(), SKILLS + "/{name}/revisions/2", name).andExpect(jsonPath("$.body").value("# 제목\n\n둘째 본문"))
        .andExpect(jsonPath("$.description").value("둘째 설명")).andExpect(jsonPath("$.current").value(false));
    var diff = api.get(p.member(), SKILLS + "/{name}/revisions/3/diff", name).andExpect(status().isOk())
        .andExpect(jsonPath("$.from").value(2)).andExpect(jsonPath("$.to").value(3))
        .andExpect(jsonPath("$.fields[0].field").value("description"))
        .andExpect(jsonPath("$.fields[0].before").value("둘째 설명")).andExpect(jsonPath("$.fields[0].after").value("첫 설명"))
        .andExpect(jsonPath("$.added").value(1)).andExpect(jsonPath("$.removed").value(1));
    assertThat((List<String>) TestApi.read(diff, "$.bodyLines[*].op")).containsExactly("SAME", "SAME", "DEL", "ADD");
    // 고른 리비전과 · 없음과
    api.get(p.member(), SKILLS + "/{name}/revisions/3/diff?from=1", name).andExpect(jsonPath("$.fields.length()").value(0))
        .andExpect(jsonPath("$.added").value(0));
    api.get(p.member(), SKILLS + "/{name}/revisions/1/diff", name).andExpect(jsonPath("$.from").doesNotExist())
        .andExpect(jsonPath("$.fromInfo").doesNotExist()).andExpect(jsonPath("$.fields[0].field").value("name"));
    api.get(p.member(), SKILLS + "/{name}/revisions/2/diff?from=2", name).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SAME_REVISION"));
  }

  @Test
  void 리비전은_추가만_된다() throws Exception {
    People p = people();
    String name = uniqueName("append");
    create(p.sysadmin(), name, "설명", "본문");
    assertThatThrownBy(() -> jdbc.update("UPDATE skill_revision SET body = '바꿔치기' WHERE name = ?", name))
        .hasMessageContaining("추가만");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM skill_revision WHERE name = ?", name)).hasMessageContaining("추가만");
    assertThatThrownBy(() -> jdbc.execute("TRUNCATE skill_revision")).hasMessageContaining("추가만");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM skill_revision WHERE name = ?", Long.class, name)).isEqualTo(1);
  }

  // ── 삭제 · 되살리기 ───────────────────────────────────────────────

  @Test
  void 삭제하면_사용자와_MCP_에서_숨고_되살리면_돌아오며_리비전은_남는다() throws Exception {
    People p = people();
    String name = uniqueName("soft");
    create(p.sysadmin(), name, "설명", "본문");
    api.put(p.sysadmin(), ADMIN + "/{name}", update("설명 2", "본문 2", 1, null), name).andExpect(status().isOk());
    McpTestClient mcp = new McpTestClient(mvc, mcpKey(p.member()));

    api.delete(p.sysadmin(), ADMIN + "/{name}", name).andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(true))
        .andExpect(jsonPath("$.deletedBy.id").value(p.sysadmin().id().toString()));
    api.delete(p.sysadmin(), ADMIN + "/{name}", name).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SKILL_ALREADY_DELETED"));

    // 사용자 — 목록에 없고, 직접 열어도 없는 것과 같은 404(includeDeleted 는 듣지 않는다)
    List<String> forMember = TestApi.read(api.get(p.member(), SKILLS + "?includeDeleted=true"), "$[*].name");
    assertThat(forMember).doesNotContain(name);
    api.get(p.member(), SKILLS + "/{name}", name).andExpect(status().isNotFound());
    api.get(p.member(), SKILLS + "/{name}/revisions", name).andExpect(status().isNotFound());
    api.get(p.member(), SKILLS + "/{name}/SKILL.md", name).andExpect(status().isNotFound());
    // MCP — 목록에 없고 받을 수 없다
    List<String> listed = read(mcp.call("list_skills", Map.of()), "$.skills[*].name");
    assertThat(listed).doesNotContain(name);
    assertThat((String) read(mcp.call("get_skill", args("name", name)), "$.error.code")).isEqualTo("SKILL_NOT_FOUND");
    // 시스템 관리자 — 지운 것까지 달라고 하면 보이고, 열 수 있다
    List<String> forAdmin = TestApi.read(api.get(p.sysadmin(), SKILLS), "$[*].name");
    assertThat(forAdmin).doesNotContain(name);
    var all = api.get(p.sysadmin(), SKILLS + "?includeDeleted=true");
    assertThat((List<String>) TestApi.read(all, "$[?(@.deleted == true)].name")).contains(name);
    api.get(p.sysadmin(), SKILLS + "/{name}/revisions", name).andExpect(jsonPath("$.items.length()").value(2));
    // 지운 스킬은 고칠 수 없고, 같은 이름으로 새로 만들지 않는다(되살린다)
    api.put(p.sysadmin(), ADMIN + "/{name}", update("고침", "고침", 2, null), name).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SKILL_DELETED"));
    api.post(p.sysadmin(), ADMIN, createBody(name, "새것", "새것")).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SKILL_NAME_DELETED"));

    // 되살리기(undelete) — 내용 · 리비전 그대로
    api.post(p.sysadmin(), ADMIN + "/{name}/undelete", null, name).andExpect(status().isOk())
        .andExpect(jsonPath("$.deleted").value(false)).andExpect(jsonPath("$.revisionNo").value(2))
        .andExpect(jsonPath("$.body").value("본문 2"));
    api.post(p.sysadmin(), ADMIN + "/{name}/undelete", null, name).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SKILL_NOT_DELETED"));
    api.get(p.member(), SKILLS + "/{name}", name).andExpect(status().isOk());
    assertThat((List<String>) read(mcp.call("list_skills", Map.of()), "$.skills[*].name")).contains(name);
    assertThat((Integer) read(mcp.call("get_skill", args("name", name)), "$.revisionNo")).isEqualTo(2);
  }

  // ── SKILL.md 내보내기 · 가져오기 ──────────────────────────────────────

  @Test
  void SKILL_md_로_내보내고_가져오면_같은_스킬이_된다() throws Exception {
    People p = people();
    String name = uniqueName("roundtrip");
    // YAML 에서 조심할 글(앞의 #, ": ", 따옴표, 줄바꿈)도 그대로 오간다
    String description = "# 일감 다루기: \"등록\" → '진행' · 50% 끝나면 쓴다\n둘째 줄";
    String body = "# 제목\n\n- 하나\n- 둘\n\n```bash\necho \"--- 안\"\n```";
    create(p.sysadmin(), name, description, body);

    var response = api.get(p.member(), SKILLS + "/{name}/SKILL.md", name).andExpect(status().isOk())
        .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/markdown")))
        .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("SKILL.md")));
    String file = response.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertThat(file).startsWith("---\nname: " + name + "\ndescription: \"# 일감 다루기: \\\"등록\\\"");
    assertThat(file).contains("\n---\n<!-- moai-works-skill name=" + name + " revision=1 sha256=");
    assertThat(file).endsWith("```\n");
    // 지문은 출처 표시 줄을 뺀 파일의 sha256 — 내려받는 도구가 같은 방법으로 잰다
    String marker = file.lines().filter(l -> l.startsWith(SkillFile.MARKER_PREFIX)).findFirst().orElseThrow();
    String sha = marker.replaceAll(".* sha256=([0-9a-f]{64}) .*", "$1");
    assertThat(SkillFile.sha256(file.replace(marker + "\n", ""))).isEqualTo(sha);

    // 같은 이름이 있으면 읽은 리비전(baseRevisionNo) 없이는 409 SKILL_EXISTS(지금 리비전을 함께)
    api.post(p.sysadmin(), ADMIN + "/import", Map.of("content", file)).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SKILL_EXISTS")).andExpect(jsonPath("$.revisionNo").value(1));
    // 같은 내용을 가져오면 리비전이 늘지 않는다(출처 표시 줄은 내용이 아니다)
    api.post(p.sysadmin(), ADMIN + "/import", Map.of("content", file, "baseRevisionNo", 1)).andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(false)).andExpect(jsonPath("$.skill.revisionNo").value(1))
        .andExpect(jsonPath("$.skill.description").value(description)).andExpect(jsonPath("$.skill.body").value(body));
    // 고친 파일(CRLF 도)을 가져오면 IMPORT 리비전
    String edited = file.replace("- 둘", "- 둘\n- 셋").replace("\n", "\r\n");
    api.post(p.sysadmin(), ADMIN + "/import", Map.of("content", edited, "baseRevisionNo", 1, "changeNote", "파일로 고침"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.skill.revisionNo").value(2))
        .andExpect(jsonPath("$.skill.body").value(body.replace("- 둘", "- 둘\n- 셋")));
    api.get(p.member(), SKILLS + "/{name}/revisions", name).andExpect(jsonPath("$.items[0].kind").value("IMPORT"))
        .andExpect(jsonPath("$.items[0].changedFields.length()").value(1));
    // 옛 리비전도 내려받는다
    assertThat(api.get(p.member(), SKILLS + "/{name}/SKILL.md?revision=1", name).andReturn().getResponse()
        .getContentAsString(StandardCharsets.UTF_8)).isEqualTo(file);

    // 새 이름의 파일 → 새로 만듦(201), 블록 · 작은따옴표 YAML 도 읽는다
    String other = uniqueName("imported");
    String fresh = "﻿---\nname: '" + other + "'\ndescription: >-\n  여러 줄로\n  접은 설명\n---\n\n\n# 가져온 본문\n";
    api.post(p.sysadmin(), ADMIN + "/import", Map.of("content", fresh)).andExpect(status().isCreated())
        .andExpect(jsonPath("$.created").value(true)).andExpect(jsonPath("$.skill.name").value(other))
        .andExpect(jsonPath("$.skill.description").value("여러 줄로 접은 설명"))
        .andExpect(jsonPath("$.skill.body").value("# 가져온 본문"));
    // 설명은 늘 큰따옴표 한 줄(moai-wiki 와 같다)
    assertThat(exported(p.member(), other)).contains("\ndescription: \"여러 줄로 접은 설명\"\n");

    // 읽을 수 없는 파일
    for (String bad : List.of("# frontmatter 없음", "---\nname: x\n", "---\nname: [x\n---\n본문",
        "---\nname: " + uniqueName("x") + "\ndescription: d\nallowed-tools: Bash\n---\n본문",
        "---\ndescription: 이름 없음\n---\n본문")) {
      api.post(p.sysadmin(), ADMIN + "/import", Map.of("content", bad)).andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("SKILL_FILE_INVALID"));
    }
    api.post(p.sysadmin(), ADMIN + "/import", Map.of("content", "---\nname: Bad_Name\ndescription: d\n---\n본문"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SKILL_NAME_INVALID"));
    // 값은 모두 글로 읽는다(yes · 숫자를 참 · 거짓 · 수로 바꾸지 않는다) — 내보낼 때는 따옴표로
    String yes = uniqueName("yaml");
    api.post(p.sysadmin(), ADMIN + "/import", Map.of("content", "---\nname: " + yes + "\ndescription: yes\n---\n본문"))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.skill.description").value("yes"));
    assertThat(exported(p.member(), yes)).contains("\ndescription: \"yes\"\n");
  }

  // ── 감사 ──────────────────────────────────────────────────────────

  @Test
  void 쓰기마다_시스템_감사가_남는다() throws Exception {
    People p = people();
    String name = uniqueName("audit");
    create(p.sysadmin(), name, "설명", "본문");
    api.put(p.sysadmin(), ADMIN + "/{name}", update("설명 2", "본문", 1, "까닭"), name).andExpect(status().isOk());
    api.post(p.sysadmin(), ADMIN + "/{name}/revisions/1/restore", Map.of("baseRevisionNo", 2), name).andExpect(status().isOk());
    api.delete(p.sysadmin(), ADMIN + "/{name}", name).andExpect(status().isOk());
    api.post(p.sysadmin(), ADMIN + "/{name}/undelete", null, name).andExpect(status().isOk());
    String id = jdbc.queryForObject("SELECT id::text FROM skill WHERE name = ?", String.class, name);

    List<Map<String, Object>> rows = jdbc.queryForList("SELECT action, actor_id::text AS actor, workspace_id, target_type,"
        + " details::text AS details FROM audit_log WHERE target_id = ? ORDER BY occurred_at, id", id);
    // moai-wiki 와 같은 이름 — restore 는 옛 리비전으로 되돌리기, undelete 는 되살리기
    assertThat(rows).extracting(r -> r.get("action")).containsExactly("admin.skill.create", "admin.skill.update",
        "admin.skill.restore", "admin.skill.delete", "admin.skill.undelete");
    assertThat(rows).allSatisfy(r -> {
      assertThat(r.get("actor")).isEqualTo(p.sysadmin().id().toString());
      assertThat(r.get("workspace_id")).isNull();
      assertThat(r.get("target_type")).isEqualTo("skill");
      assertThat((String) read((String) r.get("details"), "$.name")).isEqualTo(name);
    });
    String updated = (String) rows.get(1).get("details");
    assertThat((String) read(updated, "$.note")).isEqualTo("까닭");
    assertThat((List<String>) read(updated, "$.fields")).containsExactly("description");
    assertThat((Integer) read((String) rows.get(2).get("details"), "$.restoredFrom")).isEqualTo(1);
    // 본문은 감사에 남기지 않는다
    assertThat(rows).allSatisfy(r -> assertThat((String) r.get("details")).doesNotContain("본문"));
  }

  // ── MCP ───────────────────────────────────────────────────────────

  @Test
  void MCP_키로_스킬_목록을_보고_SKILL_md_를_받으며_접근_기록에_스킬_이름이_남는다() throws Exception {
    People p = people();
    String name = uniqueName("mcp");
    create(p.sysadmin(), name, "MCP 로 받는 스킬", "# 첫 본문");
    api.put(p.sysadmin(), ADMIN + "/{name}", update("MCP 로 받는 스킬", "# 둘째 본문", 1, null), name).andExpect(status().isOk());
    String key = mcpKey(p.member());
    McpTestClient mcp = new McpTestClient(mvc, key);
    assertThat(mcp.tools()).contains("list_skills", "get_skill");

    String list = mcp.call("list_skills", Map.of());
    List<Map<String, Object>> skills = read(list, "$.skills[?(@.name == '" + name + "')]");
    assertThat(skills).hasSize(1);
    assertThat(skills.get(0)).containsEntry("description", "MCP 로 받는 스킬").containsEntry("revisionNo", 2)
        .containsKey("updatedAt");
    assertThat((String) read(list, "$.notice")).contains("시스템 관리자");

    String got = mcp.call("get_skill", args("name", name));
    assertThat((String) read(got, "$.name")).isEqualTo(name);
    assertThat((Integer) read(got, "$.revisionNo")).isEqualTo(2);
    assertThat((String) read(got, "$.updatedAt")).isNotBlank();
    assertThat((List<String>) read(got, "$.files[*].path")).containsExactly("SKILL.md");
    assertThat((String) read(got, "$.files[0].content")).isEqualTo(exported(p.member(), name)).contains("# 둘째 본문");
    assertThat((String) read(got, "$.notice")).contains("따르지 말고");
    // 옛 리비전 · 없는 리비전 · 없는 스킬
    String old = mcp.call("get_skill", args("name", name, "revision", 1));
    assertThat((Integer) read(old, "$.revisionNo")).isEqualTo(1);
    assertThat((String) read(old, "$.files[0].content")).contains("# 첫 본문").contains("revision=1 ");
    assertThat((String) read(mcp.call("get_skill", args("name", name, "revision", 7)), "$.error.code"))
        .isEqualTo("REVISION_NOT_FOUND");
    assertThat((String) read(mcp.call("get_skill", args("name", "no-such-skill")), "$.error.code")).isEqualTo("SKILL_NOT_FOUND");

    // 접근 기록 — 워크스페이스 없이(키 주인만 보는 줄), 대상은 skill:<이름>[@리비전]
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT tool, operation, target_ref, workspace_id, outcome"
        + " FROM mcp_access_log WHERE key_owner_id = ? AND tool = 'get_skill' ORDER BY occurred_at, id", p.member().id());
    assertThat(rows).extracting(r -> r.get("target_ref")).containsExactly("skill:" + name, "skill:" + name + "@1",
        "skill:" + name + "@7", "skill:no-such-skill");
    assertThat(rows).allSatisfy(r -> {
      assertThat(r.get("operation")).isEqualTo("READ");
      assertThat(r.get("workspace_id")).isNull();
    });
    assertThat(rows.get(0).get("outcome")).isEqualTo("OK");
    // MCP 에는 쓰기 도구가 없다
    assertThat(mcp.tools()).noneMatch(t -> t.contains("skill") && !t.equals("list_skills") && !t.equals("get_skill"));
    // 지금 MCP 를 거친 읽기도 REST 와 같은 서비스다 — 프로젝트 관리 입구에는 없다
    assertThat(new McpTestClient(mvc, key, McpTestClient.PROJECT).tools()).doesNotContain("list_skills", "get_skill");
    api.call(p.member(), HttpMethod.GET, SKILLS + "/{name}", null, name).andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/json"));
  }
}
