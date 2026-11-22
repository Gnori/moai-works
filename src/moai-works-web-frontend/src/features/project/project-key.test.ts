import { describe, expect, it } from 'vitest';

import { checkProjectKey, suggestProjectKey } from './project-key';

describe('프로젝트 주소 이름', () => {
  it('규칙에 맞는 이름은 통과한다', () => {
    for (const key of ['hr', 'it-ops', 'project2', 'a1', 'next-gen-2026']) {
      expect(checkProjectKey(key), key).toBeUndefined();
    }
  });

  it('규칙에 어긋난 이름을 잡는다 (api 와 같은 규칙)', () => {
    for (const key of ['A', 'x', 'Hr', '9proj', 'proj-', '프로젝트', 'has space', 'a'.repeat(33), '']) {
      expect(checkProjectKey(key), key).toBe('invalid');
    }
  });

  it('화면 주소와 부딪히는 이름은 쓸 수 없다', () => {
    for (const key of ['new', 'archive', 'archived', 'settings']) {
      expect(checkProjectKey(key), key).toBe('reserved');
    }
  });

  it('영문 이름에서 주소 이름을 제안한다', () => {
    expect(suggestProjectKey('Mobile App')).toBe('mobile-app');
    expect(suggestProjectKey('  Next   Gen 2026 ')).toBe('next-gen-2026');
    expect(suggestProjectKey('2026 Plans')).toBe('plans');
    // 32자에서 자르고, 잘린 끝이 하이픈이면 뗀다
    expect(suggestProjectKey('A very long project name that goes past the limit')).toBe('a-very-long-project-name-that-go');
  });

  it('영문이 없거나 쓸 수 없는 이름이면 제안하지 않는다', () => {
    expect(suggestProjectKey('차세대 시스템')).toBe('');
    expect(suggestProjectKey('x')).toBe('');
    expect(suggestProjectKey('New')).toBe('');
  });
});
