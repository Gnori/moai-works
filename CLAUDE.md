Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

Tradeoff: These guidelines bias toward caution over speed. For trivial tasks, use judgment.

1. Think Before Coding
Don't assume. Don't hide confusion. Surface tradeoffs.

Before implementing:

State your assumptions explicitly. If uncertain, ask.
If multiple interpretations exist, present them - don't pick silently.
If a simpler approach exists, say so. Push back when warranted.
If something is unclear, stop. Name what's confusing. Ask.
2. Simplicity First
Minimum code that solves the problem. Nothing speculative.

No features beyond what was asked.
No abstractions for single-use code.
No "flexibility" or "configurability" that wasn't requested.
No error handling for impossible scenarios.
If you write 200 lines and it could be 50, rewrite it.
Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

3. Surgical Changes
Touch only what you must. Clean up only your own mess.

When editing existing code:

Don't "improve" adjacent code, comments, or formatting.
Don't refactor things that aren't broken.
Match existing style, even if you'd do it differently.
If you notice unrelated dead code, mention it - don't delete it.
When your changes create orphans:

Remove imports/variables/functions that YOUR changes made unused.
Don't remove pre-existing dead code unless asked.
The test: Every changed line should trace directly to the user's request.

4. Goal-Driven Execution
Define success criteria. Loop until verified.

Transform tasks into verifiable goals:

"Add validation" → "Write tests for invalid inputs, then make them pass"
"Fix the bug" → "Write a test that reproduces it, then make it pass"
"Refactor X" → "Ensure tests pass before and after"
For multi-step tasks, state a brief plan:

1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

These guidelines are working if: fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.


# MOAI Works — AI 개발 가이드

이 문서는 AI 어시스턴트가 `moai-works`(AI-native 그룹웨어) 작업 시 따라야 할 지침입니다.

> 상태: 🌱 **초기 골격** (2026-06-03) — 디렉터리 구조·설계 문서만. 실제 코드/의존성 미설치.
> 구현 착수 시 본 가이드(코딩 표준·실행 방법)를 실제 스택에 맞게 보강할 것.

---

## 프로젝트 개요

**MOAI Works**는 **멀티테넌트 SaaS + AI 업무 플랫폼** (AI-native Groupware)입니다.
그룹웨어(조직·게시판·결재…) + 메신저·메일·파일드라이브·지식관리를, **AI Agent 계층**이
가로질러 자동화·지능화하는 사내 업무 플랫폼.

| 컴포넌트 | 스택 | 책임 |
|----------|------|------|
| `moai-works-web` | React 19 + TypeScript + Vite | 대시보드·메신저·문서 UI |
| `moai-works-api` | Spring Boot (Java/Kotlin) | 그룹웨어 도메인·권한·외부연계 |
| `moai-works-ai` | Python + [moai-agent](../moai-agent/) | AI Agent·RAG·문서작성·지식화·자동화 |

- **DB**: PostgreSQL · **벡터DB**: pgvector(초기, 지식관리 RAG) · 오브젝트스토리지(문서) · Redis
- AI Agent 계층은 모노레포의 **`moai-agent`** 프레임워크 기반.

### ✅ 확정 기술 결정 (2026-06-03)
- **멀티테넌시**: 공유 DB + `tenant_id` (행 단위 격리) — **모든 테이블 tenant_id + 쿼리 테넌트 필터 강제**
- **인증**: 자체 + 외부(OIDC/SSO · LDAP/AD), 테넌트별 설정
- 상세: [docs/architecture.md](./docs/architecture.md)

> ⚠️ **SaaS 토대(F0: 테넌시·인증·RBAC)가 모든 모듈의 선행조건.** 업무 모듈은 F0 위에만 구현.

---

## 코딩 표준 (착수 후 확정)

### Backend (`moai-works-api`, Spring Boot)

- 레이어: `controller / service / repository / domain / dto`
- 패키지 루트: `com.moai.works`
- 스타일: Google Java Format 또는 Spotless (확정 필요)
- 테스트: JUnit 5 + Spring Boot Test
- **모든 테이블·쿼리에 `tenant_id` 격리 강제** (멀티테넌시 불변식)

### AI (`moai-works-ai`, Python + moai-agent)

- **스타일**: PEP 8, Black, Ruff 린터
- **타입 힌트**: 모든 함수에 필수, Google 스타일 docstring
- **테스트**: pytest + pytest-asyncio
- `moai-agent` 프레임워크 패턴 준수 (echomind-api 등 형제 프로젝트 참고)

### Frontend (`moai-works-web`, React + TS)

- 함수형 컴포넌트 + Hooks
- 스타일: ESLint + Prettier, 빌드: Vite

---

## /dev 스킬

요구사항 관리와 문서 관리를 하나의 `/dev` 명령어로 통합합니다.

### 명령어

```bash
# 요구사항 관리
/dev init                              # 요구사항 관리 구조 초기화
/dev req <name>                        # 요구사항 생성 (자동 규모 추천)
/dev req <name> --scale=simple         # 간단한 규모로 생성
/dev req <name> --scale=standard       # 표준 규모로 생성
/dev req <name> --scale=complex        # 복잡한 규모로 생성
/dev save [--reason="<이유>"]          # 체크포인트 저장
/dev load                              # 체크포인트 복원
/dev status                            # 진행 상황 리포트

# 문서 관리
/dev doc                               # 문서 초기화
/dev doc refresh                       # 전체 문서 갱신
/dev doc refresh api                   # API 문서만 갱신
/dev doc status                        # 문서 동기화 상태 확인
/dev doc modules                       # 모듈 문서 초기화
/dev doc modules refresh               # 모듈 문서 갱신
/dev doc modules status                # 모듈 문서 상태
```

### 요구사항 규모 (Scale)

| 규모 | 적합한 작업 | 템플릿 |
|------|-------------|--------|
| **simple** (간단) | 버그 수정, 오타 수정, 소규모 변경 | ~50줄 |
| **standard** (표준) | 일반 기능 개발, API 추가 [기본값] | ~150줄 |
| **complex** (복잡) | 대규모 기능, 아키텍처 변경, 마이그레이션 | ~400줄 |

### 5단계 개발 프로세스

1. **요구사항 분석** - 문제 정의, 목표 설정, 제약 조건 파악
2. **기능 정의** - 핵심 기능 목록, 우선순위, 인수 기준
3. **기능 설계** - 기술 스택, 아키텍처, API 설계
4. **개발 및 진행 관리** - 구현, 테스트, 진행 상황 추적
5. **테스트 및 검증** - 테스트 실행, 버그 수정, 최종 검증

### AI 어시스턴트 필수 지침 (MANDATORY)

**규모 선택 기준**:

| 작업 유형 | 권장 규모 | 프로세스 |
|----------|----------|----------|
| 버그 수정, 오타 수정 | simple | 간소화 (1-2-4단계) |
| 새로운 기능 추가 | standard | 5단계 전체 |
| 기존 기능 수정 | standard | 5단계 전체 |
| 아키텍처 변경 | complex | 5단계 전체 + 상세 리스크 분석 |
| 마이그레이션, 리팩토링 | complex | 5단계 전체 + 롤백 계획 |

**MUST DO (필수 수행)**:
- 코드 변경 작업 시 규모 선택 기준 확인
- 적절한 규모의 템플릿으로 요구사항 생성
- 각 단계 완료 시 사용자에게 확인 및 승인 요청
- 중단 시 `/dev save`로 체크포인트 저장
- `active_development.md` 진행 상황 지속 업데이트

**MUST NOT DO (절대 금지)**:
- 요구사항 명세서 없이 코드 작성 시작
- 복잡한 작업에 simple 규모 사용
- 체크포인트 저장 없이 세션 종료

### 단계별 체크포인트

**1단계 → 2단계**: 문제 정의 명확? 목표 측정 가능? 제약 조건 파악?
**2단계 → 3단계**: 기능 목록 완성? MoSCoW 우선순위? 인수 기준 측정 가능?
**3단계 → 4단계**: 기술 스택 결정? 아키텍처 완료? 보안 요구사항 반영?
**4단계 → 5단계**: 기능 개발 완료? 코딩 컨벤션 준수? 단위 테스트 작성?
**5단계 완료**: 모든 테스트 통과? 인수 기준 충족? 문서화 완료?

---

## 환경 변수

> 골격 단계 — 구현 착수 시 컴포넌트별 `.env` / `application.yml` 로 확정.
> 값을 추측하지 말고 컴포넌트별 설정 파일을 기준으로 한다.

- LLM API 키, DB(PostgreSQL) 접속 정보, Redis, 오브젝트스토리지 자격 증명, OIDC/LDAP
  설정, JWT 시크릿 등은 모두 환경 변수로 주입하며 커밋하지 않는다.
- 테넌트별 인증 설정(OIDC/SSO·LDAP/AD)은 환경 변수가 아닌 테넌트 설정 저장소에서 관리.

---

## 실행 방법

> 🌱 아직 코드/의존성 미설치. 골격 → 실제 프로젝트 전환 순서:

```bash
# 1) Backend 초기화 — moai-works-api
#    Spring Initializr (Web + JPA + Security + PostgreSQL …)

# 2) Frontend 초기화 — moai-works-web
cd moai-works-web
npm create vite@latest . -- --template react-ts

# 3) AI 서비스 초기화 — moai-works-ai
#    moai-agent 기반으로 구성 (echomind-api 패턴 참고)

# 4) 요구사항 명세
/dev req <name> --scale=complex   # 단계별(P0~P5) 명세 작성
```

각 컴포넌트의 실행/배포 절차는 구현 착수 후 컴포넌트별 README에 정리한다.

---

## Git 커밋 규칙

### 커밋 메시지 형식

```
<type>(<scope>): <subject>

<body>

<footer>
```

### 타입

- **feat**: 새로운 기능
- **fix**: 버그 수정
- **docs**: 문서 변경
- **style**: 포맷팅 변경
- **refactor**: 리팩토링
- **test**: 테스트 추가
- **chore**: 빌드, 설정 변경

### 예시

```
feat(api): 테넌트 격리 RBAC 권한 필터 추가

- 모든 쿼리에 tenant_id 필터 강제 인터셉터 구현
- 역할 기반 접근 제어(RBAC) 검증 로직 추가
- application.yml 설정 항목 추가

Closes #123
```

---

## 보안 지침

### API 키 관리
- API 키는 `.env` 파일에만 저장
- `.env` 파일은 절대 커밋하지 않음
- `.env.example`에는 placeholder만 사용

### HTTPS
- 프로덕션 환경에서는 반드시 HTTPS 사용
- 자체 서명 인증서는 개발 환경에서만 사용

### 입력 검증 / 테넌트 격리
- 모든 사용자 입력은 검증 (SQL Injection, XSS 방지)
- **모든 데이터 접근은 `tenant_id` 필터를 거친다** — 테넌트 간 데이터 누출 금지(멀티테넌시 핵심 불변식)
- RBAC 권한 검증을 통과한 요청만 도메인 로직 수행

---

## 작업 원칙

1. **단순함 우선** — 요청한 것만, 추측 기능 금지.
2. **외과적 변경** — 관련 없는 코드/포맷 건드리지 않기.
3. **착수 전 확정** — DB/인증/언어(Java·Kotlin)/빌드도구 등 미정 항목은 결정 후 진행.
4. **F0 선행** — 테넌시·인증·RBAC 토대 위에서만 업무 모듈 구현.

**MUST DO (필수 수행)**:
- 코드 수정 시 해당 코드 파일의 내용을 **반드시 읽고** 수정한다.
- 작업 전 [docs/architecture.md](./docs/architecture.md) 등 관련 설계 문서를 검토하고 진행한다.
- 구조/API 변경 시 해당 문서를 갱신한다.

---

## 참고 문서

- [README.md](./README.md) — 프로젝트 소개
- [docs/architecture.md](./docs/architecture.md) — 아키텍처 & 전체 기능 맵 (레이어·Agent 아키텍처·로드맵)
- 모노레포 형제 프로젝트: [moai-agent](../moai-agent/) (AI Agent 프레임워크)
- 요구사항·체크포인트는 `.claude/requirements/` 에서 `/dev` 스킬로 관리.

---

**마지막 업데이트**: 2026-06-04
