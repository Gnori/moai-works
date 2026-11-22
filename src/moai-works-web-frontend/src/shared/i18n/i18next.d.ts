import 'i18next';

import type admin from './resources/ko/admin.json';
import type auth from './resources/ko/auth.json';
import type common from './resources/ko/common.json';
import type issue from './resources/ko/issue.json';
import type workspace from './resources/ko/workspace.json';

/** 키 타입 — 한국어 리소스가 기준. 없는 키를 쓰면 tsc 가 실패한다. */
declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'common';
    resources: {
      common: typeof common;
      auth: typeof auth;
      workspace: typeof workspace;
      issue: typeof issue;
      admin: typeof admin;
    };
    returnNull: false;
  }
}
