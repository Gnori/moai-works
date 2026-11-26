package com.moai.works.api.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.moai.works.api.common.ApiException;
import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * 글로 찾기의 DB 시간 한도 (2026-11-26, 일감 #313 — moai-wiki 찾기 도구와 같은 규칙) — 글(q)로 찾을 때만 그 트랜잭션에
 * statement_timeout 을 걸고, 넘으면 400 SEARCH_TOO_BROAD + 좁히라는 안내. 한도는 찾기 문장에만 걸리고 남지 않는다.
 */
@ApiTest
class IssueSearchTimeoutTest {

  @Autowired
  TestApi api;
  @Autowired
  JdbcTemplate jdbc;
  @Autowired
  IssueService issues;
  @Autowired
  PlatformTransactionManager txManager;

  @Test
  void 보통_글_찾기는_그대로_찾는다() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "찾기 한도");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", "srch", "name", "찾기", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    api.post(owner, "/workspaces/{ws}/projects/{p}/issues", Map.of("type", "feature", "title", "고양이 사료 주문"), ws, "srch")
        .andExpect(status().isCreated());
    api.post(owner, "/workspaces/{ws}/projects/{p}/issues", Map.of("type", "feature", "title", "강아지 산책"), ws, "srch")
        .andExpect(status().isCreated());
    api.get(owner, "/workspaces/{ws}/projects/{p}/issues?q=고양이", ws, "srch")
        .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
    api.get(owner, "/workspaces/{ws}/issues?q=산책", ws)
        .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
  }

  @Test
  void 한도는_찾는_문장에만_걸리고_끝나면_되돌린다() {
    TransactionTemplate tx = new TransactionTemplate(txManager);
    String before = jdbc.queryForObject("SHOW statement_timeout", String.class);
    tx.executeWithoutResult(status -> {
      String during = issues.textSearch(IssueService.SEARCH_TIMEOUT_MS,
          () -> jdbc.queryForObject("SHOW statement_timeout", String.class));
      assertThat(during).isEqualTo("5s");
      // 같은 트랜잭션의 다음 문장(사람 · 하위 수 읽기)에는 걸리지 않는다
      assertThat(jdbc.queryForObject("SHOW statement_timeout", String.class)).isEqualTo(before);
    });
    // 한도 0 이면 걸지 않는다(글 없이 목록만 볼 때)
    tx.executeWithoutResult(status -> assertThat(issues.textSearch(0,
        () -> jdbc.queryForObject("SHOW statement_timeout", String.class))).isEqualTo(before));
    assertThat(jdbc.queryForObject("SHOW statement_timeout", String.class)).isEqualTo(before);
  }

  @Test
  void 한도를_넘으면_400_SEARCH_TOO_BROAD_와_좁히라는_안내() {
    TransactionTemplate tx = new TransactionTemplate(txManager);
    assertThatThrownBy(() -> tx.executeWithoutResult(status -> issues.textSearch(100,
        () -> jdbc.queryForObject("SELECT pg_sleep(2)::text", String.class))))
        .isInstanceOfSatisfying(ApiException.class, e -> {
          assertThat(e.code()).isEqualTo("SEARCH_TOO_BROAD");
          assertThat(e.status().value()).isEqualTo(400);
          assertThat(e.properties()).containsKey("hint");
        });
    // 끊긴 뒤 다음 트랜잭션은 한도 없이 그대로
    assertThat(jdbc.queryForObject("SELECT pg_sleep(0.2)::text", String.class)).isEmpty();
  }

  @Test
  void 끊긴_것은_SQLSTATE_57014_로_알아본다() {
    assertThat(IssueService.canceled(new QueryTimeoutException("x", new SQLException("canceled", "57014")))).isTrue();
    assertThat(IssueService.canceled(new QueryTimeoutException("x", new SQLException("other", "23505")))).isFalse();
    assertThat(IssueService.canceled(new IllegalStateException("x"))).isFalse();
  }
}
