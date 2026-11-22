import type { TFunction } from 'i18next';

import { issuePath, projectPath, workspacePath, type SettingsSectionKey } from '@/app/routes';
import koIssue from '@/shared/i18n/resources/ko/issue.json';
import { isBuiltinRole } from '@/shared/lib/labels';
import {
  issueLinkTypes,
  issueStatuses,
  notificationGroups,
  projectRoleKeys,
  type IssueLinkType,
  type IssueStatus,
  type NotificationGroup,
  type NotificationItem,
  type ProjectRoleKey,
} from '@/shared/api/types';
import { date, dateTime } from '@/shared/lib/format';

/** 알림 문장에 쓰는 t — 알림 글은 common, 상태 · 필드 · 연결 이름은 issue, 프로젝트 역할 이름은 workspace 에 있다 */
export type NotificationT = TFunction<['common', 'issue', 'workspace']>;

/** 화면이 아는 알림 종류 (api NotificationKind 와 같다). 서버가 새로 더한 종류는 일반 문장으로 보인다 */
export const notificationKinds = [
  'join.requested',
  'join.approved',
  'join.rejected',
  'member.roles_changed',
  'member.removed',
  'project.roles_changed',
  'issue.assigned',
  'issue.unassigned',
  'issue.manager_assigned',
  'issue.status_changed',
  'issue.updated',
  'issue.commented',
  'issue.child_added',
  'issue.linked',
  'issue.deleted',
  'issue.due_soon',
  'issue.overdue',
  'issue.work_logged',
  'issue.over_estimate',
  'mcp_key.expired',
  'mcp_key.expiring',
] as const;
export type NotificationKind = (typeof notificationKinds)[number];

export function isKnownKind(kind: string): kind is NotificationKind {
  return (notificationKinds as readonly string[]).includes(kind);
}

/** 종류의 짧은 이름 (알림 설정) — 모르는 종류는 코드 그대로 */
export function notificationKindLabel(t: NotificationT, kind: string): string {
  return isKnownKind(kind) ? t(`notifications.label.${kind}`) : kind;
}

function isGroup(group: string): group is NotificationGroup {
  return (notificationGroups as readonly string[]).includes(group);
}

/** 알림 설정 묶음의 이름 — 모르는 묶음은 코드 그대로 */
export function notificationGroupLabel(t: NotificationT, group: string): string {
  return isGroup(group) ? t(`notifications.groups.${group}`) : group;
}

/** 알림 설정 묶음의 짧은 설명 — 모르는 묶음은 없다(null) */
export function notificationGroupHelp(t: NotificationT, group: string): string | null {
  return isGroup(group) ? t(`notifications.groupHelp.${group}`) : null;
}

/** 알림 한 줄을 화면에 보일 문장과 갈 곳 */
export interface NotificationText {
  text: string;
  /** 눌렀을 때 갈 화면. 없으면 null(내보내진 워크스페이스 · 지운 일감) — settings 도 없으면 읽음으로만 한다 */
  to: string | null;
  /** 눌렀을 때 지금 화면 위에 열 개인 설정의 칸(MCP 키 알림 → 내 MCP 키) */
  settings?: SettingsSectionKey;
}

type KnownField = keyof typeof koIssue.fields;

function isStatus(value: string): value is IssueStatus {
  return (issueStatuses as readonly string[]).includes(value);
}

function isLinkType(value: string): value is IssueLinkType {
  return (issueLinkTypes as readonly string[]).includes(value);
}

function isProjectRole(value: string): value is ProjectRoleKey {
  return (projectRoleKeys as readonly string[]).includes(value);
}

/**
 * 알림의 종류와 값으로 문장과 링크를 만든다(moai-wiki 와 같은 자리). 서버는 본문을 주지 않는다 — 제목 · 이름 · 번호 · 수만 준다.
 * 눌러서 들어간 화면이 지금 권한으로 다시 판정한다. MCP 로 한 일은 한 사람 이름 뒤에 "(MCP)"를 붙인다.
 * 모르는 종류(화면보다 서버가 새 것일 때)는 일반 문장으로 보인다.
 */
export function describeNotification(t: NotificationT, n: NotificationItem): NotificationText {
  const params = n.params ?? {};
  const str = (key: string): string => {
    const value = params[key];
    return typeof value === 'string' ? value : typeof value === 'number' ? String(value) : '';
  };
  const int = (key: string): number | null => {
    const value = params[key];
    if (typeof value === 'number' && Number.isInteger(value)) {
      return value;
    }
    return typeof value === 'string' && /^\d{1,9}$/.test(value) ? Number(value) : null;
  };
  const list = (key: string): string[] => {
    const value = params[key];
    return Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string') : [];
  };
  const hours = (key: string): string => (str(key) ? t('issue:hours.value', { hours: str(key) }) : '');

  const slug = n.workspaceSlug;
  const workspace = n.workspaceName ?? '';
  const name = n.actorName ?? t('notifications.someone');
  const actor = str('via') === 'MCP' ? t('notifications.viaMcp', { actor: name }) : name;

  // 일감 알림 — "프로젝트 #12 「제목」" · 그 일감 보기
  const projectKey = str('project');
  const projectName = str('projectName') || projectKey;
  const number = int('number');
  const issue = t('notifications.issueRef', { project: projectName, number: number ?? '?', title: str('title') });
  const issueTo = slug && projectKey && number !== null ? issuePath(slug, projectKey, number) : null;
  const total = () =>
    hours('estimated')
      ? t('notifications.spentOf', { spent: hours('spent'), estimated: hours('estimated') })
      : t('notifications.spent', { spent: hours('spent') });
  const status = (key: string) => {
    const value = str(key);
    return isStatus(value) ? t(`issue:status.${value}`) : value;
  };

  switch (n.kind) {
    case 'join.requested':
      return { text: t('notifications.kind.join.requested', { user: str('user'), workspace }), to: slug ? workspacePath(slug, { path: 'requests' }) : null };
    case 'join.approved':
      return { text: t('notifications.kind.join.approved', { workspace }), to: slug ? workspacePath(slug, { path: '' }) : null };
    case 'join.rejected':
      return { text: t('notifications.kind.join.rejected', { workspace }), to: null };
    case 'member.roles_changed': {
      // 기본 역할은 번역, 사용자 정의 역할은 키 그대로(알림에는 이름이 없다)
      const roles = list('roles')
        .map((key) => (isBuiltinRole(key) ? t(`roles.${key}`) : key))
        .join(' · ');
      return { text: t('notifications.kind.member.roles_changed', { workspace, roles }), to: slug ? workspacePath(slug, { path: '' }) : null };
    }
    case 'member.removed':
      return { text: t('notifications.kind.member.removed', { workspace }), to: null };
    case 'project.roles_changed': {
      const roles = list('roles').map((key) => (isProjectRole(key) ? t(`workspace:projects.roles.${key}`) : key));
      const to = slug && projectKey ? projectPath(slug, projectKey) : null;
      return roles.length > 0
        ? { text: t('notifications.kind.project.roles_changed', { actor, project: projectName, roles: roles.join(' · ') }), to }
        : { text: t('notifications.kind.project.roles_removed', { actor, project: projectName }), to };
    }
    case 'issue.assigned':
      return { text: t('notifications.kind.issue.assigned', { actor, issue }), to: issueTo };
    case 'issue.unassigned':
      return { text: t('notifications.kind.issue.unassigned', { actor, issue }), to: issueTo };
    case 'issue.manager_assigned':
      return { text: t('notifications.kind.issue.manager_assigned', { actor, issue }), to: issueTo };
    case 'issue.status_changed':
      return { text: t('notifications.kind.issue.status_changed', { actor, issue, from: status('from'), to: status('to') }), to: issueTo };
    case 'issue.updated': {
      const fields = list('fields')
        .map((field) => (Object.hasOwn(koIssue.fields, field) ? t(`issue:fields.${field as KnownField}`) : field))
        .join(' · ');
      return { text: t('notifications.kind.issue.updated', { actor, issue, fields }), to: issueTo };
    }
    case 'issue.commented':
      return { text: t('notifications.kind.issue.commented', { actor, issue }), to: issueTo };
    case 'issue.child_added':
      return {
        text: t('notifications.kind.issue.child_added', { actor, issue, childNumber: int('childNumber') ?? '?', childTitle: str('childTitle') }),
        to: issueTo,
      };
    case 'issue.linked': {
      const type = str('linkType');
      const direction = str('direction');
      const link =
        isLinkType(type) && (direction === 'OUTGOING' || direction === 'INCOMING') ? t(`issue:links.${direction}.${type}`) : type;
      return {
        text: t('notifications.kind.issue.linked', { actor, issue, otherNumber: int('otherNumber') ?? '?', otherTitle: str('otherTitle'), link }),
        to: issueTo,
      };
    }
    case 'issue.deleted':
      // 지운 일감은 열 수 없다 — 읽음으로만
      return { text: t('notifications.kind.issue.deleted', { actor, issue }), to: null };
    case 'issue.due_soon':
      return { text: t('notifications.kind.issue.due_soon', { issue, dueDate: date(str('dueDate')) }), to: issueTo };
    case 'issue.overdue':
      return { text: t('notifications.kind.issue.overdue', { issue, dueDate: date(str('dueDate')) }), to: issueTo };
    case 'issue.work_logged':
      return { text: t('notifications.kind.issue.work_logged', { actor, issue, hours: hours('hours'), total: total() }), to: issueTo };
    case 'issue.over_estimate':
      return { text: t('notifications.kind.issue.over_estimate', { issue, total: total() }), to: issueTo };
    case 'mcp_key.expired': {
      const values = { actor, name: str('name'), prefix: str('keyPrefix') };
      return {
        text: str('note')
          ? t('notifications.kind.mcp_key.expired_note', { ...values, note: str('note') })
          : t('notifications.kind.mcp_key.expired', values),
        to: null,
        settings: 'mcp-keys',
      };
    }
    case 'mcp_key.expiring':
      return {
        text: t('notifications.kind.mcp_key.expiring', { name: str('name'), prefix: str('keyPrefix'), expiresAt: dateTime(str('expiresAt')) }),
        to: null,
        settings: 'mcp-keys',
      };
    default:
      return { text: t('notifications.kind.unknown'), to: null };
  }
}
