# moai-works-api

moai-works 의 API 서버. 계정 · 가입 · 워크스페이스 · 역할 · 멤버십 · 초대 · 감사, **모든 권한 판정**, 그리고 **MCP 전부**(바깥 AI Agent 가 붙는 입구)를 맡는다. DB 는 이 모듈만 쓴다. 프로젝트 · 일감 같은 업무 기능도 여기에 들어온다(계획). api 안에서 LLM 을 부르지 않는다.

| 항목 | 값 |
|---|---|
| 스택 | Spring Boot 4.2.0 · Spring Security 7.2.0 · Spring AI 2.1.0 (MCP SDK 2.2.0) · Flyway · PostgreSQL · AWS SDK 2.59.7(S3) · Java 21 |
| 포트 | 11001 (`MOAI_WORKS_API_PORT`), **HTTPS**(로컬 인증서: 저장소 맨 위에서 `infra/tls/make-certs.sh`) — REST `/moai-works-api/**` (Keycloak JWT 직접 검증), MCP 일감 `/mcp/issue` · 프로젝트 관리 `/mcp/project` (MCP 키 하나로 두 입구) |
| 비밀값 (`.env`, git 제외) | `MOAI_WORKS_DB_URL` · `MOAI_WORKS_DB_USERNAME` · `MOAI_WORKS_DB_PASSWORD` · `MOAI_WORKS_KEYCLOAK_URL` · `MOAI_WORKS_KEYCLOAK_REALM` · `MOAI_WORKS_API_CLIENT_ID` · `MOAI_WORKS_API_CLIENT_SECRET` · `MOAI_WORKS_CODE_SECRET`(32자 이상) · `MOAI_WORKS_API_S3_ACCESS_KEY` · `MOAI_WORKS_API_S3_SECRET_KEY`(첨부용 객체 저장소 — 아직 쓰는 기능이 없어 비어 있어도 뜬다) |
| 그 밖의 설정 | `MOAI_WORKS_WEB_BASE_URL`(초대 링크, 기본 https://localhost:11002) · `MOAI_WORKS_MAIL_MODE`(기본 `log`) · `MOAI_WORKS_MAIL_FROM` · `MOAI_WORKS_S3_ENDPOINT` · `MOAI_WORKS_S3_ATTACHMENT_BUCKET`(기본 `moai-works-attachments`) · `MOAI_WORKS_MAX_FILE_MB`(기본 200) · `MOAI_WORKS_TLS_ENABLED` · `MOAI_WORKS_TLS_CERT` · `MOAI_WORKS_TLS_KEY` · 로그 `MOAI_WORKS_LOG_*` |

## 빠른 실행

1. Keycloak(같이 쓰는 `MOAI-Keycloak` :8180 — 렐름은 `infra/keycloak/init-realm.sh`)과 MOAI-DB(Postgres, DB `moai_works`)를 띄운다. 첨부 기능을 만들면 MOAI-MinIO 와 `infra/minio/init-minio.sh`(버킷 · 사용자 · 이 폴더 `.env` 에 저장소 키)도.
2. 이 폴더에 `.env` 를 둔다(키 이름은 위 표, 값의 출처는 `infra/keycloak/.env` · `infra/minio/.env`).
3. `./mvnw -q package` 뒤 `bin/startup.sh` 로 띄우고 `bin/shutdown.sh` 로 끈다. 로그는 `logs/moai-works-api.log`(날짜 · 50MB 마다 넘긴다). IntelliJ 에서 `MoaiWorksApiApplication` 을 **작업 폴더 = 이 모듈 폴더**로 실행하거나 `./mvnw spring-boot:run` 도 된다.
4. 개발용 계정 · 워크스페이스: `python3 -I infra/dev-seed/seed_workspaces.py` (moaiworks01~10, 워크스페이스 A · B · C)

## 검사

```bash
./mvnw package                           # 컴파일 + 자동 테스트(66건) + 기동 검사(접근 규칙)
./mvnw test -Dtest=WorkspaceAccessTest   # 한 클래스만
```

자동 테스트는 MOAI-DB(Postgres)가 떠 있어야 한다. 개발 데이터는 건드리지 않고 같은 DB 의 시험 전용 스키마 `moai_works_test` 를 매번 다시 만든다. Keycloak 과 MinIO 는 필요 없다(가짜 · 메모리 저장소를 쓴다). 자세한 것은 [모듈 문서 §7.1](../../docs/modules/01-api.md#71-자동-테스트-srctest).

자세한 문서: [docs/modules/01-api.md](../../docs/modules/01-api.md) · 인증 · 권한: [SECURITY.md](../../SECURITY.md) · 개발 규칙: [DEVELOPE.md](../../DEVELOPE.md)
