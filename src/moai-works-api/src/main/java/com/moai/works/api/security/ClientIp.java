package com.moai.works.api.security;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.moai.works.api.config.MoaiWorksProperties;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 감사 · 접근 기록에 남길 클라이언트 IP. 판정에는 쓰지 않는다.
 *
 * <p>X-Forwarded-For 는 믿는 중계(web-backend — {@code moai-works.security.trusted-proxies})에서 온 요청일 때만 쓰고, 그 중계가
 * 덧붙인 맨 오른쪽 값을 고른다. api 에 직접 붙은 요청(브라우저 · MCP 클라이언트)은 X-Forwarded-For 를 버리고 접속 주소를 쓴다 —
 * 클라이언트가 헤더로 IP 를 꾸미지 못한다(2026-11-22, MCP 설계 「중계」).
 */
@Component
public class ClientIp {

  private final Set<String> trusted;

  public ClientIp(MoaiWorksProperties properties) {
    this.trusted = Set.copyOf(properties.security().trustedProxies());
  }

  public String of(HttpServletRequest request) {
    String remote = request.getRemoteAddr();
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded == null || forwarded.isBlank() || !trusted.contains(remote)) {
      return remote;
    }
    String[] hops = forwarded.split(",");
    String last = hops[hops.length - 1].trim();
    return last.isEmpty() || last.length() > 64 ? remote : last;
  }

  /** 믿는 중계(web-backend)에서 온 요청인가 — 중계가 붙인 요청 id(X-Correlation-Id)를 이어 쓸지 정한다 */
  public boolean fromTrustedProxy(HttpServletRequest request) {
    return trusted.contains(request.getRemoteAddr());
  }
}
