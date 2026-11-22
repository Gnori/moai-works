import { describe, expect, it } from 'vitest';

import { canPrefill, missingSections, templateSections } from './issue-template';

/** api IssueTemplateTest 의 빠진_항목_시험과 같은 경우 — 두 쪽 규칙이 같아야 한다 */
describe('issue-template', () => {
  const template = '## 현상\n## 재현 순서\n### Expected\n## 환경 (선택)\n```\n## 코드 안의 제목\n```\n';

  it('항목은 코드 블록 밖의 제목, (선택) 은 비워도 되는 항목', () => {
    expect(templateSections(template)).toEqual([
      { title: '현상', optional: false },
      { title: '재현 순서', optional: false },
      { title: 'Expected', optional: false },
      { title: '환경', optional: true },
    ]);
    expect(templateSections('## 메모 (optional)')).toEqual([{ title: '메모', optional: true }]);
  });

  it('빠진 필수 항목 — 제목 수준 · 대소문자 · 코드 블록을 가린다', () => {
    expect(missingSections(template, '# 현상\n내용\n#### expected\n')).toEqual(['재현 순서']);
    expect(missingSections(template, '```\n## 재현 순서\n```\n## 현상\n## Expected')).toEqual(['재현 순서']);
    expect(missingSections(template, '## 현상\n## 재현 순서\n## expected')).toEqual([]);
    expect(missingSections('', '아무 글')).toEqual([]);
    expect(missingSections(template, null)).toEqual(['현상', '재현 순서', 'Expected']);
  });

  it('비었거나 공백뿐일 때만 템플릿으로 채운다', () => {
    expect(canPrefill('')).toBe(true);
    expect(canPrefill('  \n ')).toBe(true);
    expect(canPrefill(undefined)).toBe(true);
    expect(canPrefill('쓴 글')).toBe(false);
  });
});
