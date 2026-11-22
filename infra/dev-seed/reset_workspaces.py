"""로컬 개발용 — 워크스페이스를 모두 지운다 (계정은 남긴다). 그다음 seed_workspaces.py 로 다시 만든다.

    python3 -I infra/dev-seed/reset_workspaces.py --yes
    python3 -I infra/dev-seed/seed_workspaces.py

언제 쓰나: 워크스페이스를 통째로 다시 만들어야 할 때(2026-11-19: 주소 space-a → space-고유id). Keycloak 조직 alias 는
바꿀 수 없어서(26.8 실측) 지우고 같은 구성으로 다시 만든다.

지우는 것
  Keycloak  moai-works 가 만든 조직(속성 moai.workspaceId 가 있는 것)
  DB        워크스페이스 · 역할 · 역할 권한 · 멤버십 · 멤버 역할 · 가입 신청 · 초대 · 프로젝트 · 프로젝트 역할 · 일감(이력 · 연결),
            계정의 개인/마지막 워크스페이스 연결
남기는 것
  계정(Keycloak 사용자 · user_profile) · 가입 정책 · 감사 기록 · Keycloak 쓰기 기록(이력)

운영 데이터에는 쓰지 않는다 — 로컬 Keycloak(localhost) · 로컬 DB 만 허용한다.
"""
import json
import os
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from typing import Any

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
KC_ENV = os.path.join(ROOT, "infra", "keycloak", ".env")
API_ENV = os.path.join(ROOT, "src", "moai-works-api", ".env")
KC = "http://localhost:8180"
REALM = "moai-works"


def env(path):
    out = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                out[k] = v
    return out


def http(method, url, token=None, form=None) -> tuple[int, Any]:
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    data = None
    if form is not None:
        data = urllib.parse.urlencode(form).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read().decode().strip()
            return r.status, (json.loads(raw) if raw[:1] in ("{", "[") else raw)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:200]


def main():
    if "--yes" not in sys.argv:
        raise SystemExit("워크스페이스를 모두 지웁니다. 계속하려면 --yes 를 붙이십시오.")
    kc = env(KC_ENV)
    db = env(API_ENV)
    url = db.get("MOAI_WORKS_DB_URL", "jdbc:postgresql://localhost:5432/moai_works")
    if "localhost" not in url and "127.0.0.1" not in url:
        raise SystemExit(f"로컬 DB 가 아닙니다: {url}")

    s, body = http("POST", f"{KC}/realms/master/protocol/openid-connect/token", form={
        "grant_type": "password", "client_id": "admin-cli",
        "username": kc["KC_ADMIN_USERNAME"], "password": kc["KC_ADMIN_PASSWORD"]})
    assert s == 200, f"Keycloak 관리자 토큰 실패: {s}"
    token = body["access_token"]
    s, orgs = http("GET", f"{KC}/admin/realms/{REALM}/organizations?max=10000&briefRepresentation=false", token)
    assert s == 200, f"조직 목록 실패: {s} {orgs}"
    ours = [o for o in orgs if "moai.workspaceId" in (o.get("attributes") or {})]
    for o in ours:
        s, b = http("DELETE", f"{KC}/admin/realms/{REALM}/organizations/{o['id']}", token)
        assert s in (204, 404), f"조직 {o.get('alias')} 삭제 실패: {s} {b}"
    print(f"Keycloak: moai-works 조직 {len(ours)}개 삭제 ({', '.join(o.get('alias', '?') for o in ours)})")

    rest = url.split("//", 1)[1]
    hostport, dbname = rest.split("/", 1)
    host, _, port = hostport.partition(":")
    sql = """
    BEGIN;
    UPDATE user_profile SET personal_workspace_id = NULL, last_workspace_id = NULL;
    DO $$ BEGIN
      IF to_regclass('invitation') IS NOT NULL THEN DELETE FROM invitation; END IF;
    END $$;
    DO $$ BEGIN
      IF to_regclass('project') IS NOT NULL THEN
        DELETE FROM issue_journal; DELETE FROM issue_link; DELETE FROM issue; DELETE FROM project_role_binding;
        DELETE FROM project;
      END IF;
    END $$;
    DELETE FROM join_request;
    DELETE FROM member_role;
    DELETE FROM workspace_member;
    DELETE FROM role_permission;
    DELETE FROM workspace_role;
    DELETE FROM workspace;
    COMMIT;
    SELECT 'workspaces left: ' || count(*) FROM workspace;
    """
    proc = subprocess.run(["psql", "-h", host, "-p", port or "5432", "-U", db["MOAI_WORKS_DB_USERNAME"], "-d",
                           dbname.split("?")[0], "-v", "ON_ERROR_STOP=1", "-At", "-c", sql],
                          env={**os.environ, "PGPASSWORD": db["MOAI_WORKS_DB_PASSWORD"]},
                          capture_output=True, text=True)
    if proc.returncode != 0:
        raise SystemExit(f"DB 초기화 실패: {proc.stderr.strip()}")
    print("DB:", proc.stdout.strip().splitlines()[-1])


if __name__ == "__main__":
    main()
