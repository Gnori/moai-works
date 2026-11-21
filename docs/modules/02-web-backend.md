[홈](../README.md) › [모듈](00-overview.md) › moai-works-web-backend

# moai-works-web-backend

웹 BFF. **로그인 세션, 사용자 토큰 전달, React 정적 파일**만 맡는다. Keycloak 클라이언트 이름은 모듈 이름과 같은 `moai-works-web-backend` 다. moai-wiki 의 web-backend 를 그대로 가져와 이름만 바꿨다.

## 1. 하는 일 · 하지 않는 일

| 한다 | 하지 않는다 |
|---|---|
| 아이디 · 비밀번호로 Keycloak 패스워드 그랜트를 대신 하고, 토큰을 **서버 세션(Redis)** 에만 둔다 | 권한 판정 (무엇을 할 수 있는지는 api 가 정한다) |
| `/api/v1/**` 를 그 사용자의 토큰과 함께 api 로 1:1 전달한다. 파일 올리기 · 내려받기도 풀거나 담아 두지 않고 그대로 흘려보낸다 | 업무 로직, DB, 올린 파일 검사(크기 · 이름 · 권한은 api) |
| CSRF · 세션 고정 방지 · 보안 헤더 · 로그인 시도 제한 | 사용자 동작에 서비스 토큰 쓰기 (api 가 누가 했는지 알아야 한다) |
| React 빌드 결과를 정적 파일로 서비스한다 | 브라우저에 토큰 주기 |
| | MCP 중계 — 바깥 AI Agent 는 BFF 를 거치지 않고 api 의 MCP 입구로 바로 붙는다 |

## 2. 한눈에

| 항목 | 값 |
|---|---|
| 스택 | Spring Boot 4.2.0 · Spring Security 7.2.0 · Spring Session 4.2.0 (`spring-boot-starter-session-data-redis`) · RestClient · Java 21 · Maven (`mvnw`) |
| 패키지 | `com.moai.works.web` |
| 포트 | 11002 (`MOAI_WORKS_WEB_PORT`), **HTTPS** — `server.ssl.*` ← `MOAI_WORKS_TLS_ENABLED`(기본 true) · `MOAI_WORKS_TLS_CERT` · `MOAI_WORKS_TLS_KEY`. 로컬 인증서는 `infra/tls/make-certs.sh`([DEVELOPE.md §5.2](../../DEVELOPE.md#52-포트--https)) |
| 세션 | Redis. 키 이름표 `moai-works:web-backend:session`(키는 `…:sessions:<세션 id>`), 8시간, `flush-mode: on-save` · `save-mode: on-set-attribute`, JDK 직렬화. 여러 web-backend 가 같은 세션을 본다 |
| 세션 쿠키 | `__Host-MOAI_WORKS_SESSION`: Secure · HttpOnly · SameSite=Lax · path `/`. `__Host-` 접두사라 평문 HTTP 나 다른 호스트가 덮어쓸 수 없다. TLS 를 끈 배치에서만 `MOAI_WORKS_SESSION_COOKIE` · `MOAI_WORKS_SESSION_SECURE` 로 바꾼다 |
| api 를 부를 때 | HTTPS. JVM 이 원래 믿는 인증 기관에 `moai-works-web.tls.ca-file`(`MOAI_WORKS_TLS_CA`, 로컬은 mkcert 인증 기관)을 **더해** 믿는다(`config/OutboundHttp`). 인증서 확인을 끄는 설정은 없다 |
| Keycloak 클라이언트 | `moai-works-web-backend`: 패스워드 그랜트만 켜져 있고 서비스 계정은 없다. 토큰 audience 에 `moai-works-api` 와 자기 자신이 든다 |
| 정적 파일 | `src/main/resources/static/` ← web-frontend 의 `npm run build` 결과(git 제외). 위치는 `MOAI_WORKS_WEB_STATIC`(기본 jar 안 `classpath:/static/`) |
| 비밀값 (`.env`, git 제외) | `MOAI_WORKS_WEB_BACKEND_CLIENT_ID` · `MOAI_WORKS_WEB_BACKEND_CLIENT_SECRET`(값은 `infra/keycloak/.env`) |
| 그 밖의 설정 | `MOAI_WORKS_KEYCLOAK_URL`(기본 http://localhost:8180) · `MOAI_WORKS_KEYCLOAK_REALM`(기본 `moai-works`) · `MOAI_WORKS_API_URL`(기본 https://localhost:11001/moai-works-api/v1) · `MOAI_WORKS_TLS_CA`(공인 인증서를 쓰면 비운다) · `MOAI_WORKS_REDIS_HOST` · `MOAI_WORKS_REDIS_PORT` · `MOAI_WORKS_REDIS_PASSWORD` · `MOAI_WORKS_REDIS_DATABASE` · 로그인 시도 제한 `moai-works-web.sign-in.per-ip-per-minute`(20) · `per-username-per-15-minutes`(10) · 로그 `MOAI_WORKS_LOG_FILE` · `MOAI_WORKS_LOG_CONSOLE` · `MOAI_WORKS_LOG_MAX_FILE_SIZE` · `MOAI_WORKS_LOG_MAX_HISTORY` · `MOAI_WORKS_LOG_TOTAL_SIZE_CAP`(`bin/startup.sh` 가 정한다) |

## 3. 구조

```
com.moai.works.web
  auth/    AuthController(/api/auth/sign-in · /session) · KeycloakPasswordAuthenticationProvider · KeycloakAuthentication
           · SessionUser · KeycloakTokenClient(패스워드 그랜트 · 갱신 · 로그아웃) · SessionTokens(세션의 토큰 · 갱신 · 그 자리 로그아웃)
           · TokenSet · SignInException · SignInRateLimiter
  config/  SecurityConfiguration(필터 체인 · CSRF · 로그아웃 · 헤더) · WebConfiguration(정적 파일 · 화면 주소 처리)
           · WebProperties · ErrorAdvice(BFF 의 입력 오류도 problem+json)
           · OutboundHttp(다른 서비스를 부르는 HTTP 클라이언트 — 믿는 인증 기관) · HstsRule(localhost 에는 HSTS 를 보내지 않음)
  relay/   ApiRelayController(/api/v1/**) · ApiRelay(헤더 허용 목록 · X-Forwarded-For · 요청과 응답 본문을 담아 두지 않고 흘려보냄) · Problems
```

## 4. 입구

| 경로 | 하는 일 |
|---|---|
| `POST /api/auth/sign-in` | 시도 제한 → `AuthenticationManager` → Keycloak 패스워드 그랜트 Provider → api `POST /v1/me/login`(계정 상태 확인. 거절하면 Keycloak 세션을 끝내고 세션을 만들지 않는다) → **세션 id · CSRF 토큰 교체** → SecurityContext 와 토큰을 세션(Redis)에 |
| `POST /api/auth/sign-out` | Spring Security 로그아웃: Keycloak 로그아웃(갱신 토큰 무효화) → 세션 삭제 → 새 CSRF 토큰과 204 |
| `GET /api/auth/session` | 로그인해야 한다(인가 규칙). api `GET /v1/me` 결과. 아니면 401 |
| `/api/v1/**` | api `/moai-works-api/v1/**` 로 1:1 전달. 경로 표가 없다. 가입(`/api/v1/signup/**`)만 토큰 없이, 나머지는 세션의 사용자 토큰을 싣는다. **파일 올리기(multipart)도 풀지 않고 그대로 흘려보낸다**(`spring.servlet.multipart.enabled: false` — 여기서 풀면 본문을 다 읽어 버려 넘길 것이 없다). 크기 상한 · 형식 확인은 api 가 한다. 파일 내려받기는 api 의 `Content-Disposition` 을 그대로 돌려준다. api 에 닿지 못하면 502 `API_UNAVAILABLE` |
| `/actuator/health` · `/actuator/info` | 공개. 다른 actuator 는 모두 거부 |
| 그 밖 | React 정적 파일. `/assets/**` 는 파일 이름에 해시가 있어 1년 캐시. 파일이 없는 화면 주소는 `index.html`, 단 `/api/**` · `/actuator/**` 는 404 |

새 업무 기능(프로젝트 · 일감 …)을 만들어도 이 모듈은 바뀌지 않는다 — `/api/v1/**` 가 경로 표 없이 그대로 넘어가기 때문이다.

## 5. Spring Security 요약

| 부분 | 무엇으로 |
|---|---|
| 인증 | `KeycloakPasswordAuthenticationProvider`(아이디 · 비밀번호 → 패스워드 그랜트) + `ProviderManager`. 로그인한 사람은 `KeycloakAuthentication`(principal `SessionUser(id, username)`, 권한 없음, 토큰은 직렬화하지 않는 필드) |
| 로그인 상태 | `SecurityContext` → `DelegatingSecurityContextRepository`(요청 속성 + `HttpSessionSecurityContextRepository`) → Spring Session(Redis) |
| 인가 | `POST /api/auth/sign-in` · `/api/v1/signup/**` · `/actuator/health` · `/actuator/info` 만 공개. 그 밖의 `/api/**` 는 로그인해야 한다(401 `UNAUTHENTICATED`). 나머지는 화면 파일. 무엇을 할 수 있는지는 api 가 정한다 |
| 세션 | 로그인 때 `ChangeSessionIdAuthenticationStrategy` + `CsrfAuthenticationStrategy`. 요청 캐시는 끈다. 익명 요청과 실패한 로그인은 세션을 만들지 않는다 |
| CSRF | SPA 모드. 쿠키 `XSRF-TOKEN`(HttpOnly 아님) → 헤더 `X-XSRF-TOKEN`. 틀리면 403 `CSRF_INVALID`. 같은 출처라 CORS 는 열지 않는다 |
| 토큰 | 세션 속성(`SessionTokens`). 만료 30초 전에 갱신한다. 여러 인스턴스가 동시에 갱신해도 둘 다 유효하고(렐름 `revokeRefreshToken=false`) 마지막에 저장한 것이 남는다. 갱신이 안 되면 그 요청에서 바로 로그아웃하고 401 을 준다. 세션 확인(`/api/auth/session`)에서 api 가 401 · 403 으로 토큰이나 계정을 거절해도 로그아웃한다 |
| 헤더 | 브라우저 요청 헤더는 허용 목록만 넘긴다: Accept · Accept-Language · Content-Type · If-Match · If-None-Match · Range · X-Correlation-Id. 응답은 Content-Type · Content-Disposition · Cache-Control · ETag · Last-Modified · Retry-After · X-Correlation-Id. `X-Forwarded-For` 는 BFF 가 직접 정한다(브라우저 값을 버린다). api 는 신원 헤더를 읽지 않는다 |
| 보안 헤더 | CSP(같은 출처만, 인라인 스크립트 금지, 인라인 스타일만 MUI 때문에 허용, 프레임 금지) · `X-Frame-Options: DENY` · `Referrer-Policy: strict-origin-when-cross-origin` · HSTS 는 `localhost` 밖에서만 |
| 로그인 시도 제한 | IP 분당 20회 · 아이디 15분당 10회(`SignInRateLimiter`, 넘으면 429 `RATE_LIMITED`) + Keycloak 무차별 대입 잠금 |

전체 인증 흐름, 쿠키, 위협별 대책은 [SECURITY.md](../../SECURITY.md) §4 에 있다. SeedSiteKeeper BFF 에서 본 문제(BFF 가 권한을 판정하던 구조, 꺼진 CSRF)는 따르지 않았다.

## 6. 실행 · 검사

1. moai-works-api(11001) · Keycloak(8180, `MOAI-Keycloak`) · Redis(6379, 로컬은 MOAI-Redis)가 떠 있어야 한다. 로컬 인증서가 없으면 먼저 `infra/tls/make-certs.sh`.
2. `.env`(git 제외)에 위 비밀값과 `MOAI_WORKS_API_URL`, 그리고 `MOAI_WORKS_WEB_STATIC=file:src/main/resources/static/` 을 넣는다. 그러면 프론트를 다시 빌드하는 즉시 반영된다. Redis 가 다르면 `MOAI_WORKS_REDIS_*` 를 준다.
3. `bin/startup.sh` → https://localhost:11002, 끄기는 `bin/shutdown.sh`(`./mvnw -q package` 로 만든 jar 를 쓴다). 로그는 `logs/moai-works-web-backend.log` 에 쌓이고 날짜 · 50MB 마다 넘어간다([DEVELOPE.md §5.1](../../DEVELOPE.md#51-띄우고-끄기)). 고치면서 볼 때는 `./mvnw spring-boot:run` 도 된다.

여러 대를 띄워 볼 때는 두 번째에 `MOAI_WORKS_WEB_PORT=11004` 처럼 쓰지 않는 다른 포트를 준다. 세션 쿠키가 같으므로 어느 쪽에서든 로그인 상태다.

검사: `./mvnw package`. 이 모듈에는 단위 테스트가 아직 없다(`src/test` 없음). moai-wiki 에서 같은 코드로 실제 흐름 39개 항목(익명 · 실패 로그인에 세션이 생기지 않음, 로그인 때 CSRF 교체, Redis 키와 TTL, 세션에 비밀번호가 없음, 다른 인스턴스에서 같은 세션, 로그아웃 뒤 키 삭제와 401, actuator 차단)을 확인했다. moai-works 이름(쿠키 · Redis 키 · 렐름)으로 같은 확인을 다시 하는 것은 남은 일이다.

## 7. 아직 안 한 것

- 로그인 시도 제한(`SignInRateLimiter`)이 아직 인스턴스마다 메모리에 있다. N 대면 N 배까지 허용된다(Keycloak 무차별 대입 잠금은 공유된다). Redis 로 옮긴다.
- 파일 전달에 시간 제한 · 크기 제한이 없다(상한은 api 의 200MB). 첨부 기능을 열기 전에 앞단(프록시)의 본문 크기 · 시간 제한과 함께 정한다.
- 세션은 JDK 직렬화(Spring Session 기본)다. Spring Security 를 올리면 기존 세션이 읽히지 않아 다시 로그인해야 할 수 있다.
- 단위 테스트 · moai-works 이름으로 한 실제 흐름 확인(§6).

## 8. 바꿀 때 함께 고칠 문서

| 바꾼 것 | 함께 고칠 곳 |
|---|---|
| 로그인 · 로그아웃 · 세션 · CSRF · 쿠키 · 보안 헤더 | [SECURITY.md](../../SECURITY.md) §4 · 이 쪽 §4 · §5 · 화면 쪽 처리가 바뀌면 [WEB-DESIGN.md](../../WEB-DESIGN.md) |
| 헤더 허용 목록 | 이 쪽 §5 · [SECURITY.md](../../SECURITY.md) §4.7 |
| Keycloak 클라이언트 설정 | `infra/keycloak/realm-moai-works.json` · `ensure-realm.py` · [SECURITY.md](../../SECURITY.md) §3.2 |
| Redis · 세션 설정 | 이 쪽 §2 · [00-overview](00-overview.md) §4 · [DEVELOPE.md](../../DEVELOPE.md) 로컬 환경 |
| 새 env 키 | 이 쪽 §2 · 모듈 README 표 |

---

**관련 문서:** [모듈 한눈에](00-overview.md) · [SECURITY.md](../../SECURITY.md) · [moai-works-web-frontend](03-web-frontend.md) · [moai-works-api](01-api.md) · [워크스페이스·계정·권한](../design/03-workspace-identity.md) · [DEVELOPE.md](../../DEVELOPE.md)

[← 이전: moai-works-api](01-api.md) | [다음: moai-works-web-frontend →](03-web-frontend.md)
