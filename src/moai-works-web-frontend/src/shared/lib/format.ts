import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';

// 언어(dayjs locale)는 shared/i18n 이 화면 언어에 맞춘다
dayjs.extend(relativeTime);

/** 2026-11-19 14:05 */
export function dateTime(value: string | null | undefined): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—';
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
