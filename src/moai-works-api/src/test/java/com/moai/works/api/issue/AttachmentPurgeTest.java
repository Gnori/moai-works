package com.moai.works.api.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.jayway.jsonpath.JsonPath;
import com.moai.works.api.storage.ObjectStore;
import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.MemoryObjectStore;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * 지운 첨부의 객체 정리 (일감 #18) — 보관 기간이 지난 것만 저장소에서 지우고, 행은 남겨 정리 표시만 한다.
 * 기간 안의 것 · 지우지 않은 것은 그대로, 워크스페이스 감사 한 줄, 용량은 그대로, 보호 트리거는 정리 표시 한 번만 허락한다.
 */
@ApiTest
class AttachmentPurgeTest {

  private static final String P = "purge";
  private static final String BASE = "/workspaces/{ws}/projects/{p}/issues/{n}/attachments";

  @Autowired
  TestApi api;
  @Autowired
  MemoryObjectStore store;
  @Autowired
  JdbcTemplate jdbc;
  @Autowired
  AttachmentPurger purger;
  @Autowired
  IssueRepositories.Attachments attachments;

  private record Space(String ws, UUID wsId, TestUser owner, int issue) {
  }

  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "정리 시험");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "정리", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    int n = TestApi.read(api.post(owner, "/workspaces/{ws}/projects/{p}/issues", Map.of("type", "feature", "title", "첨부"),
        ws, P).andExpect(status().isCreated()), "$.number");
    UUID wsId = UUID.fromString(jdbc.queryForObject("SELECT id::text FROM workspace WHERE slug = ?", String.class, ws));
    return new Space(ws, wsId, owner, n);
  }

  private String upload(Space s, String name, String body) throws Exception {
    return TestApi.read(api.upload(s.owner(), BASE, name, "text/plain", body.getBytes(StandardCharsets.UTF_8), Map.of(),
        s.ws(), P, s.issue()).andExpect(status().isCreated()), "$.id");
  }

  private String key(String id) {
    return jdbc.queryForObject("SELECT storage_key FROM issue_attachment WHERE id = ?::uuid", String.class, id);
  }

  private Instant deletedAt(String id) {
    return jdbc.queryForObject("SELECT deleted_at FROM issue_attachment WHERE id = ?::uuid", java.sql.Timestamp.class, id)
        .toInstant();
  }

  private Instant purgedAt(String id) {
    java.sql.Timestamp t = jdbc.queryForObject("SELECT purged_at FROM issue_attachment WHERE id = ?::uuid",
        java.sql.Timestamp.class, id);
    return t == null ? null : t.toInstant();
  }

  private List<String> purgeAudit(Space s) throws Exception {
    return TestApi.read(api.get(s.owner(), "/workspaces/{ws}/audit?action=" + AttachmentPurger.ACTION, s.ws()), "$[*].details");
  }

  @Test
  void 보관_기간이_지난_지운_첨부만_객체를_지우고_행은_정리_표시로_남는다() throws Exception {
    Space s = space();
    String old = upload(s, "old.txt", "오래전에 지운 것");
    String recent = upload(s, "recent.txt", "막 지운 것");
    String live = upload(s, "live.txt", "살아 있는 것");
    api.delete(s.owner(), BASE + "/{id}", s.ws(), P, s.issue(), old).andExpect(status().isNoContent());
    Thread.sleep(5);
    api.delete(s.owner(), BASE + "/{id}", s.ws(), P, s.issue(), recent).andExpect(status().isNoContent());
    assertThat(deletedAt(old)).isBefore(deletedAt(recent));
    long usedBefore = attachments.usedBytes(s.wsId());
    assertThat(usedBefore).isEqualTo("살아 있는 것".getBytes(StandardCharsets.UTF_8).length);

    // 기간 안이면 아무것도 지우지 않는다 — 지금 돌리면 셋 다 남는다
    purger.run(Instant.now());
    assertThat(store.keys(ObjectStore.Zone.ATTACHMENT, key(old))).hasSize(1);
    assertThat(purgedAt(old)).isNull();
    assertThat(purgeAudit(s)).isEmpty();

    // 막 지운 것을 지운 때로부터 꼭 30일 뒤 — 그보다 먼저 지운 것만 기간이 지났다
    AttachmentPurger.Result r = purger.run(deletedAt(recent).plus(Duration.ofDays(30)));
    assertThat(r.attachments()).isGreaterThanOrEqualTo(1);
    assertThat(store.keys(ObjectStore.Zone.ATTACHMENT, key(old))).isEmpty();
    assertThat(store.keys(ObjectStore.Zone.ATTACHMENT, key(recent))).hasSize(1);
    assertThat(store.keys(ObjectStore.Zone.ATTACHMENT, key(live))).hasSize(1);
    assertThat(purgedAt(old)).isNotNull();
    assertThat(purgedAt(recent)).isNull();
    assertThat(purgedAt(live)).isNull();
    // 행은 남는다 — 이름 · 크기 · 해시 · 누가 지웠나 그대로
    assertThat(jdbc.queryForObject("SELECT file_name FROM issue_attachment WHERE id = ?::uuid", String.class, old))
        .isEqualTo("old.txt");
    // 살아 있는 것은 그대로 받고, 용량도 그대로다(지운 것은 이미 세지 않는다)
    api.get(s.owner(), BASE + "/{id}/content", s.ws(), P, s.issue(), live).andExpect(status().isOk());
    api.get(s.owner(), BASE + "/{id}/content", s.ws(), P, s.issue(), old).andExpect(status().isNotFound());
    assertThat(attachments.usedBytes(s.wsId())).isEqualTo(usedBefore);

    // 워크스페이스마다 감사 한 줄 — 행위자는 SYSTEM
    List<String> details = purgeAudit(s);
    assertThat(details).hasSize(1);
    assertThat((Integer) JsonPath.read(details.get(0), "$.attachments")).isEqualTo(1);
    assertThat((Integer) JsonPath.read(details.get(0), "$.objects")).isEqualTo(1);
    assertThat((Integer) JsonPath.read(details.get(0), "$.retentionDays")).isEqualTo(30);
    List<String> actors = TestApi.read(api.get(s.owner(), "/workspaces/{ws}/audit?action=" + AttachmentPurger.ACTION, s.ws()),
        "$[*].actorType");
    assertThat(actors).containsExactly("SYSTEM");

    // 다시 돌려도 같은 것을 두 번 정리하지 않는다. 기간이 지나면 막 지운 것도 정리한다
    purger.run(deletedAt(recent).plus(Duration.ofDays(30)));
    assertThat(purgeAudit(s)).hasSize(1);
    purger.run(deletedAt(recent).plus(Duration.ofDays(30)).plusMillis(1));
    assertThat(purgedAt(recent)).isNotNull();
    assertThat(store.keys(ObjectStore.Zone.ATTACHMENT, key(recent))).isEmpty();
    assertThat(purgeAudit(s)).hasSize(2);
    assertThat(purgedAt(live)).isNull();
    assertThat(attachments.usedBytes(s.wsId())).isEqualTo(usedBefore);
  }

  @Test
  void 보호_트리거는_지운_행의_정리_표시_한_번만_허락한다() throws Exception {
    Space s = space();
    String gone = upload(s, "gone.txt", "지울 것");
    String live = upload(s, "keep.txt", "남길 것");
    api.delete(s.owner(), BASE + "/{id}", s.ws(), P, s.issue(), gone).andExpect(status().isNoContent());

    // 지우지 않은 행에는 정리 표시를 못 한다
    assertThatThrownBy(() -> jdbc.update("UPDATE issue_attachment SET purged_at = now() WHERE id = ?::uuid", live))
        .hasMessageContaining("지운 뒤에만");
    // 정리 표시와 함께 지움 표시 · 다른 값을 바꾸지 못한다
    assertThatThrownBy(() -> jdbc.update("UPDATE issue_attachment SET purged_at = now(), deleted_by = NULL WHERE id = ?::uuid", gone))
        .hasMessageContaining("정리 표시");
    assertThatThrownBy(() -> jdbc.update("UPDATE issue_attachment SET purged_at = now(), file_name = 'x' WHERE id = ?::uuid", gone))
        .hasMessageContaining("지움 표시만");
    // 정리 표시 한 번은 된다 — 그 뒤로는 다시 쓰지도, 지우지도 못한다
    assertThat(jdbc.update("UPDATE issue_attachment SET purged_at = now() WHERE id = ?::uuid", gone)).isEqualTo(1);
    assertThatThrownBy(() -> jdbc.update("UPDATE issue_attachment SET purged_at = now() WHERE id = ?::uuid", gone))
        .hasMessageContaining("정리 표시");
    assertThatThrownBy(() -> jdbc.update("UPDATE issue_attachment SET purged_at = NULL WHERE id = ?::uuid", gone))
        .hasMessageContaining("정리 표시");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM issue_attachment WHERE id = ?::uuid", gone))
        .hasMessageContaining("지울 수 없다");
    // 지움 표시는 예전처럼 한 번 된다
    api.delete(s.owner(), BASE + "/{id}", s.ws(), P, s.issue(), live).andExpect(status().isNoContent());
  }
}
