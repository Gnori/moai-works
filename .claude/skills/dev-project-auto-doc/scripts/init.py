#!/usr/bin/env python3
"""
프로젝트 문서 초기화 스크립트

코드 분석을 기반으로 프로젝트 문서를 생성합니다.
스케일(minimal/standard/full)에 따라 문서 세트가 달라집니다.

사용법:
    python init.py [--scale=<scale>] [--force]
"""

import argparse
import json
import sys
from datetime import datetime
from pathlib import Path

# 공통 유틸리티 임포트
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "common"))
from claude_md_utils import ensure_claude_md_with_section, get_project_root

# analyze 모듈 임포트
sys.path.insert(0, str(Path(__file__).parent))
from analyze import analyze_project


# 규모별 문서 정의
SCALE_DOCUMENTS = {
    "minimal": [
        "README.md",
        "architecture.md",
        "development.md",
    ],
    "standard": [
        "README.md",
        "architecture.md",
        "core_concepts.md",
        "api.md",
        "configuration.md",
        "development.md",
    ],
    "full": [
        "README.md",
        "architecture.md",
        "core_concepts.md",
        "api.md",
        "configuration.md",
        "development.md",
        "integrations.md",
        "testing.md",
        "security.md",
        "deployment.md",
        "monitoring.md",
        "troubleshooting.md",
    ],
}

SCALE_NAMES = {
    "minimal": "최소 구성 (Minimal)",
    "standard": "기본 구성 (Standard)",
    "full": "완전 구성 (Full)",
}

SCALE_DESCRIPTIONS = {
    "minimal": {
        "description": "개인 프로젝트, 프로토타입용",
        "doc_count": 3,
    },
    "standard": {
        "description": "팀 프로젝트, 중규모 서비스용",
        "doc_count": 6,
    },
    "full": {
        "description": "엔터프라이즈, 대규모 프로덕션용",
        "doc_count": 12,
    },
}


def get_scale_choice() -> str:
    """사용자에게 프로젝트 규모를 질문합니다."""
    print("\n📊 프로젝트 문서 규모를 선택해주세요:\n")
    print("1. 최소 구성 (Minimal)")
    print("   - 개인 프로젝트, 프로토타입")
    print("   - 문서 3개: README, architecture, development")
    print()
    print("2. 기본 구성 (Standard) ⭐ 권장")
    print("   - 팀 프로젝트, 중규모 서비스")
    print("   - 문서 6개: + core_concepts, api, configuration")
    print()
    print("3. 완전 구성 (Full)")
    print("   - 엔터프라이즈, 대규모 프로덕션")
    print("   - 문서 12개: + integrations, testing, security, deployment, monitoring, troubleshooting")
    print()

    while True:
        choice = input("선택 (1-3, 기본값 2): ").strip()
        if choice == "" or choice == "2":
            return "standard"
        elif choice == "1":
            return "minimal"
        elif choice == "3":
            return "full"
        else:
            print("❌ 잘못된 선택입니다. 1, 2, 3 중 하나를 선택하세요.")


def load_claude_md_section_template(scale: str) -> str:
    """CLAUDE.md 섹션 템플릿을 로드합니다."""
    skill_dir = Path(__file__).parent.parent
    template_path = skill_dir / "templates" / "claude_md_section.md.template"

    if not template_path.exists():
        return None

    return template_path.read_text(encoding="utf-8")


def get_project_docs_section(scale: str) -> str:
    """CLAUDE.md에 추가할 프로젝트 문서 섹션을 생성합니다."""
    docs_list = SCALE_DOCUMENTS[scale]

    # 문서 목록 문자열 생성
    doc_list_str = ""
    for doc in docs_list:
        desc = get_doc_description(doc)
        doc_list_str += f"├── {doc:<22} # {desc}\n"

    # 템플릿 로드 시도
    template_content = load_claude_md_section_template(scale)

    if template_content:
        # 템플릿 변수 치환
        section = template_content.replace("{{SCALE_NAME}}", SCALE_NAMES[scale])
        section = section.replace("{{DOC_COUNT}}", str(len(docs_list)))
        section = section.replace("{{DOC_LIST}}", doc_list_str.rstrip())
        return section

    # 템플릿이 없으면 기본 내용 생성 (fallback)
    section = f"""## 프로젝트 문서

프로젝트 문서는 코드 분석을 기반으로 자동 생성되며, `.claude/project_docs/`에 위치합니다.

**현재 규모**: {SCALE_NAMES[scale]} ({len(docs_list)}개 문서)

### 문서 구조

```
.claude/project_docs/
{doc_list_str}```

### 문서 갱신 명령어

```bash
/dev doc refresh                       # 전체 문서 갱신
/dev doc refresh api                   # api.md만 갱신
/dev doc refresh architecture          # architecture.md만 갱신
/dev doc status                        # 동기화 상태 확인
/dev doc add testing                   # 개별 문서 추가
/dev doc upgrade full                  # 규모 업그레이드
```

"""
    return section


def get_doc_description(doc_name: str) -> str:
    """문서의 설명을 반환합니다."""
    descriptions = {
        "README.md": "프로젝트 개요, 구조, 실행 방법",
        "architecture.md": "시스템 아키텍처, ER 다이어그램",
        "development.md": "개발 가이드, 코딩 컨벤션",
        "core_concepts.md": "핵심 개념 및 용어 설명",
        "api.md": "API 엔드포인트 문서",
        "configuration.md": "설정 및 환경 변수 가이드",
        "integrations.md": "외부 시스템 연동 가이드",
        "testing.md": "테스트 전략 및 가이드",
        "security.md": "보안 요구사항 및 가이드",
        "deployment.md": "배포 프로세스 가이드",
        "monitoring.md": "모니터링 및 알림 설정",
        "troubleshooting.md": "문제 해결 가이드",
    }
    return descriptions.get(doc_name, "문서")


def check_existing_setup() -> dict:
    """기존 설정을 확인합니다."""
    project_root = get_project_root()
    docs_dir = project_root / ".claude" / "project_docs"

    result = {
        "docs_dir_exists": docs_dir.exists(),
        "existing_docs": [],
        "current_scale": None,
    }

    if docs_dir.exists():
        for doc in SCALE_DOCUMENTS["full"]:
            if (docs_dir / doc).exists():
                result["existing_docs"].append(doc)

        # 현재 규모 추정
        doc_count = len(result["existing_docs"])
        if doc_count >= len(SCALE_DOCUMENTS["full"]):
            result["current_scale"] = "full"
        elif doc_count >= len(SCALE_DOCUMENTS["standard"]):
            result["current_scale"] = "standard"
        elif doc_count >= len(SCALE_DOCUMENTS["minimal"]):
            result["current_scale"] = "minimal"

    return result


def get_template_path(template_name: str) -> Path:
    """템플릿 파일 경로를 반환합니다."""
    skill_dir = Path(__file__).parent.parent
    template_dir = skill_dir / "templates" / "project_docs"
    return template_dir / f"{template_name}.template"


def load_template(doc_name: str, variables: dict) -> str:
    """템플릿을 로드하고 변수를 치환합니다."""
    template_path = get_template_path(doc_name)

    if template_path.exists():
        content = template_path.read_text(encoding="utf-8")
        for key, value in variables.items():
            content = content.replace(f"{{{{{key}}}}}", str(value))
        return content

    # 템플릿이 없으면 기본 생성 함수 사용
    return None


def generate_readme(analysis: dict) -> str:
    """README.md 내용을 생성합니다."""
    project_name = analysis["project_name"]
    project_types = analysis["project_types"]

    # 템플릿 로드 시도
    variables = {
        "PROJECT_NAME": project_name,
        "CREATION_DATE": datetime.now().strftime("%Y-%m-%d"),
    }
    template_content = load_template("README.md", variables)
    if template_content:
        return template_content

    # 템플릿이 없으면 코드 분석 기반 생성
    content = f"""# {project_name}

## 개요

[프로젝트 설명을 여기에 작성하세요]

## 기술 스택

"""

    if "java_spring" in project_types:
        content += """### Backend
- Java
- Spring Boot
- JPA / Hibernate

"""

    if "react" in project_types:
        content += """### Frontend
- React
- TypeScript

"""

    content += """## 프로젝트 구조

```
"""
    content += f"{project_name}/\n"

    # Java 구조
    java = analysis.get("java_spring", {})
    if java.get("entities") or java.get("controllers"):
        content += "├── [backend]/\n"
        content += "│   └── src/main/java/\n"
        if java.get("entities"):
            content += f"│       ├── entity/          # 엔티티 ({len(java['entities'])}개)\n"
        if java.get("controllers"):
            content += f"│       ├── controller/      # 컨트롤러 ({len(java['controllers'])}개)\n"
        if java.get("services"):
            content += f"│       ├── service/         # 서비스 ({len(java['services'])}개)\n"
        if java.get("repositories"):
            content += f"│       └── repository/      # 리포지토리 ({len(java['repositories'])}개)\n"

    # React 구조
    react = analysis.get("react", {})
    if react.get("pages") or react.get("components"):
        content += "├── [frontend]/\n"
        content += "│   └── src/\n"
        if react.get("pages"):
            content += f"│       ├── pages/           # 페이지 ({len(react['pages'])}개)\n"
        if react.get("components"):
            content += f"│       ├── components/      # 컴포넌트 ({len(react['components'])}개)\n"
        if react.get("contexts"):
            content += f"│       └── contexts/        # 컨텍스트 ({len(react['contexts'])}개)\n"

    content += """└── .claude/
    ├── project_docs/    # 프로젝트 문서
    └── requirements/    # 요구사항 관리
```

## 실행 방법

### 환경 설정

```bash
# [환경 설정 명령어]
```

### 실행

```bash
# [실행 명령어]
```

## 관련 문서

- [아키텍처](./architecture.md)
- [API 문서](./api.md)
- [개발 가이드](./development.md)
"""

    return content


def generate_architecture(analysis: dict) -> str:
    """architecture.md 내용을 생성합니다."""
    variables = {
        "PROJECT_NAME": analysis["project_name"],
        "CREATION_DATE": datetime.now().strftime("%Y-%m-%d"),
    }
    template_content = load_template("architecture.md", variables)
    if template_content:
        return template_content

    content = """# 시스템 아키텍처

## 개요

[시스템 아키텍처 개요를 작성하세요]

## 시스템 구조

```
┌─────────────┐     ┌─────────────┐
│   Client    │────▶│   Backend   │
│  (React)    │◀────│  (Spring)   │
└─────────────┘     └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │  Database   │
                    └─────────────┘
```

"""

    # Entity 기반 ER 다이어그램
    java = analysis.get("java_spring", {})
    if java.get("entities"):
        content += """## 데이터 모델 (Entity)

### 엔티티 목록

| 엔티티 | 설명 | 파일 |
|--------|------|------|
"""
        for e in java["entities"]:
            content += f"| {e['name']} | [설명] | `{e['file']}` |\n"

        content += """
### ER 다이어그램

```
"""
        for e in java["entities"]:
            content += f"[{e['name']}]\n"
        content += """
[엔티티 간 관계를 여기에 작성하세요]
```

"""

    content += """## 주요 컴포넌트

### Backend

[백엔드 컴포넌트 설명]

### Frontend

[프론트엔드 컴포넌트 설명]

## 디자인 패턴

[사용된 디자인 패턴 설명]
"""

    return content


def generate_api(analysis: dict) -> str:
    """api.md 내용을 생성합니다."""
    variables = {
        "PROJECT_NAME": analysis["project_name"],
        "CREATION_DATE": datetime.now().strftime("%Y-%m-%d"),
    }
    template_content = load_template("api.md", variables)
    if template_content:
        return template_content

    content = """# API 문서

## 개요

이 문서는 프로젝트의 REST API 엔드포인트를 설명합니다.

## Base URL

```
http://localhost:8080/api
```

## 인증

[인증 방식 설명]

"""

    java = analysis.get("java_spring", {})
    if java.get("controllers"):
        content += "## 엔드포인트\n\n"

        for controller in java["controllers"]:
            name = controller["name"].replace("Controller", "")
            content += f"### {name}\n\n"

            endpoints = controller.get("endpoints", [])
            if endpoints:
                content += "| Method | Endpoint | Description |\n"
                content += "|--------|----------|-------------|\n"
                for ep in endpoints:
                    content += f"| `{ep['method']}` | `{ep['path']}` | [설명] |\n"
            else:
                content += f"*Controller: `{controller['file']}`*\n"
                content += "\n[엔드포인트 정보를 추가하세요]\n"

            content += "\n"

    content += """## 에러 응답

| 코드 | 설명 |
|------|------|
| 400 | Bad Request |
| 401 | Unauthorized |
| 403 | Forbidden |
| 404 | Not Found |
| 500 | Internal Server Error |

## 예시

### 요청 예시

```bash
curl -X GET http://localhost:8080/api/example \\
  -H "Authorization: Bearer {token}"
```

### 응답 예시

```json
{
  "status": "success",
  "data": {}
}
```
"""

    return content


def generate_development(analysis: dict) -> str:
    """development.md 내용을 생성합니다."""
    project_types = analysis["project_types"]

    variables = {
        "PROJECT_NAME": analysis["project_name"],
        "CREATION_DATE": datetime.now().strftime("%Y-%m-%d"),
    }
    template_content = load_template("development.md", variables)
    if template_content:
        return template_content

    content = """# 개발 가이드

## 개발 환경 설정

### 필수 요구사항

"""

    if "java_spring" in project_types:
        content += """- Java 17+
- Maven 또는 Gradle
"""

    if "react" in project_types:
        content += """- Node.js 18+
- npm 또는 yarn
"""

    content += """
### 설치

```bash
# 저장소 클론
git clone [repository-url]
cd [project-name]
"""

    if "java_spring" in project_types:
        content += """
# Backend 의존성 설치
cd [backend-dir]
./mvnw install  # 또는 ./gradlew build
"""

    if "react" in project_types:
        content += """
# Frontend 의존성 설치
cd [frontend-dir]
npm install
"""

    content += """```

## 실행

"""

    if "java_spring" in project_types:
        content += """### Backend

```bash
./mvnw spring-boot:run
# 또는
./gradlew bootRun
```

"""

    if "react" in project_types:
        content += """### Frontend

```bash
npm start
# 또는
npm run dev
```

"""

    content += """## 테스트

```bash
# 단위 테스트
[테스트 명령어]

# 통합 테스트
[테스트 명령어]
```

## 빌드

```bash
# Production 빌드
[빌드 명령어]
```

## 코딩 컨벤션

### 네이밍 규칙

- 클래스: PascalCase
- 함수/메서드: camelCase
- 상수: UPPER_SNAKE_CASE
- 파일: kebab-case 또는 PascalCase

### Git 커밋 메시지

```
[type]: [subject]

[body]

[footer]
```

**Type**:
- feat: 새로운 기능
- fix: 버그 수정
- docs: 문서 변경
- refactor: 리팩토링
- test: 테스트 추가
- chore: 기타 변경

## 프로젝트 구조

"""

    # React 컴포넌트 목록
    react = analysis.get("react", {})
    if react.get("pages"):
        content += "### Pages\n\n"
        content += "| 페이지 | 파일 | 설명 |\n"
        content += "|--------|------|------|\n"
        for p in react["pages"]:
            content += f"| {p['name']} | `{p['file']}` | [설명] |\n"
        content += "\n"

    if react.get("contexts"):
        content += "### Contexts\n\n"
        content += "| Context | 파일 | 용도 |\n"
        content += "|---------|------|------|\n"
        for c in react["contexts"]:
            content += f"| {c['name']} | `{c['file']}` | [용도] |\n"
        content += "\n"

    return content


def generate_simple_doc(doc_name: str, analysis: dict) -> str:
    """간단한 문서 템플릿을 생성합니다."""
    variables = {
        "PROJECT_NAME": analysis["project_name"],
        "CREATION_DATE": datetime.now().strftime("%Y-%m-%d"),
    }

    # 템플릿 로드 시도
    template_content = load_template(doc_name, variables)
    if template_content:
        return template_content

    # 기본 템플릿 생성
    title = doc_name.replace(".md", "").replace("_", " ").title()
    description = get_doc_description(doc_name)

    return f"""# {title}

**생성일**: {datetime.now().strftime('%Y-%m-%d')}

{description}

---

## 개요

[이 섹션에 대한 개요를 작성하세요]

---

## 목표

[목표를 작성하세요]

---

## 상세 내용

### 섹션 1

[내용을 작성하세요]

### 섹션 2

[내용을 작성하세요]

---

## 도구 및 리소스

- [도구 1]
- [도구 2]

---

## 참고 자료

- [참고 자료 1]
- [참고 자료 2]
"""


def create_project_docs(analysis: dict, scale: str, force: bool = False) -> bool:
    """프로젝트 문서를 생성합니다."""
    project_root = get_project_root()
    docs_dir = project_root / ".claude" / "project_docs"

    # 기존 설정 확인
    existing = check_existing_setup()

    if existing["docs_dir_exists"] and not force:
        print("⚠️  .claude/project_docs/ 디렉토리가 이미 존재합니다.")
        print("💡 덮어쓰려면 --force 옵션을 사용하세요.")
        print("💡 또는 upgrade 명령으로 규모를 업그레이드하세요.")
        return False

    print(f"\n📁 프로젝트 문서 생성 중... ({SCALE_NAMES[scale]})\n")

    # 디렉토리 생성
    docs_dir.mkdir(parents=True, exist_ok=True)

    # 문서 생성기 매핑
    doc_generators = {
        "README.md": generate_readme,
        "architecture.md": generate_architecture,
        "api.md": generate_api,
        "development.md": generate_development,
    }

    # 규모에 따른 문서 생성
    for doc_name in SCALE_DOCUMENTS[scale]:
        target_file = docs_dir / doc_name

        if target_file.exists() and not force:
            print(f"   ⚠️  {doc_name} (이미 존재, 건너뜀)")
            continue

        # 전용 생성기가 있으면 사용, 없으면 간단 템플릿
        if doc_name in doc_generators:
            content = doc_generators[doc_name](analysis)
        else:
            content = generate_simple_doc(doc_name, analysis)

        target_file.write_text(content, encoding="utf-8")
        print(f"   ✅ {doc_name}")

    # 규모 정보 저장
    save_scale_info(scale)

    return True


def save_scale_info(scale: str) -> None:
    """현재 규모 정보를 저장합니다."""
    project_root = get_project_root()
    cache_dir = project_root / ".claude" / ".cache"
    cache_dir.mkdir(parents=True, exist_ok=True)

    scale_info = {
        "scale": scale,
        "documents": SCALE_DOCUMENTS[scale],
        "updated_at": datetime.now().isoformat(),
    }

    cache_file = cache_dir / "project_docs_scale.json"
    cache_file.write_text(json.dumps(scale_info, ensure_ascii=False, indent=2))


def get_current_scale() -> str:
    """현재 규모를 반환합니다."""
    project_root = get_project_root()
    cache_file = project_root / ".claude" / ".cache" / "project_docs_scale.json"

    if cache_file.exists():
        try:
            data = json.loads(cache_file.read_text())
            return data.get("scale", "minimal")
        except:
            pass

    return None


def update_claude_md(scale: str) -> str:
    """CLAUDE.md에 프로젝트 문서 섹션을 추가/업데이트합니다."""
    print("\n📄 CLAUDE.md 업데이트 중...")

    section_content = get_project_docs_section(scale)
    backup_path = ensure_claude_md_with_section(
        "프로젝트 문서",
        section_content
    )

    if backup_path:
        print(f"   📦 백업 완료: {backup_path}")

    print("   ✅ 프로젝트 문서 섹션 추가/업데이트 완료")

    return backup_path


def init_project_docs(scale: str = "interactive", force: bool = False) -> bool:
    """프로젝트 문서를 초기화합니다."""
    print("\n🚀 프로젝트 자동 문서화 초기화\n")

    # 1. 코드 분석
    print("🔍 프로젝트 코드 분석 중...")
    analysis = analyze_project()

    # 분석 결과 요약
    java = analysis.get("java_spring", {})
    react = analysis.get("react", {})

    print(f"   📁 프로젝트: {analysis['project_name']}")
    print(f"   🔧 타입: {', '.join(analysis['project_types'])}")

    if java:
        print(f"   ☕ Java: Entity {len(java.get('entities', []))}개, Controller {len(java.get('controllers', []))}개")
    if react:
        print(f"   ⚛️  React: Page {len(react.get('pages', []))}개, Component {len(react.get('components', []))}개")

    # 2. 규모 선택
    if scale == "interactive":
        scale = get_scale_choice()

    if scale not in SCALE_DOCUMENTS:
        print(f"❌ 잘못된 규모: {scale}")
        print("   minimal, standard, full 중 하나를 선택하세요.")
        return False

    # 3. 확인
    print(f"\n📋 {SCALE_NAMES[scale]}으로 초기화합니다.")
    print(f"   생성할 문서 수: {len(SCALE_DOCUMENTS[scale])}개\n")

    for doc in SCALE_DOCUMENTS[scale]:
        desc = get_doc_description(doc)
        print(f"   • {doc}: {desc}")

    print()
    confirm = input("진행하시겠습니까? (Y/n): ").strip().lower()
    if confirm and confirm != 'y':
        print("❌ 취소되었습니다.")
        return False

    # 4. 문서 생성
    if not create_project_docs(analysis, scale, force):
        return False

    # 5. CLAUDE.md 업데이트
    update_claude_md(scale)

    # 완료 메시지
    print("\n✅ 프로젝트 문서 초기화 완료!")
    print(f"\n📋 생성된 규모: {SCALE_NAMES[scale]}")
    print(f"📄 생성된 문서 수: {len(SCALE_DOCUMENTS[scale])}개")

    print("\n📝 생성된 문서:")
    for doc in SCALE_DOCUMENTS[scale]:
        print(f"   • .claude/project_docs/{doc}")

    print("\n💡 다음 단계:")
    print("   1. 생성된 문서를 검토하고 [설명] 부분을 채워주세요")
    print("   2. 코드 변경 후: /dev-project-auto-doc refresh")
    if scale != "full":
        print(f"   3. 더 많은 문서가 필요하면: /dev-project-auto-doc upgrade {['standard', 'full'][['minimal', 'standard'].index(scale)] if scale != 'full' else 'full'}")

    return True


def main():
    """메인 함수"""
    parser = argparse.ArgumentParser(
        description="프로젝트 문서를 초기화합니다."
    )
    parser.add_argument(
        "--scale",
        type=str,
        default="interactive",
        choices=["minimal", "standard", "full", "interactive"],
        help="프로젝트 문서 규모 (기본값: interactive)"
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="기존 파일 덮어쓰기"
    )

    args = parser.parse_args()

    success = init_project_docs(args.scale, args.force)

    if not success:
        sys.exit(1)


if __name__ == "__main__":
    main()
