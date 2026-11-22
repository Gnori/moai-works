package com.moai.works.api.workspace;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moai.works.api.account.UserProfile;
import com.moai.works.api.account.UserProfileRepository;
import com.moai.works.api.audit.AuditService;
import com.moai.works.api.common.ApiException;
import com.moai.works.api.platform.PlatformSettingService;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.security.access.WorkspaceContext;

/** 워크스페이스 조회 · 설정 · 팀 워크스페이스 만들기. 판정은 이미 끝난 상태로 들어온다(접근 어노테이션). */
@Service
public class WorkspaceService {

  private final WorkspaceRepositories.Workspaces workspaces;
  private final WorkspaceRepositories.Members members;
  private final WorkspaceRepositories.MemberRoles memberRoles;
  private final WorkspaceRepositories.Roles roles;
  private final WorkspaceRepositories.JoinRequests joinRequests;
  private final UserProfileRepository users;
  private final WorkspaceProvisioner provisioner;
  private final PlatformSettingService settings;
  private final AuditService audit;
  private final Clock clock;

  public WorkspaceService(WorkspaceRepositories.Workspaces workspaces, WorkspaceRepositories.Members members,
      WorkspaceRepositories.MemberRoles memberRoles, WorkspaceRepositories.Roles roles,
      WorkspaceRepositories.JoinRequests joinRequests, UserProfileRepository users, WorkspaceProvisioner provisioner,
      PlatformSettingService settings, AuditService audit, Clock clock) {
    this.workspaces = workspaces;
    this.members = members;
    this.memberRoles = memberRoles;
    this.roles = roles;
    this.joinRequests = joinRequests;
    this.users = users;
    this.provisioner = provisioner;
    this.settings = settings;
    this.audit = audit;
    this.clock = clock;
  }

  /** 팀 워크스페이스 만들기 — 주소(workspace-고유id)는 서버가 정한다. */
  public WorkspaceViews.Detail createTeam(CurrentUser user, String name, String description,
      Workspace.Visibility visibility, Workspace.JoinPolicy joinPolicy) {
    Workspace ws = provisioner.createTeamWorkspace(user.id(), name.trim(), description, visibility,
        Workspace.joinPolicyFor(visibility, joinPolicy), user.actor());
    return detail(ws, user.id());
  }

  @Transactional(readOnly = true)
  public WorkspaceViews.Detail detail(WorkspaceContext ctx) {
    return detail(ctx.workspace(), ctx.userId());
  }

  private WorkspaceViews.Detail detail(Workspace ws, UUID userId) {
    List<String> myRoles = memberRoles.find(ws.getId(), userId).stream().map(MemberRole::getRoleKey).sorted().toList();
    Set<String> keys = Set.copyOf(myRoles);
    List<String> myPermissions = roles.findByWorkspaceIdOrderByBuiltinDescRoleKeyAsc(ws.getId()).stream()
        .filter(r -> keys.contains(r.getRoleKey())).flatMap(r -> r.getPermissions().stream()).distinct().sorted()
        .toList();
    return new WorkspaceViews.Detail(ws.getId(), ws.getSlug(), ws.getName(), ws.getDescription(), ws.getKind(),
        ws.getVisibility(), ws.getJoinPolicy(), ws.getAllowedJoinDomains(), ws.getDefaultRoleKey(), ws.getStatus(),
        ws.getCreatedAt(), members.countByWorkspace(ws.getId()), myRoles, myPermissions);
  }

  @Transactional
  public WorkspaceViews.Detail updateSettings(WorkspaceContext ctx, String name, String description,
      Workspace.Visibility visibility, Workspace.JoinPolicy joinPolicy, List<String> allowedJoinDomains,
      String defaultRoleKey, CurrentUser by) {
    Workspace ws = workspaces.findById(ctx.workspaceId()).orElseThrow();
    if (roles.findByWorkspaceIdAndRoleKey(ws.getId(), defaultRoleKey).isEmpty()
        || BuiltinRole.OWNER.key().equals(defaultRoleKey)) {
      throw ApiException.badRequest("INVALID_DEFAULT_ROLE", "기본 역할로 쓸 수 없는 역할입니다: " + defaultRoleKey);
    }
    Map<String, Object> before = Map.of("name", ws.getName(), "visibility", ws.getVisibility(), "joinPolicy",
        ws.getJoinPolicy(), "defaultRoleKey", ws.getDefaultRoleKey());
    ws.updateSettings(name.trim(), description, visibility, joinPolicy,
        allowedJoinDomains.stream().map(String::toLowerCase).distinct().toList(), defaultRoleKey, Instant.now(clock));
    audit.record(by.actor(), ws.getId(), "workspace.settings.update", "workspace", ws.getId().toString(),
        Map.of("before", before, "after", Map.of("name", ws.getName(), "visibility", visibility, "joinPolicy",
            joinPolicy, "defaultRoleKey", defaultRoleKey)));
    return detail(ws, ctx.userId());
  }

  @Transactional(readOnly = true)
  public List<WorkspaceViews.Member> members(WorkspaceContext ctx) {
    UUID wsId = ctx.workspaceId();
    Map<UUID, List<String>> rolesByUser = memberRoles.findByWorkspace(wsId).stream()
        .collect(Collectors.groupingBy(MemberRole::getUserId,
            Collectors.mapping(MemberRole::getRoleKey, Collectors.toList())));
    List<WorkspaceMember> list = members.findByWorkspace(wsId);
    Map<UUID, UserProfile> profiles = users.findAllById(list.stream().map(WorkspaceMember::getUserId).toList())
        .stream().collect(Collectors.toMap(UserProfile::getId, Function.identity()));
    return list.stream().map(m -> {
      UserProfile p = profiles.get(m.getUserId());
      return new WorkspaceViews.Member(m.getUserId(), p.getUsername(), p.getDisplayName(), p.getEmail(), m.getStatus(),
          m.getJoinedVia(), m.getJoinedAt(), rolesByUser.getOrDefault(m.getUserId(), List.of()).stream()
              .sorted(Comparator.comparingInt(WorkspaceService::roleOrder)).toList());
    }).toList();
  }

  @Transactional(readOnly = true)
  public List<WorkspaceViews.Role> roles(WorkspaceContext ctx) {
    Map<String, Long> counts = memberRoles.findByWorkspace(ctx.workspaceId()).stream()
        .collect(Collectors.groupingBy(MemberRole::getRoleKey, Collectors.counting()));
    return roles.findByWorkspaceIdOrderByBuiltinDescRoleKeyAsc(ctx.workspaceId()).stream()
        .sorted(Comparator.comparingInt((WorkspaceRole r) -> roleOrder(r.getRoleKey())).thenComparing(WorkspaceRole::getName))
        .map(r -> WorkspaceViews.Role.of(r, counts.getOrDefault(r.getRoleKey(), 0L))).toList();
  }

  /** 내가 멤버인 워크스페이스 (활성 · 정지 상태 워크스페이스 포함, 만드는 중 · 삭제 제외) */
  @Transactional(readOnly = true)
  public List<WorkspaceViews.Summary> mine(UUID userId) {
    Map<UUID, List<String>> rolesByWs = memberRoles.findByUser(userId).stream()
        .collect(Collectors.groupingBy(MemberRole::getWorkspaceId,
            Collectors.mapping(MemberRole::getRoleKey, Collectors.toList())));
    List<WorkspaceMember> mine = members.findByUser(userId).stream().filter(WorkspaceMember::isActive).toList();
    return workspaces.findAllById(mine.stream().map(WorkspaceMember::getWorkspaceId).toList()).stream()
        .filter(w -> w.getStatus() == Workspace.Status.ACTIVE || w.getStatus() == Workspace.Status.SUSPENDED)
        .sorted(Comparator.comparing((Workspace w) -> w.getKind() != Workspace.Kind.PERSONAL)
            .thenComparing(Workspace::getName))
        .map(w -> new WorkspaceViews.Summary(w.getSlug(), w.getName(), w.getKind(), w.getStatus(),
            rolesByWs.getOrDefault(w.getId(), List.of()).stream()
                .sorted(Comparator.comparingInt(WorkspaceService::roleOrder)).toList(),
            members.countByWorkspace(w.getId())))
        .toList();
  }

  /** 공개 팀 워크스페이스 찾기 (이름 · 주소 · 설명 부분 일치) */
  @Transactional(readOnly = true)
  public List<WorkspaceViews.Discoverable> discover(UUID userId, String query) {
    String q = query == null ? "" : query.trim().toLowerCase();
    Set<UUID> memberOf = members.findByUser(userId).stream().map(WorkspaceMember::getWorkspaceId)
        .collect(Collectors.toSet());
    Set<UUID> pending = joinRequests.findByUserIdOrderByRequestedAtDesc(userId).stream()
        .filter(JoinRequest::isPending).map(JoinRequest::getWorkspaceId).collect(Collectors.toSet());
    return workspaces.findAll(PageRequest.of(0, 500)).stream()
        .filter(w -> w.isActive() && w.getKind() == Workspace.Kind.TEAM && w.getVisibility() == Workspace.Visibility.PUBLIC)
        .filter(w -> q.isEmpty() || w.getName().toLowerCase().contains(q) || w.getSlug().contains(q)
            || (w.getDescription() != null && w.getDescription().toLowerCase().contains(q)))
        .sorted(Comparator.comparing(Workspace::getName))
        .limit(50)
        .map(w -> new WorkspaceViews.Discoverable(w.getSlug(), w.getName(), w.getDescription(), w.getJoinPolicy(),
            members.countByWorkspace(w.getId()),
            memberOf.contains(w.getId()) ? "MEMBER" : pending.contains(w.getId()) ? "PENDING" : "NONE"))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<WorkspaceViews.JoinRequestView> joinRequestViews(List<JoinRequest> list) {
    Map<UUID, UserProfile> profiles = users.findAllById(list.stream().map(JoinRequest::getUserId).toList()).stream()
        .collect(Collectors.toMap(UserProfile::getId, Function.identity()));
    Map<UUID, Workspace> wss = workspaces.findAllById(list.stream().map(JoinRequest::getWorkspaceId).distinct().toList())
        .stream().collect(Collectors.toMap(Workspace::getId, Function.identity()));
    return list.stream().map(r -> {
      UserProfile p = profiles.get(r.getUserId());
      Workspace w = wss.get(r.getWorkspaceId());
      return new WorkspaceViews.JoinRequestView(r.getId(), w.getSlug(), w.getName(), r.getUserId(), p.getUsername(),
          p.getDisplayName(), p.getEmail(), r.getMessage(), r.getStatus(), r.getGrantedRoleKey(), r.getRequestedAt(),
          r.getDecidedAt(), r.getDecisionNote());
    }).toList();
  }

  @Transactional(readOnly = true)
  public List<WorkspaceViews.AuditEntry> audit(WorkspaceContext ctx, AuditService.Filter filter, int limit) {
    return audit.search(ctx.workspaceId(), filter, limit).stream()
        .map(a -> new WorkspaceViews.AuditEntry(a.getId(), a.getOccurredAt(), a.getActorType(), a.getActorId(),
            a.getActorName(), a.getIp(), a.getAction(), a.getTargetType(), a.getTargetId(), a.getDetails()))
        .toList();
  }

  /** 이 계정이 지금까지 만든 팀 워크스페이스 수(지운 것 제외) — 만들 수 있는 수와 견준다 */
  @Transactional(readOnly = true)
  public long teamWorkspacesCreatedBy(UUID userId) {
    return workspaces.countTeamWorkspacesCreatedBy(userId);
  }

  /** 로그인 뒤 들어갈 워크스페이스: 마지막에 쓴 곳 → 가장 먼저 들어간 팀 → 개인 */
  @Transactional(readOnly = true)
  public Optional<String> landing(UserProfile user) {
    List<WorkspaceMember> mine = members.findByUser(user.getId()).stream().filter(WorkspaceMember::isActive).toList();
    Map<UUID, Workspace> wss = workspaces.findAllById(mine.stream().map(WorkspaceMember::getWorkspaceId).toList())
        .stream().filter(Workspace::isActive).collect(Collectors.toMap(Workspace::getId, Function.identity()));
    if (user.getLastWorkspaceId() != null && wss.containsKey(user.getLastWorkspaceId())) {
      return Optional.of(wss.get(user.getLastWorkspaceId()).getSlug());
    }
    Optional<String> team = mine.stream().filter(m -> wss.containsKey(m.getWorkspaceId()))
        .filter(m -> wss.get(m.getWorkspaceId()).getKind() == Workspace.Kind.TEAM)
        .min(Comparator.comparing(WorkspaceMember::getJoinedAt))
        .map(m -> wss.get(m.getWorkspaceId()).getSlug());
    if (team.isPresent()) {
      return team;
    }
    return Optional.ofNullable(user.getPersonalWorkspaceId()).map(wss::get).map(Workspace::getSlug);
  }

  static int roleOrder(String key) {
    return BuiltinRole.ofKey(key).map(Enum::ordinal).orElse(100);
  }
}
