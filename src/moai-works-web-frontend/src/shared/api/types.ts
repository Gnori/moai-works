/** moai-works-api 응답 모양 (api 의 *Views 레코드와 같게 유지한다). */

export type AccountStatus =
  | 'PENDING_VERIFICATION'
  | 'PENDING_APPROVAL'
  | 'ACTIVE'
  | 'REJECTED'
  | 'SUSPENDED'
  | 'DELETED';

export type WorkspaceKind = 'PERSONAL' | 'TEAM';
export type WorkspaceVisibility = 'PUBLIC' | 'PRIVATE';
/** 퍼블릭 워크스페이스에 가입하는 방식 — 관리자 승인 후 가입 · 자동 가입. 프라이빗 · 개인에서는 쓰이지 않는다 */
export type JoinPolicy = 'APPROVAL' | 'AUTO';
export type WorkspaceStatus = 'PROVISIONING' | 'ACTIVE' | 'SUSPENDED' | 'DELETION_PENDING' | 'DELETED';
export type JoinRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELED' | 'EXPIRED';
export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'REVOKED' | 'EXPIRED';

/** 권한 코드 (api Permission enum 과 같다) */
export type PermissionCode =
  | 'workspace.read'
  | 'workspace.settings.manage'
  | 'workspace.ownership.transfer'
  | 'workspace.delete'
  | 'member.read'
  | 'member.invite'
  | 'member.request.review'
  | 'member.remove'
  | 'member.role.assign'
  | 'role.manage'
  | 'mcp.endpoint.manage'
  | 'mcp.key.issue.personal'
  | 'mcp.key.issue.service'
  | 'mcp.key.manage'
  | 'audit.read'
  | 'usage.read';

export interface WorkspaceSummary {
  slug: string;
  name: string;
  kind: WorkspaceKind;
  status: WorkspaceStatus;
  myRoles: string[];
  memberCount: number;
}

export interface Me {
  id: string;
  username: string;
  email: string;
  displayName: string;
  status: AccountStatus;
  platformRoles: string[];
  personalWorkspace: string | null;
  landingWorkspace: string | null;
  workspaces: WorkspaceSummary[];
  lastLoginAt: string | null;
  /** 만들 수 있는 팀 워크스페이스 수 — 0 이면 못 만든다(시스템 관리자가 계정마다 정한다) */
  teamWorkspaceLimit: number;
  /** 지금까지 만든 팀 워크스페이스 수(지운 것 제외) */
  teamWorkspacesCreated: number;
}

export interface WorkspaceDetail {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  kind: WorkspaceKind;
  visibility: WorkspaceVisibility;
  joinPolicy: JoinPolicy;
  allowedJoinDomains: string[];
  defaultRoleKey: string;
  status: WorkspaceStatus;
  createdAt: string;
  memberCount: number;
  myRoles: string[];
  myPermissions: PermissionCode[];
}

export interface Member {
  userId: string;
  username: string;
  displayName: string;
  email: string;
  status: 'ACTIVE' | 'SUSPENDED';
  joinedVia: 'CREATOR' | 'REQUEST' | 'AUTO' | 'INVITE' | 'ADMIN';
  joinedAt: string;
  roles: string[];
}

export interface Role {
  key: string;
  name: string;
  description: string | null;
  builtin: boolean;
  permissions: PermissionCode[];
  /** 이 역할을 가진 멤버 수 (서버가 주면) */
  memberCount?: number;
}

export const builtinRoleKeys = ['owner', 'admin', 'member'] as const;
export type BuiltinRoleKey = (typeof builtinRoleKeys)[number];

export interface Discoverable {
  slug: string;
  name: string;
  description: string | null;
  joinPolicy: JoinPolicy;
  memberCount: number;
  myStatus: 'MEMBER' | 'PENDING' | 'NONE';
}

export interface JoinRequest {
  id: string;
  workspaceSlug: string;
  workspaceName: string;
  userId: string;
  username: string;
  displayName: string;
  email: string;
  message: string | null;
  status: JoinRequestStatus;
  grantedRoleKey: string | null;
  requestedAt: string;
  decidedAt: string | null;
  decisionNote: string | null;
}

/** 초대 — 한 이메일에 묶인다. 그 이메일의 계정만 수락할 수 있다. */
export interface Invitation {
  id: string;
  workspaceSlug: string;
  workspaceName: string;
  email: string;
  roleKey: string;
  roleName: string;
  invitedByName: string | null;
  message: string | null;
  status: InvitationStatus;
  createdAt: string;
  expiresAt: string;
  decidedAt: string | null;
}

/** 초대를 만든 응답 — 링크(inviteUrl)는 이때 한 번만 온다(서버는 토큰의 해시만 갖는다) */
export interface InvitationCreated {
  invitation: Invitation;
  inviteUrl: string;
}

export interface AuditEntry {
  id: string;
  occurredAt: string;
  actorType: string;
  actorName: string | null;
  action: string;
  targetType: string | null;
  targetId: string | null;
  details: string;
}

export interface PublicSignupPolicy {
  enabled: boolean;
  allowedEmailDomains: string[];
  requireEmailVerification: boolean;
  requireAdminApproval: boolean;
}

export interface SignupPolicy extends PublicSignupPolicy {
  rateLimitPerIpHour: number;
  /** 시스템 관리자가 계정에 팀 워크스페이스 만들기를 허용할 때 기본으로 주는 수 */
  teamWorkspaceGrantDefault: number;
}

export interface AccountView {
  id: string;
  username: string;
  email: string;
  displayName: string;
  status: AccountStatus;
  origin: 'SIGNUP' | 'KEYCLOAK';
  platformRoles: string[];
  signedUpAt: string;
  activatedAt: string | null;
  lastLoginAt: string | null;
  /** 만들 수 있는 팀 워크스페이스 수 — 0 이면 못 만든다 */
  teamWorkspaceLimit: number;
  teamWorkspacesCreated: number;
}

export interface PermissionCatalog {
  permissions: { code: PermissionCode; ownerOnly: boolean }[];
  builtinRoles: { key: string; name: string; description: string; permissions: PermissionCode[] }[];
}
