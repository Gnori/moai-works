package com.moai.works.api.account;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.moai.works.api.audit.AuditService;
import com.moai.works.api.common.Actor;
import com.moai.works.api.common.ApiException;
import com.moai.works.api.security.KeycloakPrincipal;
import com.moai.works.api.security.access.CurrentUser;

/**
 * 계정 사본 관리 — 요청한 사람이 활성 계정인지 확인한다.
 *
 * <p>Keycloak 에서 직접 만든 계정(시스템 관리자 · 설치 스크립트 계정)은 처음 api 를 부를 때 사본을 만든다(origin KEYCLOAK).
 * Keycloak 이 로그인을 허락했다는 것은 Keycloak 에서 활성이라는 뜻이므로 ACTIVE 로 만든다.
 * 그 뒤로는 DB 상태가 판정 기준이다 — 정지(SUSPENDED)는 토큰이 아직 살아 있어도 다음 요청부터 막힌다.
 */
@Service
public class AccountService {

  private final UserProfileRepository users;
  private final AuditService audit;
  private final TransactionTemplate tx;
  private final Clock clock;

  public AccountService(UserProfileRepository users, AuditService audit, PlatformTransactionManager txManager,
      Clock clock) {
    this.users = users;
    this.audit = audit;
    this.tx = new TransactionTemplate(txManager);
    this.clock = clock;
  }

  /** 활성 사람 계정이면 호출자 정보, 아니면 403. 처음 보는 Keycloak 계정은 사본을 만든다. 플랫폼 역할 사본을 토큰과 맞춘다. */
  public CurrentUser requireActiveUser(KeycloakPrincipal principal, String ip) {
    UUID id = principal.userId();
    UserProfile profile = tx.execute(s -> {
      UserProfile p = users.findById(id).orElse(null);
      if (p == null) {
        return null;
      }
      p.syncPlatformRoles(principal.roles());
      return p;
    });
    if (profile == null) {
      profile = createFromKeycloak(principal, ip);
    }
    if (!profile.isActive()) {
      throw ApiException.forbidden("ACCOUNT_NOT_ACTIVE", "계정이 활성 상태가 아닙니다: " + profile.getStatus());
    }
    return new CurrentUser(profile.getId(), profile.getUsername(), profile.getEmail(), profile.getDisplayName(),
        profile.getPlatformRoles(), ip);
  }

  private UserProfile createFromKeycloak(KeycloakPrincipal principal, String ip) {
    if (principal.email() == null || principal.email().isBlank()) {
      throw ApiException.forbidden("ACCOUNT_EMAIL_REQUIRED", "Keycloak 계정에 이메일이 없습니다. 관리자에게 문의하십시오.");
    }
    String displayName = Optional.ofNullable(principal.name()).filter(n -> !n.isBlank()).orElse(principal.username());
    try {
      return tx.execute(s -> {
        Instant now = Instant.now(clock);
        UserProfile p = new UserProfile(principal.userId(), principal.username(), principal.email(), displayName,
            AccountStatus.ACTIVE, AccountOrigin.KEYCLOAK, now);
        p.syncPlatformRoles(principal.roles());
        users.saveAndFlush(p);
        audit.record(Actor.user(p.getId(), displayName, ip), null, "account.link", "user", p.getId().toString(),
            Map.of("origin", "KEYCLOAK", "username", principal.username()));
        return p;
      });
    } catch (DataIntegrityViolationException e) {
      // 같은 계정의 동시 첫 요청 — 먼저 만든 행을 쓴다. 이메일이 다른 계정과 겹치면 막는다.
      return users.findById(principal.userId())
          .orElseThrow(() -> ApiException.conflict("EMAIL_TAKEN", "같은 이메일을 쓰는 다른 계정이 있습니다."));
    }
  }
}
