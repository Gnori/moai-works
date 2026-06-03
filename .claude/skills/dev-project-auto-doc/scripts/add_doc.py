#!/usr/bin/env python3
"""
개별 문서 추가 스크립트

현재 규모에 포함되지 않은 문서를 개별적으로 추가합니다.

사용법:
    python add_doc.py <doc_name>
"""

import argparse
import sys
from datetime import datetime
from pathlib import Path

# 공통 유틸리티 임포트
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "common"))
from claude_md_utils import get_project_root

# init 모듈에서 상수 임포트
sys.path.insert(0, str(Path(__file__).parent))
from init import SCALE_DOCUMENTS, get_doc_description


# 추가 가능한 문서 정의 (full 규모에 포함된 모든 문서)
ALL_DOCUMENTS = {
    "readme": {
        "filename": "README.md",
        "title": "프로젝트 개요",
        "description": "프로젝트 개요, 구조, 실행 방법",
    },
    "architecture": {
        "filename": "architecture.md",
        "title": "시스템 아키텍처",
        "description": "시스템 구조, ER 다이어그램",
    },
    "core_concepts": {
        "filename": "core_concepts.md",
        "title": "핵심 개념",
        "description": "핵심 개념 및 용어 설명",
    },
    "api": {
        "filename": "api.md",
        "title": "API 문서",
        "description": "API 엔드포인트 문서",
    },
    "configuration": {
        "filename": "configuration.md",
        "title": "설정 가이드",
        "description": "설정 및 환경 변수 가이드",
    },
    "development": {
        "filename": "development.md",
        "title": "개발 가이드",
        "description": "개발 가이드, 코딩 컨벤션",
    },
    "integrations": {
        "filename": "integrations.md",
        "title": "통합 가이드",
        "description": "외부 시스템 연동 가이드",
    },
    "testing": {
        "filename": "testing.md",
        "title": "테스트 전략",
        "description": "테스트 전략 및 가이드",
    },
    "security": {
        "filename": "security.md",
        "title": "보안 가이드",
        "description": "보안 요구사항 및 가이드",
    },
    "deployment": {
        "filename": "deployment.md",
        "title": "배포 가이드",
        "description": "배포 프로세스 가이드",
    },
    "monitoring": {
        "filename": "monitoring.md",
        "title": "모니터링",
        "description": "모니터링 및 알림 설정",
    },
    "troubleshooting": {
        "filename": "troubleshooting.md",
        "title": "문제 해결",
        "description": "문제 해결 가이드",
    },
}


def get_template_path(doc_name: str) -> Path:
    """템플릿 파일 경로를 반환합니다."""
    skill_dir = Path(__file__).parent.parent
    template_dir = skill_dir / "templates" / "project_docs"
    return template_dir / f"{doc_name}.template"


def load_template(doc_name: str) -> str:
    """템플릿을 로드하고 변수를 치환합니다."""
    template_path = get_template_path(doc_name)
    project_root = get_project_root()

    variables = {
        "PROJECT_NAME": project_root.name,
        "CREATION_DATE": datetime.now().strftime("%Y-%m-%d"),
        "TIMESTAMP": datetime.now().strftime("%Y-%m-%d"),
    }

    if template_path.exists():
        content = template_path.read_text(encoding="utf-8")
        for key, value in variables.items():
            content = content.replace(f"{{{{{key}}}}}", str(value))
        return content

    return None


def generate_default_doc(doc_info: dict) -> str:
    """기본 문서 템플릿을 생성합니다."""
    title = doc_info["title"]
    description = doc_info["description"]

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


def add_document(doc_name: str) -> bool:
    """문서를 추가합니다."""
    doc_name_lower = doc_name.lower().replace(".md", "").replace("_", "-")

    # 별칭 처리
    alias_map = {
        "readme": "readme",
        "arch": "architecture",
        "core-concepts": "core_concepts",
        "concepts": "core_concepts",
        "config": "configuration",
        "dev": "development",
        "integration": "integrations",
        "test": "testing",
        "sec": "security",
        "deploy": "deployment",
        "monitor": "monitoring",
        "trouble": "troubleshooting",
    }

    if doc_name_lower in alias_map:
        doc_name_lower = alias_map[doc_name_lower]

    # core_concepts 특별 처리
    if doc_name_lower == "core-concepts":
        doc_name_lower = "core_concepts"

    if doc_name_lower not in ALL_DOCUMENTS:
        print(f"❌ 알 수 없는 문서: {doc_name}")
        print("\n📋 추가 가능한 문서:")
        for name, info in ALL_DOCUMENTS.items():
            print(f"  • {name}: {info['description']}")
        return False

    project_root = get_project_root()
    docs_dir = project_root / ".claude" / "project_docs"

    if not docs_dir.exists():
        print("❌ .claude/project_docs/ 디렉토리가 없습니다.")
        print("💡 먼저 문서를 초기화하세요: /dev-project-auto-doc init")
        return False

    doc_info = ALL_DOCUMENTS[doc_name_lower]
    target_file = docs_dir / doc_info["filename"]

    if target_file.exists():
        print(f"⚠️  {doc_info['filename']} 이미 존재합니다.")
        confirm = input("덮어쓰시겠습니까? (y/N): ").strip().lower()
        if confirm != 'y':
            print("❌ 취소되었습니다.")
            return False

    # 문서 생성
    print(f"\n📄 {doc_info['title']} 생성 중...")

    # 템플릿 로드 시도
    content = load_template(doc_info["filename"])
    if not content:
        content = generate_default_doc(doc_info)

    target_file.write_text(content, encoding="utf-8")

    print(f"✅ {doc_info['filename']} 생성 완료!")
    print(f"\n📝 다음 단계:")
    print(f"   문서를 열어 프로젝트에 맞게 내용을 작성하세요:")
    print(f"   .claude/project_docs/{doc_info['filename']}")

    return True


def list_available_docs():
    """추가 가능한 문서 목록을 표시합니다."""
    project_root = get_project_root()
    docs_dir = project_root / ".claude" / "project_docs"

    print("\n📋 추가 가능한 문서:\n")

    existing = []
    missing = []

    for name, info in ALL_DOCUMENTS.items():
        if docs_dir.exists() and (docs_dir / info["filename"]).exists():
            existing.append((name, info))
        else:
            missing.append((name, info))

    if existing:
        print("이미 존재하는 문서:")
        for name, info in existing:
            print(f"  ✅ {name}: {info['description']}")
        print()

    if missing:
        print("추가 가능한 문서:")
        for name, info in missing:
            print(f"  ➕ {name}: {info['description']}")
        print()
        print("💡 사용법: /dev-project-auto-doc add-doc <문서명>")
        print("   예: /dev-project-auto-doc add-doc testing")


def main():
    """메인 함수"""
    parser = argparse.ArgumentParser(
        description="특정 문서를 추가합니다."
    )
    parser.add_argument(
        "doc_name",
        nargs="?",
        default=None,
        help="추가할 문서명 (testing, security, deployment 등)"
    )

    args = parser.parse_args()

    if args.doc_name is None:
        list_available_docs()
        return

    # 문서 추가
    success = add_document(args.doc_name)

    if not success:
        sys.exit(1)


if __name__ == "__main__":
    main()
