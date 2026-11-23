import { describe, expect, it } from 'vitest';

import i18n from '@/shared/i18n/i18n';

import { auditActionLabel } from './labels';

/** 감사 동작 이름표 — 서버 코드마다 화면 말, 아래 동작이 있는 동작은 `_`, 모르는 코드는 그대로 */
describe('감사 동작 이름표', () => {
  it('한국어 · 영어 이름표를 찾는다 — 아래 동작이 있는 동작도', async () => {
    await i18n.changeLanguage('ko');
    expect(auditActionLabel(i18n, 'issue.comment')).toBe('댓글 남김');
    expect(auditActionLabel(i18n, 'issue.comment.update')).toBe('댓글 수정');
    expect(auditActionLabel(i18n, 'issue.comment.delete')).toBe('댓글 지움');
    expect(auditActionLabel(i18n, 'issue.attachment.add')).toBe('첨부 올림');
    expect(auditActionLabel(i18n, 'issue.attachment.purge')).toBe('지운 첨부 정리');
    expect(auditActionLabel(i18n, 'issue_template.reset')).toBe('일감 템플릿 되돌림');
    expect(auditActionLabel(i18n, 'issue.create')).toBe('일감 만듦');
    await i18n.changeLanguage('en');
    expect(auditActionLabel(i18n, 'issue.comment')).toBe('Commented');
    expect(auditActionLabel(i18n, 'issue.attachment.remove')).toBe('Attachment deleted');
    await i18n.changeLanguage('ko');
  });

  it('모르는 코드 · 묶음 이름(글이 아님)은 코드 그대로다', () => {
    expect(auditActionLabel(i18n, 'something.new')).toBe('something.new');
    expect(auditActionLabel(i18n, 'issue.attachment')).toBe('issue.attachment');
  });
});
