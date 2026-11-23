import { useCallback, useRef, useState } from 'react';

import type { MarkdownEditorHandle } from './MarkdownEditor';

/**
 * Markdown 편집기 손잡이 — 편집기에 넘길 ref · "아직 바깥 값으로 올리지 않은 글이 있다"(pending) · 바로 올리기(flush).
 * 편집기는 쓰는 동안 문서 전체를 적지 않으려고 글을 마지막 입력 뒤 조금 늦게(120ms, 큰 글은 300 · 600ms) 바깥 값으로 올린다(moai-wiki 995b202 · 1232b13 과 같다). 그래서
 *   - 저장 단추 · "저장 전" 표시는 값만 보지 말고 pending 도 본다 — 친 순간 켜진다
 *   - 저장 · 유형 바꾸기처럼 지금 글이 필요한 곳은 flush() 가 돌려주는 글을 쓴다(그 순간 바깥 값에도 올린다). 편집기가 없으면 null
 */
export function useMarkdownEditor() {
  const editorRef = useRef<MarkdownEditorHandle>(null);
  const [pending, setPending] = useState(false);
  const flush = useCallback((): string | null => editorRef.current?.flush() ?? null, []);
  return { editorRef, pending, onPending: setPending, flush };
}

/** 편집기 손잡이 — 양식에 넘긴다 */
export type MarkdownEditorControl = ReturnType<typeof useMarkdownEditor>;
