#!/usr/bin/env python3
"""
프로젝트 문서 규모 업그레이드 스크립트

minimal → standard → full 로 규모를 업그레이드합니다.

사용법:
    python upgrade.py <target_scale>
"""

import argparse
import json
import sys
from datetime import datetime
from pathlib import Path

# 공통 유틸리티 임포트
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "common"))
from claude_md_utils import ensure_claude_md_with_section, get_project_root

# init 모듈에서 상수와 함수 임포트
sys.path.insert(0, str(Path(__file__).parent))
from init import (
    SCALE_DOCUMENTS,
    SCALE_NAMES,
    get_doc_description,
    get_project_docs_section,
    save_scale_info,
    generate_simple_doc,
    load_template,
)
from analyze import analyze_project


def get_current_scale() -> str:
    """현재 규모를 확인합니다."""
    project_root = get_project_root()

    # 캐시 파일에서 확인
    cache_file = project_root / ".claude" / ".cache" / "project_docs_scale.json"
    if cache_file.exists():
        try:
            data = json.loads(cache_file.read_text())
            return data.get("scale")
        except:
            pass

    # 캐시가 없으면 파일 기반으로 추정
    docs_dir = project_root / ".claude" / "project_docs"

    if not docs_dir.exists():
        return None

    existing_docs = [doc for doc in SCALE_DOCUMENTS["full"] if (docs_dir / doc).exists()]
    doc_count = len(existing_docs)

    if doc_count >= len(SCALE_DOCUMENTS["full"]):
        return "full"
    elif doc_count >= len(SCALE_DOCUMENTS["standard"]):
        return "standard"
    elif doc_count >= len(SCALE_DOCUMENTS["minimal"]):
        return "minimal"

    return None


def get_missing_documents(current_scale: str, target_scale: str) -> list:
    """추가해야 할 문서 목록을 반환합니다."""
    if current_scale is None:
        return SCALE_DOCUMENTS[target_scale]

    current_docs = set(SCALE_DOCUMENTS[current_scale])
    target_docs = set(SCALE_DOCUMENTS[target_scale])

    # 순서 유지를 위해 target_scale 문서 순서대로 반환
    missing = []
    for doc in SCALE_DOCUMENTS[target_scale]:
        if doc not in current_docs:
            missing.append(doc)

    return missing


def get_template_path(doc_name: str) -> Path:
    """템플릿 파일 경로를 반환합니다."""
    skill_dir = Path(__file__).parent.parent
    template_dir = skill_dir / "templates" / "project_docs"
    return template_dir / f"{doc_name}.template"


def create_document(doc_name: str, analysis: dict) -> bool:
    """문서를 생성합니다."""
    project_root = get_project_root()
    docs_dir = project_root / ".claude" / "project_docs"
    target_file = docs_dir / doc_name

    if target_file.exists():
        print(f"   ⚠️  {doc_name} 이미 존재 (건너뜀)")
        return True

    # 템플릿 로드 시도
    variables = {
        "PROJECT_NAME": project_root.name,
        "CREATION_DATE": datetime.now().strftime("%Y-%m-%d"),
        "TIMESTAMP": datetime.now().strftime("%Y-%m-%d"),
    }

    content = load_template(doc_name, variables)
    if not content:
        content = generate_simple_doc(doc_name, analysis)

    target_file.write_text(content, encoding="utf-8")
    print(f"   ✅ {doc_name}")
    return True


def upgrade_scale(target_scale: str) -> bool:
    """규모를 업그레이드합니다."""
    current_scale = get_current_scale()

    if current_scale is None:
        print("❌ 현재 프로젝트 문서 구조가 없습니다.")
        print("💡 먼저 초기화하세요: /dev-project-auto-doc init")
        return False

    if target_scale not in SCALE_DOCUMENTS:
        print(f"❌ 잘못된 목표 규모: {target_scale}")
        print("   standard 또는 full 중 하나를 선택하세요.")
        return False

    # 규모 비교
    scale_order = ["minimal", "standard", "full"]
    current_idx = scale_order.index(current_scale)
    target_idx = scale_order.index(target_scale)

    if target_idx <= current_idx:
        print(f"⚠️  현재 이미 {SCALE_NAMES[current_scale]}입니다.")
        print(f"   {SCALE_NAMES[target_scale]}로 다운그레이드할 수 없습니다.")
        return False

    # 추가할 문서 확인
    missing_docs = get_missing_documents(current_scale, target_scale)

    if not missing_docs:
        print("✅ 이미 모든 문서가 존재합니다.")
        save_scale_info(target_scale)
        return True

    # 확인
    print(f"\n🚀 프로젝트 문서 규모 업그레이드\n")
    print(f"현재: {SCALE_NAMES[current_scale]} ({len(SCALE_DOCUMENTS[current_scale])}개)")
    print(f"목표: {SCALE_NAMES[target_scale]} ({len(SCALE_DOCUMENTS[target_scale])}개)")
    print()
    print("추가할 문서:")
    for doc in missing_docs:
        desc = get_doc_description(doc)
        print(f"  • {doc}: {desc}")
    print()

    confirm = input("진행하시겠습니까? (Y/n): ").strip().lower()
    if confirm and confirm != 'y':
        print("❌ 취소되었습니다.")
        return False

    # 코드 분석
    print("\n🔍 프로젝트 코드 분석 중...")
    analysis = analyze_project()

    # 문서 생성
    print("\n📄 문서 생성 중...")
    for doc in missing_docs:
        create_document(doc, analysis)

    # 규모 정보 저장
    save_scale_info(target_scale)

    # CLAUDE.md 업데이트
    print("\n📄 CLAUDE.md 업데이트 중...")
    section_content = get_project_docs_section(target_scale)
    backup_path = ensure_claude_md_with_section(
        "프로젝트 문서",
        section_content
    )
    if backup_path:
        print(f"   📦 백업 완료: {backup_path}")
    print("   ✅ 프로젝트 문서 섹션 업데이트 완료")

    # 완료
    print(f"\n✅ {SCALE_NAMES[target_scale]}로 업그레이드 완료!")
    print(f"\n📄 추가된 문서 수: {len(missing_docs)}개")
    print("\n📝 다음 단계:")
    print("   각 문서를 열어 프로젝트에 맞게 내용을 작성하세요:")
    for doc in missing_docs:
        print(f"   • .claude/project_docs/{doc}")

    return True


def show_current_status():
    """현재 상태를 표시합니다."""
    current_scale = get_current_scale()
    project_root = get_project_root()
    docs_dir = project_root / ".claude" / "project_docs"

    print("\n📊 프로젝트 문서 규모 상태\n")

    if current_scale is None:
        print("❌ 프로젝트 문서가 초기화되지 않았습니다.")
        print("💡 /dev-project-auto-doc init 으로 초기화하세요.")
        return

    print(f"현재 규모: {SCALE_NAMES[current_scale]}")
    print()

    # 존재하는 문서 확인
    existing = []
    for doc in SCALE_DOCUMENTS["full"]:
        if docs_dir.exists() and (docs_dir / doc).exists():
            existing.append(doc)

    print(f"생성된 문서: {len(existing)}개")
    for doc in existing:
        print(f"  ✅ {doc}")
    print()

    # 업그레이드 옵션 표시
    if current_scale == "minimal":
        print("💡 업그레이드 옵션:")
        print("   /dev-project-auto-doc upgrade standard  (+3개 문서)")
        print("   /dev-project-auto-doc upgrade full      (+9개 문서)")
    elif current_scale == "standard":
        print("💡 업그레이드 옵션:")
        print("   /dev-project-auto-doc upgrade full      (+6개 문서)")
    else:
        print("✅ 이미 완전 구성(Full)입니다.")


def main():
    """메인 함수"""
    parser = argparse.ArgumentParser(
        description="프로젝트 문서 규모를 업그레이드합니다."
    )
    parser.add_argument(
        "target_scale",
        nargs="?",
        default=None,
        choices=["standard", "full"],
        help="목표 규모 (standard/full)"
    )

    args = parser.parse_args()

    if args.target_scale is None:
        show_current_status()
        return

    # 업그레이드 실행
    success = upgrade_scale(args.target_scale)

    if not success:
        sys.exit(1)


if __name__ == "__main__":
    main()
