package com.moai.works.api.workspace;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.moai.works.api.audit.AuditService;
import com.moai.works.api.common.Actor;
import com.moai.works.api.common.ApiException;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.security.access.WorkspaceContext;

/**
 * 가입 신청 (design/03 §10) — 신청 · 철회 · 승인 · 거절.
 * <ul>
 *   <li>공개(PUBLIC) 팀 워크스페이스에만 신청할 수 있다. 비공개 · 개인 워크스페이스는 존재를 숨긴다(404)</li>
 *   <li>퍼블릭 워크스페이스만 신청을 받는다 — 프라이빗 · 개인은 있는지도 알려 주지 않는다(404)</li>
 *   <li>가입 방식 AUTO 면 바로 멤버가 된다, APPROVAL 이면 관리자 승인을 기다린다</li>
 *   <li>거절된 뒤에는 {@code rejoin_wait_days} 동안 다시 신청할 수 없다</li>
 *   <li>대기 신청은 30일 뒤 만료</li>
 * </ul>
 */
@Service
public class JoinRequestService {

  static final Duration PENDING_TTL = Duration.ofDays(30);

  private final WorkspaceRepositories.Workspaces workspaces;
  private final WorkspaceRepositories.Members members;
  private final WorkspaceRepositories.JoinRequests requests;
  private final MembershipService membership;
  private final AuditService audit;
  private final TransactionTemplate tx;
  private final Clock clock;

  public JoinRequestService(WorkspaceRepositories.Workspaces workspaces, WorkspaceRepositories.Members members,
      WorkspaceRepositories.JoinRequests requests, MembershipService membership, AuditService audit,
      PlatformTransactionManager txManager, Clock clock) {
    this.workspaces = workspaces;
    this.members = members;
    this.requests = requests;
    this.membership = membership;
    this.audit = audit;
    this.tx = new TransactionTemplate(txManager);
    this.clock = clock;
  }

  /** 신청한다. AUTO 면 바로 승인되어 멤버가 된다. */
  public JoinRequest apply(String slug, CurrentUser user, String message) {
    com.moai.works.api.account.PlatformRole.requireNotSystemAdmin(user.platformRoles());
    Workspace ws = workspaces.findBySlug(slug)
        .filter(w -> w.isActive() && w.getKind() == Workspace.Kind.TEAM && w.getVisibility() == Workspace.Visibility.PUBLIC)
        .orElseThrow(() -> ApiException.notFound("WORKSPACE_NOT_FOUND", "워크스페이스를 찾을 수 없습니다."));
    if (!ws.allowsJoinFrom(user.email())) {
      throw ApiException.forbidden("EMAIL_DOMAIN_NOT_ALLOWED", "이 워크스페이스는 허용된 이메일 도메인만 신청할 수 있습니다.");
    }
    if (members.find(ws.getId(), user.id()).isPresent()) {
      throw ApiException.conflict("ALREADY_MEMBER", "이미 멤버입니다.");
    }
    Instant now = Instant.now(clock);
    requests.findLastRejected(ws.getId(), user.id()).ifPresent(last -> {
      if (last.getDecidedAt() != null && last.getDecidedAt().plus(Duration.ofDays(ws.getRejoinWaitDays())).isAfter(now)) {
        throw ApiException.conflict("REJOIN_WAIT", "거절된 뒤 " + ws.getRejoinWaitDays() + "일이 지나야 다시 신청할 수 있습니다.");
      }
    });
    JoinRequest request;
    try {
      request = tx.execute(s -> {
        JoinRequest r = requests.saveAndFlush(new JoinRequest(ws.getId(), user.id(), message, now, now.plus(PENDING_TTL)));
        audit.record(user.actor(), ws.getId(), "join_request.create", "join_request", r.getId().toString(),
            Map.of("policy", ws.getJoinPolicy().name()));
        return r;
      });
    } catch (DataIntegrityViolationException e) {
      throw ApiException.conflict("REQUEST_PENDING", "이미 대기 중인 신청이 있습니다.");
    }
    if (ws.getJoinPolicy() == Workspace.JoinPolicy.AUTO) {
      membership.addMember(ws, user.id(), ws.getDefaultRoleKey(), WorkspaceMember.JoinedVia.AUTO, request.getId(),
          Actor.system("자동 가입"));
      return decide(request.getId(), r -> r.approve(null, ws.getDefaultRoleKey(), "자동 가입", Instant.now(clock)),
          Actor.system("자동 가입"), "join_request.approve");
    }
    return request;
  }

  /** 본인이 대기 중인 신청을 철회한다. */
  public JoinRequest cancel(UUID requestId, CurrentUser user) {
    JoinRequest r = requests.findById(requestId).filter(x -> x.getUserId().equals(user.id()))
        .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "신청을 찾을 수 없습니다."));
    return decide(r.getId(), x -> x.cancel(Instant.now(clock)), user.actor(), "join_request.cancel");
  }

  /** 승인한다 — 역할을 줄 수 있는 사람만(권한 상승 방지). Keycloak 에 멤버로 넣은 뒤 신청을 승인으로 닫는다. */
  public JoinRequest approve(WorkspaceContext by, UUID requestId, String roleKey, String note, Actor actor) {
    JoinRequest r = pendingIn(by, requestId);
    String role = roleKey == null || roleKey.isBlank() ? by.workspace().getDefaultRoleKey() : roleKey;
    membership.requireCanGrant(by, role);
    membership.addMember(by.workspace(), r.getUserId(), role, WorkspaceMember.JoinedVia.REQUEST, r.getId(), actor);
    return decide(r.getId(), x -> x.approve(actor.id(), role, note, Instant.now(clock)), actor, "join_request.approve");
  }

  public JoinRequest reject(WorkspaceContext by, UUID requestId, String note, Actor actor) {
    JoinRequest r = pendingIn(by, requestId);
    return decide(r.getId(), x -> x.reject(actor.id(), note, Instant.now(clock)), actor, "join_request.reject");
  }

  public List<JoinRequest> pending(WorkspaceContext by) {
    return requests.findByWorkspaceIdAndStatusOrderByRequestedAtAsc(by.workspaceId(), JoinRequest.Status.PENDING);
  }

  public List<JoinRequest> mine(UUID userId) {
    return requests.findByUserIdOrderByRequestedAtDesc(userId);
  }

  private JoinRequest pendingIn(WorkspaceContext by, UUID requestId) {
    JoinRequest r = requests.findById(requestId).filter(x -> x.getWorkspaceId().equals(by.workspaceId()))
        .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "신청을 찾을 수 없습니다."));
    if (!r.isPending()) {
      throw ApiException.conflict("REQUEST_CLOSED", "이미 처리된 신청입니다: " + r.getStatus());
    }
    return r;
  }

  private JoinRequest decide(UUID id, java.util.function.Consumer<JoinRequest> change, Actor actor, String action) {
    return tx.execute(s -> {
      JoinRequest r = requests.findById(id).orElseThrow();
      change.accept(r);
      audit.record(actor, r.getWorkspaceId(), action, "join_request", r.getId().toString(),
          Map.of("user", r.getUserId().toString(), "status", r.getStatus().name()));
      return r;
    });
  }
}
