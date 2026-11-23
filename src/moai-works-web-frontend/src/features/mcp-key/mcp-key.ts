import type { McpKey, McpKeyExpireReason, McpKeyPerson, McpKeyStatusFilter, WorkspaceSummary } from '@/shared/api/types';

/**
 * MCP 키 화면의 순수한 도움 함수 — 연결 안내 · 만료 까닭 · 목록 조건(주소).
 * 키 원문은 여기서도 받기만 하고 어디에도 남기지 않는다.
 */

/** 고를 수 있는 기간(일) — api 기본 90일, 최대 365일 */
export const keyPeriodPresets = [30, 90, 180, 365] as const;
export const DEFAULT_KEY_DAYS = 90;
/** 키 이름 · 만료 이유의 길이 (api 와 같다) */
export const KEY_NAME_MAX = 100;
export const EXPIRE_NOTE_MAX = 500;

/** Claude Code 에 등록하는 MCP 서버 이름 — 입구마다 하나 */
export const MCP_SERVER_NAMES = { issue: 'moai-works-issue', project: 'moai-works-project' } as const;

/**
 * MCP 입구 — 기능으로 나눈다(일감 · 프로젝트 관리). 키 하나로 두 입구 모두 붙고, 할 수 있는 일은 키 주인의 권한으로 정해진다.
 * - issue: 일감 찾기 · 읽기 · 만들기 · 고치기 · 댓글 · 작업 시간 · 첨부
 * - project: 프로젝트 목록 · 유형별 일감 템플릿 관리, 프로젝트 만들기 · 지우기 같은 관리 기능 자리
 */
export type McpEntry = 'issue' | 'project';

/**
 * 바깥 MCP 클라이언트가 붙는 주소 — 이 화면을 연 출처(web-backend)의 /mcp/issue · /mcp/project. web-backend 가 api 로 중계한다.
 * @param origin window.location.origin
 */
export function mcpUrl(origin: string, entry: McpEntry = 'issue'): string {
  return `${origin.replace(/\/+$/, '')}/mcp/${entry}`;
}

/**
 * Claude Code 에 이 키로 연결을 등록하는 명령. 범위는 사용자(--scope user) — 어느 폴더에서 Claude Code 를 열어도 보인다.
 * 범위를 빼면 명령을 실행한 폴더에만 등록된다(로컬 범위)(2026-11-23 사용자: "전역으로 등록해야 할 것 같은데").
 */
export function claudeAddCommand(url: string, key: string, entry: McpEntry = 'issue'): string {
  return `claude mcp add --scope user --transport http ${MCP_SERVER_NAMES[entry]} ${url} --header "Authorization: Bearer ${key}"`;
}

/** 만료된 까닭 — 기간이 지나 저절로 만료된 것(TIME)을 더한다 */
export type KeyEndReason = 'TIME' | McpKeyExpireReason;
export const keyEndReasons: readonly KeyEndReason[] = ['TIME', 'MANUAL', 'MEMBER_LEFT', 'ENDPOINT_DELETED'];

/** 이 키가 왜 만료됐나 — 사용 중이면 null, 까닭 없이 만료됐으면 기간이 지난 것 */
export function keyEndReason(key: Pick<McpKey, 'status' | 'expireReason'>): KeyEndReason | null {
  if (key.status !== 'EXPIRED') {
    return null;
  }
  return key.expireReason ?? 'TIME';
}

/** 사람 이름 — 표시 이름 → 아이디 → id 앞부분 (계정을 못 찾으면 이름이 비어 온다) */
export function personName(person: McpKeyPerson | null | undefined): string {
  if (!person) {
    return '—';
  }
  return person.displayName || person.username || person.id.slice(0, 8);
}

/** 키를 받을 수 있는 워크스페이스 — 내가 들어가 있고 쓰는 중인 곳 */
export function issuableWorkspaces(workspaces: readonly WorkspaceSummary[]): WorkspaceSummary[] {
  return workspaces.filter((w) => w.status === 'ACTIVE');
}

/** 발급 창의 처음 워크스페이스 — 지금(또는 마지막에 쓴) 워크스페이스 → 개인 → 첫 번째 */
export function defaultIssueWorkspace(workspaces: readonly WorkspaceSummary[], preferred: string | null | undefined): string {
  return (
    workspaces.find((w) => w.slug === preferred)?.slug ??
    workspaces.find((w) => w.kind === 'PERSONAL')?.slug ??
    workspaces[0]?.slug ??
    ''
  );
}

// ── 목록 조건 (주소 ?status=EXPIRED&owner=…) ─────────────────────────────

export const keyStatusFilters: readonly McpKeyStatusFilter[] = ['ALL', 'ACTIVE', 'EXPIRED'];

export interface KeyFilters {
  status: McpKeyStatusFilter;
  /** 주인 사용자 id — null 이면 모두 */
  owner: string | null;
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** 주소에서 조건을 읽는다 — 모르는 값은 버리고 기본값으로(손으로 고친 주소도 화면이 깨지지 않게) */
export function readKeyFilters(params: URLSearchParams, defaultStatus: McpKeyStatusFilter): KeyFilters {
  const status = params.get('status');
  const owner = params.get('owner');
  return {
    status: status !== null && (keyStatusFilters as readonly string[]).includes(status) ? (status as McpKeyStatusFilter) : defaultStatus,
    owner: owner && UUID.test(owner) ? owner : null,
  };
}

/** 조건을 주소로 — 기본값은 적지 않는다 */
export function writeKeyFilters(filters: KeyFilters, defaultStatus: McpKeyStatusFilter): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.status !== defaultStatus) {
    params.set('status', filters.status);
  }
  if (filters.owner) {
    params.set('owner', filters.owner);
  }
  return params;
}
