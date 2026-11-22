import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * 요소가 실제로 받은 너비 — 창 너비가 아니라 그 칸의 너비로 배치를 정할 때 쓴다(ResizeObserver).
 * 주 메뉴를 접고 펴면 같은 창에서도 칸 너비가 바뀌기 때문이다(moai-wiki 와 같은 방식).
 * ResizeObserver 가 없는 곳(시험의 jsdom)이나 아직 그리기 전에는 null 이다 — 부르는 쪽은 좁은 배치로 둔다.
 */
export function useElementWidth<T extends HTMLElement>(): [(node: T | null) => void, number | null] {
  const [width, setWidth] = useState<number | null>(null);
  const observer = useRef<ResizeObserver | null>(null);

  const ref = useCallback((node: T | null) => {
    observer.current?.disconnect();
    observer.current = null;
    if (!node || typeof ResizeObserver === 'undefined') {
      return;
    }
    const next = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (entry) {
        setWidth(Math.round(entry.contentRect.width));
      }
    });
    next.observe(node);
    observer.current = next;
  }, []);

  useEffect(() => () => observer.current?.disconnect(), []);
  return [ref, width];
}
