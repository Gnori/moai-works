# F0-03 — 인증 (Keycloak 통합)

## 메타데이터
- **작성일**: 2026-06-03 · **개정**: 2026-06-03 (자체인증 → **Keycloak IAM** 채택)
- **작성자**: GnoRi · AI Assistant · **상태**: 1단계 · **규모**: standard · **레벨**: L3
- **우선순위**: **P0-Critical** (Wave 0 · 최소 토대 3종)
- **상위**: [F0 마스터](./20260603-f0-saas-foundation-master.md)
- **의존**: [F0-01 테넌트 격리](./20260603-f0-01-tenant-isolation.md)(토큰→TenantContext) · [F0-07 RBAC](./20260603-f0-07-rbac.md)(권한은 앱 권위) · 외부 IdP는 [F0-04](./20260603-f0-04-auth-oidc-sso.md)/[F0-05](./20260603-f0-05-auth-ldap-ad.md)

---

## 1. 요구사항 분석
### 1.1 문제 정의
인증·SSO·페더레이션을 **자체 구현하지 않고** 검증된 IAM(**Keycloak**)에 위임한다. 앱은
Keycloak이 발급한 토큰을 검증하는 **OAuth2 Resource Server** + 로그인 리다이렉트를 처리하는
**OIDC 클라이언트**가 된다. (SAML·OAuth2·LDAP 등 외부 IdP 는 Keycloak이 처리 — F0-04/05)

### 1.2 목표
- **Keycloak 배포** (기존 `MOAI-DB` PostgreSQL 재사용 — 별도 `keycloak` database)
- 앱 ↔ Keycloak **OIDC 통합**: 로그인(Authorization Code+PKCE)·토큰 검증·로그아웃·갱신
- **멀티테넌시 = Keycloak Organizations** (단일 realm·테넌트=organization) → 토큰의 org/tenant
  claim → **F0-01 TenantContext** 주입
- Keycloak roles/claims → **앱 RBAC(F0-07) 매핑** (권한 권위는 앱)

### 1.3 제약
- Java + Spring Boot + **Spring Security (OAuth2 Resource Server + Client)**
- Keycloak(quay.io/keycloak) 컨테이너 신규 + DB는 MOAI-DB 재사용
- 토큰의 tenant 식별 claim 이 **F0-01 격리의 입력** · 자체 비번/세션 저장 안 함

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | Keycloak 배포·realm/Organizations·클라이언트 설정 | Must |
| F-002 | 앱 OIDC 로그인(리다이렉트·코드 교환)·로그아웃 | Must |
| F-003 | OAuth2 Resource Server — JWT(서명/issuer/aud) 검증 | Must |
| F-004 | 토큰 claim → **TenantContext(F0-01)** + 사용자 매핑(F0-06) | Must |
| F-005 | Keycloak roles ↔ 앱 RBAC(F0-07) 매핑·동기화 | Should |

### 2.2 상세
- **F-001**: realm `moai-works`, 테넌트 = **Organization**, 앱 클라이언트(confidential), org/tenant
  를 토큰 claim(`organization`/`tenant_id`)으로 매핑(Protocol Mapper). (강격리 필요 고객은
  realm-per-tenant 대안)
- **F-003**: `spring-boot-starter-oauth2-resource-server` — JWKS(issuer) 검증, `aud`/`azp` 확인
- **F-004**: JWT `tenant_id`(org) → `TenantContext.set()`; `sub` → 앱 사용자(F0-06) 매핑(JIT)
- **F-005**: Keycloak role/group claim 을 앱 역할로 매핑, 단 **module:action 권한 평가는 앱(F0-07) 권위**

## 3. 기능 설계
### 3.1 스택
Spring Security(OAuth2 Client+Resource Server) · Keycloak 26.x(Organizations) · PostgreSQL(MOAI-DB) · Docker
### 3.2 아키텍처
```
[브라우저] → 앱 → Keycloak OIDC 로그인(Authorization Code+PKCE)
        ← Keycloak 발급 Access/Refresh(JWT, claim: sub·tenant_id(org)·roles)
[요청] Bearer JWT → Resource Server 검증(JWKS) → SecurityContext
        → JWT tenant_id → TenantContext.set() (F0-01)
        → sub → 사용자 매핑(F0-06) · roles → RBAC(F0-07)
[로그아웃/갱신] OIDC end-session / refresh (Keycloak 위임)
```
### 3.3 배포 (기존 인프라 재사용)
- Keycloak 컨테이너 추가, `--db postgres` → **MOAI-DB**(별도 database `keycloak`)
- Realm/클라이언트/Organization/Mapper 는 **Realm Import(IaC)** 로 버전관리
### 3.4 보안
- 토큰 검증(서명·issuer·aud·exp) · PKCE · 단명 Access + Keycloak refresh · HTTPS

## 4. 개발 체크리스트
- [ ] F-001 Keycloak 배포(MOAI-DB) + realm/Org/클라이언트 IaC
- [ ] F-002 OIDC 로그인/로그아웃
- [ ] F-003 Resource Server JWT 검증
- [ ] F-004 claim→TenantContext + 사용자 매핑(JIT)
- [ ] F-005 roles→RBAC 매핑
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | Keycloak 로그인 → 앱 접근 | 인증·토큰 발급 |
| T-2 | 위조/만료 JWT | 401(검증 실패) |
| T-3 | 토큰 tenant_id → TenantContext | F0-01 격리 동작 |
| T-4 | 신규 sub | 사용자 JIT 매핑(F0-06) |
| T-5 | role claim → 권한 | RBAC(F0-07) 평가 |
| T-6 | 타 org 토큰으로 타 테넌트 접근 | 격리 차단 |

## 7. 최종 검토
- [ ] Keycloak OIDC 통합·JWT 검증·tenant claim→격리·roles→RBAC 그린
- [ ] 자체 비번/세션 미저장(IAM 위임) 확인 · Realm IaC 버전관리
