#!/usr/bin/env bash
# moai-works 용 MinIO 버킷 · 사용자 · 정책 (기존 MOAI-MinIO 컨테이너를 같이 쓴다 — 여러 번 실행해도 안전)
#
#   bash infra/minio/init-minio.sh
#
# - 버킷: moai-works-attachments(첨부 파일 — 일감 · 위키 · 문서) — 버전 보관 켬(실수 대비)
# - 사용자(최소 권한) — 객체를 쓰고 지우는 것은 api 뿐이다:
#     moai-works-api : attachments 쓰기 · 읽기 · 목록 · 삭제
# - 사용자 비밀값은 infra/minio/.env 에 쓰고, src/moai-works-api/.env 가 있으면 거기에도 옮겨 적는다(값은 화면에 내지 않는다)
# - MinIO 관리자 계정은 컨테이너 안에서만 쓴다(화면 · 파일에 남기지 않는다).
# - 사용자 비밀값은 처음 만들 때만 정해 .env(git 제외)에 쓴다. 이미 있는 사용자는 비밀값을 바꾸지 않는다.
# - ⚠️ 이 Mac 은 Docker 안에서 이미지를 받지 않는다 — 컨테이너에 들어 있는 mc 만 쓴다.
# - moai-wiki 의 같은 스크립트에서 가져왔다(2026-11-21). 첨부 기능을 만들 때 돌리면 된다.
set -euo pipefail
cd "$(dirname "$0")"
C="${MINIO_CONTAINER:-MOAI-MinIO}"
ENDPOINT="${MINIO_ENDPOINT:-http://localhost:9000}"
BUCKET="moai-works-attachments"

mcx() {  # 컨테이너 안에서 관리자 alias 로 mc 실행
  docker exec -i "$C" sh -c 'mc alias set worksadmin http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null && mc "$@"' mc "$@"
}

mcx mb --ignore-existing "worksadmin/$BUCKET" >/dev/null
mcx version enable "worksadmin/$BUCKET" >/dev/null
echo "버킷 $BUCKET — 버전 보관: $(mcx version info "worksadmin/$BUCKET" --json | python3 -I -c 'import sys,json;print(json.load(sys.stdin).get("versioning",{}).get("status"))')"

policy_api='{"Version":"2012-10-17","Statement":[
 {"Effect":"Allow","Action":["s3:ListBucket","s3:ListBucketVersions","s3:GetBucketLocation"],"Resource":["arn:aws:s3:::moai-works-attachments"]},
 {"Effect":"Allow","Action":["s3:PutObject","s3:GetObject","s3:GetObjectVersion","s3:DeleteObject","s3:DeleteObjectVersion"],"Resource":["arn:aws:s3:::moai-works-attachments/*"]}]}'

touch .env && chmod 600 .env
set_env() {
  local key="$1" val="$2" tmp
  tmp=$(mktemp)
  grep -v "^${key}=" .env > "$tmp" || true
  printf '%s=%s\n' "$key" "$val" >> "$tmp"
  cat "$tmp" > .env && rm -f "$tmp"
}
set_env MOAI_WORKS_S3_ENDPOINT "$ENDPOINT"
set_env MOAI_WORKS_S3_ATTACHMENT_BUCKET "$BUCKET"

ensure_user() {  # 사용자명 · 정책 JSON · .env 키 접두사
  local user="$1" policy="$2" prefix="$3" secret
  printf '%s' "$policy" | docker exec -i "$C" sh -c "cat > /tmp/${user}.json"
  mcx admin policy create worksadmin "$user" "/tmp/${user}.json" >/dev/null
  docker exec "$C" rm -f "/tmp/${user}.json"
  if mcx admin user info worksadmin "$user" >/dev/null 2>&1; then
    echo "사용자 $user 이미 있음 — 비밀값 유지, 정책만 갱신"
  else
    secret=$(openssl rand -hex 24)
    printf '%s' "$secret" | docker exec -i "$C" sh -c 'read -r s; mc alias set worksadmin http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null && mc admin user add worksadmin "$0" "$s" >/dev/null' "$user"
    set_env "${prefix}_S3_ACCESS_KEY" "$user"
    set_env "${prefix}_S3_SECRET_KEY" "$secret"
    echo "사용자 $user 생성 — 비밀값은 .env"
  fi
  mcx admin policy attach worksadmin "$user" --user "$user" >/dev/null 2>&1 || true
  echo "  정책: $(mcx admin user info worksadmin "$user" --json | python3 -I -c 'import sys,json;print(json.load(sys.stdin).get("policyName"))')"
}
ensure_user moai-works-api "$policy_api" MOAI_WORKS_API

# api 모듈 폴더의 .env 에 api 가 쓰는 값만 옮겨 적는다 — 값을 화면에 내지 않는다. .env 가 없으면 건너뛴다
file="../../src/moai-works-api/.env"
if [ -f "$file" ]; then
  for key in MOAI_WORKS_S3_ENDPOINT MOAI_WORKS_S3_ATTACHMENT_BUCKET MOAI_WORKS_API_S3_ACCESS_KEY MOAI_WORKS_API_S3_SECRET_KEY; do
    val=$(grep "^${key}=" .env | head -n 1 | cut -d= -f2-)
    [ -n "$val" ] || continue
    tmp=$(mktemp)
    grep -v "^${key}=" "$file" > "$tmp" || true
    printf '%s=%s\n' "$key" "$val" >> "$tmp"
    cat "$tmp" > "$file" && rm -f "$tmp"
  done
  chmod 600 "$file"
  echo "src/moai-works-api/.env — 맞춤"
else
  echo "(src/moai-works-api/.env 없음 — 건너뜀)"
fi
