package com.moai.works.api.mcpkey;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.moai.works.api.config.McpEndpoint;
import com.moai.works.api.config.MoaiWorksProperties;
import com.moai.works.api.security.KeycloakPrincipal;

/** MCP 키 발급 · 목록 · 폐기 · 검증. */
@Service
public class McpKeyService {

  private static final Duration LAST_USED_GAP = Duration.ofMinutes(1);

  private final McpApiKeyRepository keys;
  private final MoaiWorksProperties.Mcp mcp;
  private final Clock clock;

  public McpKeyService(McpApiKeyRepository keys, MoaiWorksProperties properties) {
    this.keys = keys;
    this.mcp = properties.mcp();
    this.clock = Clock.systemUTC();
  }

  /** 발급 결과 — {@code token} 원문은 이 응답에만 있다. */
  public record Issued(McpApiKey key, String token) {
  }

  /**
   * 호출자 본인 명의로 키를 발급한다.
   *
   * <p>⚠️ 골격: 역할 권한 검사가 아직 없다. seed-site-keeper-api 는 「역할에 MCP 키 사용 권한」을 본다(D20).
   * 사용자 · 역할 모델이 생기면 여기와 {@link #authenticate} 에 같은 검사를 넣는다.
   */
  @Transactional
  public Issued issue(KeycloakPrincipal owner, String name, McpEndpoint endpoint, McpApiKey.KeyType keyType,
      Integer validDays) {
    int days = validDays == null ? mcp.keyDefaultDays() : validDays;
    if (days < 1 || days > mcp.keyMaxDays()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "validDays 는 1~" + mcp.keyMaxDays() + " 이어야 합니다.");
    }
    String token = McpKeyTokenCodec.generate();
    Instant now = clock.instant();
    McpApiKey key = new McpApiKey(name, McpKeyTokenCodec.displayPrefix(token), McpKeyTokenCodec.hash(token),
        keyType == null ? McpApiKey.KeyType.PERSONAL : keyType, endpoint, owner.subject(), owner.username(),
        now, now.plus(Duration.ofDays(days)));
    return new Issued(keys.save(key), token);
  }

  @Transactional(readOnly = true)
  public List<McpApiKey> listOwned(KeycloakPrincipal owner) {
    return keys.findByOwnerSubjectOrderByCreatedAtDesc(owner.subject());
  }

  /** 본인 키만 폐기한다. 남의 키는 존재를 숨긴다(404). */
  @Transactional
  public void revoke(KeycloakPrincipal owner, UUID id) {
    McpApiKey key = keys.findById(id)
        .filter(k -> k.getOwnerSubject().equals(owner.subject()))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    key.revoke(clock.instant());
  }

  /** 검증 결과 — 성공이면 principal, 실패면 HTTP 상태와 코드. */
  public record Verdict(McpKeyPrincipal principal, int status, String code, String message) {
    static Verdict ok(McpKeyPrincipal principal) {
      return new Verdict(principal, 200, null, null);
    }

    static Verdict reject(int status, String code, String message) {
      return new Verdict(null, status, code, message);
    }
  }

  /**
   * 요청마다 검사한다(캐시 없음) — 폐기가 다음 요청에 바로 반영된다.
   * 순서: 모양 → 원문 SHA-256 으로 행 → 폐기 → 기간 → 입구 일치.
   */
  @Transactional
  public Verdict authenticate(String token, McpEndpoint entry) {
    McpApiKey key = McpKeyTokenCodec.wellFormed(token) ? keys.findByKeyHash(McpKeyTokenCodec.hash(token)).orElse(null) : null;
    if (key == null) {
      return Verdict.reject(401, "MCP_KEY_INVALID", "MCP 키가 올바르지 않습니다.");
    }
    Instant now = clock.instant();
    if (key.getRevokedAt() != null) {
      return Verdict.reject(401, "MCP_KEY_REVOKED", "폐기된 MCP 키입니다.");
    }
    if (key.getExpiresAt().isBefore(now)) {
      return Verdict.reject(401, "MCP_KEY_EXPIRED", "사용 기간이 지난 MCP 키입니다.");
    }
    if (key.getEndpoint() != entry) {
      return Verdict.reject(403, "MCP_KEY_ENDPOINT_DENIED", "이 MCP 키로는 이 입구를 쓸 수 없습니다.");
    }
    keys.touchLastUsed(key.getId(), now, now.minus(LAST_USED_GAP));
    return Verdict.ok(new McpKeyPrincipal(key.getId(), key.getName(), key.getKeyType(), key.getEndpoint(),
        key.getOwnerSubject(), key.getOwnerName()));
  }
}
