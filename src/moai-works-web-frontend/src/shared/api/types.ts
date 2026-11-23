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
  | 'project.create'
  | 'project.manage'
  | 'project.delete'
  | 'issue.read'
  | 'issue.create'
  | 'issue.edit'
  | 'issue.comment'
  | 'issue.manager.assign'
  | 'issue.delete'
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

/** 비밀번호 변경 결과 (POST /me/password — FR-WSP-006) */
export interface PasswordChangeResult {
  /** 다른 기기 · 브라우저의 로그인을 끊었나 */
  otherSessionsEnded: boolean;
  /** 이 브라우저의 로그인이 그대로인가 — false 면 이 로그인도 끝났다(다시 로그인) */
  currentSessionKept: boolean;
}

/** 비밀번호 정책 오류(PASSWORD_POLICY)의 까닭 — 서버가 Keycloak 정책 오류에서 고른다 */
export type PasswordPolicyReason =
  | 'MIN_LENGTH'
  | 'MAX_LENGTH'
  | 'DIGITS'
  | 'LOWER_CASE'
  | 'UPPER_CASE'
  | 'SPECIAL_CHARS'
  | 'NOT_USERNAME'
  | 'NOT_CONTAINS_USERNAME'
  | 'NOT_EMAIL'
  | 'PATTERN'
  | 'HISTORY'
  | 'BLACKLISTED'
  | 'OTHER';

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

export const builtinRoleKeys = ['owner', 'admin', 'curator', 'member'] as const;
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
  /** 한 사람의 사용자 id — 시스템이 한 일이면 null */
  actorId: string | null;
  actorName: string | null;
  /** 요청이 온 IP — 모르면 null */
  ip: string | null;
  action: string;
  targetType: string | null;
  targetId: string | null;
  /** 동작마다 다른 내용(JSON 글자). 화면은 글자로만 보이고 HTML 로 해석하지 않는다 */
  details: string;
}

/**
 * 감사 기록 조건 — 비운 값은 보내지 않는다. 시각은 ISO 순간(Instant).
 * person: 그 사람이 한 일 · 그 사람을 대상으로 한 일 · 그 사람의 MCP 키에 일어난 일.
 * action: 동작 이름 또는 묶음(mcp.key → mcp.key.issue · mcp.key.expire, 점 경계에서만 맞는다).
 * before: 이 시각보다 앞의 기록만(더 보기 — 앞 쪽 마지막 줄의 occurredAt).
 */
export interface AuditQuery {
  limit?: number;
  person?: string;
  action?: string;
  targetType?: string;
  from?: string;
  to?: string;
  /** 더 보기 — 앞 쪽 마지막 줄의 시각과 id(같은 밀리초의 줄을 건너뛰지 않게 함께) */
  before?: string;
  beforeId?: string;
}

// ── MCP 키 (사람의 키 — 워크스페이스마다 받는다. 지우지 않고 만료시킨다) ──────────────

export const mcpKeyStatuses = ['ACTIVE', 'EXPIRED'] as const;
/** 사용 중 · 만료(기간이 지났거나 일찍 만료시킴) */
export type McpKeyStatus = (typeof mcpKeyStatuses)[number];
/** 목록 조건 — ALL 은 둘 다 */
export type McpKeyStatusFilter = McpKeyStatus | 'ALL';

export const mcpKeyExpireReasons = ['MANUAL', 'MEMBER_LEFT', 'ENDPOINT_DELETED'] as const;
/** 일찍 만료된 까닭 — 직접 만료시킴 · 주인이 워크스페이스를 떠남 · 엔드포인트를 지움. 기간이 지나 만료되면 null */
export type McpKeyExpireReason = (typeof mcpKeyExpireReasons)[number];

/** 사람 한 명 (키 주인 · 만료시킨 사람) — 계정을 못 찾으면 이름이 null */
export interface McpKeyPerson {
  id: string;
  username: string | null;
  displayName: string | null;
}

/**
 * 키 한 줄 — 원문은 없다(발급 응답에만 한 번 온다). 키는 사용자의 것이라 워크스페이스가 없다 —
 * 주인이 멤버이고 역할에 MCP 권한이 있는 워크스페이스 모두에 닿는다(ADR-029).
 */
export interface McpKey {
  id: string;
  name: string;
  /** 알아보기용 앞부분 (mai-wks-Ab12) */
  keyPrefix: string;
  status: McpKeyStatus;
  owner: McpKeyPerson;
  createdAt: string;
  /** 만료 시각 — 일찍 만료시킨 키는 만료시킨 때 */
  expiresAt: string;
  lastUsedAt: string | null;
  /** 발급한 IP · 프로그램 — 주인에게만 온다(워크스페이스 관리 화면에서 남의 키면 null) */
  issuedIp: string | null;
  issuedUserAgent: string | null;
  expiredBy: McpKeyPerson | null;
  expireReason: McpKeyExpireReason | null;
  expireNote: string | null;
  /** 내 키인가 */
  mine: boolean;
  /** 이 키로 들어온 요청 수 = 접근 기록의 줄 수 — 주인에게만 온다(워크스페이스 관리 화면에서는 null) */
  callCount?: number | null;
}

// ── MCP 접근 기록 (키로 들어온 요청 하나 = 한 줄, 읽기까지 — FR-MCP-006 · 010) ──────────────

/** 작업 종류 — 도구 호출은 읽기 · 만들기 · 고치기 · 지우기 · 일감 연결, 그 밖에 도구 목록 · 접속(연결 시작 · 알림 · 세션 끝) */
export const mcpAccessOperations = ['READ', 'CREATE', 'UPDATE', 'DELETE', 'LINK', 'LIST', 'CONNECT'] as const;
export type McpAccessOperation = (typeof mcpAccessOperations)[number];

/** 결과 — 성공 · 거부(키 검사 거절 · 권한 없음) · 오류 */
export const mcpAccessOutcomes = ['OK', 'DENIED', 'ERROR'] as const;
export type McpAccessOutcome = (typeof mcpAccessOutcomes)[number];

/**
 * 접근 기록 한 줄 — 키 원문 · 본문은 없다. 글은 모두 바깥 클라이언트 · 서버가 준 값이라 화면은 글자로만 보인다.
 * workspaceSlug 가 null 이면 도구가 워크스페이스를 정하지 않은 줄(연결 · 도구 목록 · 거절 · 여러 워크스페이스에 걸친 목록) — 키 주인에게만 보인다.
 */
export interface McpAccessEntry {
  id: string;
  occurredAt: string;
  durationMs: number;
  keyId: string | null;
  keyPrefix: string | null;
  keyName: string | null;
  ownerId: string | null;
  ownerName: string | null;
  /** 들어온 입구 ISSUE · PROJECT · UNKNOWN(없는 입구) */
  entry: string;
  requestPath: string;
  workspaceSlug: string | null;
  workspaceName: string | null;
  /** initialize · tools/list · tools/call · notifications/… · ping · session/end · session/stream */
  mcpMethod: string;
  tool: string | null;
  operation: McpAccessOperation;
  projectKey: string | null;
  issueNumber: number | null;
  /** 연결 대상(#번호) · 첨부(attachment:id) · 템플릿 유형(type:…) */
  targetRef: string | null;
  /** 도구 인자 요약(JSON 글자) — 긴 글은 길이와 sha256 만 */
  arguments: string | null;
  outcome: McpAccessOutcome;
  httpStatus: number;
  errorCode: string | null;
  clientName: string | null;
  clientVersion: string | null;
  clientTitle: string | null;
  protocolVersion: string | null;
  clientCapabilities: string | null;
  sessionId: string | null;
  userAgent: string | null;
  ip: string | null;
  traceId: string | null;
}

/** 접근 기록 조건 — 비운 값은 보내지 않는다. 모두 함께(AND). 시각은 ISO 순간 */
export interface McpAccessQuery {
  limit?: number;
  key?: string;
  person?: string;
  tool?: string;
  operation?: string;
  outcome?: string;
  client?: string;
  project?: string;
  session?: string;
  ip?: string;
  from?: string;
  to?: string;
  /** 더 보기 — 앞 쪽 마지막 줄의 시각과 id */
  before?: string;
  beforeId?: string;
}

/** 발급 결과 — key 는 원문이고 이 응답에만 있다. 화면은 대화상자 상태에만 두고 어디에도 저장하지 않는다 */
export interface McpKeyIssued {
  view: McpKey;
  key: string;
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
  /** 시스템 관리자 계정에 남아 있는 워크스페이스 멤버십 수(보통 계정은 0) — 워크스페이스를 쓰던 계정에 역할을 준 경우 */
  leftoverMemberships: number;
}

/** 시스템 관리자가 보는 워크스페이스 한 줄 — 내용(설명 · 프로젝트 · 일감)은 없다 (api WorkspaceAdminService.WorkspaceView) */
export interface AdminWorkspace {
  id: string;
  slug: string;
  name: string;
  kind: WorkspaceKind;
  visibility: WorkspaceVisibility;
  status: WorkspaceStatus;
  /** 소유자 역할을 가진 활성 멤버 (먼저 들어온 순) */
  owners: { id: string; displayName: string; email: string }[];
  memberCount: number;
  createdAt: string;
  suspendedAt: string | null;
  /** 사용 중지한 이유 — 시스템 관리 화면 · 감사에만 보인다 */
  suspendedReason: string | null;
}

/** 워크스페이스 삭제의 영향 미리보기 — 수만 */
export interface WorkspaceDeletionPreview {
  slug: string;
  name: string;
  status: WorkspaceStatus;
  members: number;
  projects: number;
  issues: number;
  attachments: number;
  attachmentBytes: number;
}

export interface PermissionCatalog {
  permissions: { code: PermissionCode; ownerOnly: boolean }[];
  builtinRoles: { key: string; name: string; description: string; permissions: PermissionCode[] }[];
}

// ── 알림 (내 알림함 — 내 것만. moai-wiki 의 알림함과 같은 모양) ──────────────

/** 알림 한 줄 — 본문은 없다. 화면이 kind · params 로 문장과 링크를 만든다(features/notifications/describe.ts) */
export interface NotificationItem {
  id: string;
  /** 종류 코드 — issue.assigned · join.requested · mcp_key.expired … 화면보다 서버가 새 것일 수 있다 */
  kind: string;
  /** 무엇에 관한 것인가 — issue · project · mcp_key · join_request · workspace */
  subjectType: string;
  subjectId: string | null;
  /** 문장 · 링크를 만드는 값(프로젝트 key · 이름 · 일감 번호 · 제목 · 수). 종류마다 다르다 */
  params: Record<string, unknown>;
  /** 그 일을 한 사람 — 아침 알림처럼 시스템이 한 일이면 null */
  actorName: string | null;
  /** 묶인 수 — 읽지 않은 동안 같은 일이 몇 번 있었나 */
  count: number;
  workspaceSlug: string | null;
  workspaceName: string | null;
  createdAt: string;
  /** 마지막으로 묶인 때 — 알림함의 순서 */
  updatedAt: string;
  readAt: string | null;
}

export interface NotificationInbox {
  items: NotificationItem[];
  /** 읽지 않은 알림의 수(전체) */
  unread: number;
  /** 다음 쪽 커서("시각|id") — 그대로 before 로 넘긴다. 끝이면 null */
  next: string | null;
}

export const notificationGroups = ['WORKSPACE', 'PROJECT', 'ISSUE', 'WORK', 'SECURITY'] as const;
/** 알림 설정의 묶음 — 워크스페이스 · 프로젝트 · 일감 · 작업 시간 · 보안(MCP 키) */
export type NotificationGroup = (typeof notificationGroups)[number];

/** 알림 받기 설정 한 줄 — mandatory 면 알림함을 끌 수 없다(늘 받는다) */
export interface NotificationPreference {
  kind: string;
  group: string;
  /** 알림함에 쌓는가 */
  enabled: boolean;
  mandatory: boolean;
  /** 메일로도 받기로 했는가 — 고른 적이 없으면 서버의 기본값(아침 알림만 켬). 알림함을 끈 종류는 메일도 가지 않는다 */
  email: boolean;
}

// ── 프로젝트 (워크스페이스 안의 일감 묶음 — 읽기 권한의 경계. moai-wiki 의 위키 자리) ──────────────

/** WORKSPACE = 워크스페이스 멤버 모두가 독자, RESTRICTED = 지정 멤버만(소유자 · 관리자 포함) */
export type ProjectVisibility = 'WORKSPACE' | 'RESTRICTED';
export type ProjectStatus = 'ACTIVE' | 'ARCHIVED' | 'DELETED';

export interface ProjectSummary {
  id: string;
  /** 주소 이름 — 워크스페이스 안에서 하나, 바뀌지 않는다 */
  key: string;
  name: string;
  description: string | null;
  visibility: ProjectVisibility;
  status: ProjectStatus;
  updatedAt: string;
  /** 권한 코드만으로는 알 수 없는 것 — createIssue: 프로젝트가 열려 있고 일감을 만들 수 있다(일감 옮기기의 대상 고르기에 쓴다) */
  myActions: ProjectAction[];
}

export interface ProjectDetail extends ProjectSummary {
  createdAt: string;
  archivedAt: string | null;
  /** 지우지 않은 일감 수 */
  issueCount: number;
  /** 이 프로젝트에서 내가 받은 프로젝트 역할 key */
  myRoles: string[];
  /** 이 프로젝트에서 내가 가진 권한 — 화면은 이것으로 단추를 숨기기만 한다 */
  myPermissions: PermissionCode[];
}

export type ProjectAction = 'createIssue';

/** 프로젝트 역할 4개 — 고정이고 위가 아래를 모두 담는다(api ProjectRole 과 같다) */
export const projectRoleKeys = ['project-reader', 'project-editor', 'project-curator', 'project-admin'] as const;
export type ProjectRoleKey = (typeof projectRoleKeys)[number];

/**
 * 프로젝트 모듈 하나 (2026-11-23, 일감 #27) — 일감의 관련 모듈로 고른다. 지우지 않고 쓰지 않음(active=false)으로만 둔다.
 * key 는 만든 뒤 바꿀 수 없다(목록 거르기 · MCP 값).
 */
export interface ProjectModule {
  key: string;
  name: string;
  description: string | null;
  /** 목록 순서(0 부터) */
  position: number;
  /** 새 일감에서 고를 수 있는가 — false 는 쓰지 않는 모듈(이미 붙은 일감에는 남는다) */
  active: boolean;
  /** 붙은 일감 수(지운 일감 빼고) */
  issueCount: number;
  createdAt: string;
  updatedAt: string;
  rowVersion: number;
}

/** 모듈 고치기 — 준 것만. description 에 null · 빈 글이면 지운다 */
export interface ProjectModulePatch {
  name?: string;
  description?: string | null;
  active?: boolean;
  rowVersion?: number;
}

/** 프로젝트 역할을 받은 사람 한 명 */
export interface ProjectRoleHolder {
  userId: string;
  username: string;
  displayName: string | null;
  roles: string[];
  /** 워크스페이스 소유자 — 모든 프로젝트에 프로젝트 관리자로 늘 들어 있고 빼거나 바꿀 수 없다 */
  workspaceOwner: boolean;
}

// ── 일감 (워크스페이스 아래의 일 — 번호는 워크스페이스 안에서 하나, 프로젝트는 속성이다. Redmine 과 같다 — 2026-11-24) ──

/** 일감의 프로젝트 — 주소 이름 · 이름. 일감을 다른 프로젝트로 옮기면 바뀐다 */
export interface ProjectRef {
  key: string;
  name: string;
}

/** 일감 · 이력 · 리비전 · 첨부 · 작업 시간이 들어온 길 — 웹 · MCP, 그리고 서버가 스스로 한 일(SYSTEM — 번호 다시 매기기 같은 것) */
export type IssueVia = 'WEB' | 'MCP' | 'SYSTEM';

export const issueStatuses = ['NEW', 'IN_PROGRESS', 'TESTING', 'DONE', 'CLOSED'] as const;
/** 등록 · 진행 · 테스트 · 완료 · 종료 — 아무 상태로나 옮길 수 있다 (2026-11-22 사용자: "상태 : 등록, 진행, 테스트, 완료, 종료") */
export type IssueStatus = (typeof issueStatuses)[number];

export const issueLinkTypes = ['RELATES', 'BLOCKS', 'PRECEDES', 'DUPLICATES'] as const;
/** 관련 · 차단 · 선행 · 중복 */
export type IssueLinkType = (typeof issueLinkTypes)[number];

/** 일감의 날짜 다섯 — 완료 기한 · 시작일 · 완료 예정일 · 완료일 · 종료일 (yyyy-MM-dd) */
export const issueDateFields = ['dueDate', 'startDate', 'plannedEndDate', 'completedOn', 'closedOn'] as const;
export type IssueDateField = (typeof issueDateFields)[number];

/** 사람 한 명 (관리자 · 담당자 · 만든 사람) */
export interface IssuePerson {
  id: string;
  username: string | null;
  displayName: string | null;
}

export interface IssueType {
  key: string;
  name: string;
  description: string | null;
  /** 새 일감에서 고를 수 있는가 — false 는 더 쓰지 않는 옛 유형(요구사항 · 작업). 이미 그 유형인 일감에는 그대로 보인다 */
  active: boolean;
}

/** 유형별 일감 템플릿의 출처 — 처음 템플릿(코드) · 워크스페이스 기본 · 프로젝트 덮어쓰기 */
export type IssueTemplateSource = 'BUILTIN' | 'WORKSPACE' | 'PROJECT';

/** 유형별 일감 템플릿 — 새 일감의 설명에 채우는 틀(FR-ISS-020). 항목 = 코드 블록 밖의 제목, "(선택)" 이면 비워도 되는 항목 */
export interface IssueTemplate {
  type: string;
  typeName: string;
  body: string;
  source: IssueTemplateSource;
  /** 프로젝트에서 볼 때 위(워크스페이스 · 처음 템플릿)에서 물려받은 것인가 */
  inherited: boolean;
  sections: { title: string; optional: boolean }[];
  updatedBy: IssuePerson | null;
  updatedAt: string | null;
  /** 이 범위에서 정한 템플릿이면 그 판(고칠 때 보낸다). 물려받은 것 · 처음 템플릿이면 null */
  rowVersion: number | null;
}

/** 다른 일감을 가리킬 때 (상위 · 하위 · 연결). 연결 일감은 다른 프로젝트에 있을 수 있다 */
export interface IssueRef {
  number: number;
  title: string;
  status: IssueStatus;
  type: string;
  project: ProjectRef;
}

/** 목록 한 줄 */
export interface IssueSummary {
  /** 워크스페이스 안에서 하나인 번호 */
  number: number;
  project: ProjectRef;
  type: string;
  title: string;
  status: IssueStatus;
  manager: IssuePerson | null;
  assignee: IssuePerson | null;
  /** 상위 일감 번호 */
  parent: number | null;
  dueDate: string | null;
  startDate: string | null;
  plannedEndDate: string | null;
  completedOn: string | null;
  closedOn: string | null;
  updatedAt: string;
  /** 바로 아래 하위 일감 수 */
  childCount: number;
  /** 기한 지남 — 열린 일감이고 완료 기한이 오늘보다 앞(서버가 정한다) */
  overdue: boolean;
  /** 예상 공수(시간) — 처음 계획. 없으면 null */
  estimatedHours: number | null;
  /** 쓴 시간(시간) — 작업 시간 기록의 합. 기록이 없으면 0 */
  spentHours: number;
  /** 에이전트 세션 — 이 일을 맡은 AI 세션 이름. 없으면 null (2026-11-23, 일감 #26) */
  agentSession: string | null;
  /** 관련 모듈 — 모듈 순서 (2026-11-23, 일감 #27) */
  modules: IssueModuleRef[];
}

/** 일감에 붙은 모듈 하나 — active=false 는 쓰지 않게 된 모듈(이미 붙은 일감에는 남는다) */
export interface IssueModuleRef {
  key: string;
  name: string;
  active: boolean;
}

/** 워크스페이스 일감 목록의 모듈 거르기 칸 — 읽을 수 있는 프로젝트들의 모듈 key(같은 key 는 하나로) */
export interface IssueModuleOption {
  key: string;
  name: string;
}

/** 이 프로젝트의 일감에 쓰인 에이전트 세션 이름 하나와 그 세션이 맡은 일감 수(지운 일감 빼고) — 목록 거르기 고르기용 */
export interface AgentSessionCount {
  name: string;
  issueCount: number;
}

export interface IssuePage {
  items: IssueSummary[];
  total: number;
  page: number;
  size: number;
  /** 전체 쪽 수 · 다음 쪽이 있는가 (2026-11-22) */
  totalPages: number;
  hasNext: boolean;
}

/** 연결 하나 — 이 일감 쪽에서 본 모양. OUTGOING = 이 일감이 source, INCOMING = 이 일감이 target */
export interface IssueLink {
  id: string;
  type: IssueLinkType;
  direction: 'OUTGOING' | 'INCOMING';
  issue: IssueRef;
}

/**
 * 이력의 바뀐 필드 하나. 값은 필드마다 모양이 다르다 —
 * 사람(manager · assignee)은 {id, name}, 상위 일감(parent)은 번호, 연결(link)은 {type, direction, number}(예전 줄은 "BLOCKS #12"),
 * 설명(description)은 전 · 후 글, 예상 공수(estimatedHours)는 "8" · "2.5" 같은 글자,
 * 작업 시간(workLog)은 {id, user: {id, name}, date, hours, note?} — 넣었으면 before 가 null, 지웠으면 after 가 null,
 * 첨부(attachment)는 {id, name, size, image} — 올렸으면 before 가 null, 지웠으면 after 가 null (판은 생기지 않는다),
 * 프로젝트(project — 다른 프로젝트로 옮김)는 {key, name}, 번호(number — 워크스페이스 단위로 번호를 다시 매김, 서버가 한 번 남긴 줄)는
 * before {project, number} · after {number},
 * 나머지(type · title · status · 날짜)는 글자.
 */
export interface IssueChange {
  field: string;
  before: unknown;
  after: unknown;
}

/** 이력 한 줄 — 필드 변경 · 댓글(notes) · 둘 다 */
export interface IssueJournal {
  id: string;
  actorType: 'USER' | 'SERVICE';
  actorId: string | null;
  actorName: string;
  via: IssueVia;
  createdAt: string;
  notes: string | null;
  changes: IssueChange[];
  /** 이 줄이 만든 판의 번호 — 필드를 바꾼 줄(만들기 · 고치기)에만 있다. 댓글 · 연결 · 작업 시간 줄은 null */
  revisionNo: number | null;
  /** 댓글 글의 판 — 댓글이 있는 줄(지운 댓글 포함)에만 있다. 고칠 때 보낸다(다르면 409 CONCURRENT_UPDATE) */
  rowVersion: number | null;
  /** 댓글을 마지막으로 고친 때(밀리초) — 고친 적이 없으면 null */
  editedAt: string | null;
  /** 지운 댓글 — notes 는 null 이고 "지운 댓글"로 제자리에 남는다 */
  deleted: boolean;
  deletedAt: string | null;
  /** 지운 사람 — 쓴 사람이 아니면 project.manage 가 있는 사람 */
  deletedBy: IssuePerson | null;
}

/** 작업 시간 기록 한 줄 — 누가 · 어느 날 · 몇 시간 · 무엇을 · 어느 길로. 한 일감에 여러 건(일하면서 그때그때) */
export interface IssueWorkLog {
  id: string;
  /** 일한 사람 = 적은 사람(남의 시간을 대신 적지 않는다) */
  user: IssuePerson | null;
  /** 일한 날 (yyyy-MM-dd) */
  workDate: string;
  hours: number;
  note: string | null;
  via: IssueVia;
  /** 적은 때 */
  createdAt: string;
}

/** 작업 시간 적기 — 일한 날은 오늘까지, 시간은 0 보다 크고 24 이하 */
export interface IssueWorkLogBody {
  workDate: string;
  hours: number;
  note?: string;
}

export interface IssueDetail {
  /** 워크스페이스 안에서 하나인 번호 — 다른 프로젝트로 옮겨도 그대로다 */
  number: number;
  /** 지금 프로젝트 — 권한 경계. 옮기면 바뀐다 */
  project: ProjectRef;
  type: string;
  title: string;
  description: string;
  status: IssueStatus;
  manager: IssuePerson | null;
  assignee: IssuePerson | null;
  parent: IssueRef | null;
  dueDate: string | null;
  startDate: string | null;
  plannedEndDate: string | null;
  completedOn: string | null;
  closedOn: string | null;
  createdBy: IssuePerson | null;
  createdAt: string;
  updatedAt: string;
  /** 고칠 때 그대로 돌려보낸다 — 다르면 서버가 409 CONCURRENT_UPDATE */
  rowVersion: number;
  /** 기한 지남 — 열린 일감이고 완료 기한이 오늘보다 앞(서버가 정한다) */
  overdue: boolean;
  /** 지금 판의 번호 — 판은 1부터 빠짐없이 쌓인다 */
  revisionNo: number;
  /** 예상 공수(시간). 없으면 null */
  estimatedHours: number | null;
  /** 쓴 시간(시간) — 작업 시간 기록의 합 */
  spentHours: number;
  /** 에이전트 세션 — 이 일을 맡은 AI 세션 이름. 없으면 null */
  agentSession: string | null;
  /** 관련 모듈 — 모듈 순서 */
  modules: IssueModuleRef[];
  children: IssueRef[];
  links: IssueLink[];
  journal: IssueJournal[];
  /** 작업 시간 기록 — 일한 날이 늦은 것부터 */
  workLogs: IssueWorkLog[];
}

// ── 일감 첨부파일 (본문 · 댓글 중간에 넣는 이미지, 내려받는 파일 — 2026-11-22) ──

/**
 * 첨부 하나 (api AttachmentService.AttachmentView). 지운 첨부는 목록에 오지 않는다.
 * 이미지(png · jpeg · gif · webp)인지는 서버가 이름이 아니라 내용(첫 바이트)으로 정한다 — 이미지만 그 자리에서 보이고, 그 밖은 늘 내려받기로 온다.
 */
export interface AttachmentView {
  id: string;
  fileName: string;
  contentType: string;
  /** 바이트 */
  size: number;
  image: boolean;
  sha256: string;
  /** 올린 사람 — 계정을 찾지 못하면 id 만 있고 이름이 비어 온다 */
  uploadedBy: IssuePerson;
  via: IssueVia;
  createdAt: string;
  /** 본문 · 댓글에 넣을 표시 — 이미지는 ![이름](attachment:<id>), 그 밖은 [이름](attachment:<id>) */
  markdown: string;
  /**
   * 작은 미리보기 그림(긴 변 320px PNG — attachmentApi.previewUrl)을 받을 수 있다 — 서버가 내용으로 알아낸 png · jpeg · gif.
   * 만들 수 없는 그림(깨짐 · 너무 큼)이면 그 주소가 404 라 파일 표시로 바꾼다. WebP 는 false 라 원본을 쓴다.
   */
  preview: boolean;
}

// ── 일감 리비전 (필드를 바꿀 때마다 생기는 판 — moai-wiki 의 페이지 리비전 자리) ──

/** 판 목록 한 줄 — 누가 · 어느 길로 · 언제 · 무엇을 바꿨나 */
export interface IssueRevisionItem {
  no: number;
  actorType: 'USER' | 'SERVICE';
  actorId: string | null;
  actorName: string;
  via: IssueVia;
  createdAt: string;
  /** 이 판에서 바뀐 필드 이름 (1판은 처음 넣은 필드들) */
  changedFields: string[];
  /** 지금 판인가 */
  current: boolean;
}

export interface IssueRevisionList {
  /** 새 판부터 */
  items: IssueRevisionItem[];
  /** 다음 쪽을 부를 때 before 에 넣는 판 번호. 마지막 쪽이면 null */
  next: number | null;
}

/** 한 판의 일감 전체 */
export interface IssueRevision {
  no: number;
  actorType: 'USER' | 'SERVICE';
  actorId: string | null;
  actorName: string;
  via: IssueVia;
  createdAt: string;
  type: string;
  title: string;
  description: string;
  status: IssueStatus;
  manager: IssuePerson | null;
  assignee: IssuePerson | null;
  /** 상위 일감 번호 */
  parent: number | null;
  dueDate: string | null;
  startDate: string | null;
  plannedEndDate: string | null;
  completedOn: string | null;
  closedOn: string | null;
  estimatedHours: number | null;
  agentSession: string | null;
  /** 그때의 관련 모듈(그때의 이름) */
  modules: { key: string; name: string }[];
  /** 그때의 프로젝트 — 옮기기 전의 판이면 옮기기 전 프로젝트 */
  project: ProjectRef | null;
  current: boolean;
}

/** 설명의 줄 단위 차이 한 줄 (git 처럼) */
export interface IssueDiffLine {
  op: 'SAME' | 'ADD' | 'DEL';
  text: string;
  /** 옛 판에서의 줄 번호. 넣은 줄이면 null */
  oldLine: number | null;
  /** 새 판에서의 줄 번호. 지운 줄이면 null */
  newLine: number | null;
}

/** 두 판의 차이 — 필드는 전 · 후(이력과 같은 값 모양), 설명은 줄 단위 */
export interface IssueRevisionDiff {
  /** 옛 쪽 판 번호 — 1판을 "없음"과 견주면 null */
  from: number | null;
  to: number;
  fromInfo: IssueRevisionItem | null;
  toInfo: IssueRevisionItem;
  /** 바뀐 필드만 (설명은 descriptionLines 로) */
  fields: IssueChange[];
  descriptionLines: IssueDiffLine[];
  added: number;
  removed: number;
  /** 바뀐 곳이 너무 커서 줄을 하나하나 맞추지 못했다(통째로 지우고 넣은 것으로 나온다) */
  coarse: boolean;
}

export type IssueSortKey = 'number' | 'updated' | 'due' | 'start' | 'planned';
/** 앞에 - 를 붙이면 내림차순 */
export type IssueSort = IssueSortKey | `-${IssueSortKey}`;

/** 목록 조건 — status · type 은 여럿, assignee 는 사용자 id 또는 none, parent 는 번호 또는 top, agentSession 은 세션 이름 또는 none */
export interface IssueListParams {
  /** 워크스페이스 목록에서만 — 프로젝트 key(여럿). 읽을 수 없는 프로젝트는 서버가 뺀다 */
  project?: string[];
  status?: IssueStatus[];
  type?: string[];
  manager?: string;
  assignee?: string;
  parent?: string;
  agentSession?: string;
  /** 모듈 key — 여럿이면 그 가운데 하나라도 붙은 일감 */
  module?: string[];
  q?: string;
  sort?: IssueSort;
  page?: number;
  size?: number;
}

/** 일감 만들기 — 비운 값은 서버 기본값(상태 NEW · 관리자 = 만든 사람) */
export interface IssueCreateBody {
  type: string;
  title: string;
  description?: string;
  status?: IssueStatus;
  managerId?: string;
  assigneeId?: string;
  parent?: number;
  dueDate?: string;
  startDate?: string;
  plannedEndDate?: string;
  completedOn?: string;
  closedOn?: string;
  /** 예상 공수(시간, 0 ~ 10000) */
  estimatedHours?: number;
  /** 에이전트 세션 — 이 일을 맡은 AI 세션 이름(100자까지) */
  agentSession?: string;
  /** 관련 모듈 key — 쓰는 모듈만 */
  modules?: string[];
}

/**
 * 일감 고치기 — 바꾼 필드만 넣는다(null 이면 비운다). notes 를 넣으면 같은 이력 줄에 댓글로 남는다.
 * project 를 넣으면 그 프로젝트로 옮긴다(하위 일감도 함께. 이 프로젝트의 issue.edit 와 옮길 프로젝트의 issue.create 가 있어야 한다)
 */
export interface IssuePatch {
  rowVersion: number;
  /** 옮길 프로젝트 key */
  project?: string;
  type?: string;
  title?: string;
  description?: string | null;
  status?: IssueStatus;
  managerId?: string | null;
  assigneeId?: string | null;
  parent?: number | null;
  dueDate?: string | null;
  startDate?: string | null;
  plannedEndDate?: string | null;
  completedOn?: string | null;
  closedOn?: string | null;
  estimatedHours?: number | null;
  agentSession?: string | null;
  /** 관련 모듈 key — 통째로 바꾼다(빈 목록 · null 이면 모두 뺀다) */
  modules?: string[] | null;
  notes?: string;
}

// ── 공수 집계 (예상 공수 대비 실제 투입 공수 — FR-ISS-017) ──────────────

/** 모으는 기준 — 사람 · 프로젝트 · 일감 · 유형 · 날 · 주(월요일부터) · 달 */
export const effortGroupings = ['person', 'project', 'issue', 'type', 'day', 'week', 'month'] as const;
export type EffortGroupBy = (typeof effortGroupings)[number];

/** 공수 집계 조건 — 비운 것은 보내지 않는다. 날짜는 yyyy-MM-dd(포함), project · person · type 은 여러 번 */
export interface EffortQuery {
  from?: string;
  to?: string;
  project?: string[];
  person?: string[];
  type?: string[];
  groupBy: EffortGroupBy;
}

/**
 * 한 줄 — 모은 기준에 맞는 칸만 차 있다(project · issue · person · type · period).
 * 예상은 일감 · 프로젝트 · 유형으로 모을 때만(사람 · 기간이면 null). 차이 = 실제 − 예상, ratio = 실제 ÷ 예상 × 100(반올림).
 */
export interface EffortRow {
  key: string;
  label: string;
  project: { key: string; name: string } | null;
  issue: { project: string; number: number; title: string; type: string } | null;
  person: IssuePerson | null;
  type: string | null;
  /** 기간의 첫날 · 끝날 (yyyy-MM-dd) */
  period: string | null;
  periodEnd: string | null;
  estimatedHours: number | null;
  actualHours: number;
  diffHours: number | null;
  ratio: number | null;
  issueCount: number;
  logCount: number;
}

export interface EffortTotals {
  estimatedHours: number | null;
  actualHours: number;
  diffHours: number | null;
  ratio: number | null;
  issueCount: number;
  logCount: number;
  personCount: number;
}

/**
 * 공수 집계 결과 — 부른 사람이 읽을 수 있는 프로젝트의 기록만 들어 있다(서버가 판정).
 * removedLogs 는 조건에 맞지만 지워서 뺀 기록 수, projects · types · people 은 조건 고르기용(people 은 사람 조건을 뺀 범위).
 */
export interface EffortReport {
  from: string | null;
  to: string | null;
  groupBy: EffortGroupBy;
  rows: EffortRow[];
  total: EffortTotals;
  removedLogs: number;
  projects: { key: string; name: string }[];
  types: { key: string; name: string }[];
  people: IssuePerson[];
}

// ── 리비전(일감 · 스킬이 함께 쓰는 모양) ───────────────────────────────

/** 줄 단위 차이 한 줄 — 일감 설명 · 스킬 본문의 리비전 차이가 같은 모양이다 */
export type DiffLine = IssueDiffLine;

/** 리비전 타임라인 · 견주기 카드에 보이는 한 리비전 — 일감 · 스킬 리비전이 모두 이 모양을 품는다 */
export interface RevisionInfo {
  no: number;
  actorType: 'USER' | 'SERVICE';
  actorName: string;
  /** SYSTEM — 서버가 스스로 한 일(워크스페이스 단위 번호 다시 매기기, 2026-11-24) */
  via: 'WEB' | 'MCP' | 'API' | 'SYSTEM';
  createdAt: string;
  /** 지금 리비전인가 */
  current: boolean;
}

// ── Agent 스킬 (시스템 관리자가 관리, 누구나 읽기 — 일감 #30) ─────────────

export interface SkillPerson {
  id: string;
  name: string;
}

/** 스킬 목록 한 줄. 지운 스킬은 시스템 관리자가 includeDeleted 로 달라고 할 때만 온다 */
export interface SkillSummary {
  name: string;
  description: string;
  revisionNo: number;
  updatedAt: string;
  updatedBy: SkillPerson | null;
  deleted: boolean;
  deletedAt: string | null;
  deletedBy: SkillPerson | null;
}

/** 스킬 하나 — 지금 리비전의 전체. 고칠 때는 revisionNo 를 baseRevisionNo 로 보낸다(낡았으면 409 SKILL_CHANGED — moai-wiki 와 같다) */
export interface SkillDetail extends SkillSummary {
  body: string;
  createdAt: string;
  createdBy: SkillPerson | null;
}

/** 리비전을 만든 일 — 만듦 · 수정 · SKILL.md 가져오기 · 옛 리비전으로 되돌림(restore) */
export type SkillRevisionKind = 'CREATE' | 'UPDATE' | 'IMPORT' | 'RESTORE';

export interface SkillRevisionItem extends RevisionInfo {
  kind: SkillRevisionKind;
  /** RESTORE 면 되돌린 리비전 번호 */
  restoredFrom: number | null;
  /** 바로 앞 리비전과 견줘 바뀐 것 — description · body */
  changedFields: ('description' | 'body')[];
  /** 바꾼 까닭 */
  changeNote: string | null;
  actorId: string | null;
}

export interface SkillRevisionList {
  items: SkillRevisionItem[];
  next: number | null;
}

/** 한 리비전의 전체 */
export interface SkillRevision extends SkillRevisionItem {
  name: string;
  description: string;
  body: string;
}

/** 두 리비전의 차이 — 이름 · 설명은 전 · 후(fields), 본문은 줄 단위 */
export interface SkillRevisionDiff {
  from: number | null;
  to: number;
  fromInfo: SkillRevisionItem | null;
  toInfo: SkillRevisionItem;
  fields: { field: 'name' | 'description'; before: string | null; after: string | null }[];
  bodyLines: DiffLine[];
  added: number;
  removed: number;
  coarse: boolean;
}

export interface SkillCreateBody {
  name: string;
  description: string;
  body: string;
  changeNote?: string;
}

export interface SkillUpdateBody {
  description: string;
  body: string;
  /** 읽은 리비전 — 지금과 다르면 409 SKILL_CHANGED */
  baseRevisionNo: number;
  changeNote?: string;
}

/** SKILL.md 가져오기의 결과 — created 면 새로 만들었고, 아니면 있던 스킬을 고쳤다 */
export interface SkillImportResult {
  created: boolean;
  skill: SkillDetail;
}
