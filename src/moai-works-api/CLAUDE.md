# moai-works-api

DB 정본 · 모든 권한 판정 · MCP 전부. 고치기 전에 [모듈 문서](../../docs/modules/01-api.md)를 읽는다.

- REST 엔드포인트 · 접근 어노테이션 · 권한 코드 · 역할 · Keycloak 쓰기 · MCP 키를 바꾸면 먼저 [SECURITY.md](../../SECURITY.md) §5~§7 을 읽고, 바뀐 내용을 SECURITY.md 와 모듈 문서의 REST 표에 반영한다. 프로젝트 · 일감 엔드포인트(`@ProjectApi` · `@IssueApi`, 계획)는 SECURITY.md §6.7 과 [프로젝트 권한 설계](../../docs/design/04-project-permissions.md)를 따른다.
- MCP 도구는 REST 와 **같은 서비스 · 같은 판정**을 부르고, 범위는 모델이 넘긴 인자가 아니라 `McpCallContext`(키 주인)로 정한다. api 안에서 LLM 을 부르지 않는다. 도구 · 입구를 바꾸면 SECURITY.md §5.2 · §8.3 과 [MCP · AI Agent 설계](../../docs/design/07-mcp-agents.md)를 고친다.
- 스키마는 새 Flyway 파일로만(적용된 파일은 고치지 않는다).
- 검사: `./mvnw -q package`(자동 테스트 포함, MOAI-DB 필요 — 시험 전용 스키마 `moai_works_test`. Keycloak · MinIO 는 가짜를 쓴다). 새 엔드포인트 · 권한 규칙을 만들면 같은 작업에서 `src/test` 에 테스트를 더한다(`@ApiTest` · `TestApi`, [모듈 문서 §7.1](../../docs/modules/01-api.md#71-자동-테스트-srctest)).
