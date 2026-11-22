import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';

import { date, fileSize } from '@/shared/lib/format';
import { useLabels } from '@/shared/lib/labels';
import type koIssue from '@/shared/i18n/resources/ko/issue.json';

import { useIssueTypeName } from './issue-labels';
import type { ChangeValue, ReadableChange } from './journal';

type KnownField = keyof typeof koIssue.fields;

/**
 * 이력 · 리비전 차이의 말 — 일감 보기(이력)와 리비전 화면이 같이 쓴다.
 * 편집기(CodeMirror)를 끌고 오지 않게 화면 파일과 나눠 둔다(리비전 화면 조각이 가볍게).
 */

/** 필드 이름 — 아는 필드는 번역, 서버가 새로 더한 필드는 이름 그대로 */
export function useFieldName() {
  const { t, i18n } = useTranslation('issue');
  return useCallback(
    (field: string) => (i18n.exists(`issue:fields.${field}`) ? t(`fields.${field as KnownField}`) : field),
    [t, i18n],
  );
}

/** 바뀐 값 하나를 말로 — 사람은 이름, 상태 · 유형은 번역, 상위 일감은 #번호, 시간은 "8시간" */
export function useValueText() {
  const { t } = useTranslation('issue');
  const labels = useLabels();
  const typeName = useIssueTypeName();
  return useCallback(
    (v: ChangeValue): string => {
      switch (v.kind) {
        case 'empty':
          return t('journal.empty');
        case 'person':
          return v.name;
        case 'issue':
          return `#${v.number}`;
        case 'status':
          return labels.issueStatus(v.status);
        case 'type':
          return typeName(v.key);
        case 'date':
          return date(v.date);
        case 'hours':
          return t('hours.value', { hours: v.hours });
        case 'text':
          return v.text;
      }
    },
    [t, labels, typeName],
  );
}

/**
 * 바뀐 필드 하나를 사람이 읽는 한 줄로 — "상태: 신규 → 진행" · "연결 일감 추가: 차단 #12" · "작업 시간 기록: 김철수 · 2026-11-22 · 2시간" ·
 * "첨부 추가: 화면.png (1.2 MB)" · "첨부 삭제: 화면.png"
 */
export function useChangeText() {
  const { t } = useTranslation('issue');
  const labels = useLabels();
  const value = useValueText();
  const fieldName = useFieldName();
  return useCallback(
    (change: ReadableChange): string => {
      switch (change.kind) {
        case 'description':
          return t('journal.descriptionChanged');
        case 'link':
          return change.type && change.number !== null
            ? t(change.action === 'added' ? 'journal.linkAdded' : 'journal.linkRemoved', {
                link: change.direction ? t(`links.${change.direction}.${change.type}`) : labels.issueLinkType(change.type),
                number: change.number,
              })
            : `${t('fields.link')}: ${change.raw}`;
        case 'workLog': {
          const parts = [change.user, change.date && date(change.date), change.hours && t('hours.value', { hours: change.hours })]
            .filter(Boolean)
            .join(' · ');
          const entry = change.note ? `${parts} — ${change.note}` : parts;
          return t(change.action === 'added' ? 'journal.workLogAdded' : 'journal.workLogRemoved', { entry });
        }
        case 'attachment': {
          const name = change.name ?? t('journal.unnamedFile');
          return change.action === 'added'
            ? t('journal.attachmentAdded', { entry: change.size !== null ? `${name} (${fileSize(change.size)})` : name })
            : t('journal.attachmentRemoved', { name });
        }
        case 'field':
        case 'unknown':
          return t('journal.changed', { field: fieldName(change.field), before: value(change.before), after: value(change.after) });
      }
    },
    [t, labels, value, fieldName],
  );
}
