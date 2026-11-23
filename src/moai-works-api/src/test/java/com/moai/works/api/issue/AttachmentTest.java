package com.moai.works.api.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.moai.works.api.storage.ObjectStore;
import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.MemoryObjectStore;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/** 일감 첨부파일 (2026-11-22) — 저장소 구조 · 이미지 판별 · 받기 헤더 · 권한 · 크기 상한 · 이력 · 감사 · 지움 표시 */
@ApiTest
class AttachmentTest {

  private static final String P = "files";
  private static final String BASE = "/workspaces/{ws}/issues/{n}/attachments";
  /** 가장 작은 PNG 머리 — 내용으로 이미지를 알아내는지 본다 */
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

  @Autowired
  TestApi api;
  @Autowired
  MemoryObjectStore store;
  @Autowired
  JdbcTemplate jdbc;

  private record Space(String ws, TestUser owner, TestUser curator, TestUser member, int issue) {
  }

  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "첨부 시험");
    TestUser curator = api.user("curator");
    TestUser member = api.user("member");
    api.join(ws, owner, curator, "curator");
    api.join(ws, owner, member, "member");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "파일", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    int n = TestApi.read(api.post(curator, "/workspaces/{ws}/projects/{p}/issues", Map.of("type", "feature", "title", "첨부"),
        ws, P).andExpect(status().isCreated()), "$.number");
    return new Space(ws, owner, curator, member, n);
  }

  @Test
  void 이미지는_내용으로_알아내고_본문에_넣을_표시를_주며_그_자리에서_보인다() throws Exception {
    Space s = space();
    var up = api.upload(s.curator(), BASE, "../../화면 캡처.png", "application/octet-stream", PNG, Map.of(), s.ws(), s.issue())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.image").value(true))
        .andExpect(jsonPath("$.contentType").value("image/png"))
        .andExpect(jsonPath("$.fileName").value("화면 캡처.png"))
        .andExpect(jsonPath("$.size").value(PNG.length))
        .andExpect(jsonPath("$.uploadedBy.id").value(s.curator().id().toString()));
    String id = TestApi.read(up, "$.id");
    assertThat((String) TestApi.read(up, "$.markdown")).isEqualTo("![화면 캡처.png](attachment:" + id + ")");

    api.get(s.member(), "/workspaces/{ws}/issues/{n}/attachments/{id}/content", s.ws(), s.issue(), id).andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "image/png"))
        .andExpect(header().string("Content-Disposition", Matchers.startsWith("inline")))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(content().bytes(PNG));

    // 저장소 객체 이름 = ws/<워크스페이스>/p/<프로젝트>/<연>/<월>/<첨부 id> — 원래 이름은 들어가지 않는다
    String wsId = jdbc.queryForObject("SELECT id::text FROM workspace WHERE slug = ?", String.class, s.ws());
    List<String> keys = store.keys(ObjectStore.Zone.ATTACHMENT, "ws/" + wsId + "/p/");
    assertThat(keys).hasSize(1);
    assertThat(keys.get(0)).matches("ws/" + wsId + "/p/[0-9a-f-]{36}/\\d{4}/\\d{2}/" + id).doesNotContain("캡처");
  }

  @Test
  void 이미지가_아닌_파일은_늘_내려받기로만_준다() throws Exception {
    Space s = space();
    String id = TestApi.read(api.upload(s.member(), BASE, "evil.html", "text/html",
        "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8), Map.of(), s.ws(), s.issue())
        .andExpect(status().isCreated()).andExpect(jsonPath("$.image").value(false))
        .andExpect(jsonPath("$.markdown").value(Matchers.startsWith("[evil.html]"))), "$.id");
    api.get(s.member(), "/workspaces/{ws}/issues/{n}/attachments/{id}/content", s.ws(), s.issue(), id).andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/octet-stream"))
        .andExpect(header().string("Content-Disposition", Matchers.startsWith("attachment")))
        .andExpect(header().string("Content-Security-Policy", Matchers.containsString("sandbox")));
  }

  @Test
  void 권한_지움_표시_이력_감사() throws Exception {
    Space s = space();
    String id = TestApi.read(api.upload(s.curator(), BASE, "spec.pdf", "application/pdf", "pdf".getBytes(StandardCharsets.UTF_8),
        Map.of(), s.ws(), s.issue()).andExpect(status().isCreated()), "$.id");
    api.get(s.member(), BASE, s.ws(), s.issue()).andExpect(jsonPath("$.length()").value(1));
    // 남이 올린 것은 project.manage 없이 못 지운다
    api.delete(s.member(), "/workspaces/{ws}/issues/{n}/attachments/{id}", s.ws(), s.issue(), id).andExpect(status().isForbidden());
    api.delete(s.curator(), "/workspaces/{ws}/issues/{n}/attachments/{id}", s.ws(), s.issue(), id).andExpect(status().isNoContent());
    api.get(s.member(), BASE, s.ws(), s.issue()).andExpect(jsonPath("$.length()").value(0));
    api.get(s.member(), "/workspaces/{ws}/issues/{n}/attachments/{id}/content", s.ws(), s.issue(), id).andExpect(status().isNotFound());
    // 이력에 더하고 뺀 것이 남는다(판은 아니다)
    api.get(s.owner(), "/workspaces/{ws}/issues/{n}", s.ws(), s.issue())
        .andExpect(jsonPath("$.journal[?(@.changes[0].field == 'attachment')]", Matchers.hasSize(2)))
        .andExpect(jsonPath("$.revisionNo").value(1));
    List<String> actions = TestApi.read(api.get(s.owner(), "/workspaces/{ws}/audit?action=issue.attachment", s.ws()), "$[*].action");
    assertThat(actions).containsExactly("issue.attachment.remove", "issue.attachment.add");
    // DB 도 지우거나 바꾸지 못하게 막는다
    assertThatThrownBy(() -> jdbc.update("DELETE FROM issue_attachment WHERE id = ?::uuid", id)).hasMessageContaining("지울 수 없다");
    assertThatThrownBy(() -> jdbc.update("UPDATE issue_attachment SET file_name = 'x' WHERE id = ?::uuid", id))
        .hasMessageContaining("지움 표시만");
  }

  @Test
  void 독자가_아니면_첨부도_없고_크기_상한을_넘으면_413() throws Exception {
    Space s = space();
    api.post(s.owner(), "/workspaces/{ws}/projects", Map.of("key", "closed", "name", "지정", "visibility", "RESTRICTED"), s.ws())
        .andExpect(status().isCreated());
    int hidden = TestApi.read(api.post(s.owner(), "/workspaces/{ws}/projects/{p}/issues", Map.of("type", "bug", "title", "비공개"),
        s.ws(), "closed").andExpect(status().isCreated()), "$.number");
    String id = TestApi.read(api.upload(s.owner(), BASE, "a.txt", "text/plain", "a".getBytes(StandardCharsets.UTF_8), Map.of(),
        s.ws(), hidden).andExpect(status().isCreated()), "$.id");
    api.get(s.member(), "/workspaces/{ws}/issues/{n}/attachments/{id}/content", s.ws(), hidden, id).andExpect(status().isNotFound());
    api.upload(s.member(), BASE, "b.txt", "text/plain", "b".getBytes(StandardCharsets.UTF_8), Map.of(), s.ws(), hidden)
        .andExpect(status().isNotFound());
    // 시험 설정은 1MB
    api.upload(s.curator(), BASE, "big.bin", "application/octet-stream", new byte[1024 * 1024 + 1], Map.of(), s.ws(), s.issue())
        .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    api.upload(s.curator(), BASE, "empty.txt", "text/plain", new byte[0], Map.of(), s.ws(), s.issue())
        .andExpect(status().isBadRequest());
  }
}
