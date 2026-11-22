import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';

import type { IssuePerson } from '@/shared/api/types';
import type koIssue from '@/shared/i18n/resources/ko/issue.json';

type KnownType = keyof typeof koIssue.types;

/** 일감 유형 이름 — 기본 유형은 번역하고, 서버에 새로 생긴 유형은 서버가 준 이름(없으면 키)을 그대로 쓴다. */
export function useIssueTypeName() {
  const { t, i18n } = useTranslation('issue');
  return useCallback(
    (key: string, serverName?: string) =>
      i18n.exists(`issue:types.${key}`) ? t(`types.${key as KnownType}`) : (serverName ?? key),
    [t, i18n],
  );
}

/** 사람 이름 — 보이는 이름, 없으면 아이디 */
export function personName(person: Pick<IssuePerson, 'displayName' | 'username'> | null | undefined): string | null {
  if (!person) {
    return null;
  }
  return person.displayName || person.username || null;
}
