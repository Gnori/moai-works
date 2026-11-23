/**
 * 새 판이 나와 옛 조각 파일이 없어졌을 때 화면을 한 번 새로 고친다.
 *
 * 화면은 경로마다 따로 받는 조각(…-해시.js)으로 나뉜다. 다시 빌드하면 조각 이름이 바뀌고 옛 파일은 지워지므로, 먼저 열어 둔 탭이
 * 아직 받지 않은 화면으로 가면 없는 파일을 찾다가 "Failed to fetch dynamically imported module" 로 멈췄다(2026-11-23 사용자:
 * "이 에러는 왜 자꾸 나는 거야? 이 페이지뿐만이 아니야"). index.html 은 늘 다시 받으므로(no-cache) 새로 고치면 새 판의 조각 이름을 안다.
 * 같은 실패가 새로 고친 뒤에도 곧바로 되풀이되면(정말 파일이 없거나 연결이 끊김) 다시 새로 고치지 않고 오류를 그대로 보인다.
 */

/** 마지막으로 이 까닭에 새로 고친 시각 — 탭마다(sessionStorage) */
const RELOADED_AT = 'moai-works.chunk-reload-at';
/** 이 안에 또 실패하면 새로 고치지 않는다 */
const RETRY_WINDOW_MS = 10_000;

let reloading = false;

/** 조각 · 그 CSS 를 못 받아서 난 오류인가 — 브라우저마다 글이 다르다 */
export function isChunkLoadError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : typeof error === 'string' ? error : '';
  return /Failed to fetch dynamically imported module|error loading dynamically imported module|Importing a module script failed|Unable to preload CSS/i.test(
    message,
  );
}

/**
 * 한 번 새로 고친다. 새로 고치면 true, 방금 새로 고쳤는데 또 실패한 것이면 false(오류를 보여야 한다).
 * @param now 시험용
 */
export function reloadForNewVersion(now: number = Date.now()): boolean {
  if (reloading) {
    return true;
  }
  try {
    const last = Number(window.sessionStorage.getItem(RELOADED_AT) ?? 0);
    if (now - last < RETRY_WINDOW_MS) {
      return false;
    }
    window.sessionStorage.setItem(RELOADED_AT, String(now));
  } catch {
    // 저장소를 못 쓰면 되풀이를 막을 수 없다 — 새로 고치지 않고 오류를 보인다
    return false;
  }
  reloading = true;
  window.location.reload();
  return true;
}

/**
 * 동적 import 를 감싼다 — 새 판 때문에 조각이 없으면 새로 고치고, 새로 고치는 동안에는 끝나지 않는 약속을 돌려준다
 * (Suspense 가 "불러오는 중"을 보이다가 새 판으로 바뀐다). 그 밖의 오류는 그대로 던진다.
 */
export async function importWithReload<M>(load: () => Promise<M>): Promise<M> {
  try {
    return await load();
  } catch (error) {
    if (isChunkLoadError(error) && reloadForNewVersion()) {
      return new Promise<M>(() => undefined);
    }
    throw error;
  }
}

/**
 * Vite 가 동적 import 의 조각 · CSS 를 못 받으면 보내는 신호 — importWithReload 로 감싸지 않은 곳(mermaid 등)도 새로 고친다.
 * 오류는 그대로 부른 쪽으로 간다(preventDefault 하지 않는다 — 모듈 없이 이어지면 다른 오류가 난다).
 */
export function listenForStaleChunks(): void {
  window.addEventListener('vite:preloadError', (event) => {
    const payload = (event as Event & { payload?: unknown }).payload;
    if (isChunkLoadError(payload)) {
      reloadForNewVersion();
    }
  });
}

/** 시험용 — 모듈 안 상태를 처음으로 */
export function resetChunkReloadForTest(): void {
  reloading = false;
}
