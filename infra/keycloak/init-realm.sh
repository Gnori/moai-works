#!/usr/bin/env bash
# moai-works 렐름을 만들고 클라이언트 비밀값을 .env 에 기록한다 (여러 번 실행해도 안전).
#
#   bash infra/keycloak/init-realm.sh
#
# - Keycloak 은 다른 프로젝트와 같이 쓰는 컨테이너 MOAI-Keycloak(http://localhost:8180)이다. 띄우는 정의는
#   moai-wiki 저장소의 infra/keycloak/compose.yaml 에 있다 — 여기서는 렐름 moai-works 만 다룬다.
# - 같은 폴더의 .env(git 제외)에 그 컨테이너의 관리자 계정 KC_ADMIN_USERNAME · KC_ADMIN_PASSWORD 를 먼저 넣는다.
# - 렐름이 이미 있으면 만들지 않고 비밀값만 다시 읽는다.
# - 비밀값은 Keycloak 이 만든다. 이 스크립트는 화면에 출력하지 않고 .env 에만 쓴다.
# - 렐름 정의: realm-moai-works.json (비밀값 없음)
set -euo pipefail

cd "$(dirname "$0")"
KC_URL="${KC_URL:-http://localhost:8180}"   # compose.yaml 의 KC_HOSTNAME 과 같게
REALM="moai-works"

[ -f .env ] || { echo ".env 가 없습니다 — KC_ADMIN_USERNAME · KC_ADMIN_PASSWORD 를 넣으십시오(이 파일 머리말)." >&2; exit 1; }
set -a; . ./.env; set +a

json() { python3 -I -c "import sys,json; d=json.load(sys.stdin); print($1)"; }

ADMIN_TOKEN=$(curl -sf -m 10 -d grant_type=password -d client_id=admin-cli \
  --data-urlencode "username=${KC_ADMIN_USERNAME}" --data-urlencode "password=${KC_ADMIN_PASSWORD}" \
  "${KC_URL}/realms/master/protocol/openid-connect/token" | json 'd["access_token"]')
AUTH=(-H "Authorization: Bearer ${ADMIN_TOKEN}")

code=$(curl -s -o /dev/null -w '%{http_code}' -m 10 "${AUTH[@]}" "${KC_URL}/admin/realms/${REALM}")
if [ "$code" = "404" ]; then
  curl -sf -m 30 "${AUTH[@]}" -H 'Content-Type: application/json' \
    --data-binary @realm-moai-works.json "${KC_URL}/admin/realms" >/dev/null
  echo "렐름 ${REALM} 생성"
else
  # 렐름 정의에 새로 더한 클라이언트만 넣는다 — 이미 있는 것은 건너뛴다(비밀값 · 설정 유지).
  added=$(python3 -I -c 'import json,sys; d=json.load(open("realm-moai-works.json")); d["ifResourceExists"]="SKIP"; json.dump(d, sys.stdout)' \
    | curl -sf -m 30 "${AUTH[@]}" -H 'Content-Type: application/json' --data-binary @- \
      "${KC_URL}/admin/realms/${REALM}/partialImport" | json 'd.get("added", 0)')
  echo "렐름 ${REALM} 이미 있음 — 정의에서 새로 추가된 항목 ${added}개"
fi

secret_of() {
  local cid="$1" id
  id=$(curl -sf -m 10 "${AUTH[@]}" "${KC_URL}/admin/realms/${REALM}/clients?clientId=${cid}" | json 'd[0]["id"]')
  curl -sf -m 10 "${AUTH[@]}" "${KC_URL}/admin/realms/${REALM}/clients/${id}/client-secret" | json 'd["value"]'
}

set_env() {  # .env 에서 KEY 를 지운다 (이름이 바뀐 값)
  local key="$1" tmp
  tmp=$(mktemp)
  grep -v "^${key}=" .env > "$tmp" || true
  cat "$tmp" > .env && rm -f "$tmp"
}

set_env() {  # .env 의 KEY=값 을 바꾸거나 더한다
  local key="$1" val="$2" tmp
  tmp=$(mktemp)
  grep -v "^${key}=" .env > "$tmp" || true
  printf '%s=%s\n' "$key" "$val" >> "$tmp"
  cat "$tmp" > .env && rm -f "$tmp"
}

set_env MOAI_WORKS_KEYCLOAK_URL "${KC_URL}"
set_env MOAI_WORKS_KEYCLOAK_REALM "${REALM}"
set_env MOAI_WORKS_API_CLIENT_ID "moai-works-api"
set_env MOAI_WORKS_API_CLIENT_SECRET "$(secret_of moai-works-api)"
set_env MOAI_WORKS_WEB_BACKEND_CLIENT_ID "moai-works-web-backend"
set_env MOAI_WORKS_WEB_BACKEND_CLIENT_SECRET "$(secret_of moai-works-web-backend)"
echo "클라이언트 비밀값을 .env 에 기록 (api, web-backend)"

# 렐름 설정 · 역할 · 권한 코드 · 처음 시스템 관리자를 맞춘다 (부분 가져오기로는 고칠 수 없는 것들)
KC_URL="${KC_URL}" python3 -I ensure-realm.py
