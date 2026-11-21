import axios, { type AxiosError } from 'axios';

import i18n from '@/shared/i18n/i18n';

/**
 * 모든 서버 호출의 입구. 같은 출처의 BFF(/api)만 부른다 — 토큰은 브라우저에 없고, 세션 쿠키가 실린다.
 *
 * - CSRF: BFF 가 내려 준 쿠키 XSRF-TOKEN 을 헤더 X-XSRF-TOKEN 으로 보낸다(axios 기본 이름이 Spring 과 같다).
 * - 오류: 서버의 problem+json 을 {@link ApiError} 하나로 바꾼다. 화면은 code 로 분기하고 message 를 보여 준다.
 * - 언어: Accept-Language 에 화면 언어를 싣는다(서버 문장이 언어를 따를 때를 대비).
 * - 토큰은 다루지 않는다 — 브라우저에는 토큰이 없다(BFF 세션 쿠키만).
 */
export const http = axios.create({
  baseURL: '/api',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  headers: { Accept: 'application/json' },
  timeout: 30_000,
});

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: { field: string; message: string }[];
  /** 서버가 오류와 함께 준 그 밖의 값 — 예: 본문 규칙 오류(issues), 이름이 겹친 페이지(conflict) */
  readonly details: Readonly<Record<string, unknown>>;

  constructor(
    status: number,
    code: string,
    message: string,
    fieldErrors: { field: string; message: string }[] = [],
    details: Record<string, unknown> = {},
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.details = details;
  }
}

http.interceptors.request.use((config) => {
  config.headers.set('Accept-Language', i18n.language);
  return config;
});

interface ProblemBody extends Record<string, unknown> {
  status?: number;
  code?: string;
  detail?: string;
  errors?: { field: string; message: string }[];
}

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ProblemBody>) => {
    if (error.response) {
      const body = error.response.data ?? {};
      return Promise.reject(
        new ApiError(
          error.response.status,
          body.code ?? `HTTP_${error.response.status}`,
          body.detail ?? i18n.t('common:errors.request'),
          body.errors ?? [],
          body,
        ),
      );
    }
    return Promise.reject(new ApiError(0, 'NETWORK_ERROR', i18n.t('common:errors.network')));
  },
);

export function isApiError(e: unknown, code?: string): e is ApiError {
  return e instanceof ApiError && (code === undefined || e.code === code);
}

/** 화면에 보일 오류 문장 */
export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    return e.message;
  }
  return i18n.t('common:errors.unknown');
}
