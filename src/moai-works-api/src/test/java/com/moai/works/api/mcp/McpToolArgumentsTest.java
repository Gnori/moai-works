package com.moai.works.api.mcp;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;

import tools.jackson.databind.json.JsonMapper;

/** {@link McpToolArguments} 단위 시험 — DB 없이. 도구가 예외로 끝날 때 · 칸 이름이 바뀌어 넓히기가 조용히 빠질 때 (일감 #177) */
class McpToolArgumentsTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String SCHEMA = """
      {"$schema":"https://json-schema.org/draft/2020-12/schema","additionalProperties":false,"type":"object",
       "properties":{"number":{"type":"integer","description":"일감 번호"},"note":{"type":"string"}},"required":["number"]}""";

  /** 받은 인자를 적어 두고, 정한 대로 답하거나 던지는 가짜 도구 */
  private static final class Fake implements ToolCallback {
    final AtomicReference<String> received = new AtomicReference<>();
    final String name;
    final String schema;
    RuntimeException toThrow;

    Fake(String name, String schema) {
      this.name = name;
      this.schema = schema;
    }

    @Override
    public ToolDefinition getToolDefinition() {
      return ToolDefinition.builder().name(name).description("가짜").inputSchema(schema).build();
    }

    @Override
    public String call(String toolInput) {
      return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
      received.set(toolInput);
      if (toThrow != null) {
        throw toThrow;
      }
      return "{\"ok\":true}";
    }
  }

  private static ToolCallback guard(Fake fake) {
    return McpToolArguments.guard(new ToolCallback[] { fake }, JSON).get(0);
  }

  @Test
  void 숫자_글을_정수로_풀어_넘긴다() {
    Fake fake = new Fake("get_issue", SCHEMA);
    assertThat(guard(fake).call("{\"number\":\"#12\",\"note\":null}")).isEqualTo("{\"ok\":true}");
    assertThat(fake.received.get()).isEqualTo("{\"number\":12}");
  }

  @Test
  void 도구가_업무_오류_밖의_예외로_끝나면_평문이_아니라_500_INTERNAL_ERROR() {
    Fake fake = new Fake("get_issue", SCHEMA);
    fake.toThrow = new ToolExecutionException(fake.getToolDefinition(), new NullPointerException("비밀 아닌 버그"));
    String out = guard(fake).call("{\"number\":1}");
    assertThat((Integer) read(out, "$.error.status")).isEqualTo(500);
    assertThat((String) read(out, "$.error.code")).isEqualTo("INTERNAL_ERROR");
    assertThat(out).doesNotContain("NullPointerException").startsWith("{\"error\"");
  }

  @Test
  void 메서드_꼴로_바꾸지_못한_인자는_400_INVALID_ARGUMENT() {
    Fake fake = new Fake("get_issue", SCHEMA);
    fake.toThrow = new IllegalStateException("변환 실패");
    String out = guard(fake).call("{\"number\":1}");
    assertThat((String) read(out, "$.error.code")).isEqualTo("INVALID_ARGUMENT");
    assertThat((String) guard(new Fake("get_issue", SCHEMA)).call("[1,2]")).contains("INVALID_ARGUMENT");
  }

  @Test
  void 넓힐_칸이_도구에_없으면_기동을_멈춘다() {
    // search_issues 에 쉼표 글 칸 status 가 없으면 — 이름이 바뀌어 배열 받기가 조용히 빠지지 않게
    assertThatThrownBy(() -> guard(new Fake("search_issues", SCHEMA))).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("search_issues");
    assertThat(McpToolArguments.LISTS.get("search_issues")).contains("status", "module", "type", "project");
    assertThat(McpToolArguments.ISSUE_NUMBERS).containsExactlyInAnyOrderElementsOf(List.of("number", "parent", "target"));
  }
}
