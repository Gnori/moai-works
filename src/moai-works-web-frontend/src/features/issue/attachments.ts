import { projectCan } from '@/features/project/queries';
import type { AttachmentView, IssueJournal, ProjectDetail } from '@/shared/api/types';

/**
 * 일감 첨부파일 — 화면이 쓰는 순수 함수. 단추를 가리기만 하고 판정은 api 가 한다(AttachmentService 와 같은 조건).
 */

/**
 * 올리기 단추 · 붙여넣기 · 끌어다 놓기를 켤까 — 프로젝트가 열려 있고 댓글을 달 수 있다(issue.comment).
 * 보관한 프로젝트는 api 가 409 PROJECT_NOT_WRITABLE 로 막는다.
 */
export function canUploadAttachment(project: Pick<ProjectDetail, 'status' | 'myPermissions'>): boolean {
  return project.status === 'ACTIVE' && projectCan(project, 'issue.comment');
}

/**
 * 이 첨부의 지우기 단추를 보일까 — 내가 올렸거나, 프로젝트를 관리할 수 있으면(project.manage) 남의 것도.
 * 지우기도 올리기와 같은 입구(issue.comment · 열린 프로젝트)를 지나므로 그 조건(canUpload)을 함께 본다.
 */
export function canRemoveAttachment(
  attachment: Pick<AttachmentView, 'uploadedBy'>,
  myId: string,
  options: { canUpload: boolean; canManage: boolean },
): boolean {
  return options.canUpload && (options.canManage || attachment.uploadedBy.id === myId);
}

/** 댓글 수 — 이력 가운데 댓글(notes)이 있는 줄 */
export function countComments(journal: readonly Pick<IssueJournal, 'notes'>[]): number {
  return journal.filter((entry) => !!entry.notes?.trim()).length;
}

/** 올리는 동안 본문에 잠깐 넣는 자리 — ![업로드 중… 이름]() . 이름의 [ ] \ 는 Markdown 이 깨지지 않게 막는다 */
export function uploadPlaceholder(label: string): string {
  return `![${label.replace(/[[\]\\]/g, '\\$&')}]()`;
}

/**
 * 붙여넣은 것 가운데 올릴 파일. 글 · 서식이 함께 든 붙여넣기(오피스 · 웹 페이지에서 고른 글 — text/plain 과 text/html 이 둘 다 있다)는
 * 그림 사본이 함께 들어 있어도 글로 붙인다. 스크린샷 · "이미지 복사" · 탐색기에서 복사한 파일은 올린다.
 */
export function pastedFiles(data: Pick<DataTransfer, 'files' | 'getData'> | null | undefined): File[] {
  if (!data) {
    return [];
  }
  const files = Array.from(data.files ?? []);
  if (files.length === 0) {
    return [];
  }
  const richText = !!data.getData('text/plain') && !!data.getData('text/html');
  return richText ? [] : files;
}
