import { useCallback, useMemo } from 'react';
import { useTranslation } from 'react-i18next';

import {
  builtinRoleKeys,
  type AccountStatus,
  type BuiltinRoleKey,
  type InvitationStatus,
  type IssueLink,
  type IssueStatus,
  type JoinPolicy,
  type JoinRequestStatus,
  type McpKeyExpireReason,
  type McpKeyStatus,
  type McpKeyStatusFilter,
  type Member,
  type PermissionCode,
  type ProjectStatus,
  type ProjectVisibility,
  type WorkspaceVisibility,
} from '@/shared/api/types';

/**
 * 서버 코드값 → 화면 말. 말은 모두 i18n 리소스에 있고, 여기는 "어느 키인가"만 정한다(타입으로 검사됨).
 */

export type PermissionGroupKey = 'workspace' | 'member' | 'project' | 'issue' | 'mcp';

/** 권한 코드 묶음 (design/03 §4) — 역할 표 · 홈 화면이 이 순서로 그린다. moai-wiki 의 위키 · 위키 내용 자리에 프로젝트 · 일감 */
export const permissionGroups: { group: PermissionGroupKey; codes: PermissionCode[] }[] = [
  { group: 'workspace', codes: ['workspace.read', 'workspace.settings.manage', 'workspace.ownership.transfer', 'workspace.delete'] },
  {
    group: 'member',
    codes: ['member.read', 'member.invite', 'member.request.review', 'member.remove', 'member.role.assign', 'role.manage'],
  },
  { group: 'project', codes: ['project.create', 'project.manage', 'project.delete'] },
  {
    group: 'issue',
    codes: ['issue.read', 'issue.create', 'issue.edit', 'issue.comment', 'issue.manager.assign', 'issue.delete'],
  },
  {
    group: 'mcp',
    codes: ['mcp.endpoint.manage', 'mcp.key.issue.personal', 'mcp.key.issue.service', 'mcp.key.manage', 'audit.read', 'usage.read'],
  },
];

/** 소유자 역할만 가질 수 있는 권한 (사용자 정의 역할에 넣을 수 없다) */
export const ownerOnlyPermissions: readonly PermissionCode[] = ['workspace.ownership.transfer', 'workspace.delete'];

export function isBuiltinRole(key: string): key is BuiltinRoleKey {
  return (builtinRoleKeys as readonly string[]).includes(key);
}

/** 역할 칩 색 (theme palette 이름) — 사용자 정의 역할은 기본색 */
export function roleColor(key: string): 'primary' | 'secondary' | 'info' | 'default' {
  switch (key) {
    case 'owner':
      return 'primary';
    case 'admin':
      return 'info';
    case 'curator':
      return 'secondary';
    default:
      return 'default';
  }
}

/**
 * 일감 상태 칩의 모양 — 색 묶음은 theme palette 이름(연한 바탕 + 그 색 글자), outlined 는 바탕 없이 테두리만(종료),
 * paused 는 중립 바탕 · 점선 테두리 · 멈춤 아이콘(보류 — 진행의 주황과 헷갈리지 않게), muted 는 흐린 글자 · 취소선(취소).
 * 색만으로 전하지 않게 칩에는 늘 상태 이름을 함께 적는다(WEB-DESIGN §3.1)
 */
export type IssueStatusTone = 'info' | 'warning' | 'secondary' | 'success' | 'outlined' | 'paused' | 'muted';

const issueStatusTones: Record<IssueStatus, IssueStatusTone> = {
  NEW: 'info',
  IN_PROGRESS: 'warning',
  TESTING: 'secondary',
  ON_HOLD: 'paused',
  DONE: 'success',
  CLOSED: 'outlined',
  CANCELLED: 'muted',
};

export function issueStatusTone(status: IssueStatus): IssueStatusTone {
  return issueStatusTones[status];
}

/** 역할 이름 — 기본 역할은 번역, 사용자 정의 역할은 서버가 준 이름(customNames), 없으면 키 */
export function useRoleName(customNames?: Record<string, string>) {
  const { t } = useTranslation();
  return useCallback(
    (key: string) => (isBuiltinRole(key) ? t(`roles.${key}`) : (customNames?.[key] ?? key)),
    [t, customNames],
  );
}

/** 코드값 표시 이름 묶음 */
export function useLabels() {
  const { t, i18n } = useTranslation(['workspace', 'admin', 'issue', 'mcp']);
  return useMemo(
    () => ({
      permission: (code: PermissionCode) => t(`workspace:permissions.${code}`),
      permissionGroup: (group: PermissionGroupKey) => t(`workspace:permissionGroups.${group}`),
      joinPolicy: (policy: JoinPolicy) => t(`workspace:joinPolicy.${policy}`),
      joinPolicyShort: (policy: JoinPolicy) => t(`workspace:joinPolicyShort.${policy}`),
      visibility: (visibility: WorkspaceVisibility) => t(`workspace:visibility.${visibility}`),
      visibilityHelp: (visibility: WorkspaceVisibility) => t(`workspace:visibilityHelp.${visibility}`),
      joinRequestStatus: (status: JoinRequestStatus) => t(`workspace:joinRequestStatus.${status}`),
      invitationStatus: (status: InvitationStatus) => t(`workspace:invitationStatus.${status}`),
      joinedVia: (via: Member['joinedVia']) => t(`workspace:joinedVia.${via}`),
      projectVisibility: (visibility: ProjectVisibility) => t(`workspace:projects.visibility.${visibility}`),
      projectVisibilityHelp: (visibility: ProjectVisibility) => t(`workspace:projects.visibilityHelp.${visibility}`),
      projectStatus: (status: ProjectStatus) => t(`workspace:projects.status.${status}`),
      issueStatus: (status: IssueStatus) => t(`issue:status.${status}`),
      /** 상태의 뜻 한 줄 — 상태 바꾸기 메뉴 · 고치기 양식의 도움말 (종료는 "완료한 일을 사람이 확인해 닫음") */
      issueStatusHelp: (status: IssueStatus) => t(`issue:statusHelp.${status}`),
      /** 연결 종류를 이 일감 쪽에서 읽는 말 — "차단함" · "차단됨" 처럼 방향에 따라 다르다(관련은 같다) */
      issueLink: (link: Pick<IssueLink, 'type' | 'direction'>) => t(`issue:links.${link.direction}.${link.type}`),
      /** 연결 종류의 이름 (방향 없이 — 연결을 넣을 때 고르는 말) */
      issueLinkType: (type: IssueLink['type']) => t(`issue:links.type.${type}`),
      accountStatus: (status: AccountStatus) => t(`admin:accountStatus.${status}`),
      mcpKeyStatus: (status: McpKeyStatus) => t(`mcp:status.${status}`),
      mcpKeyStatusFilter: (status: McpKeyStatusFilter) => t(`mcp:statusFilter.${status}`),
      /** 만료된 까닭 — 기간이 지남(TIME) · 직접 · 주인이 떠남 · 엔드포인트를 지움 */
      mcpKeyEndReason: (reason: 'TIME' | McpKeyExpireReason) => t(`mcp:endReason.${reason}`),
      /** 감사 동작은 서버가 새로 더할 수 있다 — 리소스에 없으면 코드 그대로 */
      auditAction: (action: string) => auditActionLabel(i18n, action),
    }),
    [t, i18n],
  );
}

/**
 * 감사 동작 이름표 — 리소스의 `workspace:auditActions` 는 점으로 나뉜 계층이라, 아래 동작이 있는 동작(`issue.comment` 와
 * `issue.comment.update`)은 그 자리의 `_` 에 둔다. 서버 값이라 타입으로 좁힐 수 없다 — 글로 있는지 확인한 뒤에만 번역하고, 없으면 코드 그대로.
 */
export function auditActionLabel(i18n: { exists: (key: string) => boolean; t: unknown }, action: string): string {
  const t = i18n.t as (key: string, options?: { returnObjects?: boolean }) => unknown;
  for (const key of [`workspace:auditActions.${action}._`, `workspace:auditActions.${action}`]) {
    if (i18n.exists(key)) {
      const value = t(key, { returnObjects: true });
      if (typeof value === 'string') {
        return value;
      }
    }
  }
  return action;
}
