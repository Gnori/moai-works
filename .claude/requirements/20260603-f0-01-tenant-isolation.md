# F0-01 — 테넌트 모델 + 격리 메커니즘

## 메타데이터

- **작성일**: 2026-06-03
- **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 — 요구사항 분석
- **우선순위**: **P0-Critical** (Wave 0 최우선 · 모든 업무 모듈의 선행)
- **규모**: standard
- **레벨**: L3 (구현 단위) · 상위: [F0 SaaS 토대 마스터](./20260603-f0-saas-foundation-master.md)
- **의존**: 없음(토대). 단 테넌트 *식별값*은 F0-03(인증) 완성 시 JWT 에서 공급 — 그 전엔 임시 헤더로 주입

---

## 1. 요구사항 분석 및 상세화

### 1.1 문제 정의
멀티테넌트 SaaS 에서 **테넌트(회사) A 의 데이터가 B 에게 절대 노출되면 안 된다.** 격리 토대
없이 어떤 업무 모듈도 안전하게 만들 수 없다. 본 spec 은 **공유 DB + `tenant_id`** 격리의
**공통 메커니즘**(엔티티 토대·요청 컨텍스트·자동 필터·DB RLS)을 구축한다.

### 1.2 목표
- 모든 도메인 엔티티가 `tenant_id` 를 갖고, **저장 시 자동 주입**
- 모든 조회가 **현재 테넌트로 자동 필터**(개발자가 잊어도 강제)
- **이중 방어**: 애플리케이션(Hibernate Filter) + DB(PostgreSQL RLS)
- 교차 테넌트 접근 시 **404 위장**(존재 노출 방지)
- 다른 모듈이 상속할 **`BaseEntity` · `TenantContext` · 격리 테스트 패턴** 제공

### 1.3 제약 조건
- Java + Spring Boot · Spring Data JPA(Hibernate) · **PostgreSQL**
- 멀티테넌시 = 공유 DB + `tenant_id` (행 단위) — [architecture §6](../../docs/architecture.md)
- 기본 **deny** · 최소 권한 · 격리는 코드+DB 이중 강제

---

## 2. 기능 정의

### 2.1 핵심 기능 목록
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | `Tenant` 엔티티 + 테넌트 레지스트리 | Must |
| F-002 | `BaseEntity`(tenant_id, not null) — 전 엔티티 상속 토대 | Must |
| F-003 | `TenantContext`(요청 스코프) + 테넌트 resolution | Must |
| F-004 | 자동 격리 강제 — Hibernate `@Filter` + PostgreSQL **RLS** | Must |
| F-005 | 저장 시 tenant_id 자동 주입 + 교차 접근 404 | Must |

### 2.2 기능 상세

#### F-002: BaseEntity
- `@MappedSuperclass abstract BaseEntity { @Column(nullable=false, updatable=false) UUID tenantId; ... }`
- `@PrePersist` 에서 `TenantContext.require()` 로 tenantId 자동 주입 (수동 설정 금지)
- 모든 업무 엔티티는 BaseEntity 상속 → `tenant_id` 컬럼 + 인덱스 `(tenant_id, id)`

#### F-003: TenantContext + Resolution
- `TenantContext` = `ThreadLocal`/요청스코프, `set/get/require/clear`
- `TenantResolver` 인터페이스 → 우선순위: **인증 주체(JWT `tenant_id` claim, F0-03)** → (옵션)서브도메인
- `OncePerRequestFilter` 가 요청 시작 시 resolve → set, 종료 시 clear
- **미해결 테넌트 = 401/거부** (기본 deny)

#### F-004: 이중 격리
- **앱**: Hibernate `@Filter(name="tenantFilter", condition="tenant_id = :tenantId")` 를 BaseEntity 에
  선언 → 세션 오픈 시 인터셉터가 `TenantContext` 로 필터 활성화 → 모든 조회 자동 필터
- **DB(backstop)**: PostgreSQL **RLS** — 테이블 `ENABLE ROW LEVEL SECURITY` + 정책
  `USING (tenant_id = current_setting('app.tenant')::uuid)`. 커넥션마다 `SET app.tenant` 주입
  (필터 누락 쿼리도 DB 가 차단)

#### F-005: 교차 접근 처리
- 다른 테넌트 리소스 조회/수정 시도 → 필터/RLS 로 **결과 없음** → 핸들러는 **404** 반환(403 아님, 존재 노출 방지)

---

## 3. 기능 설계

### 3.1 기술 스택
Spring Boot · Spring Data JPA(Hibernate) · PostgreSQL(RLS) · Flyway(마이그레이션) · JUnit5/Testcontainers

### 3.2 아키텍처 설계
```
요청 → [TenantFilter(OncePerRequest)] resolve → TenantContext.set(tenantId)
     → [DataSource] 커넥션 SET app.tenant = :tenantId   (RLS 입력)
     → [Hibernate Interceptor] enableFilter("tenantFilter", tenantId)
     → Repository/Service ── 모든 쿼리 자동 tenant 필터 ──▶ PostgreSQL(RLS 2차 차단)
     → 요청 종료 → TenantContext.clear()
```

### 3.3 데이터 모델
- `tenants(id PK, name, slug/subdomain unique, status, created_at)`
- 모든 업무 테이블: `tenant_id UUID NOT NULL` + FK(tenants) + 인덱스 `(tenant_id, ...)` + RLS 정책
- 마이그레이션(Flyway): tenants 생성 + RLS 활성화 헬퍼(`apply_tenant_rls(table)`)

### 3.4 핵심 산출 클래스 (재사용 토대)
`Tenant`, `BaseEntity`, `TenantContext`, `TenantResolver`, `TenantFilter`,
`TenantAwareHibernateInterceptor`, `TenantConnectionInitializer(SET app.tenant)`,
`NotFoundException`(교차접근 404 매핑)

---

## 4. 개발 진행 체크리스트

### 4.1 환경 설정
- [ ] PostgreSQL + Flyway + Testcontainers 셋업

### 4.2 기능 개발
- [ ] F-001 Tenant 엔티티/레포 + 마이그레이션
- [ ] F-002 BaseEntity + @PrePersist 주입
- [ ] F-003 TenantContext + Resolver + Filter
- [ ] F-004 Hibernate @Filter 인터셉터 + RLS 정책/커넥션 SET
- [ ] F-005 교차접근 404 매핑

### 4.3 코드 품질
- [ ] tenant_id 수동 설정 금지(주입만) · 인덱스 확인 · 기본 deny

### 4.4 진행률
0% (1단계)

---

## 5. 테스트 결과

### 5.1 테스트 케이스 (격리 = 최우선)
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 테넌트 A 컨텍스트로 저장 → tenant_id=A 자동 주입 | 통과 |
| T-2 | A 가 B 의 엔티티 조회 | **결과 없음 → 404** |
| T-3 | **개발자가 tenant 필터 깜빡한 raw 쿼리** | **RLS 가 차단**(타 테넌트 행 0) |
| T-4 | TenantContext 미설정 요청 | 거부(401) |
| T-5 | 요청 종료 후 ThreadLocal 누수 없음 | clear 확인 |
| T-6 | 동시 요청(A·B) 교차 오염 없음 | 격리 유지 |

> ⚠️ **T-2·T-3 는 회귀 필수** — 이후 모든 모듈이 이 격리에 의존.

### 5.2 버그 수정 로그
(없음)

---

## 7. 최종 검토

### 7.1 완료 기준 체크
- [ ] 모든 엔티티 tenant_id 자동 주입 · 자동 필터
- [ ] **앱 필터 + RLS 이중 차단** 동작 (T-2·T-3 그린)
- [ ] 교차 접근 404 · 미해결 테넌트 거부
- [ ] 재사용 토대(BaseEntity/TenantContext) 문서화 → F1+ 모듈이 상속 가능

### 7.2 인수
- [ ] 격리 회귀 테스트 그린 + 코드 리뷰(보안 관점) 완료
