import { describe, expect, it } from 'vitest';

import { readChange } from './journal';

describe('이력의 바뀐 필드 읽기 (서버가 적는 모양 그대로)', () => {
  it('상태 · 유형 · 날짜 · 제목', () => {
    expect(readChange({ field: 'status', before: 'NEW', after: 'IN_PROGRESS' })).toEqual({
      kind: 'field',
      field: 'status',
      before: { kind: 'status', status: 'NEW' },
      after: { kind: 'status', status: 'IN_PROGRESS' },
    });
    expect(readChange({ field: 'type', before: 'task', after: 'bug' })).toMatchObject({
      before: { kind: 'type', key: 'task' },
      after: { kind: 'type', key: 'bug' },
    });
    expect(readChange({ field: 'dueDate', before: null, after: '2026-12-01' })).toMatchObject({
      field: 'dueDate',
      before: { kind: 'empty' },
      after: { kind: 'date', date: '2026-12-01' },
    });
    expect(readChange({ field: 'title', before: '옛 제목', after: '새 제목' })).toMatchObject({
      before: { kind: 'text', text: '옛 제목' },
      after: { kind: 'text', text: '새 제목' },
    });
  });

  it('사람은 {id, name} 에서 이름을, 상위 일감은 번호를 읽는다', () => {
    expect(
      readChange({ field: 'assignee', before: null, after: { id: 'aaaaaaaa-0000-0000-0000-000000000001', name: '김철수' } }),
    ).toMatchObject({ field: 'assignee', before: { kind: 'empty' }, after: { kind: 'person', name: '김철수' } });
    expect(readChange({ field: 'parent', before: 3, after: null })).toMatchObject({
      before: { kind: 'issue', number: 3 },
      after: { kind: 'empty' },
    });
  });

  it('설명은 바뀌었다는 것만, 연결은 넣고 뺀 것과 종류 · 방향 · 번호를 읽는다', () => {
    expect(readChange({ field: 'description', before: null, after: null })).toEqual({ kind: 'description' });
    expect(readChange({ field: 'link', before: null, after: 'BLOCKS #12' })).toEqual({
      kind: 'link',
      action: 'added',
      type: 'BLOCKS',
      direction: null,
      number: 12,
      raw: 'BLOCKS #12',
    });
    // 지금 서버가 적는 모양 — 이 일감 쪽에서 본 방향이 있다(차단됨)
    expect(readChange({ field: 'link', before: null, after: { type: 'BLOCKS', direction: 'INCOMING', number: 3 } }))
      .toMatchObject({ kind: 'link', action: 'added', type: 'BLOCKS', direction: 'INCOMING', number: 3 });
    expect(readChange({ field: 'link', before: 'RELATES #4', after: null })).toMatchObject({
      action: 'removed',
      type: 'RELATES',
      number: 4,
    });
    // 모르는 모양이면 적힌 대로
    expect(readChange({ field: 'link', before: null, after: 'SOMETHING' })).toMatchObject({ type: null, number: null, raw: 'SOMETHING' });
  });

  it('예상 공수는 시간 글자 그대로 읽는다', () => {
    expect(readChange({ field: 'estimatedHours', before: null, after: '8' })).toEqual({
      kind: 'field',
      field: 'estimatedHours',
      before: { kind: 'empty' },
      after: { kind: 'hours', hours: '8' },
    });
    expect(readChange({ field: 'estimatedHours', before: '8', after: '12.5' })).toMatchObject({
      before: { kind: 'hours', hours: '8' },
      after: { kind: 'hours', hours: '12.5' },
    });
    // 숫자로 와도 읽는다
    expect(readChange({ field: 'estimatedHours', before: 2.5, after: null })).toMatchObject({
      before: { kind: 'hours', hours: '2.5' },
      after: { kind: 'empty' },
    });
  });

  it('작업 시간 기록은 넣고 지운 것과 일한 사람 · 날 · 시간 · 메모를 읽는다', () => {
    const value = { id: 'w1', user: { id: 'aaaaaaaa-0000-0000-0000-000000000001', name: '김철수' }, date: '2026-11-22', hours: '2', note: '설계' };
    expect(readChange({ field: 'workLog', before: null, after: value })).toEqual({
      kind: 'workLog',
      action: 'added',
      user: '김철수',
      date: '2026-11-22',
      hours: '2',
      note: '설계',
    });
    // 지울 때는 after 가 null — 메모가 없으면 note 키도 없다
    const withoutNote = { id: value.id, user: value.user, date: value.date, hours: '1.5' };
    expect(readChange({ field: 'workLog', before: withoutNote, after: null })).toEqual({
      kind: 'workLog',
      action: 'removed',
      user: '김철수',
      date: '2026-11-22',
      hours: '1.5',
      note: null,
    });
    // 이름이 없으면 id, 모양이 깨졌으면 비운 채로
    expect(readChange({ field: 'workLog', before: null, after: { user: { id: 'u-1' }, hours: 3 } })).toMatchObject({
      user: 'u-1',
      hours: '3',
      date: null,
    });
    expect(readChange({ field: 'workLog', before: null, after: 'broken' })).toMatchObject({ action: 'added', user: null, hours: null });
  });

  it('화면이 모르는 필드는 이름과 값을 적힌 대로 둔다', () => {
    expect(readChange({ field: 'priority', before: 'LOW', after: { name: '높음' } })).toEqual({
      kind: 'unknown',
      field: 'priority',
      before: { kind: 'text', text: 'LOW' },
      after: { kind: 'text', text: '높음' },
    });
  });

  it('첨부는 올리고 지운 것과 이름 · 크기를 읽는다', () => {
    const value = { id: '0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b', name: '화면.png', size: 1258291, image: true };
    expect(readChange({ field: 'attachment', before: null, after: value })).toEqual({
      kind: 'attachment',
      action: 'added',
      name: '화면.png',
      size: 1258291,
    });
    expect(readChange({ field: 'attachment', before: value, after: null })).toEqual({
      kind: 'attachment',
      action: 'removed',
      name: '화면.png',
      size: 1258291,
    });
    // 모양이 깨져도 줄은 보인다
    expect(readChange({ field: 'attachment', before: null, after: 'broken' })).toEqual({ kind: 'attachment', action: 'added', name: null, size: null });
  });
});
