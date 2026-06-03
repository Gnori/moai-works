# F0 — SaaS 토대 마스터 (멀티테넌시 · 인증 · RBAC)

## 메타데이터

- **작성일**: 2026-06-03
- **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 — 요구사항 분석 (L1 도메인 마스터)
- **우선순위**: **P0-Critical** (모든 업무 모듈의 선행조건)
- **규모**: complex (마스터 — 범위·에픽·토대 설계. 상세는 하위 L3 spec)
- **레벨**: L1 도메인 마스터 → 하위 L3 = 실제 `/dev req` 구현 단위
- **연관**: [요구사항 로드맵](../../docs/requirements-roadmap.md) · [아키텍처 §6](../../docs/architecture.md)

---

## 1. 요구사항 분석

### 1.1 문제 정의
MOAI Works 는 **멀티테넌트 SaaS** 다. 테넌트(회사)별 **데이터 격리·인증·권한**이 없으면
어떤 업무 모듈(게시판·메신저·결재…)도 안전하게 만들 수 없다. F0 는 그 **공통 토대**다.

### 1.2 목표
- 테넌트(회사) 단위로 **데이터가 절대 새지 않는** 논리 격리 (공유 DB + `tenant_id`)
- 회사가 **가입→부트스트랩**(첫 관리자·도메인)하고 사용자를 초대·관리
- **자체 인증 + 외부 연동**(OIDC/SSO · LDAP/AD), 테넌트별 인증 설정
- 테넌트 범위 **RBAC**(역할·권한)로 모듈 접근 제어
- 구독/시트/quota·**감사로그** 기반 운영 토대

### 1.3 이해관계자
플랫폼 운영자(테넌트 관리) · 테넌트 관리자(회사 admin) · 일반 사용자 · 보안/감사 · 개발팀(모든 모듈이 F0 위에 구현)

### 1.4 제약 조건 (확정 결정 — [architecture §6](../../docs/architecture.md))
- 멀티테넌시: **공유 DB + `tenant_id`** (행 단위) · 쿼리 테넌트 필터 강제
- 백엔드: **Java + Spring Boot** · DB: **PostgreSQL** (RLS 활용 검토)
- **인증/IAM = Keycloak** (자체 IdP 미구축) — OIDC·OAuth2·**SAML**·**LDAP/AD** 를 Keycloak이
  페더레이션. 앱은 OAuth2 Resource Server + OIDC 클라이언트. 멀티테넌시 = Keycloak Organizations
- **기존 Docker 인프라 재사용**: `MOAI-DB`(PG, Keycloak DB 겸용) · `moai-vectorDB`(Qdrant) ·
  `MOAI-Redis` · `MOAI-MinIO` · 관측성 스택. Keycloak 컨테이너만 신규 추가.

### 1.5 리스크 분석
| 리스크 | 영향 | 완화 |
|--------|------|------|
| **테넌트 데이터 교차 누출** | 치명적(SaaS 신뢰 붕괴) | 격리 강제(인터셉터+RLS) · 모든 API "테넌트 경계" 테스트 필수 · 기본 deny |
| tenant_id 누락 쿼리 | 누출/오염 | Repository 레벨 필터 강제 · 정적 검사 · 통합 테스트 |
| 외부 IdP 다양성 | 통합 복잡 | OIDC 표준 우선 · 어댑터 패턴 · 테넌트별 설정 |
| 권한 모델 과설계 | 지연 | 최소 RBAC 부터 → 점진 확장 |

### 1.6 영향 범위
**전 모듈**. F1~F15 모든 엔티티에 `tenant_id`, 모든 API에 테넌트 컨텍스트·권한 체크. F0 의
`TenantContext`·`@RequiresPermission` 등을 전 모듈이 상속.

---

## 2. 기능 정의 — 에픽 → L3 spec

### 2.1 L3 분해 (각각 별도 `/dev req`)

| L3 ID | spec | 규모 | 핵심 인수기준(요약) |
|-------|------|------|--------------------|
| **F0-01** | 테넌트 모델 + 격리 메커니즘 | standard | 모든 쿼리에 tenant 필터 강제 · 교차 테넌트 접근 차단 테스트 통과 |
| **F0-02** | 테넌트 부트스트랩(가입·생성) | standard | 회사 생성 → 첫 관리자 → 도메인/서브도메인 매핑 |
| **F0-03** | **인증 — Keycloak 통합** | standard | 앱 = OAuth2 Resource Server(JWT 검증) + OIDC 로그인 · 토큰→TenantContext/RBAC 매핑 · Keycloak 배포(기존 PG 재사용) |
| **F0-04** | **Keycloak Identity Brokering** (OIDC·**SAML**·**OAuth2/소셜**) | standard | 테넌트별 외부 IdP 브로커링 — OIDC/SAML/OAuth2 전부 Keycloak 설정으로 |
| **F0-05** | **Keycloak User Federation** (LDAP/AD) | standard | Keycloak LDAP 페더레이션 · 사용자/부서 동기화 |
| **F0-06** | 사용자/멤버십 | standard | 초대·가입·비활성 · 테넌트-사용자 멤버십 |
| **F0-07** | RBAC(역할·권한) | standard | 역할 정의·할당 · `@RequiresPermission` 강제 |
| **F0-08** | 감사로그 | standard | 인증·권한·중요행위 audit · 테넌트별 조회 |
| **F0-09** | 구독/플랜·시트·quota | standard | 플랜·시트 한도·사용량 미터 · 한도 초과 차단 |
| **F0-10** | 테넌트 설정·관리 콘솔(기본) | standard | 테넌트 admin 설정(인증·브랜딩·한도) |

> F0-01·F0-03·F0-07 이 **최소 토대 3종** — 이것부터 구현하면 다른 모듈 착수 가능.

### 2.2 비기능 요구사항
- **보안(최우선)**: 테넌트 격리는 기본 deny. 모든 핸들러·리포지토리에서 테넌트 경계 강제.
- 성능: 테넌트 필터가 인덱스(`(tenant_id, ...)`) 타도록.
- 관측성: 모든 요청에 tenant_id·user_id·request_id 로깅.
- 테스트: **격리 회귀 테스트 필수** (다른 테넌트 데이터 접근 시 404/403).

---

## 3. 기능 설계 (토대 — L3 가 상속)

### 3.1 테넌시 격리 메커니즘
```
요청 → [인증 필터] JWT/세션에서 tenant_id 추출
     → [TenantContext] ThreadLocal(또는 요청 스코프)에 tenant_id 보관
     → [Hibernate @Filter / PG RLS] 모든 쿼리에 tenant_id = :current 자동 적용
     → [BaseEntity] 모든 엔티티 tenant_id (not null) + 저장 시 자동 주입
```
- 1차 방어: 애플리케이션(Hibernate filter + Repository 강제)
- 2차 방어: **PostgreSQL RLS**(Row-Level Security) — 누락 쿼리도 DB가 차단
- 교차 테넌트 접근 = **404 위장**(존재 노출 방지)

### 3.2 인증 토대
- 자체: email+password(테넌트 범위 unique) · JWT(tenant_id·user_id·roles claim) + Refresh
- 외부: **OIDC**(Spring Security OAuth2 Client, 테넌트별 registration) · **LDAP/AD**(동기화+바인드 인증)
- 테넌트별 "인증 방식" 설정(자체/OIDC/LDAP 혼용)

### 3.3 RBAC 모델
```
Tenant 1—* Role 1—* Permission     (역할-권한)
User  *—* Role (TenantMembership 범위)   (사용자-역할, 테넌트 내)
```
- 권한 키: `module:action`(예: `board:write`, `approval:approve`)
- 강제: `@RequiresPermission("...")` 메서드 인터셉터

### 3.4 데이터 모델 (핵심 — 상세는 L3)
`tenants` · `tenant_settings` · `users` · `tenant_memberships(user,tenant,status)` ·
`roles(tenant)` · `permissions` · `role_permissions` · `user_roles` ·
`audit_logs(tenant,user,action,target,at)` · `subscriptions/plans/usage`.
**모든 업무 테이블은 `tenant_id` FK 보유.**

### 3.5 보안 설계
- 기본 deny · 최소 권한 · 테넌트 경계는 코드+DB(RLS) 이중 강제
- 시크릿/토큰 암호화 · 감사로그 불변(append-only)

---

## 4. 개발 진행 체크리스트 (L3 단위)

- [ ] F0-01 테넌트 모델 + 격리 (RLS + Hibernate filter + 격리 테스트)
- [ ] F0-03 자체 인증
- [ ] F0-07 RBAC
- [ ] F0-02 부트스트랩 · F0-06 사용자/멤버십
- [ ] F0-04 OIDC · F0-05 LDAP/AD
- [ ] F0-08 감사로그 · F0-09 구독/quota · F0-10 관리 콘솔

> 각 L3 착수 시 `/dev req f0-xx-<name> --scale=standard` 로 상세 명세 생성.

---

## 9. 완료 기준 (F0 마스터)

- [ ] 교차 테넌트 데이터 접근이 코드·DB 양쪽에서 차단됨 (회귀 테스트 그린)
- [ ] 자체 + OIDC + LDAP 로그인 동작, 테넌트별 인증 설정
- [ ] RBAC 로 모듈 접근 제어, `@RequiresPermission` 강제
- [ ] 감사로그·구독/quota 기본 동작
- [ ] **다른 업무 모듈(F1+)이 F0 토대 위에 안전하게 올라갈 수 있음**
