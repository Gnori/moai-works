#!/usr/bin/env bash
# moai-works-web-frontend 의 개발 서버(vite)를 끈다. bin/startup.sh 로 띄운 프로세스만 끈다.
# 정상 종료(TERM)를 보내고, 30초(SHUTDOWN_WAIT) 안에 끝나지 않으면 강제로 끝낸다(KILL).
set -euo pipefail

NAME=moai-works-web-frontend
MATCH="vite/bin/vite.js"      # 프로세스 명령줄에서 이 모듈을 알아보는 글자
PORT_ENV=MOAI_WORKS_FRONTEND_PORT
DEFAULT_PORT=10014

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_FILE="$BASE_DIR/logs/$NAME.pid"

# shellcheck disable=SC1091
[ -f "$BASE_DIR/bin/setenv.sh" ] && . "$BASE_DIR/bin/setenv.sh"

# 이 스크립트로 띄운 프로세스가 살아 있으면 PID 를 찍는다. PID 가 다른 프로그램에 다시 쓰였으면 아니라고 본다.
running_pid() {
  local pid
  pid="$(cat "$PID_FILE" 2>/dev/null || true)"
  [ -n "$pid" ] || return 1
  kill -0 "$pid" 2>/dev/null || return 1
  ps -ww -p "$pid" -o command= 2>/dev/null | grep -q -- "$MATCH" || return 1
  echo "$pid"
}

if ! pid="$(running_pid)"; then
  rm -f "$PID_FILE"
  echo "$NAME: 떠 있지 않다"
  # 다른 방법(npm run dev)으로 띄운 프로세스가 포트를 쓰고 있으면 알려 준다. 끄지는 않는다.
  PORT="${!PORT_ENV:-$DEFAULT_PORT}"
  if command -v lsof >/dev/null 2>&1; then
    holder="$(lsof -nP -iTCP:"$PORT" -sTCP:LISTEN -t 2>/dev/null | head -n 1 || true)"
    if [ -n "$holder" ]; then
      echo "$NAME: 다만 포트 $PORT 을 PID $holder 가 쓰고 있다 — 이 스크립트로 띄운 것이 아니라서 그대로 둔다" >&2
    fi
  fi
  exit 0
fi

kill "$pid"
WAIT="${SHUTDOWN_WAIT:-30}"
for ((i = 1; i <= WAIT; i++)); do
  if ! kill -0 "$pid" 2>/dev/null; then
    rm -f "$PID_FILE"
    echo "$NAME: 껐다 (PID $pid)"
    exit 0
  fi
  sleep 1
done

echo "$NAME: ${WAIT}초 안에 끝나지 않아 강제로 끝낸다 (PID $pid)" >&2
kill -9 "$pid" 2>/dev/null || true
rm -f "$PID_FILE"
