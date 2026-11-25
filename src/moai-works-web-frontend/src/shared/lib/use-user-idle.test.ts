import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { IDLE_CHECK_MS, IDLE_MS, useUserIdle } from './use-user-idle';

describe('사람이 손을 놓았는지', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('2분 동안 아무것도 하지 않으면 손을 놓은 것, 건드리면 바로 돌아온다', () => {
    const { result, unmount } = renderHook(() => useUserIdle());
    expect(result.current).toBe(false);

    act(() => vi.advanceTimersByTime(IDLE_MS - IDLE_CHECK_MS));
    expect(result.current).toBe(false);
    act(() => vi.advanceTimersByTime(IDLE_CHECK_MS * 2));
    expect(result.current).toBe(true);

    act(() => window.dispatchEvent(new Event('keydown')));
    expect(result.current).toBe(false);
    unmount();
  });

  it('마우스를 움직이는 동안은 손을 놓지 않은 것이다', () => {
    const { result, unmount } = renderHook(() => useUserIdle());
    for (let i = 0; i < 6; i++) {
      act(() => vi.advanceTimersByTime(IDLE_MS / 2));
      act(() => window.dispatchEvent(new Event('pointermove')));
    }
    expect(result.current).toBe(false);
    unmount();
  });
});
