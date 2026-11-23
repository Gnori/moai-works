import { describe, expect, it } from 'vitest';

import type { NotificationPreference } from '@/shared/api/types';

import { emailChecked, emailLocked, groupPreferences, preferenceChecked, preferenceLocked } from './preferences';

function pref(kind: string, group: string, enabled = true, mandatory = false, email = false): NotificationPreference {
  return { kind, group, enabled, mandatory, email };
}

describe('알림 설정 묶기 · 잠금', () => {
  it('묶음은 워크스페이스 · 프로젝트 · 일감 · 작업 시간 · 보안 순, 안에서는 서버 순서 그대로, 빈 묶음은 뺀다', () => {
    const list = [
      pref('mcp_key.expired', 'SECURITY', true, true),
      pref('issue.assigned', 'ISSUE'),
      pref('join.requested', 'WORKSPACE'),
      pref('issue.commented', 'ISSUE', false),
      pref('member.removed', 'WORKSPACE', true, true),
    ];
    expect(groupPreferences(list).map((g) => [g.group, g.items.map((p) => p.kind)])).toEqual([
      ['WORKSPACE', ['join.requested', 'member.removed']],
      ['ISSUE', ['issue.assigned', 'issue.commented']],
      ['SECURITY', ['mcp_key.expired']],
    ]);
  });

  it('서버가 새로 더한 묶음은 아는 묶음 뒤에, 나온 순서대로', () => {
    const list = [pref('x.one', 'LATER'), pref('issue.assigned', 'ISSUE'), pref('y.two', 'NEWER'), pref('x.three', 'LATER')];
    expect(groupPreferences(list).map((g) => [g.group, g.items.map((p) => p.kind)])).toEqual([
      ['ISSUE', ['issue.assigned']],
      ['LATER', ['x.one', 'x.three']],
      ['NEWER', ['y.two']],
    ]);
    expect(groupPreferences([])).toEqual([]);
  });

  it('끌 수 없는 종류는 잠그고 늘 켜져 보인다 — 서버가 정한 mandatory 만 본다', () => {
    expect(preferenceLocked(pref('member.removed', 'WORKSPACE', true, true))).toBe(true);
    expect(preferenceChecked(pref('member.removed', 'WORKSPACE', false, true))).toBe(true);
    expect(preferenceLocked(pref('issue.assigned', 'ISSUE', false))).toBe(false);
    expect(preferenceChecked(pref('issue.assigned', 'ISSUE', false))).toBe(false);
    expect(preferenceChecked(pref('issue.assigned', 'ISSUE', true))).toBe(true);
  });

  it('메일은 알림함에 쌓이는 종류만 — 알림함을 끈 종류는 메일을 고를 수 없고 꺼져 보인다', () => {
    expect(emailLocked(pref('issue.assigned', 'ISSUE', false, false, true))).toBe(true);
    expect(emailChecked(pref('issue.assigned', 'ISSUE', false, false, true))).toBe(false);
    expect(emailLocked(pref('issue.assigned', 'ISSUE', true, false, true))).toBe(false);
    expect(emailChecked(pref('issue.assigned', 'ISSUE', true, false, true))).toBe(true);
    // 알림함을 끌 수 없는 종류도 메일은 고른다
    expect(emailLocked(pref('mcp_key.expiring', 'SECURITY', false, true, false))).toBe(false);
    expect(emailChecked(pref('mcp_key.expiring', 'SECURITY', false, true, true))).toBe(true);
  });
});
