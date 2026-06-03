#!/usr/bin/env python3
"""
개발 프로세스 단축 명령 디스패처

dev-req와 dev-project-auto-doc 스킬로 명령을 위임합니다.

사용법:
    python dispatch.py <command> [args...]
"""

import subprocess
import sys
from pathlib import Path


# 명령어 매핑
COMMAND_MAP = {
    # 요구사항 관리 (dev-req)
    "init": {
        "skill": "dev-req",
        "script": "init.py",
        "description": "요구사항 관리 구조 초기화",
    },
    "req": {
        "skill": "dev-req",
        "script": "create_requirement.py",
        "description": "요구사항 명세서 생성",
    },
    "save": {
        "skill": "dev-req",
        "script": "checkpoint_save.py",
        "description": "체크포인트 저장",
    },
    "load": {
        "skill": "dev-req",
        "script": "checkpoint_load.py",
        "description": "체크포인트 로드",
    },
    "status": {
        "skill": "dev-req",
        "script": "progress_report.py",
        "description": "진행 상황 리포트",
    },

    # 문서 자동화 (dev-project-auto-doc)
    "doc": {
        "skill": "dev-project-auto-doc",
        "script": "init.py",
        "description": "프로젝트 문서 초기화",
        "subcommands": {
            "init": "init.py",
            "refresh": "refresh.py",
            "analyze": "analyze.py",
            "status": "status.py",
            "upgrade": "upgrade.py",
            "add": "add_doc.py",
        }
    },
}


def get_skill_script_path(skill_name: str, script_name: str) -> Path:
    """스킬 스크립트 경로를 반환합니다."""
    current_dir = Path(__file__).parent.parent.parent  # .claude/skills/
    skill_dir = current_dir / skill_name / "scripts"
    return skill_dir / script_name


def show_help():
    """도움말을 표시합니다."""
    print("""
🚀 /dev - 개발 프로세스 단축 명령

사용법: /dev <command> [options]

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📋 요구사항 관리 (dev-req)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  init                          요구사항 관리 구조 초기화
  req <name> [--priority=<p>]   요구사항 명세서 생성
  save [--reason=<reason>]      체크포인트 저장
  load [--checkpoint_id=<id>]   체크포인트 로드
  status [--format=<format>]    진행 상황 리포트

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📄 문서 자동화 (dev-project-auto-doc)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  doc                           프로젝트 문서 초기화 (대화형)
  doc init [--scale=<s>]        프로젝트 문서 초기화 (minimal/standard/full)
  doc refresh [doc]             문서 갱신 (api, architecture, readme, development)
  doc analyze                   코드 분석
  doc status                    동기화 상태 확인
  doc upgrade <scale>           규모 업그레이드 (standard/full)
  doc add <doc>                 개별 문서 추가 (testing, security 등)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
기타
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  help                          이 도움말 표시

예시:
  /dev init                     요구사항 관리 구조 초기화
  /dev req 사용자인증기능       새 요구사항 생성
  /dev save --reason="퇴근"     체크포인트 저장
  /dev doc                      프로젝트 문서 초기화 (대화형)
  /dev doc --scale=standard     기본 구성으로 문서 초기화
  /dev doc refresh api          api.md 갱신
  /dev doc upgrade full         완전 구성으로 업그레이드
  /dev doc add testing          testing.md 추가

워크플로우:
  1. /dev init          → 요구사항 관리 구조 초기화
  2. /dev doc           → 프로젝트 문서 초기화 (규모 선택)
  3. /dev req <name>    → 요구사항 생성
  4. /dev status        → 진행 상황 확인
  5. /dev save          → 작업 저장
  6. /dev doc upgrade   → 필요시 문서 규모 확장

전체 명령어:
  /dev-req              요구사항 관리 (init, create, checkpoint-save, checkpoint-load, progress-report)
  /dev-project-auto-doc     문서 자동화 (init, refresh, analyze, status, upgrade, add-doc)
""")


def dispatch_command(command: str, args: list):
    """명령을 해당 스킬 스크립트로 위임합니다."""
    if command == "help" or command is None:
        show_help()
        return 0

    # doc 명령 처리 (서브커맨드 지원)
    if command == "doc":
        cmd_info = COMMAND_MAP["doc"]

        if args and args[0] in cmd_info["subcommands"]:
            script_name = cmd_info["subcommands"][args[0]]
            sub_args = args[1:]
            description = f"프로젝트 문서 {args[0]}"
        else:
            script_name = "init.py"
            sub_args = args
            description = "프로젝트 문서 초기화"

        script_path = get_skill_script_path(cmd_info["skill"], script_name)

        if not script_path.exists():
            print(f"❌ 스크립트를 찾을 수 없습니다: {script_path}")
            return 1

        print(f"🔗 {description} ({cmd_info['skill']})")
        print()

        try:
            result = subprocess.run(
                [sys.executable, str(script_path)] + sub_args,
                cwd=Path.cwd()
            )
            return result.returncode
        except Exception as e:
            print(f"❌ 실행 오류: {e}")
            return 1

    # 일반 명령 처리
    if command not in COMMAND_MAP:
        print(f"❌ 알 수 없는 명령: {command}")
        print("💡 /dev help 로 사용 가능한 명령을 확인하세요.")
        return 1

    cmd_info = COMMAND_MAP[command]
    script_path = get_skill_script_path(cmd_info["skill"], cmd_info["script"])

    if not script_path.exists():
        print(f"❌ 스크립트를 찾을 수 없습니다: {script_path}")
        print(f"💡 {cmd_info['skill']} 스킬이 설치되어 있는지 확인하세요.")
        return 1

    print(f"🔗 {cmd_info['description']} ({cmd_info['skill']})")
    print()

    try:
        result = subprocess.run(
            [sys.executable, str(script_path)] + args,
            cwd=Path.cwd()
        )
        return result.returncode
    except Exception as e:
        print(f"❌ 실행 오류: {e}")
        return 1


def main():
    """메인 함수"""
    if len(sys.argv) < 2:
        show_help()
        return

    command = sys.argv[1]
    args = sys.argv[2:]

    exit_code = dispatch_command(command, args)
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
