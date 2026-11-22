import { renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import i18n from '@/shared/i18n/i18n';
import { mcpKeyExpireReasons, mcpKeyStatuses, type WorkspaceSummary } from '@/shared/api/types';
import { useLabels } from '@/shared/lib/labels';

import {
  claudeAddCommand,
  DEFAULT_KEY_DAYS,
  defaultIssueWorkspace,
  issuableWorkspaces,
  keyEndReason,
  keyEndReasons,
  keyPeriodPresets,
  mcpUrl,
  personName,
  readKeyFilters,
  writeKeyFilters,
} from './mcp-key';

const OWNER = '11111111-2222-3333-4444-555555555555';

function ws(slug: string, kind: WorkspaceSummary['kind'], status: WorkspaceSummary['status'] = 'ACTIVE'): WorkspaceSummary {
  return { slug, name: slug, kind, status, myRoles: ['member'], memberCount: 1 };
}

describe('연결 안내', () => {
  it('MCP 주소는 이 화면 출처의 /mcp/project', () => {
    expect(mcpUrl('https://localhost:11003')).toBe('https://localhost:11003/mcp/project');
    expect(mcpUrl('https://works.example.com/')).toBe('https://works.example.com/mcp/project');
  });

  it('Claude Code 명령에 주소와 키를 넣는다', () => {
    expect(claudeAddCommand('https://localhost:11003/mcp/project', 'mai-wks-Ab12xyz')).toBe(
      'claude mcp add --transport http moai-works https://localhost:11003/mcp/project --header "Authorization: Bearer mai-wks-Ab12xyz"',
    );
  });

  it('기간은 30 · 90 · 180 · 365일, 기본 90일 (api 기본 · 최대와 같다)', () => {
    expect(keyPeriodPresets).toEqual([30, 90, 180, 365]);
    expect(DEFAULT_KEY_DAYS).toBe(90);
    expect(keyPeriodPresets).toContain(DEFAULT_KEY_DAYS);
  });
});

describe('만료 까닭 · 이름', () => {
  it('사용 중이면 까닭이 없고, 까닭 없이 만료됐으면 기간이 지난 것', () => {
    expect(keyEndReason({ status: 'ACTIVE', expireReason: null })).toBeNull();
    expect(keyEndReason({ status: 'EXPIRED', expireReason: null })).toBe('TIME');
    expect(keyEndReason({ status: 'EXPIRED', expireReason: 'MANUAL' })).toBe('MANUAL');
    expect(keyEndReason({ status: 'EXPIRED', expireReason: 'MEMBER_LEFT' })).toBe('MEMBER_LEFT');
    expect(keyEndReason({ status: 'EXPIRED', expireReason: 'ENDPOINT_DELETED' })).toBe('ENDPOINT_DELETED');
  });

  it('상태 · 만료 까닭마다 두 언어의 말이 있다', () => {
    for (const lng of ['ko', 'en']) {
      for (const status of mcpKeyStatuses) {
        expect(i18n.exists(`mcp:status.${status}`, { lng })).toBe(true);
      }
      for (const status of ['ALL', ...mcpKeyStatuses]) {
        expect(i18n.exists(`mcp:statusFilter.${status}`, { lng })).toBe(true);
      }
      for (const reason of [...keyEndReasons, ...mcpKeyExpireReasons]) {
        expect(i18n.exists(`mcp:endReason.${reason}`, { lng })).toBe(true);
      }
    }
  });

  it('useLabels 가 상태 · 까닭을 말로 바꾼다', () => {
    const { result } = renderHook(() => useLabels());
    expect(result.current.mcpKeyStatus('ACTIVE')).toBe('사용 중');
    expect(result.current.mcpKeyStatus('EXPIRED')).toBe('만료');
    expect(result.current.mcpKeyStatusFilter('ALL')).toBe('전체');
    expect(result.current.mcpKeyEndReason('TIME')).toBe('기간이 지나 만료');
    expect(result.current.mcpKeyEndReason('MANUAL')).toBe('직접 만료시킴');
    expect(result.current.mcpKeyEndReason('MEMBER_LEFT')).toBe('주인이 워크스페이스를 떠나 만료');
  });

  it('사람 이름은 표시 이름 → 아이디 → id 앞부분', () => {
    expect(personName({ id: OWNER, username: 'kim', displayName: '김철수' })).toBe('김철수');
    expect(personName({ id: OWNER, username: 'kim', displayName: null })).toBe('kim');
    expect(personName({ id: OWNER, username: null, displayName: null })).toBe('11111111');
    expect(personName(null)).toBe('—');
  });
});

describe('발급 창의 워크스페이스', () => {
  const list = [ws('team-a', 'TEAM'), ws('mine', 'PERSONAL'), ws('team-b', 'TEAM'), ws('stopped', 'TEAM', 'SUSPENDED')];

  it('쓰는 중인 워크스페이스에서만 받는다', () => {
    expect(issuableWorkspaces(list).map((w) => w.slug)).toEqual(['team-a', 'mine', 'team-b']);
  });

  it('처음 값은 지금 · 마지막에 쓴 곳 → 개인 → 첫 번째', () => {
    const active = issuableWorkspaces(list);
    expect(defaultIssueWorkspace(active, 'team-b')).toBe('team-b');
    expect(defaultIssueWorkspace(active, 'stopped')).toBe('mine');
    expect(defaultIssueWorkspace(active, null)).toBe('mine');
    expect(defaultIssueWorkspace([ws('x', 'TEAM')], undefined)).toBe('x');
    expect(defaultIssueWorkspace([], 'x')).toBe('');
  });
});

describe('키 목록 조건 (주소)', () => {
  it('기본값은 주소에 적지 않는다', () => {
    const filters = readKeyFilters(new URLSearchParams(), 'ALL');
    expect(filters).toEqual({ status: 'ALL', owner: null });
    expect(writeKeyFilters(filters, 'ALL').toString()).toBe('');
  });

  it('주소의 조건을 읽고 같은 주소로 다시 쓴다', () => {
    const search = `status=EXPIRED&owner=${OWNER}`;
    const filters = readKeyFilters(new URLSearchParams(search), 'ALL');
    expect(filters).toEqual({ status: 'EXPIRED', owner: OWNER });
    expect(writeKeyFilters(filters, 'ALL').toString()).toBe(search);
    // 내 키 화면은 기본이 사용 중 — 전체를 고르면 주소에 남는다
    expect(writeKeyFilters({ status: 'ALL', owner: null }, 'ACTIVE').toString()).toBe('status=ALL');
  });

  it('모르는 값은 버린다', () => {
    expect(readKeyFilters(new URLSearchParams('status=active&owner=kim'), 'ACTIVE')).toEqual({ status: 'ACTIVE', owner: null });
  });
});
