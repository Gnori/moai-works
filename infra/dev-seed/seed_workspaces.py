"""로컬 개발용 계정 · 워크스페이스 시드 — 실제 API 흐름을 그대로 탄다 (여러 번 실행해도 안전).

    python3 -I infra/dev-seed/seed_workspaces.py

만드는 것
  계정       moaiworks01 ~ moaiworks10 (Keycloak, 이메일 moaiworksNN@moai-works.local)
             비밀번호는 infra/keycloak/.env 의 MOAI_WORKS_SEED_PASSWORD (없으면 만들어 거기에만 쓴다)
  워크스페이스 종류마다 하나씩 (2026-11-21 사용자 결정 — 팀 워크스페이스는 퍼블릭 · 프라이빗, 퍼블릭의 가입은 승인 · 자동)
             "워크스페이스 A" — 퍼블릭 · 관리자 승인 후 가입. 소유자 moaiworks01, 관리자 02, 멤버 03 ~ 05
             "워크스페이스 B" — 퍼블릭 · 자동 가입. 소유자 moaiworks06, 멤버 07 ~ 10
             "워크스페이스 C" — 프라이빗(찾기에 안 보이고 초대로만). 소유자 moaiworks06, 관리자 07, 멤버 08
             팀 워크스페이스는 시스템 관리자가 허용한 계정만 만든다 — 이 스크립트가 시스템 관리자로
             moaiworks01 · moaiworks06 에게 2개씩 허용한다. 나머지 계정은 만들 수 없다(가입한 계정의 기본값)
             주소는 서버가 정한다(workspace-고유id). 다시 실행하면 소유자의 팀 워크스페이스를 이름으로 찾아 쓴다
             (모든 계정에는 가입 규칙대로 개인 워크스페이스가 하나씩 생긴다)
  프로젝트   아직 없다 — 프로젝트 기능을 만들면 여기에 시드를 더한다

흐름 (사람이 화면에서 하는 것과 같은 API)
  1. Keycloak 에 계정 생성 (관리자 콘솔에서 만든 계정과 같은 경우)
  2. 각자 로그인(패스워드 그랜트, 클라이언트 moai-works-web-backend) → api POST /v1/me/login (계정 사본 + 개인 워크스페이스)
  3. 01 · 06 이 팀 워크스페이스를 만든다 (POST /v1/workspaces) → 소유자
  4. 나머지가 가입 신청 (POST /v1/workspaces/{ws}/join-requests) → 소유자가 승인(member 역할)
  5. 소유자가 몇 사람의 역할을 바꾼다 (PUT …/members/{id}/roles) — 역할마다 화면 · 권한을 볼 수 있게

비밀값은 화면에 출력하지 않는다.
"""
import json
import os
import secrets
import sys
import ssl
import urllib.error
import urllib.parse
import urllib.request
from typing import Any

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KC_ENV = os.path.join(ROOT, "keycloak", ".env")
KC = os.environ.get("KC_URL", "http://localhost:8180")
API = os.environ.get("MOAI_WORKS_API_URL", "https://localhost:10011/moai-works-api/v1")
# api 는 HTTPS 다 — 로컬 인증서를 발급한 인증 기관(infra/tls/make-certs.sh)을 공인 인증 기관에 더해 믿는다
CA = os.environ.get("MOAI_WORKS_TLS_CA", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "tls", "certs", "ca.pem"))
TLS = ssl.create_default_context()
if CA and os.path.isfile(CA):
    TLS.load_verify_locations(cafile=CA)
REALM = "moai-works"

SPACES = [
    {"name": "워크스페이스 A", "owner": 1, "members": [2, 3, 4, 5],
     "visibility": "PUBLIC", "joinPolicy": "APPROVAL",
     "description": "퍼블릭 · 관리자 승인 후 가입 — moaiworks01 이 소유한 팀 워크스페이스 (개발용 시드)",
     "roles": {2: "admin"}},
    {"name": "워크스페이스 B", "owner": 6, "members": [7, 8, 9, 10],
     "visibility": "PUBLIC", "joinPolicy": "AUTO",
     "description": "퍼블릭 · 자동 가입 — moaiworks06 이 소유한 팀 워크스페이스 (개발용 시드)",
     "roles": {}},
    {"name": "워크스페이스 C", "owner": 6, "members": [7, 8],
     "visibility": "PRIVATE", "joinPolicy": "APPROVAL",
     "description": "프라이빗 — 찾기에 보이지 않고 초대로만 들어온다. moaiworks06 이 소유 (개발용 시드)",
     "roles": {7: "admin"}},
]

# 팀 워크스페이스를 만들 수 있는 계정과 개수 — 시스템 관리자가 허용한다. 나머지 계정은 0(만들 수 없음)
TEAM_WORKSPACE_LIMITS = {1: 2, 6: 2}


def username(n):
    return f"moaiworks{n:02d}"


def read_env():
    env = {}
    with open(KC_ENV, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                env[k] = v
    return env


def append_env(key, value):
    with open(KC_ENV, "a", encoding="utf-8") as f:
        f.write(f"{key}={value}\n")
    os.chmod(KC_ENV, 0o600)


def http(method, url, body=None, token=None, form=None) -> tuple[int, Any]:
    headers = {}
    data = None
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if form is not None:
        data = urllib.parse.urlencode(form).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    elif body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=60, context=TLS) as r:
            raw = r.read().decode().strip()
            return r.status, (json.loads(raw) if raw[:1] in ("{", "[") else raw)
    except urllib.error.HTTPError as e:
        raw = e.read().decode().strip()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, raw


def admin_token(env):
    s, b = http("POST", f"{KC}/realms/master/protocol/openid-connect/token", form={
        "grant_type": "password", "client_id": "admin-cli",
        "username": env["KC_ADMIN_USERNAME"], "password": env["KC_ADMIN_PASSWORD"]})
    assert s == 200, f"Keycloak 관리자 토큰 실패: {s}"
    return b["access_token"]


def user_token(env, name, password):
    s, b = http("POST", f"{KC}/realms/{REALM}/protocol/openid-connect/token", form={
        "grant_type": "password", "client_id": env["MOAI_WORKS_WEB_BACKEND_CLIENT_ID"],
        "client_secret": env["MOAI_WORKS_WEB_BACKEND_CLIENT_SECRET"], "username": name, "password": password,
        "scope": "openid email profile"})
    assert s == 200, f"{name} 로그인 실패: {s} {b}"
    return b["access_token"]


def ensure_user(admin, n, password):
    name = username(n)
    s, found = http("GET", f"{KC}/admin/realms/{REALM}/users?username={name}&exact=true", token=admin)
    if found:
        return "있음"
    s, b = http("POST", f"{KC}/admin/realms/{REALM}/users", token=admin, body={
        "username": name, "email": f"{name}@moai-works.local", "emailVerified": True, "enabled": True,
        "firstName": name, "attributes": {"moai.origin": ["SEED"]},
        "credentials": [{"type": "password", "value": password, "temporary": False}]})
    assert s == 201, f"{name} 생성 실패: {s} {b}"
    return "생성"


def main():
    env = read_env()
    password = env.get("MOAI_WORKS_SEED_PASSWORD")
    if not password:
        password = "Moai!" + secrets.token_urlsafe(9)
        append_env("MOAI_WORKS_SEED_PASSWORD", password)
        print("시드 비밀번호를 만들어 infra/keycloak/.env 의 MOAI_WORKS_SEED_PASSWORD 에 기록")

    admin = admin_token(env)
    print("1) 계정:", ", ".join(f"{username(n)}({ensure_user(admin, n, password)})" for n in range(1, 11)))

    tokens = {n: user_token(env, username(n), password) for n in range(1, 11)}
    for n in range(1, 11):
        s, me = http("POST", f"{API}/me/login", token=tokens[n])
        assert s == 200, f"{username(n)} /me/login 실패: {s} {me}"
    print("2) 로그인 · 개인 워크스페이스: 10명 완료")
    if not env.get("MOAI_WORKS_SYSADMIN_PASSWORD"):
        raise SystemExit("시스템 관리자 계정이 필요하다 — infra/keycloak/.env 의 MOAI_WORKS_SYSADMIN_EMAIL · _PASSWORD")
    sysadmin = user_token(env, env["MOAI_WORKS_SYSADMIN_EMAIL"], env["MOAI_WORKS_SYSADMIN_PASSWORD"])
    s, _ = http("POST", f"{API}/me/login", token=sysadmin)
    print("   시스템 관리자 로그인:", "완료" if s == 200 else s)

    # 팀 워크스페이스는 시스템 관리자가 허용한 계정만 만든다 — 계정마다 개수를 정한다
    allowed = []
    for n, limit in TEAM_WORKSPACE_LIMITS.items():
        s, me = http("GET", f"{API}/me", token=tokens[n])
        assert s == 200, f"{username(n)} /me 실패: {s} {me}"
        if me["teamWorkspaceLimit"] != limit:
            s, b = http("PUT", f"{API}/admin/accounts/{me['id']}/team-workspace-limit", token=sysadmin, body={"limit": limit})
            if s != 200:
                raise SystemExit(f"{username(n)} 팀 워크스페이스 허용 실패: {s} {b}")
        allowed.append(f"{username(n)}({limit}개)")
    print("   팀 워크스페이스 만들기 허용:", ", ".join(allowed), "— 나머지 계정은 만들 수 없다")

    for space in SPACES:
        owner = tokens[space["owner"]]
        s, mine = http("GET", f"{API}/me/workspaces", token=owner)
        existing = [w for w in mine if w["kind"] == "TEAM" and w["name"] == space["name"]]
        if existing:
            space["slug"], state = existing[0]["slug"], "있음"
        else:
            s, b = http("POST", f"{API}/workspaces", token=owner, body={
                "name": space["name"], "description": space["description"],
                "visibility": space["visibility"], "joinPolicy": space["joinPolicy"]})
            if s != 201:
                raise SystemExit(f"{space['name']} 생성 실패: {s} {b}")
            space["slug"], state = b["slug"], "생성"

        # 종류(퍼블릭 · 프라이빗) · 가입 방식 · 설명을 정한 대로 맞춘다
        s, now = http("GET", f"{API}/workspaces/{space['slug']}", token=owner)
        assert s == 200, f"{space['name']} 읽기 실패: {s} {now}"
        want = {"visibility": space["visibility"], "joinPolicy": space["joinPolicy"], "description": space["description"]}
        if any(now.get(k) != v for k, v in want.items()):
            s, b = http("PATCH", f"{API}/workspaces/{space['slug']}", token=owner, body={
                "name": space["name"], **want, "allowedJoinDomains": now.get("allowedJoinDomains") or [],
                "defaultRoleKey": now["defaultRoleKey"]})
            if s != 200:
                raise SystemExit(f"{space['name']} 설정 맞추기 실패: {s} {b}")
            state += " · 설정 맞춤"
        kind = "프라이빗" if space["visibility"] == "PRIVATE" else (
            "퍼블릭 · 자동 가입" if space["joinPolicy"] == "AUTO" else "퍼블릭 · 승인 후 가입")
        print(f"3) {space['name']} ({kind}) /w/{space['slug']} — 소유자 {username(space['owner'])}: {state}")

        s, members = http("GET", f"{API}/workspaces/{space['slug']}/members", token=owner)
        assert s == 200, f"멤버 목록 실패: {s} {members}"
        joined = {m["username"] for m in members}
        for n in space["members"]:
            if username(n) in joined:
                continue
            if space["visibility"] == "PRIVATE":
                # 프라이빗은 가입 신청을 받지 않는다 — 소유자가 초대하고 받은 사람이 수락한다
                s, b = http("POST", f"{API}/workspaces/{space['slug']}/invitations", token=owner, body={
                    "email": f"{username(n)}@moai-works.local", "roleKey": "member", "message": "시드 초대"})
                if s != 201:
                    raise SystemExit(f"{username(n)} 초대 실패: {s} {b}")
                token = b["inviteUrl"].rstrip("/").rsplit("/", 1)[-1]
                s, b = http("POST", f"{API}/invitations/{token}/accept", token=tokens[n])
                if s != 200:
                    raise SystemExit(f"{username(n)} 초대 수락 실패: {s} {b}")
            else:
                # 퍼블릭: 자동 가입이면 신청하는 순간 멤버가 되고, 승인 후 가입이면 아래에서 소유자가 승인한다
                s, b = http("POST", f"{API}/workspaces/{space['slug']}/join-requests", token=tokens[n],
                            body={"message": f"{username(n)} 가입 신청 (시드)"})
                if s not in (201, 409):
                    raise SystemExit(f"{username(n)} → {space['slug']} 신청 실패: {s} {b}")
        if space["visibility"] == "PUBLIC" and space["joinPolicy"] == "APPROVAL":
            s, pending = http("GET", f"{API}/workspaces/{space['slug']}/join-requests", token=owner)
            assert s == 200, f"대기 목록 실패: {s} {pending}"
            for r in pending:
                s, b = http("POST", f"{API}/workspaces/{space['slug']}/join-requests/{r['id']}/approve", token=owner,
                            body={"roleKey": "member", "note": "시드 승인"})
                assert s == 200, f"승인 실패: {s} {b}"
        s, members = http("GET", f"{API}/workspaces/{space['slug']}/members", token=owner)
        assert s == 200
        ids = {m["username"]: m["userId"] for m in members}
        for n, role in space["roles"].items():
            s, b = http("PUT", f"{API}/workspaces/{space['slug']}/members/{ids[username(n)]}/roles", token=owner,
                        body={"roles": [role]})
            assert s == 200, f"{username(n)} 역할({role}) 지정 실패: {s} {b}"
        s, members = http("GET", f"{API}/workspaces/{space['slug']}/members", token=owner)
        assert s == 200
        print("   멤버:", ", ".join(f"{m['username']}[{'/'.join(m['roles'])}]" for m in members))

    print("4) 로그인 뒤 들어갈 워크스페이스:")
    for n in range(1, 11):
        s, me = http("GET", f"{API}/me", token=tokens[n])
        print(f"   {username(n)} → {me['landingWorkspace']}  (워크스페이스 {len(me['workspaces'])}개)")


if __name__ == "__main__":
    main()
