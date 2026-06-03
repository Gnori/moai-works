#!/usr/bin/env python3
"""
문서 갱신 스크립트

코드 변경사항을 반영하여 문서를 갱신합니다.

사용법:
    python refresh.py [doc]
"""

import argparse
import sys
from pathlib import Path

# analyze, init 모듈 임포트
sys.path.insert(0, str(Path(__file__).parent))
from analyze import analyze_project
from init import generate_readme, generate_architecture, generate_api, generate_development


def get_project_root() -> Path:
    """프로젝트 루트 디렉토리를 반환합니다."""
    return Path.cwd()


def refresh_doc(doc_name: str, analysis: dict) -> bool:
    """특정 문서를 갱신합니다."""
    project_root = get_project_root()
    docs_dir = project_root / ".claude" / "project_docs"

    if not docs_dir.exists():
        print("❌ .claude/project_docs/ 디렉토리가 없습니다.")
        print("💡 먼저 /dev-project-auto-doc init 을 실행하세요.")
        return False

    doc_generators = {
        "readme": ("README.md", generate_readme),
        "architecture": ("architecture.md", generate_architecture),
        "api": ("api.md", generate_api),
        "development": ("development.md", generate_development),
    }

    doc_name_lower = doc_name.lower()

    if doc_name_lower not in doc_generators:
        print(f"❌ 알 수 없는 문서: {doc_name}")
        print(f"💡 사용 가능한 문서: {', '.join(doc_generators.keys())}")
        return False

    filename, generator = doc_generators[doc_name_lower]
    filepath = docs_dir / filename

    # 기존 파일 백업 (있으면)
    if filepath.exists():
        backup_dir = project_root / ".claude" / ".backup"
        backup_dir.mkdir(parents=True, exist_ok=True)
        from datetime import datetime
        timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        backup_path = backup_dir / f"{filename}.{timestamp}.bak"
        import shutil
        shutil.copy(filepath, backup_path)
        print(f"📦 백업: {backup_path.relative_to(project_root)}")

    # 새 내용 생성
    content = generator(analysis)
    filepath.write_text(content, encoding="utf-8")

    print(f"✅ {filename} 갱신 완료")
    return True


def refresh_all(analysis: dict) -> bool:
    """모든 문서를 갱신합니다."""
    docs = ["readme", "architecture", "api", "development"]
    success = True

    for doc in docs:
        if not refresh_doc(doc, analysis):
            success = False

    return success


def main():
    """메인 함수"""
    parser = argparse.ArgumentParser(
        description="프로젝트 문서를 갱신합니다."
    )
    parser.add_argument(
        "doc",
        nargs="?",
        default=None,
        help="갱신할 문서 (api, architecture, readme, development). 비어있으면 전체 갱신"
    )

    args = parser.parse_args()

    print("\n🔄 프로젝트 문서 갱신\n")

    # 코드 분석
    print("🔍 프로젝트 코드 분석 중...")
    analysis = analyze_project()

    java = analysis.get("java_spring", {})
    react = analysis.get("react", {})

    if java:
        print(f"   ☕ Java: Entity {len(java.get('entities', []))}개, Controller {len(java.get('controllers', []))}개")
    if react:
        print(f"   ⚛️  React: Page {len(react.get('pages', []))}개, Component {len(react.get('components', []))}개")
    print()

    # 문서 갱신
    if args.doc:
        success = refresh_doc(args.doc, analysis)
    else:
        print("📝 전체 문서 갱신...")
        success = refresh_all(analysis)

    if success:
        print("\n✅ 문서 갱신 완료!")
    else:
        sys.exit(1)


if __name__ == "__main__":
    main()
