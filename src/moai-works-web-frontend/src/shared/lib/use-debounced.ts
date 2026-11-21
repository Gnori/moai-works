import { useEffect, useState } from 'react';

/** 값이 ms 동안 바뀌지 않으면 그 값을 돌려준다 — 글자를 칠 때마다 서버를 부르지 않게 한다. */
export function useDebounced<T>(value: T, ms: number): T {
  const [settled, setSettled] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => setSettled(value), ms);
    return () => window.clearTimeout(timer);
  }, [value, ms]);
  return settled;
}
