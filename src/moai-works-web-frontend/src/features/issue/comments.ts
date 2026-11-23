import type { IssueJournal } from '@/shared/api/types';

/**
 * 댓글 고치기 · 지우기 단추를 보일지 (FR-ISS-019) — 판정은 api 가 하고, 화면은 서버가 준 이 프로젝트의 내 권한(myPermissions)과
 * 댓글을 쓴 사람(actorId) · 나(me)로 가리기만 한다.
 *   고치기: 쓴 사람 + 지금 issue.comment(남은 project.manage 가 있어도 못 고친다)
 *   지우기: 쓴 사람, 또는 project.manage
 * 지운 댓글 · 댓글이 없는 이력 줄 · 보관한 프로젝트에는 둘 다 없다.
 */
export interface CommentRights {
  /** 프로젝트가 열려 있다(보관하지 않았다) */
  open: boolean;
  /** issue.comment */
  canComment: boolean;
  /** project.manage — 남의 댓글도 지운다 */
  canManage: boolean;
}

export function commentActions(
  entry: Pick<IssueJournal, 'notes' | 'deleted' | 'actorId' | 'actorType'>,
  meId: string,
  rights: CommentRights,
): { edit: boolean; remove: boolean } {
  const live = entry.notes !== null && !entry.deleted;
  if (!live || !rights.open) {
    return { edit: false, remove: false };
  }
  const mine = entry.actorType === 'USER' && entry.actorId === meId;
  return { edit: mine && rights.canComment, remove: mine || rights.canManage };
}
