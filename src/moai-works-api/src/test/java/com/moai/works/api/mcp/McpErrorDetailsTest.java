package com.moai.works.api.mcp;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import com.moai.works.api.common.ApiException;
import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.McpTestClient;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * MCP 오류에 예외에 붙인 값(ApiException.with)도 싣는다 — REST problem+json 과 같은 값, 세 기본 값은 덮지 못한다
 * (2026-11-24, 일감 #157 — moai-wiki 와 같은 모양).
 */
@ApiTest
class McpErrorDetailsTest {

  private static final String P = "detail";

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;

  private static Map<String, Object> args(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  @Test
  void 붙인_값은_실리고_status_code_message_는_덮지_못한다() {
    Map<String, Object> out = McpScope.run(() -> {
      throw new ApiException(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "겹침").with("rowVersion", 3)
          .with("status", 200).with("code", "OK").with("message", "괜찮음");
    });
    @SuppressWarnings("unchecked")
    Map<String, Object> error = (Map<String, Object>) out.get("error");
    assertThat(error).containsEntry("status", 409).containsEntry("code", "CONCURRENT_UPDATE").containsEntry("message", "겹침")
        .containsEntry("rowVersion", 3);
    assertThat(error.keySet()).containsExactly("status", "code", "message", "rowVersion");
  }

  @Test
  void 댓글_겹침의_rowVersion_과_체크_글_못_찾음의_unmatched_가_REST_처럼_온다() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "오류 값");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "오류 값", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    McpTestClient mcp = new McpTestClient(mvc, TestApi.read(api.post(owner, "/me/mcp-keys", Map.of("name", "값 키"))
        .andExpect(status().isCreated()), "$.key"));
    String created = mcp.call("create_issue", args("project", P, "type", "feature", "title", "값", "description",
        "## 수용 기준\n- [ ] 된다"));
    int n = read(created, "$.summary.number");

    mcp.call("add_comment", args("number", n, "text", "처음"));
    String commentId = read(mcp.call("list_comments", args("number", n)), "$.items[0].id");
    mcp.call("update_comment", args("number", n, "commentId", commentId, "text", "고침", "rowVersion", 0));
    String stale = mcp.call("update_comment", args("number", n, "commentId", commentId, "text", "늦음", "rowVersion", 0));
    assertThat((String) read(stale, "$.error.code")).isEqualTo("CONCURRENT_UPDATE");
    assertThat((Integer) read(stale, "$.error.status")).isEqualTo(409);
    // REST 와 같은 값 — 다시 읽지 않고 지금 판으로 고칠 수 있다
    assertThat((Integer) read(stale, "$.error.rowVersion")).isEqualTo(1);

    String missing = mcp.call("update_issue", args("number", n, "rowVersion", read(created, "$.summary.rowVersion"),
        "checkItems", List.of("된다", "없다")));
    assertThat((String) read(missing, "$.error.code")).isEqualTo("CHECK_ITEM_NOT_FOUND");
    assertThat((List<String>) read(missing, "$.error.unmatched")).containsExactly("없다");
    assertThat((String) read(missing, "$.error.field")).isEqualTo("checkItems");
    String blank = mcp.call("update_issue", args("number", n, "rowVersion", read(created, "$.summary.rowVersion"),
        "setSection", Map.of("heading", " ", "content", "x")));
    assertThat((String) read(blank, "$.error.code")).isEqualTo("INVALID_ARGUMENT");
    assertThat((String) read(blank, "$.error.field")).isEqualTo("setSection");
  }
}
