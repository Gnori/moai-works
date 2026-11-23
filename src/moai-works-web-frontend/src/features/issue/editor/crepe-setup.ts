import { Crepe } from '@milkdown/crepe';
import { remarkStringifyOptionsCtx } from '@milkdown/kit/core';
import { uploadPlugin } from '@milkdown/kit/plugin/upload';
import { remarkPreserveEmptyLinePlugin } from '@milkdown/kit/preset/commonmark';

import { editorGuard, noBlobUploader } from './editor-guard';
import { safeImageSchema } from './safe-image';

// Crepe 의 모양 — 쓰는 기능의 것만(수식 · AI · 위 막대 · 블록 그림 · 블록 메뉴 · 떠 있는 막대는 끈다). 색은 RichEditor 가 화면 테마로 채운다
import '@milkdown/crepe/theme/common/prosemirror.css';
import '@milkdown/crepe/theme/common/reset.css';
import '@milkdown/crepe/theme/common/code-mirror.css';
import '@milkdown/crepe/theme/common/cursor.css';
import '@milkdown/crepe/theme/common/link-tooltip.css';
import '@milkdown/crepe/theme/common/list-item.css';
import '@milkdown/crepe/theme/common/placeholder.css';
import '@milkdown/crepe/theme/common/table.css';

/**
 * Milkdown(Crepe) 편집기를 만드는 한 곳 — 일감 설명 · 댓글 편집기(RichEditor)와 원문 보존 시험 자료를 만드는 개발 쪽 화면이 같이 쓴다.
 *
 * 켜는 기능: 할 일 목록(체크 칸) · 표 · 코드 블록(CodeMirror) · 링크 고치기 · 빈 칸 안내 · 커서.
 * 끄는 기능과 까닭:
 *   - 블록 그림(ImageBlock) — 저장할 때 `![이름](주소)` 의 이름 자리에 크기 비율("1.00")을 적어 첨부 표시를 망가뜨린다.
 *     그림은 Milkdown 기본 줄 안 그림(이름 · 주소 · 제목 그대로)으로 두고, 보이는 주소만 imageView 가 바꾼다.
 *   - 수식(Latex) · AI · 위 막대(TopBar) — 쓰지 않는다. AI 기능은 바깥 서비스를 부르므로 늘 끈다(api 에 AI 를 넣지 않는 규칙과 같다).
 *   - 블록 메뉴(BlockEdit, "/" 메뉴 · 끌기 손잡이) · 떠 있는 막대(Toolbar) — 글이 영어로만 나오고, 위 고정 도구 막대(EditorToolbar)와 겹친다.
 * 고치는 것(moai-wiki 와 같다):
 *   - 새 목록 기호는 `-` — 원문 보존이 손댄 덩어리만 편집기 글로 바꾸므로, 새로 쓴 목록이 기존 글(대부분 `-`)과 같은 기호가 되게.
 *   - 빈 문단을 `<br />` 로 적는 기능(remarkPreserveEmptyLinePlugin)은 뺀다 — 원문에 사람이 쓰지 않은 태그가 생긴다(startCrepe).
 * 더하는 것:
 *   - safeImageSchema — 그림 마디가 HTML 로 적히는 모양(toDOM)을 바꾼다: `attachment:<id>` 만 그 일감의 같은 출처 첨부 주소 `<img>`,
 *     나머지는 설명 글 `<span>`. 붙여 넣기 · insert 처럼 마디를 HTML 로 적었다 읽는 길에서도 바깥 `<img>` 가 생기지 않는다(safe-image.ts).
 *   - 파일 붙여넣기 · 끌어 놓기를 넘기는 플러그인(onFiles) — 첨부로 올리고 받은 표시를 그 자리에 넣는다.
 *     Crepe 에 늘 들어 있는 올리기 플러그인(@milkdown/plugin-upload)이 handlePaste · handleDrop 을 먼저 가져가므로,
 *     그 플러그인은 떼고(startCrepe — 남겨 두면 "파일 + 글자(HTML 없이)" 붙여넣기를 가져가 글자까지 사라진다, moai-wiki 69ba9fe 와 같다),
 *     파일은 handleDOMEvents 에서 받는다(editorGuard). uploader 도 빈 결과만 내게 바꿔 둔다 — 어떤 길로도 blob: 그림
 *     (이 브라우저에서만 보이는 주소)이 글에 들어가지 않게(noBlobUploader). 설정(uploadConfig)은 남아 Crepe 의 설정 코드는 그대로 돈다.
 *   - 링크 누르기 막기 — 편집 중에 남이 쓴 링크(javascript: 등)가 열리지 않게.
 */
export interface CrepeSetupOptions {
  root: HTMLElement;
  value: string;
  placeholder?: string;
  /** 링크 고치기 칸의 안내 글 */
  linkPlaceholder?: string;
  /** 그림 주소 → 보이는 주소. null 이면 그리지 않고 이름만 보인다 */
  resolveImage?: (src: string) => string | null;
  /** 붙여넣거나 끌어 놓은 파일 — pos 는 끌어 놓은 자리(붙여넣기는 null = 커서 자리) */
  onFiles?: (files: File[], pos: number | null) => void;
}

export function createCrepe(options: CrepeSetupOptions): Crepe {
  const crepe = new Crepe({
    root: options.root,
    defaultValue: options.value,
    features: {
      [Crepe.Feature.ImageBlock]: false,
      [Crepe.Feature.Latex]: false,
      [Crepe.Feature.AI]: false,
      [Crepe.Feature.TopBar]: false,
      [Crepe.Feature.BlockEdit]: false,
      [Crepe.Feature.Toolbar]: false,
      [Crepe.Feature.ListItem]: true,
      [Crepe.Feature.Table]: true,
      [Crepe.Feature.CodeMirror]: true,
      [Crepe.Feature.LinkTooltip]: true,
      [Crepe.Feature.Placeholder]: true,
      [Crepe.Feature.Cursor]: true,
    },
    featureConfigs: {
      [Crepe.Feature.Placeholder]: { text: options.placeholder ?? '', mode: 'doc' },
      ...(options.linkPlaceholder ? { [Crepe.Feature.LinkTooltip]: { inputPlaceholder: options.linkPlaceholder } } : {}),
    },
  });

  const resolve = options.resolveImage ?? (() => null);

  crepe.editor
    .config((ctx) => ctx.update(remarkStringifyOptionsCtx, (stringify) => ({ ...stringify, bullet: '-' as const })))
    .config(noBlobUploader)
    .use(safeImageSchema(resolve))
    .use(editorGuard(options.onFiles));
  return crepe;
}

/** 편집기를 띄운다 — 빈 문단을 `<br />` 로 적는 기능과 기본 올리기 플러그인을 먼저 빼고 만든다(moai-wiki 와 같다) */
export async function startCrepe(crepe: Crepe): Promise<void> {
  await crepe.editor.remove([...remarkPreserveEmptyLinePlugin, uploadPlugin]);
  await crepe.create();
}
