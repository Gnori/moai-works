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

/** 키 한 줄 — 원문은 없다(발급 응답에만 한 번 온다) */
export interface McpKey {
  id: string;
  name: string;
  /** 알아보기용 앞부분 (mai-wks-Ab12) */
  keyPrefix: string;
  status: McpKeyStatus;
  /** 워크스페이스 주소(slug) */
  workspace: string;
  workspaceName: string;
  owner: McpKeyPerson;
  createdAt: string;
  /** 만료 시각 — 일찍 만료시킨 키는 만료시킨 때 */
  expiresAt: string;
  lastUsedAt: string | null;
  /** 발급한 IP · 프로그램 — 주인과 관리하는 사람에게만 온다(그 밖에는 null) */
  issuedIp: string | null;
  issuedUserAgent: string | null;
  expiredBy: McpKeyPerson | null;
  expireReason: McpKeyExpireReason | null;
  expireNote: string | null;
  /** 내 키인가 */
  mine: boolean;
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

export interface PermissionCatalog {
  permissions: { code: PermissionCode; ownerOnly: boolean }[];
  builtinRoles: { key: string; name: string; description: string; permissions: PermissionCode[] }[];
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
  /** 권한 코드만으로는 알 수 없는 것 — createIssue: 프로젝트가 열려 있고 일감을 만들 수 있다 */
  myActions: ProjectAction[];
}

export type ProjectAction = 'createIssue';

/** 프로젝트 역할 4개 — 고정이고 위가 아래를 모두 담는다(api ProjectRole 과 같다) */
export const projectRoleKeys = ['project-reader', 'project-editor', 'project-curator', 'project-admin'] as const;
export type ProjectRoleKey = (typeof projectRoleKeys)[number];

/** 프로젝트 역할을 받은 사람 한 명 */
export interface ProjectRoleHolder {
  userId: string;
  username: string;
  displayName: string | null;
  roles: string[];
}

// ── 일감 (프로젝트 안의 일 — 번호는 프로젝트 안 순번. moai-wiki 의 페이지 자리) ──

export const issueStatuses = ['NEW', 'IN_PROGRESS', 'DONE', 'CLOSED'] as const;
/** 신규 · 진행 · 완료 · 종료 — 아무 상태로나 옮길 수 있다 */
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
}

/** 다른 일감을 가리킬 때 (상위 · 하위 · 연결) */
export interface IssueRef {
  number: number;
  title: string;
  status: IssueStatus;
  type: string;
}

/** 목록 한 줄 */
export interface IssueSummary {
  number: number;
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
}

export interface IssuePage {
  items: IssueSummary[];
  total: number;
  page: number;
  size: number;
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
  via: 'WEB' | 'MCP';
  createdAt: string;
  notes: string | null;
  changes: IssueChange[];
  /** 이 줄이 만든 판의 번호 — 필드를 바꾼 줄(만들기 · 고치기)에만 있다. 댓글 · 연결 · 작업 시간 줄은 null */
  revisionNo: number | null;
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
  via: 'WEB' | 'MCP';
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
  number: number;
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
  children: IssueRef[];
  links: IssueLink[];
  journal: IssueJournal[];
  /** 작업 시간 기록 — 일한 날이 늦은 것부터 */
  workLogs: IssueWorkLog[];
}

// ── 일감 리비전 (필드를 바꿀 때마다 생기는 판 — moai-wiki 의 페이지 리비전 자리) ──

/** 판 목록 한 줄 — 누가 · 어느 길로 · 언제 · 무엇을 바꿨나 */
export interface IssueRevisionItem {
  no: number;
  actorType: 'USER' | 'SERVICE';
  actorId: string | null;
  actorName: string;
  via: 'WEB' | 'MCP';
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
  via: 'WEB' | 'MCP';
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

/** 목록 조건 — status · type 은 여럿, assignee 는 사용자 id 또는 none, parent 는 번호 또는 top */
export interface IssueListParams {
  status?: IssueStatus[];
  type?: string[];
  manager?: string;
  assignee?: string;
  parent?: string;
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
}

/** 일감 고치기 — 바꾼 필드만 넣는다(null 이면 비운다). notes 를 넣으면 같은 이력 줄에 댓글로 남는다 */
export interface IssuePatch {
  rowVersion: number;
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
  notes?: string;
}
