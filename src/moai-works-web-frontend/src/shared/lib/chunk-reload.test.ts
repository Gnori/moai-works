import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { importWithReload, isChunkLoadError, reloadForNewVersion, resetChunkReloadForTest } from './chunk-reload';

/** 새 판이 나와 옛 조각이 없어졌을 때 한 번만 새로 고친다 */

const reload = vi.fn();
const chunkError = new TypeError('Failed to fetch dynamically imported module: https://localhost:11002/assets/ProjectMembersPage-D4KJamOQ.js');

beforeEach(() => {
  window.sessionStorage.clear();
  resetChunkReloadForTest();
  reload.mockClear();
  vi.stubGlobal('location', { ...window.location, reload });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('조각을 못 받은 오류', () => {
  it('브라우저마다 다른 글을 알아본다', () => {
    expect(isChunkLoadError(chunkError)).toBe(true);
    expect(isChunkLoadError(new TypeError('error loading dynamically imported module: /assets/a.js'))).toBe(true);
    expect(isChunkLoadError(new TypeError('Importing a module script failed.'))).toBe(true);
    expect(isChunkLoadError(new Error('Unable to preload CSS for /assets/a.css'))).toBe(true);
    expect(isChunkLoadError(new Error('Cannot read properties of undefined'))).toBe(false);
    expect(isChunkLoadError(undefined)).toBe(false);
  });
});

describe('새로 고치기', () => {
  it('한 번 새로 고치고, 10초 안에 또 실패하면 새로 고치지 않는다(되풀이 막기)', () => {
    expect(reloadForNewVersion(100_000)).toBe(true);
    expect(reload).toHaveBeenCalledTimes(1);
    resetChunkReloadForTest(); // 새로 고친 뒤의 새 화면
    expect(reloadForNewVersion(105_000)).toBe(false);
    expect(reload).toHaveBeenCalledTimes(1);
    resetChunkReloadForTest();
    expect(reloadForNewVersion(111_000)).toBe(true);
    expect(reload).toHaveBeenCalledTimes(2);
  });

  it('조각을 못 받으면 새로 고치고 끝나지 않는 약속을 돌려준다 — 오류 화면을 보이지 않는다', async () => {
    let settled = false;
    void importWithReload(() => Promise.reject(chunkError)).then(
      () => (settled = true),
      () => (settled = true),
    );
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(reload).toHaveBeenCalledTimes(1);
    expect(settled).toBe(false);
  });

  it('방금 새로 고쳤는데 또 못 받으면 오류를 그대로 던진다', async () => {
    window.sessionStorage.setItem('moai-works.chunk-reload-at', String(Date.now()));
    await expect(importWithReload(() => Promise.reject(chunkError))).rejects.toBe(chunkError);
    expect(reload).not.toHaveBeenCalled();
  });

  it('다른 오류는 새로 고치지 않고 그대로 던진다', async () => {
    const other = new Error('모듈 안에서 난 오류');
    await expect(importWithReload(() => Promise.reject(other))).rejects.toBe(other);
    expect(reload).not.toHaveBeenCalled();
  });

  it('받으면 모듈을 그대로 준다', async () => {
    await expect(importWithReload(() => Promise.resolve({ ok: 1 }))).resolves.toEqual({ ok: 1 });
  });
});
