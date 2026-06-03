---
name: dev-project-auto-doc
description: Automatically analyze project code and generate documentation. Use for (1) Initialize project docs with code analysis, (2) Refresh all or specific docs, (3) Check code-doc sync status, (4) Manage module-specific documentation
---

# Project Auto Documentation

Automatically analyze project source code and generate/update documentation.
Supports **scale-based** document sets (minimal/standard/full) for different project sizes.
Supports **module documentation** for multi-module projects (monorepo, microservices).

## Key Features

- **Scale-based Docs**: Choose minimal (3), standard (6), or full (12) document sets
- **Module Docs**: Separate documentation for each module in multi-module projects
- **Code Analysis**: Scans entities, controllers, services, components
- **Auto Generation**: Creates docs based on actual code structure
- **Sync Detection**: Identifies gaps between code and docs
- **Template Support**: Uses templates for consistent documentation
- **Upgrade Path**: Grow docs as project grows

---

## Project Scales

| Scale | Documents | Use Case |
|-------|-----------|----------|
| **Minimal** | 3 | Personal projects, prototypes |
| **Standard** | 6 | Team projects, mid-size services |
| **Full** | 12 | Enterprise, production systems |

### Minimal (3 docs)
- README.md, architecture.md, development.md

### Standard (6 docs)
- + core_concepts.md, api.md, configuration.md

### Full (12 docs)
- + integrations.md, testing.md, security.md, deployment.md, monitoring.md, troubleshooting.md

---

## Scripts

### init

Initialize project documentation with code analysis.

```bash
# Interactive mode (recommended)
/dev-project-auto-doc init

# Direct scale selection
/dev-project-auto-doc init --scale=minimal
/dev-project-auto-doc init --scale=standard
/dev-project-auto-doc init --scale=full

# With module documentation (for multi-module projects)
/dev-project-auto-doc init --scale=standard --with-modules

# Force overwrite existing docs
/dev-project-auto-doc init --scale=standard --force
```

**Actions**:
1. Scan project source code
2. Ask for scale selection (interactive mode)
3. Create `.claude/project_docs/` directory
4. Generate documentation based on analysis and templates
5. **Update CLAUDE.md** (see CLAUDE.md 처리 로직 below)
6. Save scale info for future upgrades
7. (Optional) Generate module documentation if `--with-modules`

---

### refresh

Refresh documentation based on current code.

```bash
# Refresh all docs
/dev-project-auto-doc refresh

# Refresh specific doc
/dev-project-auto-doc refresh api
/dev-project-auto-doc refresh architecture
/dev-project-auto-doc refresh readme
/dev-project-auto-doc refresh development
```

Updates existing docs to match current code structure.

---

### upgrade

Upgrade project documentation scale.

```bash
# Show current status
/dev-project-auto-doc upgrade

# Upgrade to standard (from minimal)
/dev-project-auto-doc upgrade standard

# Upgrade to full (from minimal or standard)
/dev-project-auto-doc upgrade full
```

Adds missing documents without overwriting existing ones.

---

### add-doc

Add individual documents outside current scale.

```bash
# Show available documents
/dev-project-auto-doc add-doc

# Add specific document
/dev-project-auto-doc add-doc testing
/dev-project-auto-doc add-doc security
/dev-project-auto-doc add-doc deployment
/dev-project-auto-doc add-doc monitoring
/dev-project-auto-doc add-doc troubleshooting
```

Useful for adding specific docs without full scale upgrade.

---

### analyze

Analyze project code without generating docs.

```bash
/dev-project-auto-doc analyze
```

Shows:
- Entities found
- Controllers/APIs found
- React pages/components found
- Services/repositories found
- Detected modules (for multi-module projects)

Useful for understanding project structure before documentation.

---

### status

Check synchronization status between code and docs.

```bash
/dev-project-auto-doc status
```

Reports:
- Current scale and document count
- Documents up-to-date
- Documents needing update
- New code without docs
- Module documentation status
- Upgrade options

---

## Module Documentation (NEW)

For multi-module projects (monorepo, microservices), separate documentation can be generated for each module.

### modules

Manage module-specific documentation.

```bash
# Detect modules and initialize documentation
/dev-project-auto-doc modules
/dev-project-auto-doc modules init

# Refresh all module documents
/dev-project-auto-doc modules refresh

# Refresh specific module
/dev-project-auto-doc modules refresh --module=api
/dev-project-auto-doc modules refresh --module=web

# Check module documentation status
/dev-project-auto-doc modules status

# Add custom development rule to module
/dev-project-auto-doc modules add-rule --module=web
/dev-project-auto-doc modules add-rule --module=api --category="API 호출 규칙"
```

**Module Detection**:
Modules are automatically detected by these indicators:
- **Java**: `pom.xml`, `build.gradle`, `build.gradle.kts`
- **Node.js**: `package.json`
- **Python**: `setup.py`, `pyproject.toml`, `requirements.txt`

**Excluded Directories**:
- `node_modules`, `.venv`, `venv`, `__pycache__`
- `target`, `build`, `dist`
- `.git`, `.claude`

### Module Documentation Structure

```
.claude/project_docs/
├── README.md              # Project overview
├── architecture.md        # System architecture
├── ...                    # Other project docs
│
└── modules/               # Module-specific documentation
    ├── moai-requirements-api.md
    ├── moai-requirements-web.md
    ├── moai-requirements-claudecode-agent.md
    └── moai-requirements-task-crawler.md
```

### Module Document Contents

Each module document includes:
- Module overview and purpose
- Technology stack
- Directory structure
- Key components (entities, controllers, services, etc.)
- **Development rules (auto-generated based on module type)**
- Configuration
- Development guide (build, run, test)
- API endpoints (if applicable)

### Auto-Generated Development Rules

Development rules are automatically generated based on module type:

| Module Type | Generated Rules |
|-------------|-----------------|
| **Java/Spring** | 클래스/메서드 명명, URI 명명, Bean Validation, DB 명명규칙, 보안 |
| **React (Node.js)** | 컴포넌트 명명, 디렉토리 구조, 상태 관리, UI/UX, 보안 |
| **Node.js** | 파일/함수 명명, 비동기 처리, 에러 처리, 보안 |
| **Python** | PEP8 스타일, 타입 힌트, 에러 처리, 로깅, 보안 |

Rules can be customized after generation to match project-specific standards.

### Code Pattern Analysis (Auto-Detection)

모듈 문서 생성/갱신 시 실제 코드에서 사용 중인 패턴을 자동으로 분석하여 문서에 포함합니다.

**감지되는 패턴**:

| Module Type | Detected Patterns |
|-------------|-------------------|
| **React** | Dialog (maxWidth, fullWidth, fullScreen), Snackbar (position, duration, severity), Button (color, variant), Form handling, Error handling |
| **Java/Spring** | ResponseEntity (HTTP 상태코드), Exception (커스텀 예외), Validation (@Valid, @NotNull 등), Authorization (@PreAuthorize, PermissionCode), Transaction |
| **Python** | Logging (logging module), Error handling (try-except), Type hints, Async patterns |
| **Node.js** | Async/await, Promise.all, Error handling, Logging (winston, console) |

**문서에 추가되는 내용 예시**:

```markdown
## 감지된 코드 패턴 (자동 분석)

### Dialog 패턴
| 속성 | 사용 값 |
|------|---------|
| **maxWidth** | `sm` (20회), `md` (4회), `xs` (2회) |
| **fullWidth** | `true` (1회) |

### Snackbar/알림 패턴
| 속성 | 사용 값 |
|------|---------|
| **autoHideDuration** | `6000` (1회), `3000` (1회) |
| **position** | `top-right` (1회), `bottom-center` (1회) |
```

패턴 분석은 `/dev doc modules refresh` 실행 시 자동으로 수행됩니다.

---

### AI 모듈 문서 작성 지침 (MANDATORY)

모듈 문서 생성/갱신 시, AI 어시스턴트는 **반드시** 아래 지침에 따라 각 섹션을 분석하고 작성해야 합니다.
Python 스크립트는 기본 정보만 수집하고, **의미있는 내용은 AI가 직접 코드를 분석하여 작성**합니다.

#### 1. 역할 (Role) 섹션 작성

**분석 방법**:
1. 모듈의 주요 컴포넌트(Controller, Service, Page 등) 확인
2. 빌드 파일(pom.xml, package.json)의 description 확인
3. README.md가 있으면 참고

**작성 기준**:
```markdown
### 역할

- [모듈의 핵심 책임 1] (예: "REST API 엔드포인트 제공")
- [모듈의 핵심 책임 2] (예: "사용자 인증 및 권한 관리")
- [모듈의 핵심 책임 3] (예: "비즈니스 로직 처리")
```

**예시 (Java API 모듈)**:
```markdown
### 역할

- 워크스페이스 기반 일감(Task) 관리 REST API 제공
- JWT 기반 사용자 인증 및 역할/권한(RBAC) 관리
- 외부 시스템(Jira, Redmine 등) 크롤링 데이터 수신 및 변환
- 실시간 알림 및 대시보드 데이터 제공
```

#### 2. 기술 스택 (Tech Stack) 섹션 작성

**분석 방법**:
1. **Java**: `pom.xml`의 `<dependencies>` 섹션 분석
2. **Node.js**: `package.json`의 `dependencies`, `devDependencies` 분석
3. **Python**: `requirements.txt` 또는 `pyproject.toml` 분석

**작성 기준**:
- 단순 나열이 아닌, 용도와 함께 설명
- 주요 의존성만 포함 (유틸리티 라이브러리 제외)

**예시 (Java API 모듈)**:
```markdown
## 기술 스택

| 기술 | 용도 |
|------|------|
| **Java 17** | 런타임 환경 |
| **Spring Boot 3.x** | 애플리케이션 프레임워크 |
| **Spring Security** | 인증/인가 (JWT + HttpOnly Cookie) |
| **Spring Data JPA** | 데이터 접근 계층 |
| **PostgreSQL** | 관계형 데이터베이스 |
| **Lombok** | 보일러플레이트 코드 감소 |
| **MapStruct** | DTO-Entity 매핑 |
```

**예시 (React 모듈)**:
```markdown
## 기술 스택

| 기술 | 용도 |
|------|------|
| **React 18** | UI 라이브러리 |
| **TypeScript** | 타입 안정성 |
| **Vite** | 빌드 도구 |
| **MUI (Material-UI) 7** | UI 컴포넌트 라이브러리 |
| **React Router 6** | 클라이언트 라우팅 |
| **Axios** | HTTP 클라이언트 |
| **i18next** | 다국어 지원 |
```

#### 3. 디렉토리 구조 (Directory Structure) 섹션 작성

**분석 방법**:
1. `src/` 디렉토리의 실제 구조 확인
2. 각 디렉토리의 역할 파악

**작성 기준**:
- 실제 디렉토리 구조 반영
- 주요 디렉토리에 설명 주석 추가
- 깊이는 2-3레벨까지

**예시 (Java API 모듈)**:
```markdown
## 디렉토리 구조

```
moai-requirements-api/
├── src/main/java/com/example/
│   ├── config/          # Spring 설정 (Security, Web, etc.)
│   ├── controller/      # REST API 컨트롤러
│   ├── service/         # 비즈니스 로직
│   ├── repository/      # 데이터 접근 계층
│   ├── entity/          # JPA 엔티티
│   ├── dto/             # 요청/응답 DTO
│   ├── exception/       # 커스텀 예외
│   └── security/        # 인증/인가 관련
├── src/main/resources/
│   ├── application.yml  # 메인 설정
│   └── application-*.yml # 환경별 설정
└── pom.xml              # Maven 빌드 설정
```
```

**예시 (React 모듈)**:
```markdown
## 디렉토리 구조

```
moai-requirements-web/
├── src/
│   ├── api/             # API 호출 모듈 (axios 인스턴스, 엔드포인트)
│   ├── components/      # 재사용 가능한 UI 컴포넌트
│   │   ├── dialogs/     # 다이얼로그 컴포넌트
│   │   └── common/      # 공통 컴포넌트
│   ├── contexts/        # React Context (전역 상태)
│   ├── hooks/           # 커스텀 훅
│   ├── pages/           # 라우트별 페이지 컴포넌트
│   ├── utils/           # 유틸리티 함수
│   └── i18n/            # 다국어 리소스
├── public/              # 정적 파일
├── package.json         # 의존성 및 스크립트
└── vite.config.ts       # Vite 설정
```
```

#### 4. 모듈 문서 갱신 프로세스

`/dev doc modules refresh` 실행 시:

1. **Python 스크립트 실행**: 모듈 감지, 컴포넌트 목록, 패턴 분석
2. **AI 분석 수행** (MANDATORY):
   - 모듈의 빌드 파일 읽기 (pom.xml, package.json, requirements.txt)
   - src 디렉토리 구조 확인
   - 위 지침에 따라 역할, 기술 스택, 디렉토리 구조 섹션 작성
3. **문서 생성/업데이트**: 분석 결과를 모듈 문서에 반영

**MUST DO**:
- 템플릿 플레이스홀더(`[이 모듈의 주요 역할 1]` 등)를 **절대** 그대로 두지 않음
- 실제 코드를 분석하여 **구체적인 내용**으로 작성
- 기존 문서가 있으면 내용을 보존하되, 코드와 불일치하는 부분 업데이트

**MUST NOT DO**:
- 플레이스홀더를 그대로 남기고 문서 생성 완료 처리
- 코드를 확인하지 않고 추측으로 작성
- 과도하게 상세하거나 불필요한 정보 포함

---

### modules add-rule

모듈 문서에 커스텀 개발 규칙을 추가합니다.

```bash
# 대화형으로 규칙 추가 (카테고리 선택)
/dev-project-auto-doc modules add-rule --module=web

# 특정 카테고리로 규칙 추가
/dev-project-auto-doc modules add-rule --module=web --category="API 호출 규칙"
/dev-project-auto-doc modules add-rule --module=api --category="트랜잭션 규칙"
```

**실행 프로세스**:

1. **모듈 선택** (--module 없으면 목록에서 선택)
2. **카테고리 입력** (--category 없으면 대화형으로 입력)
   - 새 카테고리 생성 또는 기존 카테고리에 추가
3. **규칙 입력** (대화형)
   - 규칙 항목과 설명을 입력
   - 여러 규칙을 순차적으로 추가 가능
   - "완료"를 입력하면 종료
4. **문서 업데이트**
   - 모듈 문서의 "개발 규칙 (MANDATORY)" 섹션에 추가

**AI 어시스턴트 지침** (MANDATORY):

`/dev doc modules add-rule` 실행 시:

```
1. 모듈 문서 파일 읽기
   └── .claude/project_docs/modules/{모듈명}.md

2. 사용자에게 카테고리 입력 요청 (--category 없으면)
   └── AskUserQuestion 사용
   └── 예: "어떤 카테고리의 규칙을 추가하시겠습니까?"
   └── 옵션: "API 호출 규칙", "컴포넌트 규칙", "스타일링 규칙", "기타 (직접 입력)"

3. 규칙 항목 입력 요청 (반복)
   └── "규칙 항목을 입력하세요 (완료 시 '완료' 입력):"
   └── 항목: "규칙명" → 설명: "규칙 설명"
   └── 예: "axios 인스턴스" → "api/axios.ts의 공통 인스턴스 사용 필수"

4. 문서에 규칙 추가
   └── "개발 규칙 (MANDATORY)" 섹션 찾기
   └── 카테고리 테이블 추가 또는 기존 카테고리에 행 추가

5. 결과 확인 메시지 출력
```

**생성되는 규칙 형식**:

```markdown
### {카테고리명}

| 항목 | 규칙 |
|------|------|
| **{규칙명1}** | {규칙 설명1} |
| **{규칙명2}** | {규칙 설명2} |
```

**예시**:

입력:
- 모듈: web
- 카테고리: API 호출 규칙
- 규칙1: axios 인스턴스 → api/axios.ts의 공통 인스턴스 사용 필수
- 규칙2: 에러 처리 → errorHandler 유틸리티 함수 사용

결과 (moai-requirements-web.md에 추가됨):
```markdown
### API 호출 규칙

| 항목 | 규칙 |
|------|------|
| **axios 인스턴스** | api/axios.ts의 공통 인스턴스 사용 필수 |
| **에러 처리** | errorHandler 유틸리티 함수 사용 |
```

---

### Shortcut Commands

```bash
/dev doc modules                    # Same as /dev-project-auto-doc modules
/dev doc modules refresh            # Same as /dev-project-auto-doc modules refresh
/dev doc modules refresh api        # Same as /dev-project-auto-doc modules refresh --module=api
/dev doc modules status             # Same as /dev-project-auto-doc modules status
/dev doc modules add-rule web       # Same as /dev-project-auto-doc modules add-rule --module=web
```

---

## Supported Project Types

**Backend (Java/Spring)**:
- Entities: `**/entity/*.java`
- Controllers: `**/*Controller.java`
- Services: `**/*Service.java`
- Repositories: `**/*Repository.java`

**Backend (Node.js/TypeScript)**:
- Models: `**/models/*.ts`
- Routes: `**/routes/*.ts`
- Controllers: `**/controllers/*.ts`

**Frontend (React)**:
- Pages: `**/pages/*.tsx`, `**/pages/*.jsx`
- Components: `**/components/**/*.tsx`
- Contexts: `**/*Context.tsx`

**Python**:
- Modules: `**/*.py`
- Models: `**/models/*.py`
- API: `**/api/*.py`

---

## Generated Structure

### Minimal
```
.claude/project_docs/
├── README.md         # Project overview
├── architecture.md   # System architecture
└── development.md    # Development guide
```

### Standard
```
.claude/project_docs/
├── README.md         # Project overview
├── architecture.md   # System architecture
├── core_concepts.md  # Key concepts
├── api.md            # API documentation
├── configuration.md  # Configuration guide
└── development.md    # Development guide
```

### Full
```
.claude/project_docs/
├── README.md         # Project overview
├── architecture.md   # System architecture
├── core_concepts.md  # Key concepts
├── api.md            # API documentation
├── configuration.md  # Configuration guide
├── development.md    # Development guide
├── integrations.md   # External integrations
├── testing.md        # Test strategy
├── security.md       # Security guidelines
├── deployment.md     # Deployment guide
├── monitoring.md     # Monitoring setup
└── troubleshooting.md # Problem solving
```

### With Modules
```
.claude/project_docs/
├── README.md
├── architecture.md
├── ...
└── modules/
    ├── module-a.md
    ├── module-b.md
    └── module-c.md
```

---

## Typical Workflow

**Initial setup:**
```bash
# Interactive - choose your scale
/dev-project-auto-doc init

# Or specify directly
/dev-project-auto-doc init --scale=standard

# For multi-module projects
/dev-project-auto-doc init --scale=standard --with-modules
```

**As project grows:**
```bash
# Add specific doc
/dev-project-auto-doc add-doc testing

# Or upgrade scale
/dev-project-auto-doc upgrade full

# Add module documentation later
/dev-project-auto-doc modules init
```

**After code changes:**
```bash
# Check what needs updating
/dev-project-auto-doc status

# Update specific doc
/dev-project-auto-doc refresh api

# Or update all
/dev-project-auto-doc refresh

# Update module docs
/dev-project-auto-doc modules refresh
```

**Shortcut commands:**
```bash
/dev doc                    # Same as /dev-project-auto-doc init
/dev doc refresh            # Same as /dev-project-auto-doc refresh
/dev doc refresh api        # Same as /dev-project-auto-doc refresh api
/dev doc upgrade full       # Same as /dev-project-auto-doc upgrade full
/dev doc add testing        # Same as /dev-project-auto-doc add-doc testing
/dev doc modules            # Same as /dev-project-auto-doc modules
/dev doc modules refresh    # Same as /dev-project-auto-doc modules refresh
```

---

## Templates

### Project Documentation Templates

Templates are stored in `templates/project_docs/`:
- Each document has a `.template` file
- Variables: `{{PROJECT_NAME}}`, `{{CREATION_DATE}}`
- Customize templates for your organization's standards

### Module Documentation Templates

Templates are stored in `templates/modules/`:
- `module.md.template`: Base template for module documentation
- Variables: `{{MODULE_NAME}}`, `{{MODULE_TYPE}}`, `{{TECH_STACK}}`

### CLAUDE.md Section Template

Template for CLAUDE.md section: `templates/claude_md_section.md.template`
- Variables: `{{SCALE_NAME}}`, `{{DOC_COUNT}}`, `{{DOC_LIST}}`, `{{MODULE_LIST}}`
- Contains AI assistant guidelines for document updates
- Defines when to refresh which documents based on code changes

**Template Structure**:
```
templates/
├── project_docs/           # 12 document templates
│   ├── README.md.template
│   ├── architecture.md.template
│   └── ...
├── modules/                # Module templates (NEW)
│   └── module.md.template
└── claude_md_section.md.template  # CLAUDE.md section template
```

---

## CLAUDE.md 처리 로직 (MANDATORY)

AI 어시스턴트는 `init` 실행 시 **반드시** 다음 로직을 따라야 합니다:

### 1. CLAUDE.md 존재 여부 확인

```
CLAUDE.md 파일이 존재하는가?
├── NO  → 신규 생성
└── YES → 2단계로 진행
```

### 2. 존재할 경우: 백업 및 사용자 선택

1. **기존 파일 백업**: `CLAUDE.md.backup.[YYYYMMDD-HHMMSS]` 형식으로 백업
2. **사용자에게 질문** (AskUserQuestion 사용):
   ```
   "CLAUDE.md가 이미 존재합니다. 어떻게 처리할까요?"

   옵션:
   - "기존에 추가": 기존 CLAUDE.md 내용을 유지하고 스킬 섹션만 추가/업데이트
   - "새로 만들기": 기존 내용을 백업 후, 스킬 섹션만 포함한 새 CLAUDE.md 생성
   - "취소": CLAUDE.md 수정 없이 진행
   ```

### 3. 선택에 따른 처리

| 선택 | 처리 방법 |
|------|----------|
| **기존에 추가** | 기존 내용 하단에 `## 프로젝트 문서` 섹션 추가/업데이트 |
| **새로 만들기** | 프로젝트 기본 정보 + `## 프로젝트 문서` 섹션만 포함 |
| **취소** | CLAUDE.md 변경 없음 |

### 4. 섹션 추가 시 중복 방지

- `## 프로젝트 문서` 섹션이 이미 존재하면 해당 섹션만 업데이트
- 섹션이 없으면 파일 끝에 추가

---

## Notes

- Generated docs are templates - customize with project-specific details
- Re-running `refresh` preserves manual edits where possible
- CLAUDE.md is automatically backed up before updates
- Scale info is cached in `.claude/.cache/project_docs_scale.json`
- Module info is cached in `.claude/.cache/modules_info.json`
- Module documentation is optional - single-module projects don't need it
