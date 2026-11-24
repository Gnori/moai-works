"""moai-works 의 Agent 스킬을 MCP(get_skill)로 받아 스킬 폴더에 쓴다.

스킬의 원본은 moai-works 시스템 관리 › 스킬이다(시스템 관리자가 등록 · 수정 · 삭제, 리비전으로 관리). 이 도구는 그 사본을
`<대상>/<name>/<path>` 에 쓴다 — 기본 대상은 이 저장소의 .claude/skills/, `--user` 면 ~/.claude/skills/.

    python3 -I tools/sync_skills.py                  # 모든 스킬을 저장소의 .claude/skills/ 로
    python3 -I tools/sync_skills.py --user           # ~/.claude/skills/ 로
    python3 -I tools/sync_skills.py --name moai-works-issues --dry-run
    python3 -I tools/sync_skills.py --force          # 그 자리에서 고친 사본도 서버 것으로 덮어쓴다

연결: Claude Code 설정(~/.claude.json)의 mcpServers["moai-works-issue"] — `claude mcp add --scope user --transport http
moai-works-issue https://localhost:11002/mcp/issue --header "Authorization: Bearer <키>"` 로 등록한 것 — 의 주소와 머리글을 그대로 쓴다.
키는 어디에도 찍지 않는다. 로컬 HTTPS 는 infra/tls/certs/ca.pem(또는 MOAI_WORKS_TLS_CA · --ca)을 공인 인증 기관에 더해 믿는다.

그 자리에서 고친 사본은 덮어쓰지 않는다(--force 없이): 서버가 내보낸 SKILL.md 의 둘째 덩어리 첫 줄(출처 표시 줄)
`<!-- moai-works-skill name=… revision=N sha256=… -->` 의 지문은 그 줄을 뺀 파일 전체(UTF-8)의 sha256 이다. 이 도구는 같은 방법으로 다시 재서
  - 같으면 손대지 않은 사본 → 서버 리비전이 더 새로우면 바꾸고, 같으면 그대로 둔다
  - 다르면 그 자리에서 고친 것 → 건너뛴다(고친 것은 moai-works 화면에서 수정하거나 --force)
  - 출처 표시 줄이 없는 파일(손으로 만든 것)은 내용이 서버 것과 같을 때만 받아 쓴다(출처 표시 줄이 붙는다)
  - 사본의 리비전이 서버보다 크면(다른 서버 · DB 를 새로 만듦) 건너뛴다
서버에 없는 스킬(삭제했거나 이름이 다름)의 폴더는 지우지 않고 알리기만 한다.
files 의 경로는 moai-wiki 가 정한 규칙을 따른다 — 상대 POSIX 경로 · '/' 로 시작하지 않음 · '.' · '..' 마디 없음 · 마디는 [A-Za-z0-9._-] ·
깊이 4 · 200자까지 · SKILL.md 는 본 파일 자리. 하나라도 어긋나거나 스킬 폴더 밖이면 그 스킬의 아무 파일도 쓰지 않는다.

받는 쪽 보호(일감 #188 — moai-wiki 세션이 옮겨 쓰다 찾음):
  - 이름 · 경로 규칙은 re.fullmatch 로 본다(re.match 와 '$' 는 끝의 줄바꿈 "name\n" 을 통과시킨다)
  - 대소문자 · 유니코드 정규화만 다른 경로 둘(a.md · A.md — macOS 에서는 같은 파일), 파일 이름이 다른 파일의 폴더 자리인 것은 그 스킬을 건너뛴다.
    본 파일이 아닌 skill.md(대소문자만 다른 SKILL.md)는 받을 수 없는 경로다
  - 스킬 폴더(<대상>/<name> — 그 자체가 링크면 따라간 실제 폴더) 안의 마디가 링크거나, 실제 경로가 실제 스킬 폴더 밖이면 그 스킬을 건너뛴다.
    쓸 때도 폴더를 마디마다 O_NOFOLLOW 로 열고, 임시 파일(O_EXCL · O_NOFOLLOW)을 같은 실제 폴더에 모두 쓴 뒤 os.replace 로 바꿔 끼운다
  - MCP 요청은 리다이렉트(3xx)를 따르지 않는다 — 따르면 Authorization(MCP 키)이 다른 주소로 간다. 3xx 는 연결 오류(끝 코드 2)다

끝 코드: 0 = 다 됨, 1 = 건너뛴 스킬이 있다(고친 사본 · 리비전이 앞섬 · 받지 못함 · 스킬 폴더 안의 링크 · 겹치는 경로),
2 = 연결 · 설정 오류(리다이렉트 · 규칙에 어긋난 이름 · 경로 포함).

바뀐 점(일감 #183 — 사용자: "버전 정보만 보고 업데이트하는 건 안 맞잖아? 간략한 설명도?"): 사본의 리비전 N 이 서버의 M 보다 작으면
get_skill(name, sinceRevision=N) 으로 그 사이 리비전마다 시스템 관리자가 쓴 "바뀐 점 요약"을 받아 함께 찍는다(요약마다 200자 · 새 것 5개까지,
더 앞선 것은 "외 앞선 N개"). 새로 받는 스킬은 list_skills 의 latestChange(지금 리비전의 요약)만. sinceRevision 을 모르는 옛 서버면 번호만 찍는다.
요약은 한 줄로 펴고 제어 문자 · 보이지 않는 서식 문자를 걷는다(터미널 · 모델 문맥에 그대로 들어간다).

훅 모드(--hook, 2026-11-24 — 사용자: "이 스킬 업데이트되면 받아서 갱신할 수 있는 구조야?"): Claude Code 의 SessionStart ·
UserPromptSubmit 훅에서 부른다. 바뀐 스킬이 있을 때만 한 줄을 찍고(그 줄이 모델에게 보인다), 그대로면 아무것도 찍지 않는다 —
"moai-works 스킬이 바뀌어 새로 받았습니다 — <이름> 리비전 2 → 4: [3] …; [4] … — 바뀐 점을 보고 SKILL.md 를 다시 읽으십시오."
--min-interval 초(훅 모드 기본 600) 안에 이미 봤으면 서버에 묻지 않는다(대상 폴더마다 ~/.cache/moai-works/skill-sync/ 의 시각 표시).
연결 실패 · 오류에도 끝 코드는 늘 0 이다 — 훅이 프롬프트를 막지 않게. 서버를 기다리는 시간은 5초까지.

    python3 -I <moai-works>/tools/sync_skills.py --hook --target "$CLAUDE_PROJECT_DIR/.claude/skills" --name moai-works-issues
"""
import argparse
import errno
import hashlib
import json
import os
import re
import secrets
import ssl
import stat
import sys
import unicodedata
import urllib.error
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
REPO_SKILLS = os.path.join(ROOT, '.claude', 'skills')
USER_SKILLS = os.path.join(os.path.expanduser('~'), '.claude', 'skills')
DEFAULT_CA = os.environ.get('MOAI_WORKS_TLS_CA', os.path.join(ROOT, 'infra', 'tls', 'certs', 'ca.pem'))
SERVER = 'moai-works-issue'
PROTOCOL = '2025-06-18'
CLIENT = {'name': 'moai-works-sync-skills', 'version': '1'}
# 규칙은 늘 fullmatch 로 쓴다 — re.match 와 '$' 는 끝의 줄바꿈("name\n")을 통과시킨다(일감 #188)
MARKER = re.compile(r'<!-- moai-works-skill name=(?P<name>[a-z0-9-]+) revision=(?P<revision>[0-9]+) sha256=(?P<sha>[0-9a-f]{64})\b.*-->\s*')
NAME = re.compile(r'[a-z0-9]+(-[a-z0-9]+)*')
SEGMENT = re.compile(r'[A-Za-z0-9._-]+')
MAIN_FILE = 'SKILL.md'
PATH_MAX, DEPTH_MAX = 200, 4
SUMMARY_MAX, CHANGES_SHOWN = 200, 5


class SyncError(Exception):
    """연결 · 설정 오류 — 키 원문은 담지 않는다."""


class McpError(SyncError):
    """서버가 JSON-RPC 오류로 답했다(붙기는 했다) — 예: 옛 서버가 모르는 인자를 거절"""


# ── 출처 표시 줄 · 지문 (서버의 SkillFile 과 같은 규칙) ─────────────────────

def split_marker(text):
    """(출처 표시 줄의 값 dict 또는 None, 그 줄을 뺀 글). 첫 출처 표시 줄 하나만 본다."""
    lines = text.splitlines(keepends=True)
    for i, line in enumerate(lines):
        m = MARKER.fullmatch(line.rstrip('\r\n'))
        if m:
            return {'name': m['name'], 'revision': int(m['revision']), 'sha256': m['sha']}, ''.join(lines[:i] + lines[i + 1:])
    return None, text


def sha256(text):
    return hashlib.sha256(text.encode('utf-8')).hexdigest()


def decide(local, server_text, server_revision, force=False):
    """한 SKILL.md 를 어떻게 할지 — (할 일, 까닭). 할 일: write · skip · same.

    local 은 지금 파일의 글(없으면 None), server_text 는 서버가 내보낸 SKILL.md(출처 표시 줄 포함)다.
    """
    if local is None:
        return 'write', '새로 받음'
    if local == server_text:
        return 'same', '같음'
    marker, plain = split_marker(local)
    _, server_plain = split_marker(server_text)
    if marker is None:
        if loose(plain) == loose(server_plain) or same_skill(plain, server_plain):
            return 'write', '출처 표시 줄을 붙임(내용은 같음)'
        return ('write', '출처 표시 줄이 없는 파일을 덮어씀(--force)') if force else ('skip', '출처 표시 줄이 없고 내용이 다름 — 손으로 고친 파일')
    if sha256(plain) != marker['sha256']:
        return ('write', '그 자리에서 고친 사본을 덮어씀(--force)') if force else ('skip', '그 자리에서 고친 사본 — moai-works 화면에서 수정하거나 --force')
    if marker['revision'] > server_revision:
        return ('write', '리비전이 서버보다 앞선 사본을 덮어씀(--force)') if force else (
            'skip', f'사본의 리비전 {marker["revision"]} 이 서버의 {server_revision} 보다 큼 — 다른 서버의 사본?')
    if marker['revision'] == server_revision:
        return 'write', '같은 리비전 · 내보내는 모양이 바뀜'
    return 'write', f'리비전 {marker["revision"]} → {server_revision}'


def loose(text):
    """손으로 만든 파일과 견줄 모양 — 줄바꿈 LF, frontmatter 뒤의 빈 줄 · 끝의 빈 줄을 하나로(서버가 가져올 때 걷는 것과 같다)."""
    lines = text.replace('\r\n', '\n').replace('\r', '\n').split('\n')
    if lines and lines[0].strip() == '---':
        for i in range(1, len(lines)):
            if lines[i].rstrip() in ('---', '...'):
                j = i + 1
                while j < len(lines) and not lines[j].strip():
                    j += 1
                lines = lines[:i + 1] + [''] + lines[j:]
                break
    return '\n'.join(lines).rstrip() + '\n'


def frontmatter(text):
    """(name, description, 본문) — 한 줄짜리 name · description 만 읽는다(따옴표 없이 · 큰따옴표). 못 읽으면 None.
    손으로 만든 파일이 서버 것과 설명의 따옴표만 다를 때 같은 스킬로 알아보는 데 쓴다(덧붙는 모듈 없이)."""
    lines = text.replace('\r\n', '\n').split('\n')
    if not lines or lines[0].strip() != '---':
        return None
    values = {}
    for i in range(1, len(lines)):
        line = lines[i]
        if line.rstrip() in ('---', '...'):
            body = '\n'.join(lines[i + 1:]).strip('\n').rstrip()
            if set(values) != {'name', 'description'}:
                return None
            return values['name'], values['description'], body
        m = re.fullmatch(r'(name|description):\s*(.*?)\s*', line)
        if not m:
            return None
        raw = m.group(2)
        if raw.startswith('"'):
            try:
                values[m.group(1)] = json.loads(raw)
            except json.JSONDecodeError:
                return None
        elif raw.startswith("'") or raw[:1] in ('|', '>', ''):
            return None
        else:
            values[m.group(1)] = raw
    return None


def same_skill(a, b):
    left, right = frontmatter(a), frontmatter(b)
    return left is not None and left == right


def fold(path):
    """대소문자 · 유니코드 정규화를 가리지 않는 파일 시스템(macOS 기본)에서 같은 이름인지 견줄 모양 — NFC → casefold → NFC"""
    return unicodedata.normalize('NFC', unicodedata.normalize('NFC', path).casefold())


def check_file_path(name, path, main=False):
    """서버가 준 파일 경로의 규칙(moai-wiki 가 정한 딸린 파일 규칙과 같다) — 상대 POSIX 경로 · '/' 로 시작하지 않음 · '.' · '..' 마디 없음 ·
    마디는 [A-Za-z0-9._-] · 깊이 4 · 200자까지 · SKILL.md 는 본 파일 자리(딸린 파일 이름으로 쓰지 않는다 — 대소문자만 다른 skill.md 도.
    macOS 에서는 본 파일과 같은 파일이라 서로 덮어쓴다, 일감 #188)."""
    ok = (isinstance(path, str) and 0 < len(path) <= PATH_MAX and not path.startswith('/') and '\\' not in path
          and len(path.split('/')) <= DEPTH_MAX
          and all(SEGMENT.fullmatch(part) and part not in ('.', '..') for part in path.split('/')))
    if not ok or main != (path == MAIN_FILE) or (path != MAIN_FILE and fold(path) == fold(MAIN_FILE)):
        raise SyncError(f'{name}: 받을 수 없는 파일 경로 {path!r}')


def safe_path(base, name, path):
    """<base>/<name>/<path> — 규칙에 맞고, 이름을 붙여 정리한 경로가 그 스킬 폴더 안이어야 한다(밖이면 거절).
    디스크의 링크는 여기서 보지 않는다 — 쓰기 전에 unsafe_destination, 쓸 때 write_skill 이 본다."""
    if not isinstance(name, str) or not NAME.fullmatch(name):
        raise SyncError(f'스킬 이름이 규칙에 맞지 않습니다: {name!r}')
    check_file_path(name, path, main=path == MAIN_FILE)
    folder = os.path.normpath(os.path.join(base, name))
    target = os.path.normpath(os.path.join(folder, *path.split('/')))
    if os.path.commonpath([folder, target]) != folder or target == folder:
        raise SyncError(f'{name}: 받을 수 없는 파일 경로 {path!r}')
    return folder, target


def overlapping(paths):
    """files 의 경로끼리 겹치는가 — 대소문자 · 유니코드 정규화만 다른 둘(macOS 에서는 같은 파일이라 서로 덮어쓴다), 한 파일 이름이 다른
    파일의 폴더 자리(쓰다가 반쯤에서 막힌다). 경로는 규칙(check_file_path)을 이미 지난 것이다."""
    keys = [fold(p) for p in paths]
    folders = {'/'.join(parts[:i]) for parts in (k.split('/') for k in keys) for i in range(1, len(parts))}
    return len(set(keys)) != len(keys) or bool(folders & set(keys))


def inside(folder, path):
    return os.path.commonpath([folder, path]) == folder and path != folder


def unsafe_destination(folder, paths):
    """<folder>/<path> 들에 써도 되는가 — 안 되면 까닭(한 줄), 되면 None. 쓰기 · 읽기 전에 본다(일감 #188).

    스킬 폴더 자체는 링크여도 된다(전역 등록 — ~/.claude/skills/<name> → 저장소의 스킬 폴더). 그 안은 이미 있는 마디가 링크면 안 되고,
    가운데 마디는 폴더, 끝 마디는 보통 파일이어야 하며(FIFO 를 읽으면 훅이 멈춘다), 실제 경로(realpath)가 실제 스킬 폴더 안이어야 한다.
    검사와 쓰기 사이에 바꿔치기하는 것은 write_skill 이 막는다(O_NOFOLLOW)."""
    try:
        if not stat.S_ISDIR(os.stat(folder).st_mode):
            return '스킬 폴더 자리에 폴더가 아닌 것이 있음'
    except FileNotFoundError:
        if os.path.lexists(folder):
            return '스킬 폴더가 끊긴 링크 — 따라가 만들지 않음'
    except OSError as e:
        return f'스킬 폴더를 볼 수 없음({e.strerror or type(e).__name__})'
    real = os.path.realpath(folder)
    for path in paths:
        parts, current = path.split('/'), real
        for i, part in enumerate(parts):
            current = os.path.join(current, part)
            try:
                mode = os.lstat(current).st_mode
            except FileNotFoundError:
                break  # 여기부터는 새로 만든다
            except OSError as e:
                return f'{path}: 볼 수 없음({e.strerror or type(e).__name__})'
            where = '/'.join(parts[:i + 1])
            if stat.S_ISLNK(mode):
                return f'스킬 폴더 안의 링크 {where} — 링크는 따라가지 않음'
            if i < len(parts) - 1 and not stat.S_ISDIR(mode):
                return f'{where} 이 폴더가 아님'
            if i == len(parts) - 1 and not stat.S_ISREG(mode):
                return f'{where} 이 보통 파일이 아님'
        if not inside(real, os.path.join(os.path.realpath(os.path.dirname(os.path.join(real, *parts))), parts[-1])):
            return f'{path}: 실제 경로가 스킬 폴더 밖'
    return None


NOFOLLOW = getattr(os, 'O_NOFOLLOW', 0)
CLOEXEC = getattr(os, 'O_CLOEXEC', 0)
DIR_FLAGS = os.O_RDONLY | getattr(os, 'O_DIRECTORY', 0) | NOFOLLOW | CLOEXEC
NEW_FILE_FLAGS = os.O_WRONLY | os.O_CREAT | os.O_EXCL | NOFOLLOW | CLOEXEC


class Refused(Exception):
    """쓰는 도중에 본 위험 — 까닭만 담는다"""


def write_skill(folder, writes):
    """한 스킬의 파일들(writes = [(경로, 글)])을 쓴다 — 다 쓰거나 하나도 쓰지 않는다. 안 되면 까닭(한 줄), 되면 None(일감 #188).

    실제 스킬 폴더(링크면 따라간 곳)를 열고 그 안은 마디마다 dir_fd 와 O_NOFOLLOW 로 연다(없으면 만든다) — 미리 본 뒤에 누가 마디를
    링크로 바꿔도 따라가지 않는다. 먼저 파일마다 같은 실제 폴더에 임시 파일(O_EXCL · O_NOFOLLOW)을 모두 쓰고, 다 되면 os.replace 로
    바꿔 끼운다(이름 바꾸기는 끝 마디가 링크여도 따라가지 않고 그 자리를 바꾼다). 도중에 막히면 임시 파일과 새로 만든 폴더를 치운다."""
    dirs, made, staged, created, done = {}, [], [], None, False
    try:
        if not os.path.isdir(folder):
            if os.path.lexists(folder):
                raise Refused('스킬 폴더 자리에 폴더가 아닌 것(끊긴 링크 등)이 있음 — 따라가지 않음')
            os.makedirs(folder)
            created = folder
        dirs[''] = os.open(os.path.realpath(folder), DIR_FLAGS)
        for path, content in writes:
            *parents, leaf = path.split('/')
            here = ''
            for part in parents:
                parent, here = dirs[here], f'{here}/{part}' if here else part
                if here in dirs:
                    continue
                try:
                    dirs[here] = os.open(part, DIR_FLAGS, dir_fd=parent)
                except FileNotFoundError:
                    os.mkdir(part, dir_fd=parent)
                    made.append((parent, part))
                    dirs[here] = os.open(part, DIR_FLAGS, dir_fd=parent)
                except OSError as e:
                    if e.errno in (errno.ELOOP, errno.ENOTDIR):  # O_NOFOLLOW 가 링크를 막았다(macOS 는 ENOTDIR)
                        raise Refused(f'{here} 이 링크이거나 폴더가 아님 — 따라가지 않음')
                    raise
            fd = dirs[here]
            try:
                if not stat.S_ISREG(os.stat(leaf, dir_fd=fd, follow_symlinks=False).st_mode):
                    raise Refused(f'{path} 이 링크이거나 보통 파일이 아님 — 따라가지 않음')
            except FileNotFoundError:
                pass
            tmp = f'.sync-{secrets.token_hex(8)}'
            handle = os.open(tmp, NEW_FILE_FLAGS, 0o644, dir_fd=fd)
            staged.append((fd, tmp, leaf))
            with os.fdopen(handle, 'w', encoding='utf-8', newline='') as f:
                f.write(content)
        while staged:
            fd, tmp, leaf = staged[0]
            os.replace(tmp, leaf, src_dir_fd=fd, dst_dir_fd=fd)
            staged.pop(0)
        done = True
        return None
    except Refused as e:
        return str(e)
    except (OSError, ValueError, NotImplementedError) as e:  # NotImplementedError — dir_fd 가 없는 OS 면 쓰지 않는다
        return f'쓰지 못함({getattr(e, "strerror", None) or type(e).__name__})'
    finally:
        for fd, tmp, _ in staged:
            try:
                os.unlink(tmp, dir_fd=fd)
            except OSError:
                pass
        if not done:
            for parent, part in reversed(made):
                try:
                    os.rmdir(part, dir_fd=parent)
                except OSError:
                    pass
        for fd in dirs.values():
            os.close(fd)
        if created and not done:
            try:
                os.rmdir(created)
            except OSError:
                pass


# ── 연결 ──────────────────────────────────────────────────────────────
# tools/attach_files.py 도 이 절(load_server · McpClient · tls_context · SyncError)을 importlib 로 가져다 쓴다(일감 #179).
# 다른 저장소의 훅이 이 파일 하나를 부르므로 따로 떼지 않는다 — 바꿀 때는 두 도구의 시험을 함께 돌린다.

def load_server(config_path, server):
    """~/.claude.json 의 mcpServers[server] — 맨 위(--scope user), 없으면 이 저장소 프로젝트의 것."""
    try:
        with open(config_path, encoding='utf-8') as f:
            config = json.load(f)
    except FileNotFoundError:
        raise SyncError(f'Claude Code 설정이 없습니다: {config_path}')
    except json.JSONDecodeError as e:
        raise SyncError(f'Claude Code 설정을 읽을 수 없습니다: {config_path} ({e.msg})')
    entry = (config.get('mcpServers') or {}).get(server)
    if entry is None:
        for path, project in (config.get('projects') or {}).items():
            if os.path.abspath(path) == ROOT and (project.get('mcpServers') or {}).get(server):
                entry = project['mcpServers'][server]
    if not entry or not entry.get('url'):
        raise SyncError(f'MCP 서버 "{server}" 가 등록돼 있지 않습니다 — claude mcp add --scope user --transport http {server} '
                        'https://localhost:11002/mcp/issue --header "Authorization: Bearer <MCP 키>"')
    headers = {k: expand(str(v)) for k, v in (entry.get('headers') or {}).items()}
    if not any(k.lower() == 'authorization' for k in headers):
        raise SyncError(f'MCP 서버 "{server}" 에 Authorization 머리글(MCP 키)이 없습니다.')
    return entry['url'], headers


def expand(value):
    """${VAR} 를 환경 변수 값으로(Claude Code 설정과 같다). 없으면 그대로 둔다."""
    return re.sub(r'\$\{([A-Za-z_][A-Za-z0-9_]*)\}', lambda m: os.environ.get(m.group(1), m.group(0)), value)


class _RefuseRedirect(urllib.request.HTTPRedirectHandler):
    """리다이렉트를 따르지 않는다 — urllib 는 POST 의 301 · 302 · 303 을 GET 으로 따라가며 Authorization(MCP 키 · 티켓)을 새 주소로
    다시 보낸다(일감 #188). None 을 돌려주면 3xx 가 HTTPError 로 올라온다."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def no_redirect_opener(context):
    """리다이렉트를 따르지 않는 opener — 이 도구 · attach_files 의 모든 요청이 쓴다. 3xx 는 HTTPError(redirected 로 가린다)"""
    return urllib.request.build_opener(_RefuseRedirect(), urllib.request.HTTPSHandler(context=context))


def redirected(error):
    return 300 <= error.code < 400


class McpClient:
    """MCP Streamable HTTP 클라이언트 — 세션을 열고(initialize) 도구를 부른다. 응답은 JSON 이거나 SSE(data: 줄)다.
    client 는 접근 기록에 남는 이름(clientInfo · User-Agent) — 가져다 쓰는 도구가 제 이름을 준다.
    리다이렉트는 따르지 않는다(3xx 는 SyncError — 새 주소는 찍지 않는다)."""

    def __init__(self, url, headers, context, timeout=60, client=None):
        self.url, self.headers, self.context, self.timeout = url, headers, context, timeout
        self.opener = no_redirect_opener(context)
        self.client = client or CLIENT
        self.session, self.next_id = None, 0

    def _post(self, body, expect_reply=True):
        data = json.dumps(body).encode('utf-8')
        headers = {**self.headers, 'Content-Type': 'application/json', 'Accept': 'application/json, text/event-stream',
                   'User-Agent': f'{self.client["name"]}/{self.client["version"]}', 'MCP-Protocol-Version': PROTOCOL}
        if self.session:
            headers['Mcp-Session-Id'] = self.session
        request = urllib.request.Request(self.url, data=data, headers=headers, method='POST')
        try:
            with self.opener.open(request, timeout=self.timeout) as response:
                self.session = response.headers.get('Mcp-Session-Id') or self.session
                raw = response.read().decode('utf-8')
                kind = response.headers.get('Content-Type', '')
        except urllib.error.HTTPError as e:
            if redirected(e):
                e.close()
                raise SyncError(f'MCP 서버가 다른 주소로 보냈습니다(HTTP {e.code}) — MCP 키가 따라가지 않게 따르지 않습니다. '
                                'Claude Code 설정에 등록한 주소를 확인하십시오')
            raise SyncError(f'MCP 서버가 거절했습니다: HTTP {e.code} {problem_code(e)}')
        except urllib.error.URLError as e:
            hint = ' — 로컬 인증 기관(infra/tls/certs/ca.pem)을 --ca 로 주십시오' if isinstance(e.reason, ssl.SSLError) else ''
            raise SyncError(f'MCP 서버에 붙지 못했습니다: {e.reason}{hint}')
        if not expect_reply:
            return None
        for message in messages(raw, kind):
            if message.get('id') == body.get('id'):
                if 'error' in message:
                    raise McpError(f'MCP 오류: {message["error"].get("message", message["error"])}')
                return message.get('result')
        raise SyncError('MCP 응답에서 결과를 찾지 못했습니다.')

    def _request(self, method, params=None):
        self.next_id += 1
        return self._post({'jsonrpc': '2.0', 'id': self.next_id, 'method': method, 'params': params or {}})

    def open(self):
        self._request('initialize', {'protocolVersion': PROTOCOL, 'capabilities': {}, 'clientInfo': self.client})
        self._post({'jsonrpc': '2.0', 'method': 'notifications/initialized'}, expect_reply=False)

    def call(self, tool, arguments):
        """도구 결과(JSON). 도구가 돌려준 오류는 {"error": {code, message}} 그대로."""
        result = self._request('tools/call', {'name': tool, 'arguments': arguments})
        content = (result or {}).get('content') or []
        text = next((c.get('text') for c in content if c.get('type') == 'text'), None)
        if text is None:
            raise SyncError(f'{tool}: 결과가 비었습니다.')
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            raise SyncError(f'{tool}: 결과를 읽을 수 없습니다: {text[:200]}')

    def close(self):
        if not self.session:
            return
        request = urllib.request.Request(self.url, headers={**self.headers, 'Mcp-Session-Id': self.session}, method='DELETE')
        try:
            self.opener.open(request, timeout=min(self.timeout, 10)).close()
        except (urllib.error.URLError, OSError):
            pass


def problem_code(error):
    try:
        body = json.loads(error.read().decode('utf-8'))
        return f'{body.get("code", "")} {body.get("detail", "")}'.strip()
    except (ValueError, OSError):
        return ''


def messages(raw, kind):
    """응답 본문의 JSON-RPC 메시지들 — JSON 하나(또는 배열) 이거나 SSE 의 data: 덩어리들."""
    if 'text/event-stream' not in kind:
        loaded = json.loads(raw) if raw.strip() else []
        return loaded if isinstance(loaded, list) else [loaded]
    out, data = [], []
    for line in raw.splitlines() + ['']:
        if line.startswith('data:'):
            data.append(line[5:].lstrip())
        elif line == '' and data:
            try:
                out.append(json.loads('\n'.join(data)))
            except json.JSONDecodeError:
                pass
            data = []
    return out


def tls_context(ca):
    context = ssl.create_default_context()
    if ca and os.path.isfile(ca):
        context.load_verify_locations(cafile=ca)
    return context


# ── 바뀐 점 ─────────────────────────────────────────────────────────

def one_line(text, limit=SUMMARY_MAX):
    """한 줄로 — 줄바꿈 · 제어 문자 · 보이지 않는 서식 문자(폭 없는 공백 · 양방향 덮어쓰기 등)를 빈칸으로, 빈칸은 하나로, limit 자에서 자른다.
    서버가 준 글이 터미널 · 모델 문맥에 그대로 들어가므로 숨긴 글자 · 줄 바꿔 끼우기 · 터미널 제어를 막는다."""
    if not isinstance(text, str):
        return ''
    flat = ''.join(' ' if c.isspace() or unicodedata.category(c) in ('Cc', 'Cf', 'Cs', 'Co', 'Zl', 'Zp') else c for c in text)
    flat = ' '.join(flat.split())
    return flat if len(flat) <= limit else flat[:limit - 1].rstrip() + '…'


def change_items(changes, omitted=0, shown=CHANGES_SHOWN):
    """바뀐 점 → ['[3] 요약', …] — 새 것 shown 개(옛 것부터), 더 앞선 것이 있으면 끝에 '외 앞선 N개'. 요약이 없는 옛 리비전은 '(요약 없음)'."""
    rows = [c for c in changes if isinstance(c, dict) and isinstance(c.get('revisionNo'), int)]
    rows.sort(key=lambda c: c['revisionNo'])
    more = max(0, len(rows) - shown) + max(0, omitted)
    items = [f'[{c["revisionNo"]}] {one_line(c.get("summary")) or "(요약 없음)"}' for c in rows[-shown:]]
    if more:
        items.append(f'외 앞선 {more}개')
    return items


def get_skill(client, name, since):
    """get_skill — since(사본의 리비전)가 있으면 sinceRevision 으로 그 사이 바뀐 점(changes)도 받는다. sinceRevision 을 모르는 옛 서버가
    거절하면 없이 다시 묻는다(바뀐 점 없이 번호만 알린다)."""
    if since is not None:
        try:
            got = client.call('get_skill', {'name': name, 'sinceRevision': since})
            if 'error' not in got:
                return got
        except McpError:
            pass  # 붙지 못한 것(SyncError)은 다시 묻지 않는다 — 훅이 오래 기다리지 않게
    return client.call('get_skill', {'name': name})


def changes_of(got, listed, new, since, revision):
    """이번에 받은 것의 바뀐 점 — (changes, omitted). 고쳐 받기는 get_skill 의 changes(since 다음 ~ 받는 리비전), 새로 받기(new — 사본 없음)는
    list_skills 의 latestChange 하나. 서버가 주지 않으면(옛 서버) · 견줄 리비전이 없으면(손으로 만든 사본) None."""
    if new:
        latest = listed.get('latestChange')
        if isinstance(latest, dict) and latest.get('revisionNo') == revision:
            return [latest], 0
        return None, 0
    if since is None or not isinstance(got.get('changes'), list):
        return None, 0
    rows = [c for c in got['changes'] if isinstance(c, dict) and isinstance(c.get('revisionNo'), int)
            and since < c['revisionNo'] <= revision]
    omitted = got.get('changesOmitted') if isinstance(got.get('changesOmitted'), int) else 0
    return rows, omitted


# ── 동기화 ────────────────────────────────────────────────────────────

def sync(client, target, names=None, force=False, dry_run=False, out=print, updates=None):
    """서버의 스킬을 target 에 쓴다. @return 건너뛴 스킬 수

    updates 에 목록을 주면 쓴(쓸) 스킬마다 {name, from, to, why, changes, omitted} 를 더한다 — from 은 사본의 리비전(없으면 None),
    changes 는 바뀐 점(서버가 주지 않으면 None)."""
    listed = client.call('list_skills', {})
    if 'error' in listed:
        raise SyncError(f'list_skills: {listed["error"].get("code")} {listed["error"].get("message")}')
    server = {s['name']: s for s in listed.get('skills', [])}
    wanted = names or sorted(server)
    skipped = 0
    for name in wanted:
        if name not in server:
            out(f'  {name}: 서버에 없음(삭제했거나 이름이 다름) — 건너뜀')
            skipped += 1
            continue
        folder, skill_path = safe_path(target, name, MAIN_FILE)
        # 사본을 읽기 전에 본다 — SKILL.md 가 링크면 바깥 파일을, FIFO 면 멈춘 채 읽게 된다
        unsafe = unsafe_destination(folder, [MAIN_FILE])
        if unsafe:
            out(f'  {name}: 건너뜀 — {unsafe}')
            skipped += 1
            continue
        local = read_text(skill_path)
        local_marker = split_marker(local)[0] if local is not None else None
        server_revision = server[name].get('revisionNo')
        # 사본이 손대지 않은 옛 리비전이면 그 뒤로 바뀐 점을 함께 받는다(고친 사본이면 어차피 건너뛰거나 --force)
        since = (local_marker['revision'] if local_marker and local_marker['name'] == name and isinstance(server_revision, int)
                 and local_marker['revision'] < server_revision else None)
        got = get_skill(client, name, since)
        if 'error' in got:
            out(f'  {name}: 받지 못함 — {got["error"].get("code")} {got["error"].get("message")}')
            skipped += 1
            continue
        revision = int(got['revisionNo'])
        files = got.get('files') or []
        if not isinstance(files, list) or not all(isinstance(f, dict) and isinstance(f.get('content'), str) for f in files):
            out(f'  {name}: files 의 모양이 맞지 않음 — 건너뜀')
            skipped += 1
            continue
        paths = [f.get('path') for f in files]
        # 서버가 준 경로를 먼저 모두 본다 — 하나라도 규칙에 어긋나거나 폴더 밖이면 아무것도 쓰지 않는다(끝 코드 2)
        for path in paths:
            safe_path(target, name, path)
        if paths.count(MAIN_FILE) != 1 or overlapping(paths):
            out(f'  {name}: files 에 SKILL.md 가 하나가 아니거나 같은 경로가 둘(대소문자만 다른 것 · 파일과 폴더 자리가 겹치는 것 포함) — 건너뜀')
            skipped += 1
            continue
        # 디스크 쪽 — 스킬 폴더 안의 링크 · 폴더 밖으로 가는 실제 경로면 그 스킬의 아무 파일도 쓰지 않는다
        unsafe = unsafe_destination(folder, paths)
        if unsafe:
            out(f'  {name}: 건너뜀 — {unsafe}')
            skipped += 1
            continue
        skill_md = next(f for f in files if f['path'] == MAIN_FILE)
        writes = [(f['path'], f['content']) for f in files]
        action, why = decide(local, skill_md['content'], revision, force)
        if action == 'skip':
            out(f'  {name}: 건너뜀 — {why}')
            skipped += 1
            continue
        if action == 'same':
            out(f'  {name}: 리비전 {revision} 그대로')
            continue
        if not dry_run:
            # SKILL.md 를 판정했으면 그 스킬의 파일을 모두 쓴다 — 다 쓰거나 하나도 쓰지 않는다(딸린 파일은 나중 — files 모양은 그대로 둔다)
            refused = write_skill(folder, writes)
            if refused:
                out(f'  {name}: 건너뜀 — {refused}')
                skipped += 1
                continue
        changes, omitted = changes_of(got, server[name], local is None, since, revision)
        out(f'  {name}: {why}{f" (리비전 {revision})" if local is None else ""}{" (시험 — 쓰지 않음)" if dry_run else ""}')
        for item in change_items(changes or [], omitted):
            out(f'      {item}')
        if updates is not None:
            updates.append({'name': name, 'from': local_marker['revision'] if local_marker else None, 'to': revision,
                            'new': local is None, 'why': why, 'changes': changes, 'omitted': omitted})
    # 서버에 없는 사본 — 지우지 않고 알린다(스킬 이름 꼴인 폴더만 — 폴더 이름이 그대로 찍힌다)
    if not names and os.path.isdir(target):
        for entry in sorted(os.listdir(target)):
            if entry in server or not NAME.fullmatch(entry) or not os.path.isdir(os.path.join(target, entry)):
                continue
            try:
                marker, _ = split_marker(read_text(os.path.join(target, entry, MAIN_FILE)) or '')
            except (SyncError, OSError, ValueError):
                continue
            if marker:
                out(f'  {entry}: 서버에 없는 사본(리비전 {marker["revision"]}) — 지우지 않음')
    return skipped


def read_text(path):
    """파일의 글 — 없으면 None. 끝 마디가 링크면 따라가지 않고(O_NOFOLLOW), 보통 파일이 아니면(FIFO 면 멈춘다) 읽지 않는다(SyncError)."""
    try:
        fd = os.open(path, os.O_RDONLY | NOFOLLOW | getattr(os, 'O_NONBLOCK', 0) | CLOEXEC)
    except FileNotFoundError:
        return None
    except OSError as e:
        raise SyncError(f'읽지 않음(링크이거나 열 수 없음 — {e.strerror or type(e).__name__}): {path}')
    with os.fdopen(fd, encoding='utf-8', newline='') as f:
        if not stat.S_ISREG(os.fstat(fd).st_mode):
            raise SyncError(f'읽지 않음(보통 파일이 아님): {path}')
        return f.read()


STAMP_DIR = os.path.join(os.path.expanduser('~'), '.cache', 'moai-works', 'skill-sync')


def stamp_path(target):
    """대상 폴더마다의 '마지막으로 본 때' 표시 — 저장소를 더럽히지 않게 사용자 캐시에 둔다"""
    return os.path.join(STAMP_DIR, hashlib.sha256(target.encode('utf-8')).hexdigest()[:16])


def recently_checked(target, min_interval, now=None):
    """min_interval 초 안에 봤으면 True. 아니면 지금 본 것으로 적고 False(실패해도 다음 창까지는 다시 묻지 않는다)"""
    if min_interval <= 0:
        return False
    import time
    now = time.time() if now is None else now
    path = stamp_path(target)
    try:
        if now - os.path.getmtime(path) < min_interval:
            return True
    except OSError:
        pass
    try:
        os.makedirs(STAMP_DIR, exist_ok=True)
        with open(path, 'w', encoding='utf-8') as f:
            f.write(target)
        os.utime(path, (now, now))
    except OSError:
        pass
    return False


def hook_part(update):
    """훅 한 줄의 스킬 하나 — '이름 리비전 2 → 4: [3] …; [4] …' · '이름 새로 받음(리비전 4): [4] …' · '이름: 까닭'"""
    name, items = update['name'], change_items(update['changes'] or [], update['omitted'])
    if update['new']:
        head = f'{name} 새로 받음(리비전 {update["to"]})'
    elif update['from'] is not None and update['from'] < update['to']:
        head = f'{name} 리비전 {update["from"]} → {update["to"]}'
    else:
        head = f'{name}: {update["why"]}'
    return head + (': ' + '; '.join(items) if items else '')


def run_hook(args, target):
    """훅 모드 — 바뀐 것만 한 줄로(바뀐 점 요약과 함께). 받지 않은 것이 있으면 한 줄 더. 늘 0 을 돌려준다"""
    interval = 600 if args.min_interval is None else args.min_interval
    if recently_checked(target, interval):
        return 0
    lines, updates = [], []
    try:
        url, headers = load_server(args.config, args.server)
        client = McpClient(url, headers, tls_context(args.ca), timeout=5)
        client.open()
        try:
            sync(client, target, args.name, args.force, False, out=lines.append, updates=updates)
        finally:
            client.close()
    except (SyncError, OSError, ValueError) as e:
        print(f'moai-works 스킬 받기 실패(다음에 다시): {e}', file=sys.stderr)
        return 0
    except Exception as e:  # 서버가 엉뚱한 모양을 줘도 훅은 막지 않는다(끝 코드 0) — 값은 찍지 않고 종류만
        print(f'moai-works 스킬 받기 실패(다음에 다시): {type(e).__name__}', file=sys.stderr)
        return 0
    if updates:
        print('moai-works 스킬이 바뀌어 새로 받았습니다 — ' + ' · '.join(hook_part(u) for u in updates)
              + ' — 바뀐 점을 보고 SKILL.md 를 다시 읽으십시오.')
    skipped = [line.strip() for line in lines if '건너뜀' in line or '받지 못함' in line]
    if skipped:
        print('moai-works 스킬 중 받지 않은 것 — ' + ' · '.join(skipped)
              + ' (고친 사본은 moai-works 시스템 관리 › 스킬에서 수정한 뒤 받으십시오).')
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(description='moai-works 의 Agent 스킬을 MCP(get_skill)로 받아 스킬 폴더에 쓴다.')
    where = parser.add_mutually_exclusive_group()
    where.add_argument('--target', help=f'스킬 폴더 (기본 {REPO_SKILLS})')
    where.add_argument('--user', action='store_true', help=f'{USER_SKILLS} 에 쓴다')
    parser.add_argument('--name', action='append', help='이 스킬만 (여러 번 줄 수 있다)')
    parser.add_argument('--force', action='store_true', help='그 자리에서 고친 사본도 서버 것으로 덮어쓴다')
    parser.add_argument('--dry-run', action='store_true', help='무엇을 할지 보이기만 한다')
    parser.add_argument('--server', default=SERVER, help=f'~/.claude.json 의 MCP 서버 이름 (기본 {SERVER})')
    parser.add_argument('--config', default=os.path.join(os.path.expanduser('~'), '.claude.json'), help='Claude Code 설정 파일')
    parser.add_argument('--ca', default=DEFAULT_CA, help='로컬 인증 기관 (기본 infra/tls/certs/ca.pem)')
    parser.add_argument('--hook', action='store_true', help='Claude Code 훅에서 — 바뀐 것만 한 줄, 실패해도 끝 코드 0, 서버는 5초까지')
    parser.add_argument('--min-interval', type=int, help='이 초 안에 이미 봤으면 묻지 않는다(--hook 기본 600, 아니면 0)')
    args = parser.parse_args(argv)
    target = os.path.abspath(USER_SKILLS if args.user else (args.target or REPO_SKILLS))
    if args.hook:
        return run_hook(args, target)
    if args.min_interval and recently_checked(target, args.min_interval):
        print('최근에 이미 봤습니다 — 건너뜀')
        return 0
    try:
        url, headers = load_server(args.config, args.server)
        print(f'moai-works 스킬 받기 — {url} → {target}')
        client = McpClient(url, headers, tls_context(args.ca))
        client.open()
        try:
            skipped = sync(client, target, args.name, args.force, args.dry_run)
        finally:
            client.close()
    except SyncError as e:
        print(f'오류: {e}', file=sys.stderr)
        return 2
    print('끝 — 건너뛴 스킬이 있습니다.' if skipped else '끝.')
    return 1 if skipped else 0


if __name__ == '__main__':
    sys.exit(main())
