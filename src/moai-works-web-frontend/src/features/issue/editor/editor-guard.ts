import type { Ctx } from '@milkdown/kit/ctx';
import { uploadConfig } from '@milkdown/kit/plugin/upload';
import { Plugin, PluginKey } from '@milkdown/kit/prose/state';
import { $prose } from '@milkdown/kit/utils';

/**
 * 편집기가 파일 · 링크를 다루는 규칙 — createCrepe 가 붙인다. Crepe 없이 시험할 수 있게 따로 둔다(editor-guard.test.ts).
 */

/** 붙여넣은 · 끌어 놓은 것 가운데 파일만 */
function filesOf(data: DataTransfer | null): File[] {
  if (!data) {
    return [];
  }
  return Array.from(data.files ?? []);
}

/**
 * Milkdown 올리기 플러그인이 파일을 그림 마디로 만들지 않게 한다 — Crepe 의 기본 uploader 는 URL.createObjectURL 로 blob: 주소 그림을 넣는다.
 * 파일은 editorGuard 가 먼저 받아 첨부로 올리므로 여기까지 오지 않아야 하고, 오더라도(onFiles 가 없는 편집기) 아무것도 넣지 않는다.
 * Crepe 의 설정(생성자에서 등록)보다 뒤에 등록해야 덮어쓴다.
 */
export function noBlobUploader(ctx: Ctx): void {
  ctx.update(uploadConfig.key, (prev) => ({ ...prev, uploader: () => Promise.resolve([]) }));
}

/**
 * 파일 붙여넣기 · 끌어 놓기와 링크 누르기를 다루는 플러그인.
 * 파일은 handleDOMEvents 에서 받는다 — ProseMirror 는 handleDOMEvents 가 true 를 돌려주면 기본 처리(그 안에서 부르는 모든 플러그인의
 * handlePaste · handleDrop)를 건너뛰므로, 플러그인 순서와 상관없이 올리기 플러그인보다 먼저 받는다. 파일이 없으면 손대지 않는다.
 */
export function editorGuard(onFiles?: (files: File[], pos: number | null) => void) {
  return $prose(
    () =>
      new Plugin({
        key: new PluginKey('moai-works-editor-guard'),
        props: {
          handleDOMEvents: {
            paste: (_view, event) => {
              const files = filesOf(event.clipboardData);
              if (!onFiles || files.length === 0) {
                return false;
              }
              event.preventDefault();
              onFiles(files, null);
              return true;
            },
            drop: (view, event) => {
              const files = filesOf(event.dataTransfer);
              if (!onFiles || files.length === 0) {
                return false;
              }
              event.preventDefault();
              const at = view.posAtCoords({ left: event.clientX, top: event.clientY });
              onFiles(files, at ? at.pos : null);
              return true;
            },
            // 편집 중에는 링크를 열지 않는다 — 남이 쓴 javascript: · 바깥 주소가 열리지 않게(보기 화면에서 연다)
            click: (_view, event) => {
              if (event.target instanceof Element && event.target.closest('a')) {
                event.preventDefault();
              }
              return false;
            },
            auxclick: (_view, event) => {
              if (event.target instanceof Element && event.target.closest('a')) {
                event.preventDefault();
                return true;
              }
              return false;
            },
          },
        },
      }),
  );
}
