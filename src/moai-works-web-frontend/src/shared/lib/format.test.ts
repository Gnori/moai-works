import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';

import { dateTimeMs } from './format';

describe('바뀐 때를 밀리초까지 (dateTimeMs)', () => {
  it('밀리초를 세 자리로 채운다', () => {
    // 시간대 표시가 없는 값은 이 브라우저의 시간대로 읽는다 — 그대로 나온다
    expect(dateTimeMs('2026-11-22T09:03:04.005')).toBe('2026-11-22 09:03:04.005');
    expect(dateTimeMs('2026-11-22T09:03:04.5')).toBe('2026-11-22 09:03:04.500');
    expect(dateTimeMs('2026-11-22T09:03:04')).toBe('2026-11-22 09:03:04.000');
  });

  it('소수 초가 더 길면 앞 세 자리만 쓴다(반올림하지 않는다)', () => {
    expect(dateTimeMs('2026-11-22T09:03:04.123456')).toBe('2026-11-22 09:03:04.123');
    expect(dateTimeMs('2026-11-22T09:03:04.999999999')).toBe('2026-11-22 09:03:04.999');
  });

  it('서버의 UTC 시각(Z)은 이 브라우저의 시간대로 바꿔 보인다', () => {
    const expected = dayjs('2026-11-22T00:00:00.007Z').format('YYYY-MM-DD HH:mm:ss.SSS');
    expect(dateTimeMs('2026-11-22T00:00:00.007Z')).toBe(expected);
    expect(dateTimeMs('2026-11-22T00:00:00.007123Z')).toBe(expected);
    expect(expected.endsWith('.007')).toBe(true);
  });

  it('값이 없거나 읽을 수 없으면 —', () => {
    expect(dateTimeMs(null)).toBe('—');
    expect(dateTimeMs(undefined)).toBe('—');
    expect(dateTimeMs('')).toBe('—');
    expect(dateTimeMs('not a time')).toBe('—');
  });
});
