/**
 * 새 화면을 올린 뒤에도 열려 있던 창이 옛 화면을 계속 쓰는 것을 알린다(일감 #311 — 2026-11-26 사용자: "#304 이건 처리했다는대 왜? 안보이는것 같지?"
 * → "아니다 이거 강력 새로고침 아니까 나오네").
 *
 * 화면은 한 번 열면 그 안에서만 오가므로(SPA) index.html 을 다시 읽지 않는다. 빌드할 때 판 이름을 화면 코드와 version.json 에 함께 넣고,
 * 열린 창이 version.json 을 가끔 읽어 자기 판과 다르면 새 버전이 있다고 본다. 저절로 새로 고치지는 않는다 — 쓰던 글을 날리지 않게 사람이 고른다.
 * 옛 조각 파일이 없어져 화면이 멈추는 경우는 chunk-reload 가 따로 한 번 새로 고친다.
 */

/** 이 화면의 판 — 개발 서버 · 시험에서는 없다(그때는 묻지 않는다) */
export const APP_BUILD: string | undefined = import.meta.env.VITE_APP_BUILD;
/** 판을 이만큼마다 묻는다(창으로 돌아올 때도). 사람이 손을 놓은 동안은 묻지 않는다 */
export const VERSION_CHECK_MS = 5 * 60_000;
export const VERSION_URL = '/version.json';

/** 서버의 지금 판 — 읽지 못하면 null(그 판은 건너뛴다) */
export async function fetchServerBuild(): Promise<string | null> {
  try {
    const response = await fetch(VERSION_URL, { cache: 'no-store', credentials: 'same-origin' });
    if (!response.ok) {
      return null;
    }
    const body: unknown = await response.json();
    const build = (body as { build?: unknown } | null)?.build;
    return typeof build === 'string' && build.length > 0 ? build : null;
  } catch {
    return null;
  }
}

/** 서버의 판이 이 화면과 다른가 — 이 화면의 판을 모르거나 서버 판을 못 읽었으면 아니다 */
export function isNewBuild(server: string | null, mine: string | undefined): boolean {
  return !!server && !!mine && server !== mine;
}
