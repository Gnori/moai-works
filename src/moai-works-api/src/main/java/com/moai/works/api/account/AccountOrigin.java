package com.moai.works.api.account;

/** 계정이 어디서 생겼나. */
public enum AccountOrigin {
  /** moai-works 가입 화면 */
  SIGNUP,
  /** Keycloak 에서 직접 만든 계정(관리자 · 설치 스크립트)의 첫 로그인 */
  KEYCLOAK
}
