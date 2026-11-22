# moai-works-web-backend

BFF — 로그인 세션(Spring Security · Spring Session Redis) · 토큰 전달 · 정적 파일만. 권한 판정 · 업무 로직 · DB 를 두지 않는다. 고치기 전에 [모듈 문서](../../docs/modules/02-web-backend.md)를 읽는다.

- 로그인 · 세션 · CSRF · 토큰 갱신 · 로그아웃 · 전달 헤더 · 보안 헤더를 바꾸면 먼저 [SECURITY.md](../../SECURITY.md) §4 를 읽고 같은 작업에서 고친다.
- `/api/v1/**` 는 경로 표 없이 api 로 1:1 전달한다. 새 업무 기능(프로젝트 · 일감 …)을 만들어도 여기에 경로를 더하지 않는다. MCP 는 web-backend 가 중계한다(2026-11-22 사용자 결정 — 바깥 MCP 클라이언트는 web-backend 포트로 붙는다). `/mcp/**` 는 로그인 세션 · CSRF 밖에서 api 로 그대로 흘려보내고, 판정은 하지 않는다([MCP 설계 — 중계](../../docs/design/06-mcp.md#mc-relay)).
- 파일 올리기(multipart)는 풀지 않고 api 로 흘려보낸다(`spring.servlet.multipart.enabled: false`). 본문을 읽는 필터 · 컨트롤러를 `/api/v1/**` 앞에 두지 않는다.
- 검사: `./mvnw -q package` + 로그인 → 요청 → 로그아웃 → 401, 실패한 로그인이 세션을 만들지 않는지.
