# moai-works-web-frontend

React + MUI 화면. 고치기 전에 [모듈 문서](../../docs/modules/03-web-frontend.md)를 읽는다.

- 화면 · 테마 · 스킨 · 글 · 배치는 [WEB-DESIGN.md](../../WEB-DESIGN.md), 가드 · 저장소 · CSRF · `returnTo` 는 [SECURITY.md](../../SECURITY.md) §9. 규칙을 바꾸면 그 문서도 고친다.
- 권한은 서버가 준 `myPermissions` · `platformRoles`(프로젝트 · 일감은 응답의 `myPermissions` · `myActions`, 계획)로만 숨긴다. 화면이 권한 규칙을 다시 계산하지 않는다.
- 검사: `npm run lint && npm run typecheck && npm test && npm run build` + 브라우저(ego-browser)에서 데스크톱 · 390px · 다크 · 영어.
