import { Editor, editorViewCtx, rootCtx, serializerCtx } from '@milkdown/kit/core';
import { upload, uploadConfig, uploadPlugin } from '@milkdown/kit/plugin/upload';
import { commonmark } from '@milkdown/kit/preset/commonmark';
import type { EditorView } from '@milkdown/kit/prose/view';
import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import { editorGuard, noBlobUploader } from './editor-guard';

/**
 * 파일 붙여 넣기 · 끌어 놓기는 첨부로 올라가야 한다 — 편집기에 늘 들어 있는 올리기 플러그인(@milkdown/plugin-upload)이 먼저 가져가
 * blob: 그림을 넣거나(이미지) 아무것도 하지 않던(그 밖의 파일) 버그(2026-11-23)의 재발 막기.
 * Crepe · startCrepe 와 같게 만든다: 올리기 플러그인이 먼저, 우리 플러그인이 나중, 만들기 전에 올리기 플러그인을 뗀다.
 */

// jsdom 에는 ClipboardEvent · DragEvent 가 없다 — 올리기 플러그인은 instanceof 로 가르므로 있는 것처럼 만든다
class FakeClipboardEvent extends Event {
  constructor(
    type: string,
    readonly clipboardData: DataTransfer,
  ) {
    super(type, { bubbles: true, cancelable: true });
  }
}
class FakeDragEvent extends Event {
  readonly clientX = 10;
  readonly clientY = 10;
  constructor(
    type: string,
    readonly dataTransfer: DataTransfer,
  ) {
    super(type, { bubbles: true, cancelable: true });
  }
}
const g = globalThis as Record<string, unknown>;
const saved = { ClipboardEvent: g.ClipboardEvent, DragEvent: g.DragEvent };

/** 클립보드 · 끌어 놓기 자료 — 파일만이면 macOS 스크린샷 붙여 넣기와 같은 모양(text/plain · text/html 없음) */
function transfer(files: File[], text = '', html = ''): DataTransfer {
  const getData = (type: string) => (type === 'text/plain' || type === 'Text' ? text : type === 'text/html' ? html : '');
  return { files, types: ['Files'], items: [], getData } as unknown as DataTransfer;
}

const png = () => new File([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], 'shot.png', { type: 'image/png' });
const pdf = () => new File([new Uint8Array([0x25, 0x50, 0x44, 0x46])], 'spec.pdf', { type: 'application/pdf' });

let editor: Editor;
let view: EditorView;
const onFiles = vi.fn<(files: File[], pos: number | null) => void>();
const markdown = () => editor.action((ctx) => ctx.get(serializerCtx)(ctx.get(editorViewCtx).state.doc));

beforeAll(async () => {
  g.ClipboardEvent = FakeClipboardEvent;
  g.DragEvent = FakeDragEvent;
  const root = document.createElement('div');
  document.body.append(root);
  const made = Editor.make()
    .config((ctx) => ctx.set(rootCtx, root))
    .use(commonmark)
    .use(upload)
    .config(noBlobUploader)
    .use(editorGuard(onFiles));
  editor = await (await made.remove(uploadPlugin)).create();
  view = editor.action((ctx) => ctx.get(editorViewCtx));
  // jsdom 은 좌표 → 자리를 못 구한다
  view.posAtCoords = () => ({ pos: 1, inside: -1 });
});

afterAll(async () => {
  await editor.destroy();
  g.ClipboardEvent = saved.ClipboardEvent;
  g.DragEvent = saved.DragEvent;
});

beforeEach(() => onFiles.mockClear());

describe('편집기 파일 붙여 넣기 · 끌어 놓기', () => {
  it('붙여 넣은 이미지 파일은 첨부 올리기로 넘기고, 글에 그림 마디를 넣지 않는다', async () => {
    const file = png();
    const event = new FakeClipboardEvent('paste', transfer([file]));
    view.dom.dispatchEvent(event);
    await Promise.resolve();
    expect(onFiles).toHaveBeenCalledWith([file], null);
    expect(event.defaultPrevented).toBe(true);
    expect(markdown()).not.toMatch(/!\[|blob:|data:/);
  });

  it('끌어 놓은 파일은 이미지가 아니어도 놓은 자리로 넘긴다', async () => {
    const file = pdf();
    const event = new FakeDragEvent('drop', transfer([file]));
    view.dom.dispatchEvent(event);
    await Promise.resolve();
    expect(onFiles).toHaveBeenCalledWith([file], 1);
    expect(event.defaultPrevented).toBe(true);
  });

  it('파일과 함께 온 실제 글자는 글로 붙인다 — 파일을 올리지 않고 글자도 잃지 않는다', () => {
    const event = new FakeClipboardEvent('paste', transfer([png()], '회의 메모 첫 줄'));
    view.dom.dispatchEvent(event);
    expect(onFiles).not.toHaveBeenCalled();
    expect(markdown()).toContain('회의 메모 첫 줄');
  });

  it('서식 있는 글과 함께 온 그림 사본(표 · 문서에서 복사)은 글로 붙인다', () => {
    const event = new FakeClipboardEvent('paste', transfer([png()], '표 칸', '<table><tr><td>표 칸</td></tr></table>'));
    view.dom.dispatchEvent(event);
    expect(onFiles).not.toHaveBeenCalled();
  });

  it('파일이 없는 붙여 넣기는 손대지 않는다', () => {
    const event = new FakeClipboardEvent('paste', transfer([]));
    view.dom.dispatchEvent(event);
    expect(onFiles).not.toHaveBeenCalled();
  });

  it('올리기 플러그인의 uploader 는 아무 마디도 만들지 않는다 — blob: 주소 그림이 생길 길이 없다', async () => {
    const uploader = editor.action((ctx) => ctx.get(uploadConfig.key).uploader);
    const result = await editor.action((ctx) =>
      uploader(transfer([png()]).files as unknown as FileList, ctx.get(editorViewCtx).state.schema, ctx, 0),
    );
    expect(result).toEqual([]);
  });
});
