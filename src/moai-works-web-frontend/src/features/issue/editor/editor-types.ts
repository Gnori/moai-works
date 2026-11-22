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

/** 커서 자리의 서식 — 도구 막대 단추의 눌림 표시(heading 은 제목 수준, 문단이면 0) */
export interface EditorActive {
  bold: boolean;
  italic: boolean;
  strike: boolean;
  inlineCode: boolean;
  heading: number;
}

export const noActive: EditorActive = { bold: false, italic: false, strike: false, inlineCode: false, heading: 0 };

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
  /**
   * 지금 글을 바로 내놓는다 — 편집기의 바뀜 알림은 조금 늦게(약 200ms) 오므로, 원문 보기로 바꾸기 직전처럼 편집 칸을 떠나지 않고
   * 다른 일을 할 때 부른다(늦게 오는 알림을 기다리면 마지막에 친 글을 잃는다)
   */
  flush: () => void;
}
