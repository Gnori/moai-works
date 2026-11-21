#!/usr/bin/env bash
# moai-works-web-frontend 의 개발 서버(vite, 11003)를 백그라운드로 띄운다. 끄기는 bin/shutdown.sh
# 화면을 고치는 동안에만 쓴다 — 운영에서는 `npm run build` 결과를 web-backend 가 내보내므로 이 모듈은 띄울 것이 없다.
#
#   logs/moai-works-web-frontend.log   개발 서버 출력. 날짜가 바뀌면 <파일>.<날짜> 로 넘기고 30일 둔다(bin/rolling-log.mjs)
#   logs/moai-works-web-frontend.out   로그를 쓰다가 난 오류만. 띄울 때마다 새로 쓴다
#   logs/moai-works-web-frontend.pid   PID
#
# 이 자리에서만 쓰는 값(PATH · MOAI_WORKS_FRONTEND_PORT · STARTUP_WAIT)은 bin/setenv.sh 에 둔다(git 제외).
set -euo pipefail

NAME=moai-works-web-frontend
MATCH="vite/bin/vite.js"      # 프로세스 명령줄에서 이 모듈을 알아보는 글자
PORT_ENV=MOAI_WORKS_FRONTEND_PORT
DEFAULT_PORT=11003

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$BASE_DIR/logs"
LOG_FILE="$LOG_DIR/$NAME.log"
OUT_FILE="$LOG_DIR/$NAME.out"
PID_FILE="$LOG_DIR/$NAME.pid"

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

if pid="$(running_pid)"; then
  echo "$NAME: 이미 떠 있다 (PID $pid)"
  exit 0
fi

PORT="${!PORT_ENV:-$DEFAULT_PORT}"

if command -v lsof >/dev/null 2>&1; then
  holder="$(lsof -nP -iTCP:"$PORT" -sTCP:LISTEN -t 2>/dev/null | head -n 1 || true)"
  if [ -n "$holder" ]; then
    echo "$NAME: 포트 $PORT 을 다른 프로세스(PID $holder)가 쓰고 있다 — 그 프로세스를 먼저 끈다" >&2
    exit 1
  fi
fi

# 개발 서버도 HTTPS 로 연다 — 인증서는 infra/tls/make-certs.sh 가 만든다(vite.config.ts 가 읽는다)
TLS_CA="$BASE_DIR/../../infra/tls/certs/ca.pem"
if [ ! -f "$TLS_CA" ]; then
  echo "$NAME: TLS 인증서가 없다 — 저장소 맨 위에서 infra/tls/make-certs.sh 를 먼저 돌린다" >&2
  exit 1
fi

NODE="$(command -v node || true)"
if [ -z "$NODE" ]; then
  echo "$NAME: node 를 찾지 못했다 — PATH 를 잡거나 bin/setenv.sh 에 적는다(Node 22 이상)" >&2
  exit 1
fi
VITE="$BASE_DIR/node_modules/vite/bin/vite.js"
if [ ! -f "$VITE" ]; then
  echo "$NAME: node_modules 가 없다 — 먼저 받는다: npm install" >&2
  exit 1
fi

mkdir -p "$LOG_DIR"
cd "$BASE_DIR"
: > "$OUT_FILE"
# vite 는 표준 출력에만 쓴다 → 날짜별로 넘기는 스크립트로 흘려 보낸다. 다른 포트로 옮겨 뜨지 않게 --strictPort.
NO_COLOR=1 nohup "$NODE" "$VITE" --port "$PORT" --strictPort \
  > >("$NODE" "$BASE_DIR/bin/rolling-log.mjs" "$LOG_FILE" >> "$OUT_FILE" 2>&1) 2>&1 < /dev/null &
pid=$!
echo "$pid" > "$PID_FILE"

# 응답할 때까지 기다린다. 그 전에 죽으면 마지막 출력을 보이고 실패로 끝낸다.
WAIT="${STARTUP_WAIT:-30}"
for ((i = 1; i <= WAIT; i++)); do
  if ! kill -0 "$pid" 2>/dev/null; then
    rm -f "$PID_FILE"
    echo "$NAME: 뜨다가 멈췄다. 마지막 출력:" >&2
    tail -n 30 "$OUT_FILE" "$LOG_FILE" >&2 2>/dev/null || true
    exit 1
  fi
  if curl -fsS -o /dev/null --max-time 2 --cacert "$TLS_CA" "https://localhost:$PORT/" 2>/dev/null; then
    echo "$NAME: 띄웠다 (PID $pid, https://localhost:$PORT, ${i}초) — 로그 $LOG_FILE"
    exit 0
  fi
  sleep 1
done

echo "$NAME: ${WAIT}초 안에 응답하지 않는다 (PID $pid 는 살아 있다) — 로그 $LOG_FILE" >&2
exit 1
