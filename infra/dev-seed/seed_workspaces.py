"""로컬 개발용 계정 · 워크스페이스 시드 — 실제 API 흐름을 그대로 탄다 (여러 번 실행해도 안전).

    python3 -I infra/dev-seed/seed_workspaces.py

만드는 것
  계정       moaiworks01 ~ moaiworks10 (Keycloak, 이메일 moaiworksNN@moai-works.local)
             비밀번호는 infra/keycloak/.env 의 MOAI_WORKS_SEED_PASSWORD (없으면 만들어 거기에만 쓴다) — 처음 비밀번호다.
             이미 있는 계정은 건드리지 않으므로, 누가 비밀번호를 바꾸면 .env 의 값은 더 이상 맞지 않는다
  워크스페이스 종류마다 하나씩 (2026-11-21 사용자 결정 — 팀 워크스페이스는 퍼블릭 · 프라이빗, 퍼블릭의 가입은 승인 · 자동)
             "워크스페이스 A" — 퍼블릭 · 관리자 승인 후 가입. 소유자 moaiworks01, 관리자 02, 큐레이터 03, 멤버 04 · 05
             "워크스페이스 B" — 퍼블릭 · 자동 가입. 소유자 moaiworks06, 큐레이터 07, 멤버 08~10
             "워크스페이스 C" — 프라이빗(찾기에 안 보이고 초대로만). 소유자 moaiworks06, 관리자 07, 큐레이터 08
             팀 워크스페이스는 시스템 관리자가 허용한 계정만 만든다 — 이 스크립트가 시스템 관리자로
             moaiworks01 · moaiworks06 에게 2개씩 허용한다. 나머지 계정은 만들 수 없다(가입한 계정의 기본값)
             주소는 서버가 정한다(workspace-고유id). 다시 실행하면 소유자의 팀 워크스페이스를 이름으로 찾아 쓴다
             (모든 계정에는 가입 규칙대로 개인 워크스페이스가 하나씩 생긴다)
  프로젝트   워크스페이스마다 몇 개 (moai-wiki 시드의 위키 자리). A 의 web 에는 일감 몇 개(유형 · 상위 · 하위 · 연결 · 날짜)를,
             A 의 지정 멤버 프로젝트 sec 에는 moaiworks04 에게 프로젝트 편집자 역할을 준다

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
API = os.environ.get("MOAI_WORKS_API_URL", "https://localhost:11001/moai-works-api/v1")
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
     "roles": {2: "admin", 3: "curator"},
     "projects": [
         {"key": "web", "name": "웹 서비스", "description": "웹 서비스 개발 프로젝트", "visibility": "WORKSPACE"},
         {"key": "sec", "name": "보안 점검", "description": "소유자 · 관리자와 지정 멤버만 보는 프로젝트", "visibility": "RESTRICTED"},
     ],
     "project_roles": {"sec": {4: ["project-editor"]}}},
    {"name": "워크스페이스 B", "owner": 6, "members": [7, 8, 9, 10],
     "visibility": "PUBLIC", "joinPolicy": "AUTO",
     "description": "퍼블릭 · 자동 가입 — moaiworks06 이 소유한 팀 워크스페이스 (개발용 시드)",
     "roles": {7: "curator"},
     "projects": [
         {"key": "product", "name": "제품", "description": "제품 기능 요구사항", "visibility": "WORKSPACE"},
         {"key": "ops", "name": "운영", "description": "운영 작업과 장애 대응", "visibility": "WORKSPACE"},
     ]},
    {"name": "워크스페이스 C", "owner": 6, "members": [7, 8],
     "visibility": "PRIVATE", "joinPolicy": "APPROVAL",
     "description": "프라이빗 — 찾기에 보이지 않고 초대로만 들어온다. moaiworks06 이 소유 (개발용 시드)",
     "roles": {7: "admin", 8: "curator"},
     "projects": [
         {"key": "notes", "name": "내부 과제", "description": "초대받은 사람만 보는 과제", "visibility": "WORKSPACE"},
     ]},
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


def seed_issues(slug, token, ids):
    """워크스페이스 A 의 web 프로젝트에 일감 몇 개 — 이미 있으면 건너뛴다(다시 돌려도 안전)."""
    base = f"{API}/workspaces/{slug}/projects/web"
    s, page = http("GET", f"{base}/issues?size=1", token=token)
    assert s == 200, f"일감 목록 실패: {s} {page}"
    if page["total"] > 0:
        print(f"   일감: web 에 {page['total']}개 있음")
        return

    def create(body):
        s, b = http("POST", f"{base}/issues", token=token, body=body)
        assert s == 201, f"일감 생성 실패: {s} {b}"
        return b["number"]

    req = create({"type": "feature", "title": "회원은 이메일로 로그인한다", "description": "## 왜\n로그인 화면 하나로 들어온다.",
                  "managerId": ids[username(3)], "assigneeId": ids[username(4)], "status": "IN_PROGRESS",
                  "startDate": "2026-11-20", "plannedEndDate": "2026-12-05", "dueDate": "2026-12-10"})
    feat = create({"type": "feature", "title": "로그인 화면", "parent": req, "assigneeId": ids[username(5)],
                   "startDate": "2026-11-21", "plannedEndDate": "2026-11-28"})
    create({"type": "test", "title": "로그인 실패 메시지 확인", "parent": feat, "status": "TESTING"})
    create({"type": "research", "title": "패스키(WebAuthn) 로그인 조사", "status": "DONE"})
    bug = create({"type": "bug", "title": "비밀번호를 틀리면 화면이 멈춘다", "assigneeId": ids[username(3)],
                  "dueDate": "2026-11-25"})
    s, b = http("POST", f"{base}/issues/{bug}/links", token=token, body={"target": feat, "type": "BLOCKS"})
    assert s == 200, f"연결 실패: {s} {b}"
    s, b = http("POST", f"{base}/issues/{req}/comments", token=token, body={"notes": "이번 주 안에 화면 시안을 올리겠습니다."})
    assert s == 200, f"댓글 실패: {s} {b}"
    print(f"   일감: web 에 #{req} 요구사항 · #{feat} 기능(하위) · 작업(하위의 하위) · #{bug} 버그(연결) 를 만들었다")


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

        s, projects = http("GET", f"{API}/workspaces/{space['slug']}/projects?archived=true", token=owner)
        assert s == 200, f"프로젝트 목록 실패: {s} {projects}"
        have = {p["key"] for p in projects}
        made = []
        for project in space["projects"]:
            if project["key"] in have:
                made.append(f"{project['key']}(있음)")
                continue
            s, b = http("POST", f"{API}/workspaces/{space['slug']}/projects", token=owner, body=project)
            if s != 201:
                raise SystemExit(f"프로젝트 {project['key']} 생성 실패: {s} {b}")
            made.append(f"{project['key']}(생성)")
        print("   프로젝트:", ", ".join(made))
        for key, grants in space.get("project_roles", {}).items():
            for n, roles in grants.items():
                s, b = http("PUT", f"{API}/workspaces/{space['slug']}/projects/{key}/roles/{ids[username(n)]}",
                            token=owner, body={"roles": roles})
                assert s == 200, f"{username(n)} 프로젝트 역할({key}: {roles}) 실패: {s} {b}"
                print(f"   프로젝트 역할: {key} — {username(n)} {roles}")
        if space["name"] == "워크스페이스 A":
            seed_issues(space["slug"], owner, ids)

    print("4) 로그인 뒤 들어갈 워크스페이스:")
    for n in range(1, 11):
        s, me = http("GET", f"{API}/me", token=tokens[n])
        print(f"   {username(n)} → {me['landingWorkspace']}  (워크스페이스 {len(me['workspaces'])}개)")


if __name__ == "__main__":
    main()
