import { API_BASE, ApiError, http } from './http';
import type {
  AccountStatus,
  AccountView,
  AdminWorkspace,
  AttachmentView,
  AuditEntry,
  AuditQuery,
  Discoverable,
  EffortQuery,
  EffortReport,
  Invitation,
  InvitationCreated,
  IssueCreateBody,
  IssueDetail,
  IssueLinkType,
  IssueListParams,
  IssuePage,
  IssuePatch,
  IssueRevision,
  IssueRevisionDiff,
  IssueRevisionList,
  IssueTemplate,
  IssueType,
  IssueWorkLogBody,
  JoinPolicy,
  JoinRequest,
  McpKey,
  McpKeyIssued,
  McpKeyStatusFilter,
  Me,
  Member,
  NotificationInbox,
  NotificationPreference,
  PermissionCatalog,
  PermissionCode,
  ProjectDetail,
  ProjectRoleHolder,
  ProjectSummary,
  ProjectVisibility,
  PublicSignupPolicy,
  Role,
  SignupPolicy,
  WorkspaceDeletionPreview,
  WorkspaceDetail,
  WorkspaceKind,
  WorkspaceStatus,
  WorkspaceVisibility,
} from './types';

/** 서버 호출 함수 — 화면은 이 함수만 쓰고 경로를 직접 적지 않는다. */

const ws = (slug: string) => `/v1/workspaces/${encodeURIComponent(slug)}`;
const byToken = (token: string) => `/v1/invitations/${encodeURIComponent(token)}`;
const adminWs = (slug: string) => `/v1/admin/workspaces/${encodeURIComponent(slug)}`;

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

/**
 * 내 알림함 — 내 것만 닿는다(남의 알림 id 는 404). 시스템 관리자 계정은 403 이라 화면이 알림을 그리지 않는다.
 * moai-wiki 의 notificationApi 와 같은 모양에 받기 설정을 더했다.
 */
export const notificationApi = {
  /** 새 것부터 한 쪽. before 는 앞 쪽의 next 를 그대로(시각|id) */
  inbox: (params: { unread?: boolean; before?: string; limit?: number } = {}) =>
    http.get<NotificationInbox>('/v1/me/notifications', { params }).then((r) => r.data),
  unreadCount: () => http.get<{ unread: number }>('/v1/me/notifications/unread-count').then((r) => r.data.unread),
  /** 하나 읽음 — 응답은 남은 읽지 않은 수 */
  read: (id: string) =>
    http.post<{ unread: number }>(`/v1/me/notifications/${encodeURIComponent(id)}/read`).then((r) => r.data.unread),
  /** 모두 읽음 — 응답은 바꾼 수 */
  readAll: () => http.post<{ read: number }>('/v1/me/notifications/read-all').then((r) => r.data.read),
  preferences: () => http.get<NotificationPreference[]>('/v1/me/notifications/preferences').then((r) => r.data),
  /**
   * 종류 하나의 알림함(enabled) · 메일(email) 켬 · 끔 — 준 쪽만 바뀐다. 응답은 모든 종류.
   * 꼭 받아야 하는 종류의 알림함을 끄면 400 NOTIFICATION_MANDATORY
   */
  setPreference: (kind: string, change: { enabled?: boolean; email?: boolean }) =>
    http
      .put<NotificationPreference[]>(`/v1/me/notifications/preferences/${encodeURIComponent(kind)}`, change)
      .then((r) => r.data),
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
  /** 감사 기록 — 새 것부터. 비운 조건은 보내지 않는다(axios 가 undefined 를 뺀다) */
  audit: (slug: string, query: AuditQuery = {}) =>
    http.get<AuditEntry[]>(`${ws(slug)}/audit`, { params: query }).then((r) => r.data),
  catalog: () => http.get<PermissionCatalog>('/v1/permission-catalog').then((r) => r.data),
};

/**
 * 파일로 받는 요청(responseType blob)의 오류 — 오류 본문(problem+json)도 Blob 으로 와서 code · 문장이 비어 있다. 읽어서 다시 만든다.
 */
async function problemFromBlob(error: unknown): Promise<unknown> {
  if (!(error instanceof ApiError) || !((error.details as unknown) instanceof Blob)) {
    return error;
  }
  try {
    const body = JSON.parse(await (error.details as unknown as Blob).text()) as { code?: string; detail?: string };
    return new ApiError(error.status, body.code ?? error.code, body.detail ?? error.message);
  } catch {
    return error;
  }
}

/**
 * 공수 집계 — 예상 공수 대비 실제 투입 공수(작업 시간 기록). 워크스페이스 멤버면 부를 수 있고, 어느 프로젝트의 기록이 들어가는지는
 * 서버가 프로젝트 판정으로 정한다(읽을 수 없는 프로젝트를 조건으로 주면 404 PROJECT_NOT_FOUND). project · person · type 은 같은 이름을 여러 번.
 */
export const effortApi = {
  report: (slug: string, query: EffortQuery) =>
    http.get<EffortReport>(`${ws(slug)}/effort`, { params: query, paramsSerializer: { indexes: null } }).then((r) => r.data),
  /** 같은 집계의 CSV — 내려받으면 서버가 감사에 남긴다. lang 은 머리 줄의 언어 */
  csv: (slug: string, query: EffortQuery, lang: string) =>
    http
      .get<Blob>(`${ws(slug)}/effort.csv`, {
        params: { ...query, lang },
        paramsSerializer: { indexes: null },
        responseType: 'blob',
      })
      .then((r) => r.data)
      .catch(async (e: unknown) => {
        throw await problemFromBlob(e);
      }),
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
  /** 댓글 고치기 — 쓴 사람만(지금 issue.comment 도). rowVersion 은 그 댓글(이력 줄)의 것 — 다르면 409 CONCURRENT_UPDATE, 지운 댓글은 409 COMMENT_DELETED */
  editComment: (slug: string, key: string, number: number, commentId: string, notes: string, rowVersion: number) =>
    http
      .patch<IssueDetail>(`${issue(slug, key, number)}/comments/${encodeURIComponent(commentId)}`, { notes, rowVersion })
      .then((r) => r.data),
  /** 댓글 지우기 — 쓴 사람, 또는 project.manage. 지움 표시만 하고 "지운 댓글"로 제자리에 남는다 */
  deleteComment: (slug: string, key: string, number: number, commentId: string) =>
    http.delete<IssueDetail>(`${issue(slug, key, number)}/comments/${encodeURIComponent(commentId)}`).then((r) => r.data),
  link: (slug: string, key: string, number: number, target: number, type: IssueLinkType) =>
    http.post<IssueDetail>(`${issue(slug, key, number)}/links`, { target, type }).then((r) => r.data),
  unlink: (slug: string, key: string, number: number, linkId: string) =>
    http.delete<IssueDetail>(`${issue(slug, key, number)}/links/${encodeURIComponent(linkId)}`).then((r) => r.data),
  /** 판 목록 — 새 판부터. before 는 앞 쪽 응답의 next */
  revisions: (slug: string, key: string, number: number, before?: number) =>
    http.get<IssueRevisionList>(`${issue(slug, key, number)}/revisions`, { params: { before } }).then((r) => r.data),
  /** 한 판의 일감 전체. 없는 판이면 404 REVISION_NOT_FOUND */
  revision: (slug: string, key: string, number: number, no: number) =>
    http.get<IssueRevision>(`${issue(slug, key, number)}/revisions/${no}`).then((r) => r.data),
  /** 두 판의 차이 — from 을 비우면 바로 앞 판과. 같은 판끼리는 400 SAME_REVISION */
  diff: (slug: string, key: string, number: number, no: number, from?: number) =>
    http.get<IssueRevisionDiff>(`${issue(slug, key, number)}/revisions/${no}/diff`, { params: { from } }).then((r) => r.data),
  /** 작업 시간 적기 — 일감을 고칠 수 있으면 담당자가 아니어도 자기 시간을 적는다 */
  logWork: (slug: string, key: string, number: number, body: IssueWorkLogBody) =>
    http.post<IssueDetail>(`${issue(slug, key, number)}/work-logs`, body).then((r) => r.data),
  /** 작업 시간 기록 지우기 — 자기 기록, 또는 project.manage 가 있으면 남의 기록도 */
  removeWork: (slug: string, key: string, number: number, logId: string) =>
    http.delete<IssueDetail>(`${issue(slug, key, number)}/work-logs/${encodeURIComponent(logId)}`).then((r) => r.data),
};

const attachments = (slug: string, key: string, number: number) => `${issue(slug, key, number)}/attachments`;

/**
 * 일감 첨부파일 — 목록 · 받기는 독자, 올리기는 댓글을 달 수 있는 사람(issue.comment), 지우기는 올린 사람 또는 project.manage(아니면 403).
 * 보관한 프로젝트에서는 올리기 · 지우기를 409 PROJECT_NOT_WRITABLE 로 막는다. 올리고 지운 것은 일감 이력에 남는다(판은 생기지 않는다).
 */
/**
 * 유형별 일감 템플릿 — 워크스페이스 기본(읽기 workspace.read · 고치기 workspace.settings.manage)과
 * 프로젝트 덮어쓰기(읽기 issue.read · 고치기 project.manage). 지우면(되돌리기) 한 단계 위로 돌아가고, 그 결과를 돌려준다.
 * 이미 정한 것을 고칠 때는 읽은 rowVersion 을 보낸다(다르면 409 CONCURRENT_UPDATE).
 */
export const issueTemplateApi = {
  workspace: (slug: string) => http.get<IssueTemplate[]>(`${ws(slug)}/issue-templates`).then((r) => r.data),
  setWorkspace: (slug: string, type: string, body: string, rowVersion: number | null) =>
    http.put<IssueTemplate>(`${ws(slug)}/issue-templates/${encodeURIComponent(type)}`, { body, rowVersion }).then((r) => r.data),
  resetWorkspace: (slug: string, type: string) =>
    http.delete<IssueTemplate>(`${ws(slug)}/issue-templates/${encodeURIComponent(type)}`).then((r) => r.data),
  project: (slug: string, key: string) => http.get<IssueTemplate[]>(`${project(slug, key)}/issue-templates`).then((r) => r.data),
  setProject: (slug: string, key: string, type: string, body: string, rowVersion: number | null) =>
    http.put<IssueTemplate>(`${project(slug, key)}/issue-templates/${encodeURIComponent(type)}`, { body, rowVersion }).then((r) => r.data),
  resetProject: (slug: string, key: string, type: string) =>
    http.delete<IssueTemplate>(`${project(slug, key)}/issue-templates/${encodeURIComponent(type)}`).then((r) => r.data),
};

export const attachmentApi = {
  /** 오래된 것부터, 지운 것은 빼고 */
  list: (slug: string, key: string, number: number) =>
    http.get<AttachmentView[]>(attachments(slug, key, number)).then((r) => r.data),
  /**
   * 파일 하나를 multipart 의 file 로 올린다 — 413 FILE_TOO_LARGE(파일 하나 상한) · WORKSPACE_STORAGE_FULL, 400 EMPTY_FILE.
   * 큰 파일은 30초를 넘길 수 있어 시간 제한을 두지 않는다. onProgress 는 보낸 몫(0 ~ 1).
   */
  upload: (
    slug: string,
    key: string,
    number: number,
    file: File,
    options: { onProgress?: (fraction: number) => void; signal?: AbortSignal } = {},
  ) => {
    const body = new FormData();
    body.append('file', file);
    return http
      .post<AttachmentView>(attachments(slug, key, number), body, {
        timeout: 0,
        signal: options.signal,
        onUploadProgress: (event) => {
          if (options.onProgress && event.total) {
            options.onProgress(Math.min(1, event.loaded / event.total));
          }
        },
      })
      .then((r) => r.data);
  },
  /** 지움 표시만 한다 — 본문에 넣은 표시는 남지만 더는 열리지 않는다(404) */
  remove: (slug: string, key: string, number: number, id: string) =>
    http.delete(`${attachments(slug, key, number)}/${encodeURIComponent(id)}`).then(() => undefined),
  /**
   * 내용 주소 — <img src> · 내려받기 링크에 그대로 쓴다(같은 출처라 세션 쿠키가 함께 간다).
   * 이미지는 inline, 그 밖은 attachment(application/octet-stream)로 온다.
   */
  contentUrl: (slug: string, key: string, number: number, id: string) =>
    `${API_BASE}${attachments(slug, key, number)}/${encodeURIComponent(id)}/content`,
  /**
   * 작은 미리보기 그림 주소(긴 변 320px PNG, 처음 받을 때 서버가 만든다) — 첨부 목록의 작은 그림에 쓴다.
   * 판정 · 머리글은 내용 주소와 같고, 미리보기가 없는 그림이면 404 다(AttachmentView.preview 가 true 일 때만 쓴다).
   */
  previewUrl: (slug: string, key: string, number: number, id: string) =>
    `${API_BASE}${attachments(slug, key, number)}/${encodeURIComponent(id)}/preview`,
};

/**
 * MCP 키 — 키는 사용자의 것이다(ADR-029). 받기 · 내 키 · 만료시키기는 /v1/me/mcp-keys(워크스페이스를 고르지 않는다),
 * 워크스페이스 관리 화면(mcp.key.manage)은 그 워크스페이스 멤버들의 키를 보기만 한다.
 */
export const mcpKeyApi = {
  /** 이 워크스페이스 멤버들의 키 — mcp.key.manage 가 없으면 403 */
  list: (slug: string, params: { status?: McpKeyStatusFilter; owner?: string } = {}) =>
    http.get<McpKey[]>(`${ws(slug)}/mcp-keys`, { params }).then((r) => r.data),
  /**
   * 원문(key)은 이 응답에만 온다 — 받은 쪽은 화면 상태에만 두고 쿼리 캐시 · 저장소에 넣지 않는다.
   * MCP 로 일할 수 있는 워크스페이스가 하나도 없으면 403 MCP_KEY_ISSUE_NOT_ALLOWED
   */
  issue: (body: { name: string; days?: number }) => http.post<McpKeyIssued>('/v1/me/mcp-keys', body).then((r) => r.data),
  /** 내 키만 — 이미 만료됐으면 409 MCP_KEY_ALREADY_EXPIRED */
  expire: (id: string, note?: string) =>
    http.post<McpKey>(`/v1/me/mcp-keys/${encodeURIComponent(id)}/expire`, { note }).then((r) => r.data),
  /** 내 키 — 시스템 관리자 계정은 403 */
  mine: (status?: McpKeyStatusFilter) =>
    http.get<McpKey[]>('/v1/me/mcp-keys', { params: { status } }).then((r) => r.data),
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
  /** 워크스페이스 목록 — 지운 것은 빼고 이름 순. 내용은 없다 */
  workspaces: (params: { kind?: WorkspaceKind; status?: WorkspaceStatus; query?: string } = {}) =>
    http.get<AdminWorkspace[]>('/v1/admin/workspaces', { params: { ...params, limit: 500 } }).then((r) => r.data),
  /** 팀 워크스페이스 사용 중지 — 이유는 시스템 관리 화면 · 감사에만 남는다. 개인 워크스페이스는 409 PERSONAL_WORKSPACE */
  suspendWorkspace: (slug: string, reason: string) =>
    http.post<AdminWorkspace>(`${adminWs(slug)}/suspend`, { reason }).then((r) => r.data),
  resumeWorkspace: (slug: string) => http.post<AdminWorkspace>(`${adminWs(slug)}/resume`).then((r) => r.data),
  workspaceDeletionPreview: (slug: string) =>
    http.get<WorkspaceDeletionPreview>(`${adminWs(slug)}/deletion-preview`).then((r) => r.data),
  /** 사용 중지한 워크스페이스만(아니면 409 WORKSPACE_NOT_SUSPENDED). confirm 은 주소 — api 가 다시 본다(틀리면 400 CONFIRM_MISMATCH) */
  deleteWorkspace: (slug: string, confirm: string) =>
    http.delete<void>(adminWs(slug), { params: { confirm } }).then(() => undefined),
};
