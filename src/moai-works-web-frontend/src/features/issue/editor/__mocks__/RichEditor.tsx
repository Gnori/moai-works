import { useEffect, useImperativeHandle, useRef, useState, type ClipboardEvent } from 'react';

import type { RichEditorProps } from '../RichEditor';
import type { EditorMark } from '../editor-types';

/**
 * 시험용 편집기 — jsdom 에서는 Milkdown(contenteditable · 글자 자리 재기 · CodeMirror)이 돌지 않아 이것으로 바꾼다(test-setup 의 vi.mock).
 * 진짜 편집기와 같은 손잡이(RichEditorApi)와 알림(onMarkdown · onFiles)을 흉내 낸다. 글은 Markdown 그대로 보이고, 커서는 늘 글 끝이다.
 * 진짜 편집기의 동작(그리기 · 명령 · 원문 보존)은 브라우저 확인과 markdown-preserve 시험이 맡는다.
 */
export default function FakeRichEditor({ value, onReady, onMarkdown, ariaLabel, onFiles, ref }: RichEditorProps) {
  const [text, setText] = useState(value);
  const textRef = useRef(value);
  const marks = useRef(new Map<number, string>());
  const next = useRef(1);

  const change = (updated: string) => {
    textRef.current = updated;
    setText(updated);
    onMarkdown(updated);
  };

  // 진짜 편집기처럼 만든 직후 baseline 을 알린다(시험용은 글을 다시 적지 않으므로 원문과 같다). 다시 만들기는 감싸는 쪽의 key 가 한다
  useEffect(() => {
    onReady?.(value);
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
    <div data-testid="rich-editor" role="textbox" aria-multiline="true" aria-label={ariaLabel} tabIndex={0} onPaste={paste}>
      {text}
    </div>
  );
}
