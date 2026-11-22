/** 위 도구 막대의 명령 — 편집기(RichEditor)가 Milkdown 명령으로 돌린다 */
export type EditorAction =
  | 'paragraph'
  | 'heading1'
  | 'heading2'
  | 'heading3'
  | 'bold'
  | 'italic'
  | 'strike'
  | 'inlineCode'
  | 'link'
  | 'bulletList'
  | 'orderedList'
  | 'taskList'
  | 'table'
  | 'quote'
  | 'codeBlock'
  | 'hr'
  | 'undo'
  | 'redo';

/** 목차의 한 줄 */
export interface EditorHeading {
  text: string;
  level: number;
}

/** 넣은 글의 자리 — 그 뒤로 사람이 앞뒤를 고쳐도 따라간다 */
export interface EditorMark {
  readonly id: number;
}

/** 쓰면서 바로 그려지는 편집기를 밖에서 다루는 손잡이 */
export interface RichEditorApi {
  run: (action: EditorAction, payload?: unknown) => void;
  focus: () => void;
  /** 커서를 문서 자리 pos 로(끌어 놓은 자리) */
  placeCursor: (pos: number) => void;
  /** 커서 자리에 Markdown(올리는 중 표시)을 넣고 그 자리를 돌려준다 */
  insertMarker: (markdown: string) => EditorMark | null;
  /** 넣은 자리의 표시를 Markdown 으로 바꾼다. 사람이 그 표시를 지웠으면 그 자리에 넣는다 */
  replaceMarker: (mark: EditorMark | null, markdown: string) => void;
  /** 넣은 자리의 표시를 지운다 */
  removeMarker: (mark: EditorMark | null) => void;
  scrollToHeading: (index: number) => void;
}
