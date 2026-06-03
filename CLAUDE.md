# MOAI Works — AI 개발 가이드

이 문서는 AI 어시스턴트가 `moai-works`(그룹웨어) 작업 시 따라야 할 지침입니다.

> 상태: 🌱 초기 골격. 실제 코드/의존성 미설치 — 구현 착수 시 본 가이드 보강.

---

## 프로젝트 개요

**MOAI Works**는 **멀티테넌트 SaaS + AI 업무 플랫폼** (AI-native Groupware)입니다.

- **Backend (코어)**: **Java + Spring Boot** (`moai-works-api`) — 그룹웨어 도메인
- **Backend (AI)**: **Python + moai-agent** (`moai-works-ai`) — Agent·RAG·자동화
- **Frontend**: React 19 + TypeScript + Vite (`moai-works-web`)

### ✅ 확정 기술 결정 (2026-06-03)
- **멀티테넌시**: 공유 DB + `tenant_id` (행 단위 격리) — **모든 테이블 tenant_id + 쿼리 테넌트 필터 강제**
- **인증**: 자체 + 외부(OIDC/SSO · LDAP/AD) · 테넌트별 설정
- **DB**: PostgreSQL · **벡터DB**: pgvector(초기) — 지식관리 RAG
- 상세: [docs/architecture.md §6](./docs/architecture.md), 요구사항 플랜: [docs/requirements-roadmap.md](./docs/requirements-roadmap.md)

> ⚠️ **SaaS 토대(F0: 테넌시·인증·RBAC)가 모든 모듈의 선행조건.** 업무 모듈은 F0 위에만 구현.

## 코딩 표준 (착수 후 확정)

### Backend (Spring Boot)
- 레이어: `controller / service / repository / domain / dto`
- 패키지 루트: `com.moai.works`
- 스타일: Google Java Format 또는 Spotless (확정 필요)
- 테스트: JUnit 5 + Spring Boot Test

### Frontend (React + TS)
- 함수형 컴포넌트 + Hooks
- 스타일: ESLint + Prettier
- 빌드: Vite

## 작업 원칙

1. **단순함 우선** — 요청한 것만, 추측 기능 금지.
2. **외과적 변경** — 관련 없는 코드/포맷 건드리지 않기.
3. **착수 전 확정** — DB/인증/언어(Java·Kotlin)/빌드도구 등 미정 항목은 결정 후 진행.

## 참고

- 모노레포 컨벤션: 형제 프로젝트 [moai-echomind](../moai-echomind/CLAUDE.md) 의 가이드·`/dev` 요구사항 관리 방식 참고 가능.
- 아키텍처 초안: [docs/architecture.md](./docs/architecture.md)

---

**마지막 업데이트**: 2026-06-03
