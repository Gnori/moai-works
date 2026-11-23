import { describe, expect, it } from 'vitest';

import { commentActions } from './comments';

const ME = 'me';
const live = { notes: '글', deleted: false, actorId: ME, actorType: 'USER' as const };
const all = { open: true, canComment: true, canManage: false };

describe('댓글 단추 가리기 (판정은 api)', () => {
  it('내 댓글은 고치고 지운다', () => {
    expect(commentActions(live, ME, all)).toEqual({ edit: true, remove: true });
  });

  it('남의 댓글은 고칠 수 없고, project.manage 가 있으면 지우기만 한다', () => {
    const theirs = { ...live, actorId: 'other' };
    expect(commentActions(theirs, ME, all)).toEqual({ edit: false, remove: false });
    expect(commentActions(theirs, ME, { ...all, canManage: true })).toEqual({ edit: false, remove: true });
  });

  it('지금 댓글 권한이 없으면 내 댓글도 고치지 못하지만 지울 수는 있다', () => {
    expect(commentActions(live, ME, { ...all, canComment: false })).toEqual({ edit: false, remove: true });
  });

  it('지운 댓글 · 댓글이 없는 줄 · 보관한 프로젝트 · 서비스가 쓴 줄은 단추가 없다', () => {
    const none = { edit: false, remove: false };
    expect(commentActions({ ...live, notes: null, deleted: true }, ME, all)).toEqual(none);
    expect(commentActions({ ...live, notes: null }, ME, all)).toEqual(none);
    expect(commentActions(live, ME, { ...all, open: false, canManage: true })).toEqual(none);
    expect(commentActions({ ...live, actorType: 'SERVICE', actorId: null }, ME, all)).toEqual(none);
  });
});
