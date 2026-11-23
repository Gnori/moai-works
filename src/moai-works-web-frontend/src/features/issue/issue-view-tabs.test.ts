import { describe, expect, it } from 'vitest';

import { issueRevisionsPath } from '@/app/routes';

import { readIssueViewTab, withIssueViewTab } from './issue-view-tabs';

const q = (search: string) => new URLSearchParams(search);

describe('일감 보기의 탭 주소', () => {
  it('?tab= 의 넷(내용 · 댓글 · 첨부파일 · 리비전)을 읽고, 없거나 모르는 값은 내용', () => {
    expect(readIssueViewTab(q(''))).toBe('content');
    expect(readIssueViewTab(q('tab=comments'))).toBe('comments');
    expect(readIssueViewTab(q('tab=attachments'))).toBe('attachments');
    expect(readIssueViewTab(q('tab=revisions'))).toBe('revisions');
    expect(readIssueViewTab(q('tab=history'))).toBe('content');
    // 고른 리비전만 있고 탭이 없으면 내용 — 리비전 탭은 ?tab=revisions 로 연다
    expect(readIssueViewTab(q('rev=2'))).toBe('content');
  });

  it('탭을 적을 때 내용은 지우고, 다른 주소 값(설정 · 고른 리비전)은 그대로 둔다', () => {
    expect(withIssueViewTab(q('settings=profile'), 'revisions').toString()).toBe('settings=profile&tab=revisions');
    expect(withIssueViewTab(q('tab=revisions&rev=2&view=snapshot'), 'content').toString()).toBe('rev=2&view=snapshot');
    expect(withIssueViewTab(q('rev=2&view=snapshot'), 'revisions').toString()).toBe('rev=2&view=snapshot&tab=revisions');
  });

  it('리비전 탭 주소 — 고른 리비전 · 견줄 리비전 · 전체 보기를 담는다', () => {
    expect(issueRevisionsPath('a', 'web', 7)).toBe('/w/a/projects/web/issues/7?tab=revisions');
    expect(issueRevisionsPath('a', 'web', 7, { rev: 3 })).toBe('/w/a/projects/web/issues/7?tab=revisions&rev=3');
    expect(issueRevisionsPath('a', 'web', 7, { rev: 3, from: 0, view: 'snapshot' })).toBe('/w/a/projects/web/issues/7?tab=revisions&rev=3&from=0&view=snapshot');
  });
});
