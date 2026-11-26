import i18n from '@/shared/i18n/i18n';
import { ApiError } from '@/shared/api/http';

/**
 * 첨부 내용 받기(첨부 보기) — 내용 주소(attachmentApi.contentUrl, 같은 출처의 BFF)에서 바이트를 받는다.
 *
 * axios 는 브라우저에서 받는 중간에 끊을 수 없어(XHR) 이것만 fetch 로 한다 — 같은 출처라 세션 쿠키가 함께 가고,
 * GET 이라 CSRF 머리글은 필요 없다. 글로 보는 형식은 limit 바이트까지만 받고 나머지는 끊는다(api 는 Range 를 받지 않는다).
 * 오류는 서버의 problem+json 을 읽어 http.ts 와 같은 ApiError 로 바꾼다.
 */

export interface Fetched {
  bytes: Uint8Array<ArrayBuffer>;
  /** limit 에서 끊었다 — 뒤가 더 있다 */
  truncated: boolean;
}

export async function fetchAttachment(url: string, options: { limit?: number; signal?: AbortSignal } = {}): Promise<Fetched> {
  const { limit, signal } = options;
  let response: Response;
  try {
    response = await fetch(url, { credentials: 'same-origin', signal, headers: { 'Accept-Language': i18n.language } });
  } catch (e) {
    if (signal?.aborted) {
      throw e;
    }
    throw new ApiError(0, 'NETWORK_ERROR', i18n.t('common:errors.network'));
  }
  if (!response.ok) {
    throw await problem(response);
  }
  const body = limit !== undefined ? response.body : null;
  if (limit === undefined || !body) {
    const all = new Uint8Array(await response.arrayBuffer());
    return limit !== undefined && all.length > limit ? { bytes: all.slice(0, limit), truncated: true } : { bytes: all, truncated: false };
  }
  const reader = body.getReader();
  const chunks: Uint8Array[] = [];
  let received = 0;
  let truncated = false;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    chunks.push(value);
    received += value.length;
    if (received > limit) {
      truncated = true;
      await reader.cancel().catch(() => undefined);
      break;
    }
  }
  const bytes = new Uint8Array(Math.min(received, limit));
  let offset = 0;
  for (const chunk of chunks) {
    const part = chunk.subarray(0, bytes.length - offset);
    bytes.set(part, offset);
    offset += part.length;
    if (offset >= bytes.length) {
      break;
    }
  }
  return { bytes, truncated };
}

async function problem(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as { code?: string; detail?: string };
    return new ApiError(response.status, body.code ?? `HTTP_${response.status}`, body.detail ?? i18n.t('common:errors.request'));
  } catch {
    return new ApiError(response.status, `HTTP_${response.status}`, i18n.t('common:errors.request'));
  }
}
