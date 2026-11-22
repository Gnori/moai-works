import { http } from './http';
import type {
  AccountStatus,
  AccountView,
  AuditEntry,
  Discoverable,
  Invitation,
  InvitationCreated,
  IssueCreateBody,
  IssueDetail,
  IssueLinkType,
  IssueListParams,
  IssuePage,
  IssuePatch,
  IssueType,
  JoinPolicy,
  JoinRequest,
  Me,
  Member,
  PermissionCatalog,
  PermissionCode,
  ProjectDetail,
  ProjectRoleHolder,
  ProjectSummary,
  ProjectVisibility,
  PublicSignupPolicy,
  Role,
  SignupPolicy,
  WorkspaceDetail,
  WorkspaceVisibility,
} from './types';

/** 서버 호출 함수 — 화면은 이 함수만 쓰고 경로를 직접 적지 않는다. */

const ws = (slug: string) => `/v1/workspaces/${encodeURIComponent(slug)}`;
const byToken = (token: string) => `/v1/invitations/${encodeURIComponent(token)}`;

export interface RoleBody {
  name: string;
  description?: string;
  permissions: PermissionCode[];
}

export const authApi = {
  session: () => http.get<Me>('/auth/session').then((r) => r.data),
  signIn: (username: string, password: string) =>
    http.post<Me>('/auth/sign-in', { username, password }).then((r) => r.data),
  signOut: () => http.post('/auth/sign-out').then(() => undefined),
};

export const signupApi = {
  policy: () => http.get<PublicSignupPolicy>('/v1/signup/policy').then((r) => r.data),
  signUp: (body: { email: string; password: string; displayName: string; agreeTerms: boolean }) =>
    http.post<{ userId: string; status: AccountStatus }>('/v1/signup', body).then((r) => r.data),
  verify: (email: string, code: string) =>
    http.post<{ status: AccountStatus }>('/v1/signup/verify', { email, code }).then((r) => r.data),
  resend: (email: string) => http.post('/v1/signup/resend', { email }).then(() => undefined),
};

export const meApi = {
  get: () => http.get<Me>('/v1/me').then((r) => r.data),
  rename: (displayName: string) => http.patch<Me>('/v1/me', { displayName }).then((r) => r.data),
  rememberWorkspace: (slug: string) => http.put('/v1/me/last-workspace', { slug }).then(() => undefined),
  joinRequests: () => http.get<JoinRequest[]>('/v1/me/join-requests').then((r) => r.data),
  cancelJoinRequest: (id: string) => http.delete<JoinRequest>(`/v1/me/join-requests/${id}`).then((r) => r.data),
};

export const workspaceApi = {
  discover: (query: string) =>
    http.get<Discoverable[]>('/v1/workspaces', { params: { query } }).then((r) => r.data),
  /** 주소(slug)는 보내지 않는다 — 서버가 workspace-고유id 로 정해 돌려준다 */
  create: (body: {
    name: string;
    description?: string;
    visibility: WorkspaceVisibility;
    joinPolicy: JoinPolicy;
  }) => http.post<WorkspaceDetail>('/v1/workspaces', body).then((r) => r.data),
  detail: (slug: string) => http.get<WorkspaceDetail>(ws(slug)).then((r) => r.data),
  updateSettings: (
    slug: string,
    body: {
      name: string;
      description: string | null;
      visibility: WorkspaceVisibility;
      joinPolicy: JoinPolicy;
      allowedJoinDomains: string[];
      defaultRoleKey: string;
    },
  ) => http.patch<WorkspaceDetail>(ws(slug), body).then((r) => r.data),
  roles: (slug: string) => http.get<Role[]>(`${ws(slug)}/roles`).then((r) => r.data),
  /** 사용자 정의 역할 — 키(custom-…)는 서버가 정한다. 내가 가진 권한 안에서만 넣을 수 있다 */
  createRole: (slug: string, body: RoleBody) => http.post<Role>(`${ws(slug)}/roles`, body).then((r) => r.data),
  updateRole: (slug: string, key: string, body: RoleBody) =>
    http.patch<Role>(`${ws(slug)}/roles/${encodeURIComponent(key)}`, body).then((r) => r.data),
  deleteRole: (slug: string, key: string) =>
    http.delete(`${ws(slug)}/roles/${encodeURIComponent(key)}`).then(() => undefined),
  invite: (slug: string, body: { email: string; roleKey: string; message?: string }) =>
    http.post<InvitationCreated>(`${ws(slug)}/invitations`, body).then((r) => r.data),
  invitations: (slug: string) => http.get<Invitation[]>(`${ws(slug)}/invitations`).then((r) => r.data),
  revokeInvitation: (slug: string, id: string) =>
    http.delete<Invitation>(`${ws(slug)}/invitations/${id}`).then((r) => r.data),
  members: (slug: string) => http.get<Member[]>(`${ws(slug)}/members`).then((r) => r.data),
  setRoles: (slug: string, userId: string, roles: string[]) =>
    http.put<string[]>(`${ws(slug)}/members/${userId}/roles`, { roles }).then((r) => r.data),
  removeMember: (slug: string, userId: string) =>
    http.delete(`${ws(slug)}/members/${userId}`).then(() => undefined),
  leave: (slug: string) => http.post(`${ws(slug)}/leave`).then(() => undefined),
  apply: (slug: string, message: string) =>
    http.post<JoinRequest>(`${ws(slug)}/join-requests`, { message }).then((r) => r.data),
  pendingRequests: (slug: string) => http.get<JoinRequest[]>(`${ws(slug)}/join-requests`).then((r) => r.data),
  approve: (slug: string, id: string, roleKey: string, note?: string) =>
    http.post<JoinRequest>(`${ws(slug)}/join-requests/${id}/approve`, { roleKey, note }).then((r) => r.data),
  reject: (slug: string, id: string, note?: string) =>
    http.post<JoinRequest>(`${ws(slug)}/join-requests/${id}/reject`, { note }).then((r) => r.data),
  audit: (slug: string, limit = 100) =>
    http.get<AuditEntry[]>(`${ws(slug)}/audit`, { params: { limit } }).then((r) => r.data),
  catalog: () => http.get<PermissionCatalog>('/v1/permission-catalog').then((r) => r.data),
};

const project = (slug: string, key: string) => `${ws(slug)}/projects/${encodeURIComponent(key)}`;

export interface ProjectSettingsBody {
  name: string;
  description: string | null;
  visibility: ProjectVisibility;
}

/**
 * 프로젝트 — 주소 이름(key)으로 가리킨다. 독자가 아니면 서버가 404 로 숨긴다(있는지도 알리지 않는다).
 * moai-wiki 의 wikiApi 와 같은 모양이다.
 */
export const projectApi = {
  /** archived=true 면 보관한 프로젝트도 — 관리할 수 있는 사람에게만 실제로 온다 */
  list: (slug: string, archived = false) =>
    http.get<ProjectSummary[]>(`${ws(slug)}/projects`, { params: { archived } }).then((r) => r.data),
  create: (slug: string, body: { key: string; name: string; description?: string; visibility: ProjectVisibility }) =>
    http.post<ProjectDetail>(`${ws(slug)}/projects`, body).then((r) => r.data),
  detail: (slug: string, key: string) => http.get<ProjectDetail>(project(slug, key)).then((r) => r.data),
  update: (slug: string, key: string, body: ProjectSettingsBody) =>
    http.patch<ProjectDetail>(project(slug, key), body).then((r) => r.data),
  archive: (slug: string, key: string) => http.post<ProjectDetail>(`${project(slug, key)}/archive`).then((r) => r.data),
  restore: (slug: string, key: string) => http.post<ProjectDetail>(`${project(slug, key)}/restore`).then((r) => r.data),
  /** 보관한 프로젝트만 지울 수 있다. confirm 에 주소 이름을 다시 보낸다(이중 확인) */
  remove: (slug: string, key: string) =>
    http.delete(project(slug, key), { params: { confirm: key } }).then(() => undefined),
  /** 프로젝트 역할을 받은 사람 (이름순) */
  roles: (slug: string, key: string) => http.get<ProjectRoleHolder[]>(`${project(slug, key)}/roles`).then((r) => r.data),
  /** 한 사람의 프로젝트 역할을 이 목록으로 바꾼다 — 빈 목록이면 모두 뺀다 */
  setRoles: (slug: string, key: string, userId: string, roles: string[]) =>
    http
      .put<{ userId: string; roles: string[] }>(`${project(slug, key)}/roles/${encodeURIComponent(userId)}`, { roles })
      .then((r) => r.data),
};

const issue = (slug: string, key: string, number: number) => `${project(slug, key)}/issues/${number}`;

/**
 * 일감 — 프로젝트 안 번호로 가리킨다. 고치기는 바꾼 필드만 rowVersion 과 함께 보낸다(다르면 409 CONCURRENT_UPDATE).
 * 응답은 모두 일감 상세(하위 · 연결 · 이력 포함)라 받은 것을 그대로 화면에 쓴다.
 */
export const issueApi = {
  types: (slug: string, key: string) => http.get<IssueType[]>(`${project(slug, key)}/issue-types`).then((r) => r.data),
  /** status · type 은 같은 이름을 여러 번 보낸다(status=NEW&status=DONE) — 서버가 그렇게 받는다 */
  list: (slug: string, key: string, params: IssueListParams = {}) =>
    http
      .get<IssuePage>(`${project(slug, key)}/issues`, { params, paramsSerializer: { indexes: null } })
      .then((r) => r.data),
  create: (slug: string, key: string, body: IssueCreateBody) =>
    http.post<IssueDetail>(`${project(slug, key)}/issues`, body).then((r) => r.data),
  detail: (slug: string, key: string, number: number) => http.get<IssueDetail>(issue(slug, key, number)).then((r) => r.data),
  update: (slug: string, key: string, number: number, patch: IssuePatch) =>
    http.patch<IssueDetail>(issue(slug, key, number), patch).then((r) => r.data),
  /** 하위 일감이 있으면 서버가 거절한다(409 ISSUE_HAS_CHILDREN) */
  remove: (slug: string, key: string, number: number) => http.delete(issue(slug, key, number)).then(() => undefined),
  comment: (slug: string, key: string, number: number, notes: string) =>
    http.post<IssueDetail>(`${issue(slug, key, number)}/comments`, { notes }).then((r) => r.data),
  link: (slug: string, key: string, number: number, target: number, type: IssueLinkType) =>
    http.post<IssueDetail>(`${issue(slug, key, number)}/links`, { target, type }).then((r) => r.data),
  unlink: (slug: string, key: string, number: number, linkId: string) =>
    http.delete<IssueDetail>(`${issue(slug, key, number)}/links/${encodeURIComponent(linkId)}`).then((r) => r.data),
};

export const invitationApi = {
  preview: (token: string) => http.get<Invitation>(byToken(token)).then((r) => r.data),
  acceptByToken: (token: string) => http.post<Invitation>(`${byToken(token)}/accept`).then((r) => r.data),
  declineByToken: (token: string) => http.post<Invitation>(`${byToken(token)}/decline`).then((r) => r.data),
  mine: () => http.get<Invitation[]>('/v1/me/invitations').then((r) => r.data),
  accept: (id: string) => http.post<Invitation>(`/v1/me/invitations/${id}/accept`).then((r) => r.data),
  decline: (id: string) => http.post<Invitation>(`/v1/me/invitations/${id}/decline`).then((r) => r.data),
};

export const adminApi = {
  signupPolicy: () => http.get<SignupPolicy>('/v1/admin/settings/signup').then((r) => r.data),
  updateSignupPolicy: (policy: SignupPolicy) =>
    http.put<SignupPolicy>('/v1/admin/settings/signup', policy).then((r) => r.data),
  accounts: (status?: AccountStatus) =>
    http.get<AccountView[]>('/v1/admin/accounts', { params: { status, limit: 500 } }).then((r) => r.data),
  accountAction: (id: string, action: 'approve' | 'reject' | 'suspend' | 'resume') =>
    http.post<AccountView>(`/v1/admin/accounts/${id}/${action}`).then((r) => r.data),
  /** 그 계정이 만들 수 있는 팀 워크스페이스 수 — 0 이면 못 만든다 */
  setTeamWorkspaceLimit: (id: string, limit: number) =>
    http.put<AccountView>(`/v1/admin/accounts/${id}/team-workspace-limit`, { limit }).then((r) => r.data),
};
