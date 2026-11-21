#!/usr/bin/env bash
# moai-works-api 를 백그라운드로 띄운다. 끄기는 bin/shutdown.sh
#
#   logs/moai-works-api.log   로그. 날짜가 바뀌거나 50MB 를 넘으면 넘기고 30일 둔다(application.yaml 의 logging)
#   logs/moai-works-api.out   표준 출력 — 로깅이 뜨기 전 오류 · JVM 오류만. 띄울 때마다 새로 쓴다
#   logs/moai-works-api.pid   PID
#
# 이 자리에서만 쓰는 값(JAVA_HOME · JAVA_OPTS · APP_JAR · STARTUP_WAIT)은 bin/setenv.sh 에 둔다(git 제외).
set -euo pipefail

NAME=moai-works-api
MATCH="$NAME"                 # 프로세스 명령줄에서 이 모듈을 알아보는 글자
PORT_ENV=MOAI_WORKS_API_PORT
DEFAULT_PORT=10011

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

# 포트: 환경변수 → .env → 기본값 (application.yaml 이 읽는 순서와 같다)
PORT="${!PORT_ENV:-$(sed -n "s/^$PORT_ENV=//p" "$BASE_DIR/.env" 2>/dev/null | tail -n 1)}"
PORT="${PORT:-$DEFAULT_PORT}"

if command -v lsof >/dev/null 2>&1; then
  holder="$(lsof -nP -iTCP:"$PORT" -sTCP:LISTEN -t 2>/dev/null | head -n 1 || true)"
  if [ -n "$holder" ]; then
    echo "$NAME: 포트 $PORT 을 다른 프로세스(PID $holder)가 쓰고 있다 — 그 프로세스를 먼저 끈다" >&2
    exit 1
  fi
fi

# 모든 서비스는 HTTPS 로 연다(MOAI_WORKS_TLS_ENABLED=false 로 끈 배치만 HTTP). 뜬 것을 확인할 때 로컬 인증 기관으로 인증서를 본다.
TLS_ENABLED="${MOAI_WORKS_TLS_ENABLED:-$(sed -n 's/^MOAI_WORKS_TLS_ENABLED=//p' "$BASE_DIR/.env" 2>/dev/null | tail -n 1)}"
TLS_CA="${MOAI_WORKS_TLS_CA:-$BASE_DIR/../../infra/tls/certs/ca.pem}"
if [ "${TLS_ENABLED:-true}" = "false" ]; then
  SCHEME=http
  CURL_TLS=()
elif [ -f "$TLS_CA" ]; then
  SCHEME=https
  CURL_TLS=(--cacert "$TLS_CA")
else
  SCHEME=https
  CURL_TLS=()   # 공인 인증서를 쓰는 배치 — 운영체제가 믿는 인증 기관으로 확인한다
fi
if [ "$SCHEME" = https ] && [ -z "${MOAI_WORKS_TLS_CERT:-}" ] && [ ! -f "$BASE_DIR/../../infra/tls/certs/localhost.pem" ] \
  && ! grep -q '^MOAI_WORKS_TLS_CERT=' "$BASE_DIR/.env" 2>/dev/null; then
  echo "$NAME: TLS 인증서가 없다 — 로컬이면 저장소 맨 위에서 infra/tls/make-certs.sh 를 먼저 돌린다" >&2
  exit 1
fi

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA="$(command -v java || true)"
fi
if [ -z "$JAVA" ]; then
  echo "$NAME: java 를 찾지 못했다 — JAVA_HOME 을 잡거나 bin/setenv.sh 에 적는다(JDK 21)" >&2
  exit 1
fi

JAR="${APP_JAR:-$(ls -t "$BASE_DIR"/target/"$NAME"-*.jar 2>/dev/null | head -n 1 || true)}"
if [ ! -f "$JAR" ]; then
  echo "$NAME: 실행할 jar 가 없다 — 먼저 빌드한다: ./mvnw -q package" >&2
  exit 1
fi

mkdir -p "$LOG_DIR"
cd "$BASE_DIR"                       # .env 를 실행 폴더에서 읽는다(spring.config.import)
export MOAI_WORKS_LOG_FILE="$LOG_FILE"
export MOAI_WORKS_LOG_CONSOLE=OFF     # 로그는 파일에만 — .out 이 끝없이 커지지 않게
# shellcheck disable=SC2086
nohup "$JAVA" ${JAVA_OPTS:-} -jar "$JAR" > "$OUT_FILE" 2>&1 < /dev/null &
pid=$!
echo "$pid" > "$PID_FILE"

# 응답할 때까지 기다린다. 그 전에 죽으면 마지막 출력을 보이고 실패로 끝낸다.
WAIT="${STARTUP_WAIT:-90}"
for ((i = 1; i <= WAIT; i++)); do
  if ! kill -0 "$pid" 2>/dev/null; then
    rm -f "$PID_FILE"
    echo "$NAME: 뜨다가 멈췄다. 마지막 출력:" >&2
    tail -n 30 "$OUT_FILE" "$LOG_FILE" >&2 2>/dev/null || true
    exit 1
  fi
  if curl -fsS -o /dev/null --max-time 2 ${CURL_TLS[@]+"${CURL_TLS[@]}"} "$SCHEME://localhost:$PORT/actuator/health" 2>/dev/null; then
    echo "$NAME: 띄웠다 (PID $pid, $SCHEME://localhost:$PORT, ${i}초) — 로그 $LOG_FILE"
    exit 0
  fi
  sleep 1
done

echo "$NAME: ${WAIT}초 안에 응답하지 않는다 (PID $pid 는 살아 있다) — 로그 $LOG_FILE" >&2
exit 1
