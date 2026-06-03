#!/usr/bin/env python3
"""
문서 동기화 상태 확인 스크립트

코드와 문서 간 동기화 상태를 확인합니다.

사용법:
    python status.py
"""

import json
import sys
from datetime import datetime
from pathlib import Path

# analyze 모듈 임포트
sys.path.insert(0, str(Path(__file__).parent))
from analyze import analyze_project


def get_project_root() -> Path:
    """프로젝트 루트 디렉토리를 반환합니다."""
    return Path.cwd()


def check_docs_exist() -> dict:
    """문서 존재 여부를 확인합니다."""
    project_root = get_project_root()
    docs_dir = project_root / ".claude" / "project_docs"

    docs = {
        "README.md": docs_dir / "README.md",
        "architecture.md": docs_dir / "architecture.md",
        "api.md": docs_dir / "api.md",
        "development.md": docs_dir / "development.md",
    }

    result = {}
    for name, path in docs.items():
        result[name] = {
            "exists": path.exists(),
            "modified": datetime.fromtimestamp(path.stat().st_mtime).strftime("%Y-%m-%d %H:%M") if path.exists() else None,
            "size": path.stat().st_size if path.exists() else 0,
        }

    return result


def load_cached_analysis() -> dict:
    """캐시된 분석 결과를 로드합니다."""
    cache_file = get_project_root() / ".claude" / ".cache" / "project_analysis.json"

    if cache_file.exists():
        try:
            return json.loads(cache_file.read_text())
        except:
            pass

    return {}


def compare_analysis(current: dict, cached: dict) -> dict:
    """현재 분석과 캐시된 분석을 비교합니다."""
    changes = {
        "new_entities": [],
        "new_controllers": [],
        "new_pages": [],
        "removed_entities": [],
        "removed_controllers": [],
        "removed_pages": [],
    }

    # Java 비교
    current_java = current.get("java_spring", {})
    cached_java = cached.get("java_spring", {})

    current_entities = {e["name"] for e in current_java.get("entities", [])}
    cached_entities = {e["name"] for e in cached_java.get("entities", [])}
    changes["new_entities"] = list(current_entities - cached_entities)
    changes["removed_entities"] = list(cached_entities - current_entities)

    current_controllers = {c["name"] for c in current_java.get("controllers", [])}
    cached_controllers = {c["name"] for c in cached_java.get("controllers", [])}
    changes["new_controllers"] = list(current_controllers - cached_controllers)
    changes["removed_controllers"] = list(cached_controllers - current_controllers)

    # React 비교
    current_react = current.get("react", {})
    cached_react = cached.get("react", {})

    current_pages = {p["name"] for p in current_react.get("pages", [])}
    cached_pages = {p["name"] for p in cached_react.get("pages", [])}
    changes["new_pages"] = list(current_pages - cached_pages)
    changes["removed_pages"] = list(cached_pages - current_pages)

    return changes


def check_status():
    """동기화 상태를 확인합니다."""
    project_root = get_project_root()

    print("\n📊 프로젝트 문서 동기화 상태\n")

    # 1. 문서 존재 여부 확인
    docs_status = check_docs_exist()
    docs_dir = project_root / ".claude" / "project_docs"

    print("📁 문서 상태:")
    print(f"   디렉토리: {'.claude/project_docs/'}")
    print()

    all_exist = True
    for name, status in docs_status.items():
        if status["exists"]:
            print(f"   ✅ {name:<20} (수정: {status['modified']})")
        else:
            print(f"   ❌ {name:<20} (없음)")
            all_exist = False

    print()

    # 2. 코드 분석
    print("🔍 현재 코드 분석 중...")
    current_analysis = analyze_project()

    # 3. 캐시된 분석과 비교
    cached_analysis = load_cached_analysis()

    if cached_analysis:
        changes = compare_analysis(current_analysis, cached_analysis)

        has_changes = any([
            changes["new_entities"],
            changes["new_controllers"],
            changes["new_pages"],
            changes["removed_entities"],
            changes["removed_controllers"],
            changes["removed_pages"],
        ])

        if has_changes:
            print("\n⚠️  코드 변경 감지:\n")

            if changes["new_entities"]:
                print(f"   ➕ 새 Entity: {', '.join(changes['new_entities'])}")
            if changes["new_controllers"]:
                print(f"   ➕ 새 Controller: {', '.join(changes['new_controllers'])}")
            if changes["new_pages"]:
                print(f"   ➕ 새 Page: {', '.join(changes['new_pages'])}")

            if changes["removed_entities"]:
                print(f"   ➖ 삭제된 Entity: {', '.join(changes['removed_entities'])}")
            if changes["removed_controllers"]:
                print(f"   ➖ 삭제된 Controller: {', '.join(changes['removed_controllers'])}")
            if changes["removed_pages"]:
                print(f"   ➖ 삭제된 Page: {', '.join(changes['removed_pages'])}")

            print("\n💡 문서 갱신 필요:")
            if changes["new_entities"] or changes["removed_entities"]:
                print("   /dev-project-auto-doc refresh architecture")
            if changes["new_controllers"] or changes["removed_controllers"]:
                print("   /dev-project-auto-doc refresh api")
            if changes["new_pages"] or changes["removed_pages"]:
                print("   /dev-project-auto-doc refresh development")
        else:
            print("\n✅ 코드와 문서가 동기화되어 있습니다.")
    else:
        print("\n⚠️  이전 분석 캐시가 없습니다.")
        print("💡 /dev-project-auto-doc analyze 를 실행하여 캐시를 생성하세요.")

    # 4. 요약
    print("\n" + "─" * 50)

    if not all_exist:
        print("\n📋 권장 작업:")
        print("   /dev-project-auto-doc init  (문서 초기화)")
    elif cached_analysis and has_changes:
        print("\n📋 권장 작업:")
        print("   /dev-project-auto-doc refresh  (문서 갱신)")
    else:
        print("\n✅ 문서가 최신 상태입니다.")

    print()


def main():
    """메인 함수"""
    check_status()


if __name__ == "__main__":
    main()
