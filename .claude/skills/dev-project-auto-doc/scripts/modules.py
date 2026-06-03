#!/usr/bin/env python3
"""
모듈 문서화 관리 스크립트

멀티모듈 프로젝트의 각 모듈별 문서를 자동으로 생성하고 관리합니다.

사용법:
    python modules.py                           # 모듈 감지 및 초기화
    python modules.py --action=init             # 모듈 문서 초기화
    python modules.py --action=refresh          # 전체 모듈 문서 갱신
    python modules.py --action=refresh --module=api  # 특정 모듈만 갱신
    python modules.py --action=status           # 모듈 문서 상태 확인
"""

import argparse
import json
import re
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Tuple

# 패턴 분석기 import
try:
    from pattern_analyzer import analyze_module_patterns, format_detected_patterns
    HAS_PATTERN_ANALYZER = True
except ImportError:
    HAS_PATTERN_ANALYZER = False


# 모듈 감지 설정
MODULE_INDICATORS = {
    "java": ["pom.xml", "build.gradle", "build.gradle.kts"],
    "nodejs": ["package.json"],
    "python": ["setup.py", "pyproject.toml", "requirements.txt"],
}

EXCLUDE_DIRS = [
    "node_modules",
    ".venv",
    "venv",
    "__pycache__",
    "target",
    "build",
    "dist",
    ".git",
    ".claude",
    ".idea",
    ".vscode",
]


def get_project_root() -> Path:
    """프로젝트 루트 디렉토리를 반환합니다."""
    return Path.cwd()


def get_modules_docs_dir() -> Path:
    """모듈 문서 디렉토리를 반환합니다."""
    return get_project_root() / ".claude" / "project_docs" / "modules"


def get_cache_dir() -> Path:
    """캐시 디렉토리를 반환합니다."""
    return get_project_root() / ".claude" / ".cache"


def should_exclude_dir(path: Path) -> bool:
    """제외해야 할 디렉토리인지 확인합니다."""
    for part in path.parts:
        if part in EXCLUDE_DIRS:
            return True
    return False


def detect_module_type(module_path: Path) -> Tuple[str, Optional[str]]:
    """모듈의 타입과 이름을 감지합니다."""
    for lang, indicators in MODULE_INDICATORS.items():
        for indicator in indicators:
            indicator_path = module_path / indicator
            if indicator_path.exists():
                # 모듈 이름 추출
                module_name = extract_module_name(indicator_path, lang)
                return lang, module_name
    return "unknown", None


def extract_module_name(indicator_path: Path, lang: str) -> Optional[str]:
    """빌드 파일에서 모듈 이름을 추출합니다."""
    try:
        content = indicator_path.read_text(encoding="utf-8")

        if lang == "java":
            if indicator_path.name == "pom.xml":
                # parent 태그 밖의 artifactId를 찾기 위해 parent 태그 제거 후 검색
                content_without_parent = re.sub(r'<parent>.*?</parent>', '', content, flags=re.DOTALL)
                match = re.search(r'<artifactId>([^<]+)</artifactId>', content_without_parent)
                if match:
                    return match.group(1)
            elif "build.gradle" in indicator_path.name:
                # rootProject.name 또는 project.name
                match = re.search(r"rootProject\.name\s*=\s*['\"]([^'\"]+)['\"]", content)
                if match:
                    return match.group(1)

        elif lang == "nodejs":
            data = json.loads(content)
            return data.get("name")

        elif lang == "python":
            if indicator_path.name == "pyproject.toml":
                match = re.search(r'name\s*=\s*["\']([^"\']+)["\']', content)
                if match:
                    return match.group(1)
            elif indicator_path.name == "setup.py":
                match = re.search(r"name\s*=\s*['\"]([^'\"]+)['\"]", content)
                if match:
                    return match.group(1)
    except Exception:
        pass

    # 기본값: 디렉토리 이름
    return indicator_path.parent.name


def detect_modules(root: Path) -> List[Dict]:
    """프로젝트의 모든 모듈을 감지합니다."""
    modules = []
    root_module_type, root_name = detect_module_type(root)

    # 루트가 모듈인 경우 (싱글 모듈 프로젝트)
    is_root_module = root_module_type != "unknown"

    # 하위 모듈 검색
    for item in root.iterdir():
        if not item.is_dir():
            continue
        if should_exclude_dir(item):
            continue

        module_type, module_name = detect_module_type(item)
        if module_type != "unknown":
            modules.append({
                "path": str(item.relative_to(root)),
                "absolute_path": str(item),
                "name": module_name or item.name,
                "type": module_type,
                "is_root": False,
            })

    # 하위 모듈이 없고 루트가 모듈인 경우, 멀티모듈이 아님
    if not modules and is_root_module:
        print("ℹ️  싱글 모듈 프로젝트입니다. 모듈별 문서화가 필요하지 않습니다.")
        return []

    return modules


def analyze_module(module_path: Path, module_type: str) -> Dict:
    """단일 모듈을 분석합니다."""
    result = {
        "entities": [],
        "controllers": [],
        "services": [],
        "components": [],
        "pages": [],
        "routes": [],
        "endpoints": [],
        "tech_stack": [],
        "detected_patterns": {},  # 코드에서 감지된 패턴
    }

    if module_type == "java":
        result = analyze_java_module(module_path, result)
    elif module_type == "nodejs":
        result = analyze_nodejs_module(module_path, result)
    elif module_type == "python":
        result = analyze_python_module(module_path, result)

    # 코드 패턴 분석 (패턴 분석기가 있는 경우)
    if HAS_PATTERN_ANALYZER:
        try:
            result["detected_patterns"] = analyze_module_patterns(module_path, module_type)
        except Exception as e:
            print(f"   ⚠️  패턴 분석 중 오류: {e}")

    return result


def analyze_java_module(module_path: Path, result: Dict) -> Dict:
    """Java/Spring 모듈을 분석합니다."""
    result["tech_stack"].append("Java")

    src_dir = module_path / "src" / "main" / "java"
    if not src_dir.exists():
        return result

    # pom.xml에서 의존성 확인
    pom_path = module_path / "pom.xml"
    if pom_path.exists():
        pom_content = pom_path.read_text(encoding="utf-8", errors="ignore")
        if "spring-boot" in pom_content:
            result["tech_stack"].append("Spring Boot")
        if "spring-webflux" in pom_content:
            result["tech_stack"].append("WebFlux")
        if "spring-data-jpa" in pom_content:
            result["tech_stack"].append("JPA")

    # Entity
    for f in src_dir.rglob("**/entity/*.java"):
        class_name = extract_java_class_name(f)
        if class_name:
            result["entities"].append({"name": class_name, "file": str(f.name)})

    # Controller
    for f in src_dir.rglob("**/*Controller.java"):
        class_name = extract_java_class_name(f)
        endpoints = extract_java_endpoints(f)
        if class_name:
            result["controllers"].append({
                "name": class_name,
                "file": str(f.name),
                "endpoints": endpoints
            })
            result["endpoints"].extend(endpoints)

    # Service
    for f in src_dir.rglob("**/*Service.java"):
        if "Impl" not in f.name:
            class_name = extract_java_class_name(f)
            if class_name:
                result["services"].append({"name": class_name, "file": str(f.name)})

    return result


def analyze_nodejs_module(module_path: Path, result: Dict) -> Dict:
    """Node.js 모듈을 분석합니다."""
    result["tech_stack"].append("Node.js")

    pkg_path = module_path / "package.json"
    if pkg_path.exists():
        try:
            pkg = json.loads(pkg_path.read_text())
            deps = {**pkg.get("dependencies", {}), **pkg.get("devDependencies", {})}

            if "react" in deps:
                result["tech_stack"].append("React")
            if "vue" in deps:
                result["tech_stack"].append("Vue.js")
            if "express" in deps:
                result["tech_stack"].append("Express")
            if "typescript" in deps:
                result["tech_stack"].append("TypeScript")
            if "vite" in deps:
                result["tech_stack"].append("Vite")
        except Exception:
            pass

    src_dir = module_path / "src"
    if not src_dir.exists():
        src_dir = module_path

    # React Pages
    pages_dir = src_dir / "pages"
    if pages_dir.exists():
        for f in pages_dir.rglob("*.tsx"):
            result["pages"].append({"name": f.stem, "file": str(f.name)})
        for f in pages_dir.rglob("*.jsx"):
            result["pages"].append({"name": f.stem, "file": str(f.name)})

    # React Components
    components_dir = src_dir / "components"
    if components_dir.exists():
        for f in components_dir.rglob("*.tsx"):
            if "index" not in f.name.lower():
                result["components"].append({"name": f.stem, "file": str(f.name)})

    # Routes (Express)
    routes_dir = src_dir / "routes"
    if routes_dir.exists():
        for f in routes_dir.rglob("*.ts"):
            result["routes"].append({"name": f.stem, "file": str(f.name)})
        for f in routes_dir.rglob("*.js"):
            result["routes"].append({"name": f.stem, "file": str(f.name)})

    return result


def analyze_python_module(module_path: Path, result: Dict) -> Dict:
    """Python 모듈을 분석합니다."""
    result["tech_stack"].append("Python")

    # requirements.txt에서 의존성 확인
    req_path = module_path / "requirements.txt"
    if req_path.exists():
        req_content = req_path.read_text(encoding="utf-8", errors="ignore").lower()
        if "flask" in req_content:
            result["tech_stack"].append("Flask")
        if "django" in req_content:
            result["tech_stack"].append("Django")
        if "fastapi" in req_content:
            result["tech_stack"].append("FastAPI")
        if "playwright" in req_content:
            result["tech_stack"].append("Playwright")

    # Python 파일 분석
    for f in module_path.rglob("*.py"):
        if should_exclude_dir(f):
            continue

        # api 디렉토리
        if "api" in f.parts:
            result["routes"].append({"name": f.stem, "file": str(f.name)})
        # models 디렉토리
        elif "models" in f.parts:
            result["entities"].append({"name": f.stem, "file": str(f.name)})

    return result


def extract_java_class_name(file_path: Path) -> Optional[str]:
    """Java 파일에서 클래스명을 추출합니다."""
    try:
        content = file_path.read_text(encoding="utf-8", errors="ignore")
        match = re.search(r'public\s+(?:class|interface)\s+(\w+)', content)
        if match:
            return match.group(1)
    except Exception:
        pass
    return file_path.stem


def extract_java_endpoints(file_path: Path) -> List[Dict]:
    """Java Controller 파일에서 API 엔드포인트를 추출합니다."""
    endpoints = []
    try:
        content = file_path.read_text(encoding="utf-8", errors="ignore")

        # 클래스 레벨 @RequestMapping
        class_mapping = re.search(r'@RequestMapping\s*\(\s*["\']([^"\']+)["\']', content)
        base_path = class_mapping.group(1) if class_mapping else ""

        # 메서드 레벨 매핑
        patterns = [
            (r'@GetMapping\s*\(\s*["\']([^"\']*)["\']', 'GET'),
            (r'@PostMapping\s*\(\s*["\']([^"\']*)["\']', 'POST'),
            (r'@PutMapping\s*\(\s*["\']([^"\']*)["\']', 'PUT'),
            (r'@DeleteMapping\s*\(\s*["\']([^"\']*)["\']', 'DELETE'),
            (r'@PatchMapping\s*\(\s*["\']([^"\']*)["\']', 'PATCH'),
        ]

        for pattern, method in patterns:
            matches = re.findall(pattern, content)
            for match in matches:
                path = base_path + match
                endpoints.append({"method": method, "path": path})
    except Exception:
        pass

    return endpoints


def generate_development_rules(module_type: str, tech_stack: List[str]) -> str:
    """모듈 타입에 따른 개발 규칙을 생성합니다."""
    rules = []

    rules.append("> AI 어시스턴트는 이 모듈 작업 시 아래 규칙을 **반드시** 준수해야 합니다.")
    rules.append("")

    if module_type == "java":
        rules.append("### 코드 스타일")
        rules.append("")
        rules.append("| 항목 | 규칙 |")
        rules.append("|------|------|")
        rules.append("| **클래스 명명** | PascalCase, 단수형 (`TaskService`, `WorkspaceController`) |")
        rules.append("| **메서드 명명** | camelCase, 동사로 시작 (`createTask`, `findById`) |")
        rules.append("| **패키지 구조** | `{basepackage}.{domain}.{layer}` 형식 |")
        rules.append("| **DTO 명명** | `{Action}{Entity}Request/Response` 형식 |")
        rules.append("")

        rules.append("### API 설계 규칙")
        rules.append("")
        rules.append("| 항목 | 규칙 |")
        rules.append("|------|------|")
        rules.append("| **URI 명명** | kebab-case, 복수형 리소스 (`/resources/{id}`) |")
        rules.append("| **HTTP 메서드** | GET(조회), POST(생성), PUT(전체수정), PATCH(부분수정), DELETE(삭제) |")
        rules.append("| **응답 형식** | 성공: 데이터 직접 반환, 에러: `{ error: { code, message } }` |")
        rules.append("")

        rules.append("### 검증 규칙")
        rules.append("")
        rules.append("| 항목 | 규칙 |")
        rules.append("|------|------|")
        rules.append("| **입력 검증** | DTO에 `@Valid` + Bean Validation 어노테이션 |")
        rules.append("| **필수값** | `@NotNull`, `@NotBlank`, `@NotEmpty` |")
        rules.append("")

        rules.append("### 데이터베이스 규칙")
        rules.append("")
        rules.append("| 항목 | 규칙 |")
        rules.append("|------|------|")
        rules.append("| **테이블 명명** | snake_case, 복수형 (`tasks`, `users`) |")
        rules.append("| **컬럼 명명** | snake_case (`created_at`, `user_id`) |")
        rules.append("| **엔티티 명명** | PascalCase, 단수형 (`Task`, `User`) |")

    elif module_type == "nodejs":
        is_react = any("React" in t or "react" in t.lower() for t in tech_stack)

        if is_react:
            rules.append("### 코드 스타일")
            rules.append("")
            rules.append("| 항목 | 규칙 |")
            rules.append("|------|------|")
            rules.append("| **파일 명명** | PascalCase for components (`TaskCard.jsx`), camelCase for utils |")
            rules.append("| **컴포넌트 명명** | PascalCase, 기능 설명적 (`TaskListPage`, `NotificationDropdown`) |")
            rules.append("| **함수 명명** | camelCase, 동사로 시작 (`handleClick`, `fetchData`) |")
            rules.append("")

            rules.append("### 디렉토리 구조 규칙")
            rules.append("")
            rules.append("| 디렉토리 | 용도 |")
            rules.append("|----------|------|")
            rules.append("| `src/pages/` | 라우트 페이지 컴포넌트 |")
            rules.append("| `src/components/` | 재사용 가능한 컴포넌트 |")
            rules.append("| `src/contexts/` | React Context (전역 상태) |")
            rules.append("| `src/api/` | API 호출 모듈 |")
            rules.append("")

            rules.append("### 상태 관리 규칙")
            rules.append("")
            rules.append("| 항목 | 규칙 |")
            rules.append("|------|------|")
            rules.append("| **전역 상태** | React Context + useReducer |")
            rules.append("| **로컬 상태** | useState (단순), useReducer (복잡) |")
            rules.append("| **API 호출** | useEffect + useState 또는 전용 훅 |")
            rules.append("")

            rules.append("### UI/UX 규칙")
            rules.append("")
            rules.append("| 항목 | 규칙 |")
            rules.append("|------|------|")
            rules.append("| **UI 라이브러리** | 프로젝트에서 사용 중인 UI 라이브러리 우선 |")
            rules.append("| **스타일링** | 프로젝트 스타일링 방식 준수 |")
            rules.append("| **반응형** | 모바일-데스크톱 대응 필수 |")
        else:
            rules.append("### 코드 스타일")
            rules.append("")
            rules.append("| 항목 | 규칙 |")
            rules.append("|------|------|")
            rules.append("| **파일 명명** | camelCase (`taskService.js`, `apiClient.js`) |")
            rules.append("| **클래스 명명** | PascalCase (`TaskService`, `ApiClient`) |")
            rules.append("| **함수 명명** | camelCase, 동사로 시작 (`processTask`, `sendRequest`) |")
            rules.append("")

            rules.append("### 비동기 처리 규칙")
            rules.append("")
            rules.append("| 항목 | 규칙 |")
            rules.append("|------|------|")
            rules.append("| **비동기 함수** | async/await 사용 |")
            rules.append("| **에러 처리** | try-catch로 감싸기 |")
            rules.append("| **병렬 처리** | Promise.all 활용 |")

    elif module_type == "python":
        rules.append("### 코드 스타일")
        rules.append("")
        rules.append("| 항목 | 규칙 |")
        rules.append("|------|------|")
        rules.append("| **파일 명명** | snake_case (`task_service.py`, `api_client.py`) |")
        rules.append("| **클래스 명명** | PascalCase (`TaskService`, `ApiClient`) |")
        rules.append("| **함수 명명** | snake_case (`process_task`, `send_request`) |")
        rules.append("| **상수 명명** | UPPER_SNAKE_CASE (`MAX_RETRY`, `API_URL`) |")
        rules.append("")

        rules.append("### 타입 힌트 규칙")
        rules.append("")
        rules.append("| 항목 | 규칙 |")
        rules.append("|------|------|")
        rules.append("| **함수 인자** | 타입 힌트 필수 (`def func(name: str) -> bool:`) |")
        rules.append("| **반환값** | 반환 타입 명시 |")
        rules.append("")

        rules.append("### 에러 처리 규칙")
        rules.append("")
        rules.append("| 항목 | 규칙 |")
        rules.append("|------|------|")
        rules.append("| **예외 처리** | 구체적인 예외 타입 catch |")
        rules.append("| **로깅** | logging 모듈 사용 |")

    # 공통 보안 규칙
    rules.append("")
    rules.append("### 보안 규칙 (공통)")
    rules.append("")
    rules.append("| 항목 | 규칙 |")
    rules.append("|------|------|")
    rules.append("| **인증 정보** | 환경변수로 관리, 코드에 하드코딩 금지 |")
    rules.append("| **입력 검증** | 모든 외부 입력 검증 필수 |")
    rules.append("| **민감정보 로깅** | 비밀번호, 토큰 로그 출력 금지 |")

    return "\n".join(rules)


def generate_module_doc(module: Dict, analysis: Dict) -> str:
    """모듈 문서를 생성합니다."""
    template = get_module_template()

    now = datetime.now().strftime("%Y-%m-%d")

    # 기본 정보
    content = template.replace("{{MODULE_NAME}}", module["name"])
    content = content.replace("{{MODULE_PATH}}", module["path"])
    content = content.replace("{{MODULE_TYPE}}", module["type"])
    content = content.replace("{{CREATION_DATE}}", now)

    # 기술 스택
    tech_stack = ", ".join(analysis["tech_stack"]) if analysis["tech_stack"] else "N/A"
    content = content.replace("{{TECH_STACK}}", tech_stack)

    # 주요 컴포넌트
    components_section = generate_components_section(analysis)
    content = content.replace("{{COMPONENTS_SECTION}}", components_section)

    # 개발 규칙 (기본 규칙 + 감지된 패턴)
    development_rules = generate_development_rules(module["type"], analysis["tech_stack"])

    # 감지된 패턴 추가
    detected_patterns = analysis.get("detected_patterns", {})
    if detected_patterns and HAS_PATTERN_ANALYZER:
        patterns_section = format_detected_patterns(module["type"], detected_patterns)
        if patterns_section.strip():
            development_rules += "\n\n---\n\n## 감지된 코드 패턴 (자동 분석)\n\n"
            development_rules += "> 아래는 코드 분석을 통해 자동으로 감지된 패턴입니다. 일관성을 위해 이 패턴을 준수하세요.\n\n"
            development_rules += patterns_section

    content = content.replace("{{DEVELOPMENT_RULES}}", development_rules)

    # API 엔드포인트
    endpoints_section = generate_endpoints_section(analysis)
    content = content.replace("{{ENDPOINTS_SECTION}}", endpoints_section)

    return content


def get_module_template() -> str:
    """모듈 문서 템플릿을 반환합니다."""
    template_path = Path(__file__).parent.parent / "templates" / "modules" / "module.md.template"
    if template_path.exists():
        return template_path.read_text(encoding="utf-8")

    # 기본 템플릿
    return """# {{MODULE_NAME}}

**생성일**: {{CREATION_DATE}}
**경로**: `{{MODULE_PATH}}`
**타입**: {{MODULE_TYPE}}

---

## 개요

{{MODULE_NAME}} 모듈에 대한 설명을 여기에 작성하세요.

---

## 기술 스택

{{TECH_STACK}}

---

## 주요 컴포넌트

{{COMPONENTS_SECTION}}

---

## API 엔드포인트

{{ENDPOINTS_SECTION}}

---

## 설정

이 모듈의 설정에 대한 설명을 여기에 작성하세요.

---

## 개발 가이드

### 빌드

```bash
# 빌드 명령어
```

### 실행

```bash
# 실행 명령어
```

### 테스트

```bash
# 테스트 명령어
```

---

**최종 업데이트**: {{CREATION_DATE}}
"""


def generate_components_section(analysis: Dict) -> str:
    """컴포넌트 섹션을 생성합니다."""
    sections = []

    if analysis["entities"]:
        sections.append(f"### Entities ({len(analysis['entities'])}개)")
        for e in analysis["entities"]:
            sections.append(f"- `{e['name']}`")

    if analysis["controllers"]:
        sections.append(f"\n### Controllers ({len(analysis['controllers'])}개)")
        for c in analysis["controllers"]:
            sections.append(f"- `{c['name']}`")

    if analysis["services"]:
        sections.append(f"\n### Services ({len(analysis['services'])}개)")
        for s in analysis["services"]:
            sections.append(f"- `{s['name']}`")

    if analysis["pages"]:
        sections.append(f"\n### Pages ({len(analysis['pages'])}개)")
        for p in analysis["pages"]:
            sections.append(f"- `{p['name']}`")

    if analysis["components"]:
        sections.append(f"\n### Components ({len(analysis['components'])}개)")
        for c in analysis["components"][:15]:  # 최대 15개
            sections.append(f"- `{c['name']}`")
        if len(analysis["components"]) > 15:
            sections.append(f"- ... 외 {len(analysis['components']) - 15}개")

    if analysis["routes"]:
        sections.append(f"\n### Routes ({len(analysis['routes'])}개)")
        for r in analysis["routes"]:
            sections.append(f"- `{r['name']}`")

    return "\n".join(sections) if sections else "컴포넌트가 감지되지 않았습니다."


def generate_endpoints_section(analysis: Dict) -> str:
    """엔드포인트 섹션을 생성합니다."""
    if not analysis["endpoints"]:
        return "API 엔드포인트가 감지되지 않았습니다."

    lines = ["| Method | Path |", "|--------|------|"]
    for ep in analysis["endpoints"][:20]:  # 최대 20개
        lines.append(f"| {ep['method']} | `{ep['path']}` |")

    if len(analysis["endpoints"]) > 20:
        lines.append(f"\n... 외 {len(analysis['endpoints']) - 20}개의 엔드포인트")

    return "\n".join(lines)


def init_modules() -> None:
    """모듈 문서를 초기화합니다."""
    root = get_project_root()
    modules_dir = get_modules_docs_dir()
    cache_dir = get_cache_dir()

    print("\n🔍 모듈 감지 중...")
    modules = detect_modules(root)

    if not modules:
        return

    print(f"\n📦 감지된 모듈: {len(modules)}개")
    for m in modules:
        print(f"   • {m['name']} ({m['type']}) - {m['path']}")

    # 디렉토리 생성
    modules_dir.mkdir(parents=True, exist_ok=True)
    cache_dir.mkdir(parents=True, exist_ok=True)

    print("\n📝 모듈 문서 생성 중...")

    for module in modules:
        module_path = Path(module["absolute_path"])
        analysis = analyze_module(module_path, module["type"])

        doc_content = generate_module_doc(module, analysis)
        doc_path = modules_dir / f"{module['name']}.md"
        doc_path.write_text(doc_content, encoding="utf-8")

        print(f"   ✅ {doc_path.name}")

    # 캐시에 모듈 정보 저장
    cache_file = cache_dir / "modules_info.json"
    cache_data = {
        "last_updated": datetime.now().isoformat(),
        "modules": modules,
    }
    cache_file.write_text(json.dumps(cache_data, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"\n✅ 모듈 문서 초기화 완료!")
    print(f"   위치: {modules_dir.relative_to(root)}/")


def refresh_modules(module_name: Optional[str] = None) -> None:
    """모듈 문서를 갱신합니다."""
    root = get_project_root()
    modules_dir = get_modules_docs_dir()
    cache_dir = get_cache_dir()

    # 캐시에서 모듈 정보 로드
    cache_file = cache_dir / "modules_info.json"
    if not cache_file.exists():
        print("⚠️  모듈 정보가 없습니다. 먼저 `modules init`을 실행하세요.")
        return

    cache_data = json.loads(cache_file.read_text(encoding="utf-8"))
    modules = cache_data.get("modules", [])

    if module_name:
        modules = [m for m in modules if m["name"] == module_name]
        if not modules:
            print(f"⚠️  모듈 '{module_name}'을(를) 찾을 수 없습니다.")
            return

    print(f"\n🔄 모듈 문서 갱신 중...")

    for module in modules:
        module_path = Path(module["absolute_path"])
        if not module_path.exists():
            print(f"   ⚠️  {module['name']} - 경로가 존재하지 않음")
            continue

        analysis = analyze_module(module_path, module["type"])
        doc_content = generate_module_doc(module, analysis)
        doc_path = modules_dir / f"{module['name']}.md"
        doc_path.write_text(doc_content, encoding="utf-8")

        print(f"   ✅ {doc_path.name}")

    # 캐시 업데이트
    cache_data["last_updated"] = datetime.now().isoformat()
    cache_file.write_text(json.dumps(cache_data, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"\n✅ 모듈 문서 갱신 완료!")


def check_status() -> None:
    """모듈 문서 상태를 확인합니다."""
    root = get_project_root()
    modules_dir = get_modules_docs_dir()
    cache_dir = get_cache_dir()

    print("\n📊 모듈 문서 상태\n")

    # 캐시 확인
    cache_file = cache_dir / "modules_info.json"
    if not cache_file.exists():
        print("⚠️  모듈 문서가 초기화되지 않았습니다.")
        print("   `/dev doc modules init` 명령어로 초기화하세요.")
        return

    cache_data = json.loads(cache_file.read_text(encoding="utf-8"))
    modules = cache_data.get("modules", [])
    last_updated = cache_data.get("last_updated", "알 수 없음")

    print(f"📅 마지막 업데이트: {last_updated}")
    print(f"📦 등록된 모듈: {len(modules)}개\n")

    # 현재 모듈 감지
    current_modules = detect_modules(root)
    current_names = {m["name"] for m in current_modules}
    cached_names = {m["name"] for m in modules}

    # 새로운 모듈
    new_modules = current_names - cached_names
    if new_modules:
        print("🆕 새로운 모듈 (문서 없음):")
        for name in new_modules:
            print(f"   • {name}")

    # 삭제된 모듈
    removed_modules = cached_names - current_names
    if removed_modules:
        print("🗑️  삭제된 모듈 (문서 있음):")
        for name in removed_modules:
            print(f"   • {name}")

    # 문서 상태
    print("\n📄 모듈별 문서 상태:")
    for module in modules:
        doc_path = modules_dir / f"{module['name']}.md"
        status = "✅" if doc_path.exists() else "❌"
        print(f"   {status} {module['name']}.md")

    if new_modules:
        print(f"\n💡 새 모듈을 추가하려면 `/dev doc modules refresh`를 실행하세요.")


def main():
    """메인 함수"""
    parser = argparse.ArgumentParser(description="모듈 문서화 관리")
    parser.add_argument("--action", type=str, default="init",
                        choices=["init", "refresh", "status"],
                        help="수행할 작업 (init/refresh/status)")
    parser.add_argument("--module", type=str, default=None,
                        help="특정 모듈명 (비어있으면 전체)")

    args = parser.parse_args()

    if args.action == "init":
        init_modules()
    elif args.action == "refresh":
        refresh_modules(args.module)
    elif args.action == "status":
        check_status()


if __name__ == "__main__":
    main()
