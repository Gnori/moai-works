import { describe, expect, it } from 'vitest';

import i18n from '@/shared/i18n/i18n';
import type { NotificationItem } from '@/shared/api/types';
import { dateTime } from '@/shared/lib/format';

import {
  describeNotification,
  notificationGroupLabel,
  notificationKindLabel,
  notificationKinds,
  type NotificationKind,
  type NotificationT,
} from './describe';

/**
 * 알림 문장 · 링크 — 종류마다 실제 언어 리소스로 문장을 만들어 본다(두 언어 모두 빠진 글이 없는지),
 * 그리고 갈 곳 · MCP 표시 · 모르는 종류를 본다.
 */

const ko = i18n.getFixedT('ko', ['common', 'issue', 'workspace']) as unknown as NotificationT;
const en = i18n.getFixedT('en', ['common', 'issue', 'workspace']) as unknown as NotificationT;

const SLUG = 'team-a';
const issueParams = { project: 'web', projectName: '웹', number: 12, title: '로그인 실패', via: 'WEB' };
// 일감 링크는 정본 주소 — 번호는 워크스페이스 안에서 하나다(2026-11-24)
const ISSUE = `/w/${SLUG}/issues/12`;

/** 종류마다 서버가 주는 모양의 값과 갈 곳 */
const samples: Record<NotificationKind, { params: Record<string, unknown>; to: string | null; settings?: 'mcp-keys' }> = {
  'join.requested': { params: { user: '이영희' }, to: `/w/${SLUG}/requests` },
  'join.approved': { params: {}, to: `/w/${SLUG}` },
  'join.rejected': { params: {}, to: null },
  'member.roles_changed': { params: { roles: ['admin', 'custom-x1'] }, to: `/w/${SLUG}` },
  'member.removed': { params: {}, to: null },
  'project.roles_changed': { params: { project: 'web', projectName: '웹', roles: ['project-editor'] }, to: `/w/${SLUG}/projects/web` },
  'issue.assigned': { params: issueParams, to: ISSUE },
  'issue.unassigned': { params: issueParams, to: ISSUE },
  'issue.manager_assigned': { params: issueParams, to: ISSUE },
  'issue.status_changed': { params: { ...issueParams, from: 'NEW', to: 'IN_PROGRESS' }, to: ISSUE },
  'issue.updated': { params: { ...issueParams, fields: ['title', 'dueDate', 'mystery'] }, to: ISSUE },
  'issue.commented': { params: issueParams, to: ISSUE },
  'issue.child_added': { params: { ...issueParams, childNumber: 13, childTitle: '세션 만료' }, to: ISSUE },
  'issue.linked': {
    params: { ...issueParams, linkType: 'BLOCKS', direction: 'OUTGOING', otherNumber: 20, otherTitle: '배포' },
    to: ISSUE,
  },
  'issue.deleted': { params: issueParams, to: null },
  'issue.due_soon': { params: { ...issueParams, via: 'SYSTEM', dueDate: '2026-11-23' }, to: ISSUE },
  'issue.overdue': { params: { ...issueParams, via: 'SYSTEM', dueDate: '2026-11-20' }, to: ISSUE },
  'issue.work_logged': { params: { ...issueParams, workDate: '2026-11-22', hours: '2', spent: '5.5', estimated: '8' }, to: ISSUE },
  'issue.over_estimate': { params: { ...issueParams, workDate: '2026-11-22', hours: '3', spent: '9', estimated: '8' }, to: ISSUE },
  // MCP 키 — 화면을 옮기지 않고 지금 화면 위에 개인 설정의 내 MCP 키를 연다
  'mcp_key.expired': { params: { name: '노트북', keyPrefix: 'mai-wks-Ab12', note: '분실' }, to: null, settings: 'mcp-keys' },
  'mcp_key.expiring': { params: { name: '노트북', keyPrefix: 'mai-wks-Ab12', expiresAt: '2026-11-29T00:00:00Z' }, to: null, settings: 'mcp-keys' },
};

function item(kind: string, params: Record<string, unknown>, extra: Partial<NotificationItem> = {}): NotificationItem {
  return {
    id: 'n1',
    kind,
    subjectType: 'issue',
    subjectId: null,
    params,
    actorName: '김철수',
    count: 1,
    workspaceSlug: SLUG,
    workspaceName: '팀 A',
    createdAt: '2026-11-22T09:00:00.001Z',
    updatedAt: '2026-11-22T09:00:00.001Z',
    readAt: null,
    ...extra,
  };
}

describe('알림 문장 · 링크 (describeNotification)', () => {
  it('화면이 아는 종류는 api 의 21개와 같다', () => {
    expect(notificationKinds).toHaveLength(21);
    expect(Object.keys(samples).sort()).toEqual([...notificationKinds].sort());
  });

  it.each(notificationKinds)('%s — 두 언어 모두 문장이 있고, 정해진 곳으로 간다', (kind) => {
    const sample = samples[kind];
    for (const t of [ko, en]) {
      const out = describeNotification(t, item(kind, sample.params));
      expect(out.to).toBe(sample.to);
      expect(out.settings).toBe(sample.settings);
      // 빠진 글(열쇠가 그대로 보임) · 채우지 못한 자리표시자 · 일반 문장이 아니다
      expect(out.text).not.toMatch(/notifications\.|\{\{|undefined/);
      expect(out.text).not.toBe(t('notifications.kind.unknown'));
    }
  });

  it('일감 문장 — 누가 · 프로젝트 #번호 「제목」 · 바뀐 값을 사람이 읽는 말로', () => {
    const say = (kind: NotificationKind, extra: Partial<NotificationItem> = {}) => describeNotification(ko, item(kind, samples[kind].params, extra)).text;
    expect(say('issue.assigned')).toBe('김철수 님이 나를 웹 #12 「로그인 실패」의 담당자로 정했습니다');
    expect(say('issue.status_changed')).toBe('김철수 님이 웹 #12 「로그인 실패」의 상태를 바꿨습니다: 등록 → 진행');
    // 보류 · 취소도 이름으로(2026-11-24, 일감 #152)
    const held = { ...issueParams, from: 'ON_HOLD', to: 'CANCELLED' };
    expect(describeNotification(ko, item('issue.status_changed', held)).text).toBe('김철수 님이 웹 #12 「로그인 실패」의 상태를 바꿨습니다: 보류 → 취소');
    expect(describeNotification(en, item('issue.status_changed', held)).text).toContain('On hold → Cancelled');
    // 아는 필드는 번역, 서버가 새로 더한 필드는 이름 그대로
    expect(say('issue.updated')).toBe('김철수 님이 웹 #12 「로그인 실패」을(를) 수정했습니다: 제목 · 완료 기한 · mystery');
    expect(say('issue.linked')).toBe('김철수 님이 웹 #12 「로그인 실패」에 #20 「배포」을(를) 연결했습니다 (차단함)');
    // 다른 프로젝트 일감과 이으면 서버가 상대 제목을 싣지 않는다 — 번호만(받는 사람이 그 프로젝트를 못 읽을 수 있다)
    expect(
      describeNotification(ko, item('issue.linked', { ...issueParams, linkType: 'BLOCKS', direction: 'OUTGOING', otherNumber: 57 })).text,
    ).toBe('김철수 님이 웹 #12 「로그인 실패」에 #57 을(를) 연결했습니다 (차단함)');
    expect(say('issue.work_logged')).toBe('김철수 님이 웹 #12 「로그인 실패」에 작업 시간 2시간을 적었습니다 · 소요 시간 5.5시간 / 예상 8시간');
    // 아침 알림은 한 사람이 없다
    expect(say('issue.due_soon', { actorName: null })).toBe('웹 #12 「로그인 실패」의 완료 기한이 내일(2026-11-23)입니다');
    expect(describeNotification(en, item('issue.assigned', issueParams)).text).toBe('김철수 assigned you to 웹 #12 “로그인 실패”');
  });

  it('MCP 로 한 일은 한 사람 뒤에 (MCP) 를 붙인다', () => {
    const viaMcp = { ...issueParams, via: 'MCP' };
    expect(describeNotification(ko, item('issue.commented', viaMcp)).text).toBe('김철수 (MCP) 님이 웹 #12 「로그인 실패」에 댓글을 남겼습니다');
    expect(describeNotification(en, item('issue.commented', viaMcp)).text).toBe('김철수 (MCP) commented on 웹 #12 “로그인 실패”');
    expect(describeNotification(ko, item('issue.commented', issueParams)).text).not.toContain('(MCP)');
  });

  it('역할 · MCP 키 — 기본 역할은 번역, 빠진 역할은 다른 문장, 만료 이유는 있을 때만', () => {
    expect(describeNotification(ko, item('member.roles_changed', { roles: ['admin', 'custom-x1'] })).text).toBe(
      '팀 A에서 내 역할이 바뀌었습니다: 관리자 · custom-x1',
    );
    expect(describeNotification(ko, item('project.roles_changed', samples['project.roles_changed'].params)).text).toBe(
      '김철수 님이 웹 프로젝트의 내 역할을 바꿨습니다: 편집자',
    );
    expect(describeNotification(ko, item('project.roles_changed', { project: 'web', projectName: '웹', roles: [] })).text).toBe(
      '김철수 님이 웹 프로젝트의 내 역할을 모두 뺐습니다',
    );
    expect(describeNotification(ko, item('mcp_key.expired', samples['mcp_key.expired'].params)).text).toBe(
      '김철수 님이 내 MCP 키 「노트북」(mai-wks-Ab12…)를 만료시켰습니다. 이유: 분실',
    );
    expect(describeNotification(ko, item('mcp_key.expired', { name: '노트북', keyPrefix: 'mai-wks-Ab12', note: '' })).text).not.toContain('이유');
    expect(describeNotification(ko, item('mcp_key.expiring', samples['mcp_key.expiring'].params, { actorName: null })).text).toBe(
      `내 MCP 키 「노트북」(mai-wks-Ab12…)가 ${dateTime('2026-11-29T00:00:00Z')}에 만료됩니다`,
    );
  });

  it('한 사람 이름이 없으면 "누군가"', () => {
    expect(describeNotification(ko, item('issue.assigned', issueParams, { actorName: null })).text).toMatch(/^누군가 님이/);
  });

  it('모르는 종류는 일반 문장 · 링크 없음', () => {
    expect(describeNotification(ko, item('something.new', { title: 'x' }))).toEqual({ text: '새 알림이 있습니다', to: null });
    expect(describeNotification(en, item('something.new', {}))).toEqual({ text: 'You have a new notification', to: null });
  });

  it('워크스페이스 주소가 없으면 워크스페이스 링크를 만들지 않는다 (내 MCP 키 칸은 그대로)', () => {
    const noSlug = { workspaceSlug: null };
    expect(describeNotification(ko, item('issue.assigned', issueParams, noSlug)).to).toBeNull();
    expect(describeNotification(ko, item('join.requested', { user: 'x' }, noSlug)).to).toBeNull();
    expect(describeNotification(ko, item('mcp_key.expiring', samples['mcp_key.expiring'].params, noSlug)).settings).toBe('mcp-keys');
    // 일감 번호를 알 수 없으면 링크를 만들지 않는다
    expect(describeNotification(ko, item('issue.assigned', { ...issueParams, number: 'x' })).to).toBeNull();
  });
});

describe('알림 설정의 이름', () => {
  it('종류마다 두 언어의 짧은 이름이 있고, 모르는 종류는 코드 그대로', () => {
    for (const kind of notificationKinds) {
      expect(notificationKindLabel(ko, kind)).not.toBe(kind);
      expect(notificationKindLabel(en, kind)).not.toBe(kind);
    }
    expect(notificationKindLabel(ko, 'issue.assigned')).toBe('나를 담당자로 정함');
    expect(notificationKindLabel(ko, 'something.new')).toBe('something.new');
  });

  it('묶음 이름 — 다섯 묶음은 번역, 모르는 묶음은 코드 그대로', () => {
    expect(['WORKSPACE', 'PROJECT', 'ISSUE', 'WORK', 'SECURITY'].map((g) => notificationGroupLabel(ko, g))).toEqual([
      '워크스페이스',
      '프로젝트',
      '일감',
      '작업 시간',
      '보안 · MCP 키',
    ]);
    expect(notificationGroupLabel(en, 'WORK')).toBe('Work time');
    expect(notificationGroupLabel(ko, 'NEW_GROUP')).toBe('NEW_GROUP');
  });
});
