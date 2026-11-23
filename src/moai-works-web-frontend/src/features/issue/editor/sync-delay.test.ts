import { describe, expect, it } from 'vitest';

import { syncDelay } from './sync-delay';

describe('syncDelay — moai-wiki 와 같은 값', () => {
  it('8천 자 아래 120ms · 2만 자 아래 300ms · 그 위 600ms', () => {
    expect(syncDelay(0)).toBe(120);
    expect(syncDelay(7_999)).toBe(120);
    expect(syncDelay(8_000)).toBe(300);
    expect(syncDelay(19_999)).toBe(300);
    expect(syncDelay(20_000)).toBe(600);
    expect(syncDelay(30_000)).toBe(600);
  });
});
