"""moai-works 렐름의 설정을 맞춘다 — 여러 번 실행해도 안전(있으면 건너뛰고, 다르면 고친다).

    python3 -I infra/keycloak/ensure-realm.py                 (init-realm.sh 가 끝에 부른다)

realm-moai-works.json 의 부분 가져오기는 이미 있는 항목을 고치지 못한다. 그래서 렐름 설정 · 역할 · 권한처럼
나중에 바뀌는 것은 이 스크립트가 맞춘다.

  1. 렐름: 조직(Organizations) 켬, 무차별 대입 잠금, 쓴 갱신 토큰 무효화 안 함(web-backend 여러 인스턴스의 동시 갱신)
  2. 사용자 프로필: firstName · lastName 필수 해제 (없으면 패스워드 그랜트가 "Account is not fully set up" 으로 막힘)
  3. 렐름 역할: system-admin · security-officer          ← permission-catalog.json
  4. 권한 코드: 클라이언트 moai-works-api 의 클라이언트 역할   ← permission-catalog.json
  5. api 서비스 계정: 클라이언트 · 조직 관리에 필요한 realm-management 역할
     + api 클라이언트의 패스워드 그랜트(비밀번호 변경 때 "지금 비밀번호" 확인 전용, 2026-11-24 — FR-WSP-006)
  0. 클라이언트 이름 바꾸기(--rename-only): moai-wiki 에서 가져온 장치. 지금은 바꿀 이름이 없다(RENAMED_CLIENTS 가 비었다)
  6. web-backend 클라이언트: 자기 audience · 렐름 역할을 토큰에 싣도록 범위 매핑 (fullScopeAllowed=false 이므로)
  7. 처음 시스템 관리자 계정 (없을 때만 만들고, 비밀번호는 .env 에만 쓴다 — 처음 비밀번호다. 이미 있는 계정의 비밀번호는 바꾸지 않는다)

비밀값은 화면에 출력하지 않는다.
"""
import json
import os
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request
from typing import Any

HERE = os.path.dirname(os.path.abspath(__file__))
ENV_PATH = os.path.join(HERE, ".env")
KC = os.environ.get("KC_URL", "http://localhost:8180")
REALM = "moai-works"
API_CLIENT = "moai-works-api"
WEB_CLIENT = "moai-works-web-backend"
#: 예전 이름 → 지금 이름 (Keycloak 클라이언트는 쓰는 모듈 이름과 같게: moai-works-api · moai-works-web-backend)
RENAMED_CLIENTS: dict[str, str] = {}
AUDIENCE_MAPPER = "oidc-audience-mapper"
API_SA_ROLES = ["manage-realm", "manage-users", "query-groups", "query-users", "view-realm", "view-users",
                "view-clients", "query-clients"]


def read_env():
    env = {}
    with open(ENV_PATH, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                env[k] = v
    return env


def set_env(key, value):
    lines = []
    with open(ENV_PATH, encoding="utf-8") as f:
        lines = [l for l in f.read().splitlines() if not l.startswith(key + "=")]
    lines.append(f"{key}={value}")
    with open(ENV_PATH, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    os.chmod(ENV_PATH, 0o600)


class Admin:
    def __init__(self, env):
        form = urllib.parse.urlencode({"grant_type": "password", "client_id": "admin-cli",
                                       "username": env["KC_ADMIN_USERNAME"],
                                       "password": env["KC_ADMIN_PASSWORD"]}).encode()
        req = urllib.request.Request(f"{KC}/realms/master/protocol/openid-connect/token", data=form)
        self.token = json.load(urllib.request.urlopen(req, timeout=20))["access_token"]

    def call(self, method, path, body=None, ok=(200, 201, 204)) -> tuple[int, Any, dict]:
        data = None if body is None else json.dumps(body).encode()
        headers = {"Authorization": f"Bearer {self.token}"}
        if data is not None:
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(f"{KC}/admin/realms/{REALM}{path}", data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                raw = r.read().decode().strip()
                return r.status, (json.loads(raw) if raw[:1] in ("{", "[") else raw), dict(r.headers)
        except urllib.error.HTTPError as e:
            if e.code in ok:
                return e.code, None, dict(e.headers)
            raise SystemExit(f"Keycloak {method} {path} → {e.code} {e.read().decode()[:300]}")

    def get(self, path) -> Any:
        return self.call("GET", path)[1]


def client_uuid(admin, client_id):
    found = admin.get(f"/clients?clientId={urllib.parse.quote(client_id)}")
    if not found:
        raise SystemExit(f"클라이언트 {client_id} 가 없습니다 — init-realm.sh 를 먼저 실행하십시오.")
    return found[0]["id"]


def rename_clients(admin) -> list[str]:
    """예전 이름의 클라이언트를 지금 이름으로 — 내부 id · 비밀값 · 매퍼 · 범위 매핑은 그대로."""
    changes = []
    for old, new in RENAMED_CLIENTS.items():
        found = admin.get(f"/clients?clientId={urllib.parse.quote(old)}")
        if not found:
            continue
        if admin.get(f"/clients?clientId={urllib.parse.quote(new)}"):
            raise SystemExit(f"클라이언트 {old} 와 {new} 가 둘 다 있습니다 — 어느 쪽을 쓰는지 확인한 뒤 하나를 지우십시오.")
        client = admin.get(f"/clients/{found[0]['id']}")
        client.update(clientId=new, name=new)
        admin.call("PUT", f"/clients/{client['id']}", client)
        changes.append(f"클라이언트 이름 {old} → {new}")
    return changes


def ensure_self_audience(admin, client_id) -> list[str]:
    """클라이언트가 자기 토큰의 aud 에 들어가게 한다. 예전 이름을 가리키는 audience 매퍼는 지금 이름으로 고친다."""
    uuid = client_uuid(admin, client_id)
    changes = []
    mappers = [m for m in admin.get(f"/clients/{uuid}/protocol-mappers/models") if m["protocolMapper"] == AUDIENCE_MAPPER]
    for m in mappers:
        target = m["config"].get("included.client.audience")
        if RENAMED_CLIENTS.get(target) == client_id:
            m["name"] = f"audience {client_id}"
            m["config"]["included.client.audience"] = client_id
            admin.call("PUT", f"/clients/{uuid}/protocol-mappers/models/{m['id']}", m)
            changes.append(f"{client_id} audience 매퍼 이름 고침")
    if not any(m["config"].get("included.client.audience") == client_id for m in mappers):
        admin.call("POST", f"/clients/{uuid}/protocol-mappers/models", {
            "name": f"audience {client_id}", "protocol": "openid-connect", "protocolMapper": AUDIENCE_MAPPER,
            "config": {"included.client.audience": client_id, "access.token.claim": "true",
                       "introspection.token.claim": "true", "id.token.claim": "false"}})
        changes.append(f"{client_id} audience 매퍼 추가")
    return changes


def main():
    env = read_env()
    catalog = json.load(open(os.path.join(HERE, "permission-catalog.json"), encoding="utf-8"))
    admin = Admin(env)
    changes = rename_clients(admin)
    if "--rename-only" in sys.argv[1:]:
        print("클라이언트 이름: " + ("; ".join(changes) if changes else "바꿀 것 없음"))
        return

    # 1. 렐름 설정
    realm = admin.call("GET", "")[1]
    want = {"organizationsEnabled": True, "bruteForceProtected": True, "failureFactor": 10,
            "permanentLockout": False, "maxFailureWaitSeconds": 900, "registrationAllowed": False,
            # web-backend 세션은 Redis 로 여러 인스턴스가 같이 본다 — 같은 세션을 동시에 갱신해도 둘 다 유효해야 한다
            "revokeRefreshToken": False}
    diff = {k: v for k, v in want.items() if realm.get(k) != v}
    if diff:
        admin.call("PUT", "", {**realm, **diff})
        changes.append(f"렐름 설정 {sorted(diff)}")

    # 2. 사용자 프로필 — 이름 필수 해제
    profile = admin.get("/users/profile")
    touched = False
    for attr in profile["attributes"]:
        if attr["name"] in ("firstName", "lastName") and attr.get("required"):
            attr.pop("required")
            touched = True
    if touched:
        admin.call("PUT", "/users/profile", profile)
        changes.append("사용자 프로필: firstName · lastName 필수 해제")

    # 3. 렐름 역할
    existing = {r["name"] for r in admin.get("/roles")}
    for role in catalog["realmRoles"]:
        if role["name"] not in existing:
            admin.call("POST", "/roles", {"name": role["name"], "description": role["description"]})
            changes.append(f"렐름 역할 {role['name']}")

    # 4. 권한 코드 = moai-works-api 클라이언트 역할
    api_uuid = client_uuid(admin, API_CLIENT)
    existing = {r["name"]: r for r in admin.get(f"/clients/{api_uuid}/roles?briefRepresentation=false")}
    added = 0
    for perm in catalog["permissions"]:
        if perm["code"] not in existing:
            admin.call("POST", f"/clients/{api_uuid}/roles", {"name": perm["code"], "description": perm["description"]})
            added += 1
    if added:
        changes.append(f"권한 코드 {added}개")
    unknown = sorted(set(existing) - {p["code"] for p in catalog["permissions"]})
    if unknown:
        print(f"주의: 카탈로그에 없는 클라이언트 역할 {unknown} (지우지 않음)")

    # 5. api 서비스 계정 — realm-management 역할
    rm_uuid = client_uuid(admin, "realm-management")
    sa = admin.get(f"/clients/{api_uuid}/service-account-user")
    have = {r["name"] for r in admin.get(f"/users/{sa['id']}/role-mappings/clients/{rm_uuid}")}
    missing = [n for n in API_SA_ROLES if n not in have]
    if missing:
        reps = [admin.get(f"/clients/{rm_uuid}/roles/{n}") for n in missing]
        admin.call("POST", f"/users/{sa['id']}/role-mappings/clients/{rm_uuid}", reps)
        changes.append(f"api 서비스 계정 역할 {missing}")
    # 서비스 계정 토큰에 그 역할이 실리도록 범위 매핑 (fullScopeAllowed=false)
    have = {r["name"] for r in admin.get(f"/clients/{api_uuid}/scope-mappings/clients/{rm_uuid}")}
    missing = [n for n in API_SA_ROLES if n not in have]
    if missing:
        reps = [admin.get(f"/clients/{rm_uuid}/roles/{n}") for n in missing]
        admin.call("POST", f"/clients/{api_uuid}/scope-mappings/clients/{rm_uuid}", reps)
        changes.append(f"api 범위 매핑 {missing}")

    # 5-1. api 클라이언트 패스워드 그랜트 — 비밀번호 변경의 "지금 비밀번호" 확인(KeycloakAdminClient.checkPassword).
    #      Keycloak 관리 API 에는 비밀번호를 확인하는 입구가 없다. 서비스 계정이 이미 비밀번호를 정할 수 있으므로(manage-users)
    #      이것을 켜도 api 비밀값이 할 수 있는 일은 늘지 않는다. 받은 토큰은 api 가 바로 버리고 그 세션을 끝낸다
    api_client = admin.get(f"/clients/{api_uuid}")
    if not api_client.get("directAccessGrantsEnabled"):
        api_client["directAccessGrantsEnabled"] = True
        admin.call("PUT", f"/clients/{api_uuid}", api_client)
        changes.append("api 클라이언트 패스워드 그랜트(비밀번호 확인용)")

    # 6. web-backend 클라이언트 — 자기 audience, 렐름 역할(system-admin 등)을 사용자 토큰에 싣는다
    changes += ensure_self_audience(admin, WEB_CLIENT)
    web_uuid = client_uuid(admin, WEB_CLIENT)
    have = {r["name"] for r in admin.get(f"/clients/{web_uuid}/scope-mappings/realm")}
    names = [r["name"] for r in catalog["realmRoles"]]
    missing = [n for n in names if n not in have]
    if missing:
        reps = [admin.get(f"/roles/{n}") for n in missing]
        admin.call("POST", f"/clients/{web_uuid}/scope-mappings/realm", reps)
        changes.append(f"web 범위 매핑 {missing}")

    # 7. 처음 시스템 관리자
    email = env.get("MOAI_WORKS_SYSADMIN_EMAIL", "sysadmin@moai-works.local").lower()
    users = admin.get(f"/users?email={urllib.parse.quote(email)}&exact=true")
    if not users:
        password = env.get("MOAI_WORKS_SYSADMIN_PASSWORD") or secrets.token_urlsafe(12)
        admin.call("POST", "/users", {
            "username": "sysadmin", "email": email, "emailVerified": True, "enabled": True,
            "firstName": "시스템 관리자",
            "credentials": [{"type": "password", "value": password, "temporary": False}]})
        users = admin.get(f"/users?email={urllib.parse.quote(email)}&exact=true")
        set_env("MOAI_WORKS_SYSADMIN_EMAIL", email)
        set_env("MOAI_WORKS_SYSADMIN_PASSWORD", password)
        changes.append(f"시스템 관리자 계정 {email} (비밀번호는 .env)")
    uid = users[0]["id"]
    have = {r["name"] for r in admin.get(f"/users/{uid}/role-mappings/realm")}
    if "system-admin" not in have:
        admin.call("POST", f"/users/{uid}/role-mappings/realm", [admin.get("/roles/system-admin")])
        changes.append("시스템 관리자에게 system-admin 역할")

    print("렐름 맞춤: " + ("; ".join(changes) if changes else "바꿀 것 없음"))


if __name__ == "__main__":
    main()
