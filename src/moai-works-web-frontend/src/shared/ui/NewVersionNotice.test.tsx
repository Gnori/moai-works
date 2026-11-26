import { act, render } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import '@/shared/i18n/i18n';
import { VERSION_CHECK_MS, isNewBuild } from '@/shared/lib/app-version';

const toastInfo = vi.fn();
vi.mock('sonner', () => ({ toast: { info: (...args: unknown[]) => toastInfo(...args) } }));

const { NewVersionNotice } = await import('./NewVersionNotice');

function serverBuild(build: string | null, ok = true) {
  return vi.fn(async () => ({ ok, json: async () => (build === null ? {} : { build }) }) as unknown as Response);
}

/** 기다리는 약속(fetch · json)을 모두 흘려보낸다 */
async function flush() {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
  });
}

describe('새 버전 알림', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    toastInfo.mockReset();
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('판이 다를 때만 새 버전이다 — 판을 모르면(개발 서버) · 서버 판을 못 읽으면 아니다', () => {
    expect(isNewBuild('b2', 'b1')).toBe(true);
    expect(isNewBuild('b1', 'b1')).toBe(false);
    expect(isNewBuild(null, 'b1')).toBe(false);
    expect(isNewBuild('b2', undefined)).toBe(false);
  });

  it('같은 판이면 조용하고, 서버 판이 바뀌면 남는 알림을 한 번 띄운다', async () => {
    const fetchMock = serverBuild('b1');
    vi.stubGlobal('fetch', fetchMock);
    const { unmount } = render(<NewVersionNotice build="b1" />);
    await flush();
    expect(fetchMock).toHaveBeenCalledWith('/version.json', expect.objectContaining({ cache: 'no-store' }));
    expect(toastInfo).not.toHaveBeenCalled();

    // 쓰는 동안(손을 놓지 않음) 정해진 간격이 지나면 다시 묻는다
    vi.stubGlobal('fetch', serverBuild('b2'));
    for (let waited = 0; waited < VERSION_CHECK_MS; waited += 60_000) {
      act(() => window.dispatchEvent(new Event('pointermove')));
      await act(async () => vi.advanceTimersByTime(60_000));
    }
    await flush();
    expect(toastInfo).toHaveBeenCalledTimes(1);
    const [title, options] = toastInfo.mock.calls[0] as [string, { duration: number; action: { label: string } }];
    expect(title).toBe('새 버전이 있습니다');
    expect(options.duration).toBe(Infinity);
    expect(options.action.label).toBe('새로고침');

    // 한 번 알렸으면 더 묻지 않는다
    const later = serverBuild('b3');
    vi.stubGlobal('fetch', later);
    act(() => document.dispatchEvent(new Event('visibilitychange')));
    for (let waited = 0; waited < VERSION_CHECK_MS * 2; waited += 60_000) {
      act(() => window.dispatchEvent(new Event('pointermove')));
      await act(async () => vi.advanceTimersByTime(60_000));
    }
    await flush();
    expect(later).not.toHaveBeenCalled();
    expect(toastInfo).toHaveBeenCalledTimes(1);
    unmount();
  });

  it('손을 놓은 동안은 묻지 않고, 돌아오면 바로 한 번 묻는다', async () => {
    vi.stubGlobal('fetch', serverBuild('b1'));
    const { unmount } = render(<NewVersionNotice build="b1" />);
    await flush();
    const whileIdle = serverBuild('b2');
    vi.stubGlobal('fetch', whileIdle);
    // 1분씩 흘려 손을 놓은 것이 화면에 닿게 한다(한 번에 흘리면 다시 그리기 전에 타이머가 돈다)
    for (let waited = 0; waited < VERSION_CHECK_MS * 3; waited += 60_000) {
      await act(async () => vi.advanceTimersByTime(60_000));
    }
    await flush();
    expect(whileIdle).not.toHaveBeenCalled();

    act(() => window.dispatchEvent(new Event('keydown')));
    await flush();
    expect(whileIdle).toHaveBeenCalledTimes(1);
    expect(toastInfo).toHaveBeenCalledTimes(1);
    unmount();
  });

  it('판을 모르는 화면(개발 서버)은 묻지 않고, 서버가 판을 못 주면 알리지 않는다', async () => {
    const fetchMock = serverBuild('b2');
    vi.stubGlobal('fetch', fetchMock);
    const first = render(<NewVersionNotice />);
    await flush();
    expect(fetchMock).not.toHaveBeenCalled();
    first.unmount();

    vi.stubGlobal('fetch', serverBuild(null, false));
    const second = render(<NewVersionNotice build="b1" />);
    await flush();
    expect(toastInfo).not.toHaveBeenCalled();
    second.unmount();
  });
});
