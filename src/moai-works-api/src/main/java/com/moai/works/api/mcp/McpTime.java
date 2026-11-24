package com.moai.works.api.mcp;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * MCP 결과의 시각 — 모두 한국 시간 오프셋을 붙인 ISO-8601 하나({@code 2026-11-24T21:05:12.345+09:00}, {@code serverTime} 과 같은 꼴)
 * (일감 #204, 써 보기 3회차: {@code statusChangedAt} 은 UTC {@code …Z} 이고 {@code serverTime} 은 {@code +09:00} 이라 Agent 가 견주기 전에
 * 바꿔 셈해야 했다).
 *
 * <ul>
 *   <li>도구 결과의 시각 칸(summary · get_issue · search_issues 줄 · log_work · 댓글 · 첨부 · 프로젝트 · 스킬)과 checks 메시지 안의 시각이
 *       모두 이 꼴이다. 자리 수가 늘 같아(밀리초 셋 · 오프셋 같음) 글로 견줘도 차례가 맞다</li>
 *   <li>REST 는 그대로 UTC({@code …Z}) — 이 꼴은 MCP 도구 결과에만 쓴다(전역 JsonMapper 는 건드리지 않는다)</li>
 * </ul>
 */
final class McpTime {

  static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  /** 한국 시간, 오프셋을 붙인 ISO — 밀리초는 늘 세 자리 */
  static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(SEOUL);

  private McpTime() {
  }

  /** 시각 하나 — 없으면 null */
  static String format(Instant instant) {
    return instant == null ? null : FORMAT.format(instant);
  }

  /**
   * MCP 결과를 만드는 JsonMapper — 주어진 것(전역)과 같고 {@link Instant} 만 {@link #FORMAT} 으로 쓴다. 도구 클래스가 결과 값(view)을
   * Map 으로 바꿀 때 쓴다.
   */
  static JsonMapper mapper(JsonMapper base) {
    return base.rebuild().addModule(new SimpleModule("mcp-time").addSerializer(Instant.class, new SeoulInstant())).build();
  }

  private static final class SeoulInstant extends StdSerializer<Instant> {

    SeoulInstant() {
      super(Instant.class);
    }

    @Override
    public void serialize(Instant value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
      gen.writeString(FORMAT.format(value));
    }
  }
}
