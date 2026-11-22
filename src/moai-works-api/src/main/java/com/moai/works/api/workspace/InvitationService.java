package com.moai.works.api.workspace;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.moai.works.api.account.Mailer;
import com.moai.works.api.account.UserProfile;
import com.moai.works.api.account.UserProfileRepository;
import com.moai.works.api.audit.AuditService;
import com.moai.works.api.common.ApiException;
import com.moai.works.api.config.MoaiWorksProperties;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.security.access.WorkspaceContext;

/**
 * 초대 (design/03 §10) — 보내기 · 목록 · 취소 · 받기(수락 · 거절).
 * <ul>
 *   <li>보내는 사람은 {@code member.invite} 가 있고, 줄 역할의 권한을 모두 가져야 한다(권한 상승 방지 — 가입 승인과 같은 규칙)</li>
 *   <li>초대는 이메일 하나에 묶인다. 그 이메일의 계정만 수락할 수 있다 — 링크가 새도 다른 사람은 못 들어온다</li>
 *   <li>링크 토큰은 32바이트 무작위, DB 에는 SHA-256 만. 7일 뒤 만료</li>
 *   <li>수락하면 Keycloak 조직 멤버 + 역할 그룹에 넣고(MembershipService), 같은 사람의 대기 가입 신청은 승인으로 닫는다</li>
 * </ul>
 */
@Service
public class InvitationService {

  static final Duration TTL = Duration.ofDays(7);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final Invitation.Repository invitations;
  private final WorkspaceRepositories.Workspaces workspaces;
  private final WorkspaceRepositories.Members members;
  private final WorkspaceRepositories.Roles roles;
  private final WorkspaceRepositories.JoinRequests joinRequests;
  private final UserProfileRepository users;
  private final MembershipService membership;
  private final Mailer mailer;
  private final AuditService audit;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final String webBaseUrl;

  public InvitationService(Invitation.Repository invitations, WorkspaceRepositories.Workspaces workspaces,
      WorkspaceRepositories.Members members, WorkspaceRepositories.Roles roles,
      WorkspaceRepositories.JoinRequests joinRequests, UserProfileRepository users, MembershipService membership,
      Mailer mailer, AuditService audit, PlatformTransactionManager txManager, Clock clock,
      MoaiWorksProperties properties) {
    this.invitations = invitations;
    this.workspaces = workspaces;
    this.members = members;
    this.roles = roles;
    this.joinRequests = joinRequests;
    this.users = users;
    this.membership = membership;
    this.mailer = mailer;
    this.audit = audit;
    this.tx = new TransactionTemplate(txManager);
    this.clock = clock;
    this.webBaseUrl = properties.web() == null ? "https://localhost:11002" : properties.web().baseUrl();
  }

  // ── 보내는 쪽 ─────────────────────────────────────────────────────

  public WorkspaceViews.InvitationCreated create(WorkspaceContext by, String email, String roleKey, String message,
      CurrentUser inviter) {
    Workspace ws = by.workspace();
    String to = email.trim().toLowerCase();
    membership.requireCanGrant(by, roleKey);
    users.findByEmail(to).ifPresent(u -> {
      if (members.find(ws.getId(), u.getId()).isPresent()) {
        throw ApiException.conflict("ALREADY_MEMBER", "이미 멤버입니다.");
      }
    });
    String token = newToken();
    Instant now = Instant.now(clock);
    Invitation saved;
    try {
      saved = tx.execute(s -> {
        invitations.findPendingFor(ws.getId(), to).ifPresent(old -> {
          if (old.expireIfDue(now)) {
            invitations.saveAndFlush(old);
          }
        });
        Invitation inv = invitations.saveAndFlush(new Invitation(ws.getId(), to, roleKey, blankToNull(message),
            inviter.id(), sha256(token), now, now.plus(TTL)));
        audit.record(inviter.actor(), ws.getId(), "invitation.create", "invitation", inv.getId().toString(),
            Map.of("email", to, "role", roleKey));
        return inv;
      });
    } catch (DataIntegrityViolationException e) {
      throw ApiException.conflict("INVITATION_PENDING", "이 이메일로 보낸 대기 중인 초대가 이미 있습니다.");
    }
    String url = webBaseUrl + "/invitations/" + token;
    mailer.send(to, "[moai-works] " + ws.getName() + " 초대",
        inviter.displayName() + "님이 " + ws.getName() + " 워크스페이스에 초대했습니다.\n수락하려면: " + url
            + "\n7일 뒤 만료됩니다.");
    return new WorkspaceViews.InvitationCreated(views(List.of(saved)).get(0), url);
  }

  public List<WorkspaceViews.InvitationView> pending(WorkspaceContext by) {
    return views(openOnly(invitations.findPending(by.workspaceId())));
  }

  public WorkspaceViews.InvitationView revoke(WorkspaceContext by, UUID id, CurrentUser user) {
    Invitation inv = invitations.findById(id).filter(i -> i.getWorkspaceId().equals(by.workspaceId()))
        .orElseThrow(() -> ApiException.notFound("INVITATION_NOT_FOUND", "초대를 찾을 수 없습니다."));
    Invitation done = decide(inv.getId(), i -> i.revoke(Instant.now(clock)), user, "invitation.revoke");
    return views(List.of(done)).get(0);
  }

  // ── 받는 쪽 ───────────────────────────────────────────────────────

  /** 링크로 들어온 초대 — 받는 사람 이메일이 아니어도 내용은 보여 주고, 수락은 막는다(화면이 안내한다). */
  public WorkspaceViews.InvitationView preview(String token) {
    return views(List.of(byToken(token))).get(0);
  }

  public List<WorkspaceViews.InvitationView> mine(CurrentUser user) {
    return views(openOnly(invitations.findPendingForEmail(user.email())));
  }

  public WorkspaceViews.InvitationView acceptByToken(String token, CurrentUser user) {
    return accept(byToken(token).getId(), user);
  }

  public WorkspaceViews.InvitationView declineByToken(String token, CurrentUser user) {
    return decline(byToken(token).getId(), user);
  }

  public WorkspaceViews.InvitationView accept(UUID id, CurrentUser user) {
    com.moai.works.api.account.PlatformRole.requireNotSystemAdmin(user.platformRoles());
    Invitation inv = forMe(id, user);
    Workspace ws = workspaces.findById(inv.getWorkspaceId()).filter(Workspace::isActive)
        .orElseThrow(() -> ApiException.notFound("WORKSPACE_NOT_FOUND", "워크스페이스를 찾을 수 없습니다."));
    if (members.find(ws.getId(), user.id()).isPresent()) {
      // 이미 멤버(다른 경로로 들어옴) — 초대는 수락으로 닫고 그대로 성공
      return views(List.of(decide(inv.getId(), i -> i.accept(user.id(), Instant.now(clock)), user,
          "invitation.accept"))).get(0);
    }
    if (roles.findByWorkspaceIdAndRoleKey(ws.getId(), inv.getRoleKey()).isEmpty()) {
      throw ApiException.conflict("ROLE_NOT_FOUND", "초대한 역할이 없어졌습니다. 다시 초대를 받으십시오.");
    }
    UserProfile inviter = users.findById(inv.getInvitedBy()).orElse(null);
    membership.addMember(ws, user.id(), inv.getRoleKey(), WorkspaceMember.JoinedVia.INVITE, inv.getId(),
        com.moai.works.api.common.Actor.user(inv.getInvitedBy(), inviter == null ? null : inviter.getDisplayName(), null));
    Invitation done = decide(inv.getId(), i -> i.accept(user.id(), Instant.now(clock)), user, "invitation.accept");
    // 같은 사람의 대기 가입 신청은 승인으로 닫는다
    tx.executeWithoutResult(s -> joinRequests.findPending(ws.getId(), user.id())
        .ifPresent(r -> r.approve(inv.getInvitedBy(), inv.getRoleKey(), "초대로 가입", Instant.now(clock))));
    return views(List.of(done)).get(0);
  }

  public WorkspaceViews.InvitationView decline(UUID id, CurrentUser user) {
    Invitation inv = forMe(id, user);
    return views(List.of(decide(inv.getId(), i -> i.decline(Instant.now(clock)), user, "invitation.decline"))).get(0);
  }

  // ── 내부 ─────────────────────────────────────────────────────────

  private Invitation byToken(String token) {
    Invitation inv = invitations.findByTokenHash(sha256(token))
        .orElseThrow(() -> ApiException.notFound("INVITATION_NOT_FOUND", "초대를 찾을 수 없습니다. 링크를 확인하십시오."));
    expireIfDue(inv);
    return invitations.findById(inv.getId()).orElseThrow();
  }

  /** 내 이메일로 온 대기 초대인가 — 아니면 403(남의 초대) 또는 409(이미 처리됨). */
  private Invitation forMe(UUID id, CurrentUser user) {
    Invitation inv = invitations.findById(id)
        .orElseThrow(() -> ApiException.notFound("INVITATION_NOT_FOUND", "초대를 찾을 수 없습니다."));
    if (!inv.isFor(user.email())) {
      throw ApiException.forbidden("INVITATION_EMAIL_MISMATCH",
          "이 초대는 다른 이메일로 왔습니다. 초대받은 이메일의 계정으로 로그인하십시오.");
    }
    expireIfDue(inv);
    Invitation fresh = invitations.findById(id).orElseThrow();
    if (!fresh.open(Instant.now(clock))) {
      throw ApiException.conflict("INVITATION_CLOSED", "이미 처리됐거나 기한이 지난 초대입니다.");
    }
    return fresh;
  }

  private void expireIfDue(Invitation inv) {
    Instant now = Instant.now(clock);
    if (inv.getStatus() == Invitation.Status.PENDING && !now.isBefore(inv.getExpiresAt())) {
      tx.executeWithoutResult(s -> invitations.findById(inv.getId()).ifPresent(i -> i.expireIfDue(now)));
    }
  }

  private List<Invitation> openOnly(List<Invitation> list) {
    Instant now = Instant.now(clock);
    list.forEach(this::expireIfDue);
    return list.stream().filter(i -> i.open(now)).toList();
  }

  private Invitation decide(UUID id, java.util.function.Consumer<Invitation> change, CurrentUser user, String action) {
    return tx.execute(s -> {
      Invitation inv = invitations.findById(id).orElseThrow();
      change.accept(inv);
      audit.record(user.actor(), inv.getWorkspaceId(), action, "invitation", inv.getId().toString(),
          Map.of("email", inv.getEmail(), "status", inv.getStatus().name()));
      return inv;
    });
  }

  private List<WorkspaceViews.InvitationView> views(List<Invitation> list) {
    Map<UUID, Workspace> wss = workspaces.findAllById(list.stream().map(Invitation::getWorkspaceId).distinct().toList())
        .stream().collect(Collectors.toMap(Workspace::getId, Function.identity()));
    Map<UUID, UserProfile> inviters = users.findAllById(list.stream().map(Invitation::getInvitedBy).distinct().toList())
        .stream().collect(Collectors.toMap(UserProfile::getId, Function.identity()));
    return list.stream().map(i -> {
      Workspace w = wss.get(i.getWorkspaceId());
      String roleName = roles.findByWorkspaceIdAndRoleKey(i.getWorkspaceId(), i.getRoleKey())
          .map(WorkspaceRole::getName).orElse(i.getRoleKey());
      return new WorkspaceViews.InvitationView(i.getId(), w.getSlug(), w.getName(), i.getEmail(), i.getRoleKey(),
          roleName, Optional.ofNullable(inviters.get(i.getInvitedBy())).map(UserProfile::getDisplayName).orElse(null),
          i.getMessage(), i.getStatus(), i.getCreatedAt(), i.getExpiresAt(), i.getDecidedAt());
    }).toList();
  }

  private static String newToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String sha256(String token) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
