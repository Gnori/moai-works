#!/usr/bin/env python3
"""
체크포인트 로드 스크립트

사용법:
    python checkpoint_load.py [--checkpoint_id=<id>]
"""

import argparse
import sys
from pathlib import Path

from claude_md_utils import get_project_root


def find_checkpoints(checkpoints_dir: Path) -> list:
    """체크포인트 파일 목록을 반환합니다 (최신순)."""
    if not checkpoints_dir.exists():
        return []

    checkpoints = list(checkpoints_dir.glob("CP-*.md"))
    # 파일명으로 정렬 (최신순)
    checkpoints.sort(reverse=True)
    return checkpoints


def load_checkpoint(checkpoint_id: str = "") -> dict:
    """
    체크포인트를 로드합니다.

    Args:
        checkpoint_id: 체크포인트 ID (비어있으면 최신 체크포인트)

    Returns:
        체크포인트 정보 딕셔너리
    """
    project_root = get_project_root()
    checkpoints_dir = project_root / ".claude" / "requirements" / "checkpoints"

    # 체크포인트 디렉토리 확인
    if not checkpoints_dir.exists():
        print("❌ 체크포인트 디렉토리가 없습니다.")
        print(f"💡 경로: {checkpoints_dir.relative_to(project_root)}")
        return {}

    # 체크포인트 목록 가져오기
    checkpoints = find_checkpoints(checkpoints_dir)

    if not checkpoints:
        print("❌ 저장된 체크포인트가 없습니다.")
        print("💡 먼저 체크포인트를 저장하세요:")
        print("   /dev-req checkpoint-save")
        return {}

    # 체크포인트 선택
    if checkpoint_id:
        # 특정 체크포인트 로드
        checkpoint_file = checkpoints_dir / f"{checkpoint_id}.md"
        if not checkpoint_file.exists():
            print(f"❌ 체크포인트를 찾을 수 없습니다: {checkpoint_id}")
            print(f"\n📋 사용 가능한 체크포인트:")
            for i, cp in enumerate(checkpoints[:5], 1):
                print(f"   {i}. {cp.stem}")
            return {}
    else:
        # 최신 체크포인트 로드
        checkpoint_file = checkpoints[0]
        checkpoint_id = checkpoint_file.stem

    # 체크포인트 파일 읽기
    content = checkpoint_file.read_text(encoding="utf-8")

    # 기본 정보 파싱
    checkpoint_info = parse_checkpoint(content, checkpoint_id)

    # 출력
    print_checkpoint_info(checkpoint_info, checkpoint_file, project_root)

    return checkpoint_info


def parse_checkpoint(content: str, checkpoint_id: str) -> dict:
    """체크포인트 내용을 파싱합니다."""
    info = {
        "checkpoint_id": checkpoint_id,
        "requirement": "",
        "saved_at": "",
        "reason": "",
        "completed_tasks": [],
        "current_tasks": [],
        "next_tasks": [],
        "files": [],
        "notes": ""
    }

    # 기본 정보 추출
    lines = content.split("\n")
    for line in lines:
        if "**요구사항**:" in line:
            info["requirement"] = line.split(":", 1)[1].strip()
        elif "**저장 시각**:" in line:
            info["saved_at"] = line.split(":", 1)[1].strip()
        elif "**저장 이유**:" in line:
            info["reason"] = line.split(":", 1)[1].strip()

    return info


def print_checkpoint_info(info: dict, checkpoint_file: Path, project_root: Path):
    """체크포인트 정보를 출력합니다."""
    print("📥 체크포인트 복원 완료!")
    print(f"🔖 체크포인트: {info['checkpoint_id']}")
    print(f"📋 요구사항: {info['requirement']}")
    print(f"⏱️  저장 시각: {info['saved_at']}")
    print(f"📍 저장 이유: {info['reason']}")
    print("")
    print(f"📄 체크포인트 파일: {checkpoint_file.relative_to(project_root)}")
    print("")
    print("📝 다음 단계:")
    print("1. 체크포인트 파일을 열어 상세 내용 확인")
    print("2. 요구사항 명세서 체크리스트 확인")
    print("3. Git 상태 확인 (git status)")
    print("4. 수정 중이던 파일들 열기")
    print("5. 다음 작업 계획에 따라 작업 재개")
    print("")
    print("💡 팁: 체크포인트 파일에서 다음 정보를 확인하세요:")
    print("   - 완료된 작업")
    print("   - 현재 진행 중인 작업")
    print("   - 다음 작업 계획")
    print("   - 기술적 메모")


def main():
    """메인 함수"""
    parser = argparse.ArgumentParser(
        description="저장된 체크포인트를 로드합니다."
    )
    parser.add_argument(
        "--checkpoint_id",
        type=str,
        default="",
        help="체크포인트 ID (비어있으면 최신 체크포인트)"
    )

    args = parser.parse_args()

    # 체크포인트 로드
    result = load_checkpoint(args.checkpoint_id)

    if not result:
        sys.exit(1)


if __name__ == "__main__":
    main()
