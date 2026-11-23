import { useEffect, useImperativeHandle, useRef, useState, type ClipboardEvent } from 'react';

import type { RichEditorProps } from '../RichEditor';
import type { EditorMark } from '../editor-types';

/** 진짜 편집기(RichEditor 의 SYNC_DELAY)와 같다 */
const FAKE_SYNC_DELAY = 120;

/**
 * 시험용 편집기 — jsdom 에서는 Milkdown(contenteditable · 글자 자리 재기 · CodeMirror)이 돌지 않아 이것으로 바꾼다(test-setup 의 vi.mock).
 * 진짜 편집기와 같은 손잡이(RichEditorApi)와 알림(onMarkdown · onFiles · onPending)을 흉내 낸다. 글은 Markdown 그대로 보이고, 커서는 늘 글 끝이다.
 * 올리는 때도 진짜와 같다: 글이 바뀐 순간 onPending(true), 글(onMarkdown)은 마지막 바뀜 뒤 SYNC_DELAY 에 — flush · 칸을 떠남 · 사라짐은 바로.
 * 진짜 편집기의 동작(그리기 · 명령 · 원문 보존)은 브라우저 확인과 markdown-preserve 시험이 맡는다.
 */
export default function FakeRichEditor({ value, onReady, onMarkdown, onPending, ariaLabel, onFiles, ref }: RichEditorProps) {
  const [text, setText] = useState(value);
  const textRef = useRef(value);
  const marks = useRef(new Map<number, string>());
  const next = useRef(1);
  const latest = useRef({ onMarkdown, onPending });
  useEffect(() => {
    latest.current = { onMarkdown, onPending };
  });
  const sync = useRef({ pending: false, timer: undefined as ReturnType<typeof setTimeout> | undefined });

  const flush = () => {
    const state = sync.current;
    clearTimeout(state.timer);
    state.timer = undefined;
    if (state.pending) {
      state.pending = false;
      latest.current.onMarkdown(textRef.current);
      latest.current.onPending?.(false);
    }
  };

  const change = (updated: string) => {
    textRef.current = updated;
    setText(updated);
    const state = sync.current;
    if (!state.pending) {
      state.pending = true;
      latest.current.onPending?.(true);
    }
    clearTimeout(state.timer);
    state.timer = setTimeout(flush, FAKE_SYNC_DELAY);
  };

  // 진짜 편집기처럼 만든 직후 baseline 을 알린다(시험용은 글을 다시 적지 않으므로 원문과 같다). 다시 만들기는 감싸는 쪽의 key 가 한다.
  // 사라질 때는 남은 글을 올린다
  useEffect(() => {
    onReady?.(value);
    return flush;
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 만들 때 한 번
  }, []);

  useImperativeHandle(ref, () => ({
    run: () => undefined,
    focus: () => undefined,
    placeCursor: () => undefined,
    insertMarker: (markdown: string) => {
      const id = next.current++;
      marks.current.set(id, markdown);
      change(textRef.current + markdown);
      return { id };
    },
    replaceMarker: (mark: EditorMark | null, markdown: string) => {
      const placed = mark ? marks.current.get(mark.id) : undefined;
      if (!mark || placed === undefined) {
        return;
      }
      marks.current.delete(mark.id);
      change(textRef.current.includes(placed) ? textRef.current.replace(placed, markdown) : textRef.current + markdown);
    },
    removeMarker: (mark: EditorMark | null) => {
      const placed = mark ? marks.current.get(mark.id) : undefined;
      if (!mark || placed === undefined) {
        return;
      }
      marks.current.delete(mark.id);
      change(textRef.current.replace(placed, ''));
    },
    scrollToHeading: () => undefined,
    flush,
  }));

  const paste = (event: ClipboardEvent<HTMLDivElement>) => {
    const files = Array.from(event.clipboardData?.files ?? []);
    event.preventDefault();
    if (files.length > 0) {
      onFiles?.(files, null);
      return;
    }
    const pasted = event.clipboardData?.getData('text/plain') ?? '';
    if (pasted) {
      change(textRef.current + pasted);
    }
  };

  return (
    <div data-testid="rich-editor" role="textbox" aria-multiline="true" aria-label={ariaLabel} tabIndex={0} onPaste={paste} onBlur={flush}>
      {text}
    </div>
  );
}
