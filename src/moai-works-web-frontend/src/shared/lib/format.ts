import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';

// 언어(dayjs locale)는 shared/i18n 이 화면 언어에 맞춘다
dayjs.extend(relativeTime);

/** 2026-11-19 14:05 */
export function dateTime(value: string | null | undefined): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—';
}

/**
 * 바뀐 때(만듦 · 고침 · 판 · 이력 · 작업 시간을 적은 때) — 밀리초까지, 이 브라우저의 시간대로. 2026-11-19 14:05:03.007
 * 서버의 시각(Instant)은 소수 초를 1 ~ 9자리로 줄 수 있다 — 정확히 3자리로 맞춘 뒤 읽는다(남는 자리는 버리고, 모자라면 0 을 채운다.
 * dayjs 는 ".5" 를 5밀리초로 읽는다).
 * 날짜만 있는 값(완료 기한 · 일한 날 …)에는 쓰지 않는다 — 그것은 {@link date}.
 */
export function dateTimeMs(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const parsed = dayjs(value.replace(/\.(\d+)/, (_, digits: string) => `.${digits.slice(0, 3).padEnd(3, '0')}`));
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss.SSS') : '—';
}

/** 2026-11-19 */
export function date(value: string | null | undefined): string {
  return value ? dayjs(value).format('YYYY-MM-DD') : '—';
}

/** 3분 전 */
export function fromNow(value: string | null | undefined): string {
  return value ? dayjs(value).fromNow() : '—';
}

/** 파일 크기 — 1.2 MB */
export function fileSize(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined) {
    return '—';
  }
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  const units = ['KB', 'MB', 'GB', 'TB'] as const;
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value >= 100 ? value.toFixed(0) : value.toFixed(1)} ${units[unit] ?? 'TB'}`;
}

/** 이름의 첫 글자(아바타) */
export function initials(name: string): string {
  const trimmed = name.trim();
  if (!trimmed) {
    return '?';
  }
  const hangul = /[가-힣]/.test(trimmed);
  return hangul ? trimmed.slice(0, 1) : trimmed.slice(0, 2).toUpperCase();
}
