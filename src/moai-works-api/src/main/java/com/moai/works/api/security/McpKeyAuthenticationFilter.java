package com.moai.works.api.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import com.moai.works.api.config.McpEndpoint;
import com.moai.works.api.config.MoaiWorksProperties;
import com.moai.works.api.mcpkey.McpKeyPrincipal;
import com.moai.works.api.mcpkey.McpKeyService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * MCP 입구({@code /mcp/project})의 모든 HTTP 요청을 MCP 키로 검사한다
 * (seed-site-keeper-api {@code McpKeyAuthenticationFilter}).
 *
 * <p>MCP 세션을 열었다고 다음 요청을 건너뛰지 않는다. 결과를 캐시하지 않는다.
 * 통과하면 {@link McpKeyPrincipal} 을 request attribute 에 두고, MCP 전송 문맥 추출기가 도구까지 옮긴다.
 * {@code @Component} 로 두지 않는다 — {@link SecurityConfiguration} 이 만든다.
 */
public class McpKeyAuthenticationFilter extends OncePerRequestFilter {

  private final MoaiWorksProperties.Mcp mcp;
  private final McpKeyService keyService;
  private final SecurityContextRepository contextRepository = new RequestAttributeSecurityContextRepository();

  public McpKeyAuthenticationFilter(MoaiWorksProperties.Mcp mcp, McpKeyService keyService) {
    this.mcp = mcp;
    this.keyService = keyService;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !mcp.isEntry(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String token = BearerTokens.read(request);
    if (token == null) {
      JsonErrors.write(response, 401, "MCP_KEY_REQUIRED", "Authorization: Bearer <MCP 키> 가 필요합니다.");
      return;
    }
    McpEndpoint entry = mcp.endpointOf(request.getRequestURI());
    McpKeyService.Verdict verdict = keyService.authenticate(token, entry);
    if (verdict.principal() == null) {
      JsonErrors.write(response, verdict.status(), verdict.code(), verdict.message());
      return;
    }
    McpKeyPrincipal principal = verdict.principal();
    request.setAttribute(McpKeyPrincipal.REQUEST_ATTRIBUTE, principal);
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    SecurityContextHolder.setContext(context);
    contextRepository.saveContext(context, request, response);
    chain.doFilter(request, response);
  }
}
