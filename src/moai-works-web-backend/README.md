# moai-works-web-backend

웹 BFF. 로그인 세션, 사용자 토큰 전달(파일 올리기 · 내려받기도 풀지 않고 그대로), React 정적 파일만 맡는다. 권한 판정 · 업무 로직 · DB 는 없다(모두 moai-works-api 몫). Keycloak 클라이언트 이름은 모듈 이름과 같은 `moai-works-web-backend` 다.

| 항목 | 값 |
|---|---|
| 스택 | Spring Boot 4.2.0 · Spring Security 7.2.0 · Spring Session 4.2.0 (Redis) · Java 21 |
| 포트 | 11002 (`MOAI_WORKS_WEB_PORT`), **HTTPS**(로컬 인증서: 저장소 맨 위에서 `infra/tls/make-certs.sh`) |
| 비밀값 (`.env`, git 제외) | `MOAI_WORKS_WEB_BACKEND_CLIENT_ID` · `MOAI_WORKS_WEB_BACKEND_CLIENT_SECRET` (값은 `infra/keycloak/.env`) |
| 그 밖의 설정 | `MOAI_WORKS_KEYCLOAK_URL` · `MOAI_WORKS_KEYCLOAK_REALM` · `MOAI_WORKS_API_URL` · `MOAI_WORKS_WEB_STATIC` · `MOAI_WORKS_TLS_CA` · `MOAI_WORKS_REDIS_HOST/PORT/PASSWORD/DATABASE` · `MOAI_WORKS_SESSION_COOKIE` · `MOAI_WORKS_SESSION_SECURE`(TLS 를 끈 배치에서만) · `MOAI_WORKS_TLS_ENABLED` · `MOAI_WORKS_TLS_CERT` · `MOAI_WORKS_TLS_KEY` · 로그 `MOAI_WORKS_LOG_*` |

## 빠른 실행

1. moai-works-api(11001) · Keycloak(같이 쓰는 `MOAI-Keycloak` :8180) · Redis(6379, 로컬은 MOAI-Redis)를 띄운다. 로컬 인증서가 없으면 `infra/tls/make-certs.sh`.
2. `.env` 에 위 키와 `MOAI_WORKS_WEB_STATIC=file:src/main/resources/static/` 을 넣는다. 그러면 프론트를 다시 빌드하는 즉시 반영된다.
3. `./mvnw -q package` 뒤 `bin/startup.sh` → https://localhost:11002, 끄기는 `bin/shutdown.sh`. 로그는 `logs/moai-works-web-backend.log`(날짜 · 50MB 마다 넘긴다). `./mvnw spring-boot:run` 도 된다(두 번째 인스턴스는 `MOAI_WORKS_WEB_PORT=11004` 처럼 쓰지 않는 다른 포트).

정적 파일(`src/main/resources/static/`)은 moai-works-web-frontend 의 `npm run build` 결과이고 git 에서 뺀다. 세션은 Redis 키 `moai-works:web-backend:session:…`, 쿠키는 `__Host-MOAI_WORKS_SESSION` 이다.

## 검사

```bash
./mvnw package     # 단위 테스트: relay/ApiRelayTest (중계 규칙 — Redis · Keycloak 없이 돈다)
```

자세한 문서: [docs/modules/02-web-backend.md](../../docs/modules/02-web-backend.md) · 인증 구조: [SECURITY.md](../../SECURITY.md) · 개발 규칙: [DEVELOPE.md](../../DEVELOPE.md)
