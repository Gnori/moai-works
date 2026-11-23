import { describe, expect, it } from 'vitest';

import type { PermissionCode, ProjectStatus } from '@/shared/api/types';

import { canRemoveAttachment, canUploadAttachment, countComments, pastedFiles, thumbnailSource, uploadPlaceholder } from './attachments';

const ME = 'aaaaaaaa-0000-0000-0000-000000000001';
const LEE = 'aaaaaaaa-0000-0000-0000-000000000002';

function project(status: ProjectStatus, myPermissions: PermissionCode[]) {
  return { status, myPermissions };
}

describe('첨부 단추를 보일 조건 (서버가 준 권한으로 가리기만)', () => {
  it('올리기 — 열린 프로젝트에서 issue.comment 가 있을 때만', () => {
    expect(canUploadAttachment(project('ACTIVE', ['issue.read', 'issue.comment']))).toBe(true);
    expect(canUploadAttachment(project('ACTIVE', ['issue.read', 'issue.edit']))).toBe(false);
    expect(canUploadAttachment(project('ARCHIVED', ['issue.read', 'issue.comment', 'project.manage']))).toBe(false);
  });

  it('지우기 — 내가 올린 것, 또는 project.manage 면 남의 것도. 올릴 수 없으면(독자 · 보관) 숨긴다', () => {
    const mine = { uploadedBy: { id: ME, username: 'kim', displayName: '김철수' } };
    const theirs = { uploadedBy: { id: LEE, username: 'lee', displayName: '이영희' } };
    expect(canRemoveAttachment(mine, ME, { canUpload: true, canManage: false })).toBe(true);
    expect(canRemoveAttachment(theirs, ME, { canUpload: true, canManage: false })).toBe(false);
    expect(canRemoveAttachment(theirs, ME, { canUpload: true, canManage: true })).toBe(true);
    expect(canRemoveAttachment(mine, ME, { canUpload: false, canManage: true })).toBe(false);
  });
});

describe('첨부 목록의 작은 그림', () => {
  const urls = { content: '/c', preview: '/p' };
  it('미리보기 그림이 있으면 그것, 미리보기가 없는 이미지(WebP)는 원본, 파일은 없음', () => {
    expect(thumbnailSource({ image: true, preview: true }, urls)).toBe('/p');
    expect(thumbnailSource({ image: true, preview: false }, urls)).toBe('/c');
    expect(thumbnailSource({ image: false, preview: false }, urls)).toBeNull();
  });
});

describe('댓글 수', () => {
  it('이력 가운데 글(notes)이 있는 줄만 센다', () => {
    expect(countComments([{ notes: '좋습니다' }, { notes: null }, { notes: '  ' }, { notes: '확인' }])).toBe(2);
    expect(countComments([])).toBe(0);
  });
});

describe('올리는 동안의 자리', () => {
  it('![업로드 중… 이름]() — 이름의 괄호는 막는다', () => {
    expect(uploadPlaceholder('업로드 중… 화면.png')).toBe('![업로드 중… 화면.png]()');
    expect(uploadPlaceholder('업로드 중… a]b[c\\.png')).toBe('![업로드 중… a\\]b\\[c\\\\.png]()');
  });
});

describe('붙여넣은 파일', () => {
  const png = new File(['x'], '화면.png', { type: 'image/png' });
  const clip = (files: File[], text = '', html = '') => ({
    files: files as unknown as FileList,
    getData: (type: string) => (type === 'text/plain' ? text : type === 'text/html' ? html : ''),
  });

  it('스크린샷 · 이미지 복사 · 복사한 파일은 올린다', () => {
    expect(pastedFiles(clip([png]))).toEqual([png]);
    expect(pastedFiles(clip([png], '', '<img src="x">'))).toEqual([png]);
    expect(pastedFiles(clip([png], '화면.png'))).toEqual([png]);
    expect(pastedFiles(clip([png], '/Users/me/Desktop/화면.png'))).toEqual([png]);
    expect(pastedFiles(clip([png], 'C:\\Users\\me\\화면.png'))).toEqual([png]);
  });

  it('글 · 서식이 함께 든 붙여넣기(오피스 · 웹 페이지)는 글로 둔다', () => {
    expect(pastedFiles(clip([png], '표 내용', '<table></table>'))).toEqual([]);
    expect(pastedFiles(clip([], '글만'))).toEqual([]);
    // 파일과 함께 온 실제 글자(HTML 없이)는 글로 — 글자를 버리지 않는다
    expect(pastedFiles(clip([png], '회의 메모 첫 줄'))).toEqual([]);
    expect(pastedFiles(clip([png], '화면.png\n다른 글'))).toEqual([]);
    expect(pastedFiles(null)).toEqual([]);
  });
});
