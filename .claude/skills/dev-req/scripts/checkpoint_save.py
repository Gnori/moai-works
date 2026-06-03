#!/usr/bin/env python3
"""
개발 체크포인트 저장 스크립트

사용법:
    python checkpoint_save.py [--reason=<reason>] [--requirement_file=<file>]
"""

import argparse
import subprocess
import sys
from datetime import datetime
from pathlib import Path

from claude_md_utils import get_project_root


def get_git_status() -> dict:
    """Git 상태 정보를 수집합니다."""
    try:
        # 현재 브랜치
        branch = subprocess.check_output(
            ["git", "rev-parse", "--abbrev-ref", "HEAD"],
            stderr=subprocess.DEVNULL
        ).decode().strip()

        # 최근 커밋
        last_commit = subprocess.check_output(
            ["git", "log", "-1", "--format=%h %s"],
            stderr=subprocess.DEVNULL
        ).decode().strip()

        # Staged 파일
        staged = subprocess.check_output(
            ["git", "diff", "--cached", "--name-only"],
            stderr=subprocess.DEVNULL
        ).decode().strip().split("\n")

        # Modified 파일
        modified = subprocess.check_output(
            ["git", "diff", "--name-only"],
            stderr=subprocess.DEVNULL
        ).decode().strip().split("\n")

        return {
            "branch": branch,
            "last_commit": last_commit,
            "staged": [f for f in staged if f],
            "modified": [f for f in modified if f]
        }
    except subprocess.CalledProcessError:
        return {
            "branch": "N/A",
            "last_commit": "N/A",
            "staged": [],
            "modified": []
        }


def get_template_path() -> Path:
    """체크포인트 템플릿 경로를 반환합니다."""
    project_root = get_project_root()
    return project_root / ".claude" / "requirements" / "templates" / "checkpoint.md"


def save_checkpoint(reason: str = "수동 저장", requirement_file: str = "") -> str:
    """
    개발 체크포인트를 저장합니다.

    Args:
        reason: 저장 이유
        requirement_file: 연관된 요구사항 파일명

    Returns:
        체크포인트 ID
    """
    project_root = get_project_root()

    # 디렉토리 경로
    requirements_dir = project_root / ".claude" / "requirements"
    checkpoints_dir = requirements_dir / "checkpoints"

    # 체크포인트 디렉토리 생성
    checkpoints_dir.mkdir(parents=True, exist_ok=True)

    # 체크포인트 ID 생성
    now = datetime.now()
    checkpoint_id = f"CP-{now.strftime('%Y%m%d-%H%M%S-%f')[:20]}"
    checkpoint_file = checkpoints_dir / f"{checkpoint_id}.md"

    # Git 상태 수집
    git_status = get_git_status()

    # 템플릿 로드 시도
    template_path = get_template_path()
    created_at = now.strftime('%Y-%m-%d %H:%M:%S')

    if template_path.exists():
        content = template_path.read_text(encoding="utf-8")
        content = content.replace("{{CHECKPOINT_ID}}", checkpoint_id)
        content = content.replace("{{REQUIREMENT_FILE}}", requirement_file if requirement_file else "[미지정]")
        content = content.replace("{{CREATION_DATE}}", created_at)
        content = content.replace("{{REASON}}", reason)
        content = content.replace("{{GIT_BRANCH}}", git_status['branch'])
        content = content.replace("{{GIT_LAST_COMMIT}}", git_status['last_commit'])
        content = content.replace("{{GIT_STAGED}}", '\n'.join(git_status['staged']) if git_status['staged'] else '[없음]')
        content = content.replace("{{GIT_MODIFIED}}", '\n'.join(git_status['modified']) if git_status['modified'] else '[없음]')
    else:
        # 템플릿이 없으면 기본 내용 생성
        content = f"""# 개발 체크포인트: {checkpoint_id}

## 📍 체크포인트 정보

- **체크포인트 번호**: {checkpoint_id}
- **요구사항**: {requirement_file if requirement_file else "[미지정]"}
- **저장 시각**: {created_at}
- **저장 이유**: {reason}

---

## ✅ 완료된 작업

### 주요 완료 항목
- [ ] [완료 항목을 여기에 기록하세요]

### 구체적 작업 내용
```
[어떤 작업을 했는지 구체적으로 기술하세요]
```

---

## 🔄 현재 진행 중인 작업

### 작업 중인 항목
- [ ] [진행 중인 작업을 여기에 기록하세요] (← 여기서 중단)

---

## 📋 다음 작업 계획

### 우선순위 1 (즉시 진행)
1. [다음에 해야 할 작업 1]
2. [다음에 해야 할 작업 2]

---

## 🔍 환경 정보

### Git 상태
```bash
# 현재 브랜치
{git_status['branch']}

# 최근 커밋
{git_status['last_commit']}

# Staged 파일
{chr(10).join(git_status['staged']) if git_status['staged'] else '[없음]'}

# Modified 파일
{chr(10).join(git_status['modified']) if git_status['modified'] else '[없음]'}
```

---

## 🔄 재개 시 체크리스트

- [ ] 1. 이 체크포인트 문서 전체 읽기
- [ ] 2. 요구사항 명세서 체크리스트 확인
- [ ] 3. active_development.md 업데이트
- [ ] 4. Git 상태 확인 (`git status`)
- [ ] 5. "다음 작업 계획"의 우선순위 1부터 시작

---

**체크포인트 저장 완료**: {created_at}
**다음 재개 시**: 이 문서를 먼저 읽으세요!
"""

    # 파일 저장
    checkpoint_file.write_text(content, encoding="utf-8")

    # active_development.md 업데이트
    update_active_development_checkpoint(project_root, checkpoint_id, created_at)

    # 성공 메시지
    print("💾 체크포인트 저장 완료!")
    print(f"🔖 체크포인트 ID: {checkpoint_id}")
    print(f"📍 저장 이유: {reason}")
    print(f"📁 위치: {checkpoint_file.relative_to(project_root)}")
    print("")
    print("⚠️  중요: 체크포인트 파일을 열어 실제 작업 내용을 기록하세요!")
    print("")
    print("📋 기록해야 할 내용:")
    print("  1. 완료된 작업")
    print("  2. 현재 진행 중인 작업")
    print("  3. 다음 작업 계획")
    print("  4. 작업 컨텍스트 (파일, 명령어)")
    print("  5. 기술적 메모")

    return checkpoint_id


def update_active_development_checkpoint(
    project_root: Path,
    checkpoint_id: str,
    saved_at: str
) -> None:
    """active_development.md의 체크포인트 섹션을 업데이트합니다."""
    active_dev_file = project_root / ".claude" / "requirements" / "active_development.md"

    if not active_dev_file.exists():
        print("⚠️  active_development.md 파일이 없습니다.")
        return

    # 파일 읽기
    content = active_dev_file.read_text(encoding="utf-8")

    # 최근 체크포인트 테이블 찾기
    table_marker = "| 체크포인트 ID | 저장 시각 | 요구사항 | 상태 |"

    if table_marker in content:
        separator_start = content.find("|", content.find(table_marker) + len(table_marker))
        next_section = content.find("\n\n", separator_start)

        if next_section == -1:
            next_section = len(content)

        # 새 행 추가
        new_row = f"| {checkpoint_id} | {saved_at} | [활성 요구사항] | 저장됨 |"

        # "[없음]" 행 제거 및 새 행 추가
        table_section = content[separator_start:next_section]
        if "| [없음]" in table_section:
            table_section = table_section.replace("| [없음] | - | - | - |", new_row)
        else:
            # 기존 행이 있으면 맨 위에 추가
            table_end = table_section.find("\n")
            table_section = table_section[:table_end] + "\n" + new_row + table_section[table_end:]

        # 내용 업데이트
        content = content[:separator_start] + table_section + content[next_section:]

        # 파일 쓰기
        active_dev_file.write_text(content, encoding="utf-8")


def main():
    """메인 함수"""
    parser = argparse.ArgumentParser(
        description="개발 체크포인트를 저장합니다."
    )
    parser.add_argument(
        "--reason",
        type=str,
        default="수동 저장",
        help="저장 이유"
    )
    parser.add_argument(
        "--requirement_file",
        type=str,
        default="",
        help="연관된 요구사항 파일명"
    )

    args = parser.parse_args()

    # 체크포인트 저장
    checkpoint_id = save_checkpoint(args.reason, args.requirement_file)

    if not checkpoint_id:
        sys.exit(1)


if __name__ == "__main__":
    main()
