-- 비밀번호 변경 · 재설정 (FR-WSP-006, moai-works 일감 #28 — 2026-11-24 사용자: "로그인 후 자기 비밀번호 변경 기능 있어?" · "응 등록하고 진행해")
--
-- "다른 기기의 로그인 끊기" 와 메일 코드 재설정은 Keycloak 로그인 세션을 지운다. 그래도 이미 나간 액세스 토큰은 api 가
-- 서명만 보고 받으므로(JWKS, 요청마다 Keycloak 에 묻지 않는다) 만료(몇 분)까지 남는다. 그 틈을 막는 표시다:
--   sessions_revoked_at 보다 먼저 시작한 로그인(토큰의 auth_time, 없으면 iat)의 토큰은 401 SESSION_REVOKED 로 거절한다.
--   kept_session_id(Keycloak 세션 id = 토큰의 sid)는 비밀번호를 바꾼 그 로그인 — 그 세션의 토큰만 계속 받는다(재설정이면 비어 있다).
-- 비밀번호 · 코드는 여기에 두지 않는다(비밀번호는 Keycloak 에만, 코드는 email_verification 의 HMAC 만).

ALTER TABLE user_profile
    ADD COLUMN sessions_revoked_at timestamptz,
    ADD COLUMN kept_session_id     varchar(64);
