import dayjs from 'dayjs';
import 'dayjs/locale/en';
import 'dayjs/locale/ko';
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

import { readSetting, storageKeys, writeSetting } from '@/shared/lib/storage';

import enAdmin from './resources/en/admin.json';
import enAuth from './resources/en/auth.json';
import enCommon from './resources/en/common.json';
import enEffort from './resources/en/effort.json';
import enIssue from './resources/en/issue.json';
import enMcp from './resources/en/mcp.json';
import enWorkspace from './resources/en/workspace.json';
import koAdmin from './resources/ko/admin.json';
import koAuth from './resources/ko/auth.json';
import koCommon from './resources/ko/common.json';
import koEffort from './resources/ko/effort.json';
import koIssue from './resources/ko/issue.json';
import koMcp from './resources/ko/mcp.json';
import koWorkspace from './resources/ko/workspace.json';

/**
 * 다국어 — 한국어가 기본이자 대체 언어. 화면의 모든 글은 resources/<언어>/<묶음>.json 에 있다.
 * 묶음(namespace): common(공통 · 메뉴 · 상태) · auth(로그인 · 가입) · workspace(워크스페이스 화면 · 권한 이름 · 프로젝트) ·
 * issue(일감 · 목록 · 편집 · 이력 — moai-wiki 의 page 자리) · effort(공수 집계) · mcp(MCP 키 — 내 키 · 워크스페이스 키 관리 · 연결 안내) · admin(플랫폼 관리).
 * 키가 없으면 타입 검사가 실패한다(i18next.d.ts). 두 언어의 키가 같은지는 i18n.test.ts 가 확인한다.
 */
export const languages = ['ko', 'en'] as const;
export type Language = (typeof languages)[number];
export const defaultLanguage: Language = 'ko';

export const resources = {
  ko: { common: koCommon, auth: koAuth, workspace: koWorkspace, issue: koIssue, effort: koEffort, mcp: koMcp, admin: koAdmin },
  en: { common: enCommon, auth: enAuth, workspace: enWorkspace, issue: enIssue, effort: enEffort, mcp: enMcp, admin: enAdmin },
} as const;

export function isLanguage(value: string | null | undefined): value is Language {
  return !!value && (languages as readonly string[]).includes(value);
}

/** 저장한 언어 → 없으면 한국어 (브라우저 언어로 바꾸지 않는다 — 사내 기본 언어가 한국어) */
function initialLanguage(): Language {
  const saved = readSetting(storageKeys.language);
  return isLanguage(saved) ? saved : defaultLanguage;
}

void i18n.use(initReactI18next).init({
  resources,
  lng: initialLanguage(),
  fallbackLng: defaultLanguage,
  supportedLngs: [...languages],
  ns: ['common', 'auth', 'workspace', 'issue', 'effort', 'mcp', 'admin'],
  defaultNS: 'common',
  interpolation: { escapeValue: false },
  returnNull: false,
});

function applyLanguage(lng: string) {
  const language = isLanguage(lng) ? lng : defaultLanguage;
  dayjs.locale(language);
  if (typeof document !== 'undefined') {
    document.documentElement.lang = language;
  }
}
applyLanguage(i18n.language);
i18n.on('languageChanged', applyLanguage);

/** 언어를 바꾸고 기억한다 */
export async function changeLanguage(language: Language): Promise<void> {
  writeSetting(storageKeys.language, language);
  await i18n.changeLanguage(language);
}

export function currentLanguage(): Language {
  return isLanguage(i18n.language) ? i18n.language : defaultLanguage;
}

export default i18n;
