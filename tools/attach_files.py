"""moai-works 일감에 파일(고치기 전 · 후 캡처 등)을 첨부하고, 설명 · 댓글에 넣을 markdown 을 한 줄씩 찍는다(일감 #179).

    python3 -I tools/attach_files.py --workspace <워크스페이스 주소> <일감 번호> <파일> [<파일> …]
    python3 -I tools/attach_files.py --workspace workspace-… 179 before.png after.png --alt "고치기 전" --alt "고친 뒤"

파일마다 MCP request_attachment_upload(number, workspace, fileName, size = 정확한 바이트 수, contentType = 확장자로 짐작)로
일회용 티켓을 받고, 곧바로 uploadUrl 에 PUT(Authorization: Bearer <티켓>, 본문 = 파일 그대로)해 201 의 markdown
(`![이름](attachment:<id>)`)을 stdout 에 인자 차례대로 한 줄씩 찍는다. 오류는 stderr 로만 간다.
  --project  일감 번호는 워크스페이스에서 하나라 비워도 된다(주면 서버가 그 일감의 지금 프로젝트와 맞는지 본다)
  --alt      markdown 의 [ ] 안 글(기본은 파일 이름). 한 번 주면 모든 파일에, 파일 수만큼 주면 차례대로

끝 코드: 0 = 다 됨, 1 = 실패한 파일이 있다(나머지는 올렸다 — 서버의 code 를 stderr 에), 2 = 설정 · 연결 오류.

연결은 tools/sync_skills.py 와 같다 — 그 파일의 load_server · McpClient · tls_context 를 importlib 로 가져다 쓴다(덧붙는 모듈 없음).
Claude Code 설정(~/.claude.json)의 mcpServers["moai-works-issue"] 의 주소와 머리글을 쓰고, 로컬 HTTPS 는 infra/tls/certs/ca.pem
(또는 MOAI_WORKS_TLS_CA · --ca)을 공인 인증 기관에 더해 믿는다. 인증서 확인은 끄지 않는다. MCP 키와 티켓은 어디에도 찍지 않는다.
티켓은 올리는 곳에만 보내고(MCP 키는 보내지 않는다), 올릴 주소가 https 가 아니면 보내지 않는다(MCP 주소부터 http 인 시험은 예외).
"""
import argparse
import importlib.util
import json
import os
import socket
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))


def _load_mcp():
    """tools/sync_skills.py 를 모듈로 — -I(격리 모드)에서는 스크립트 폴더가 sys.path 에 없어 import 로 못 부른다"""
    spec = importlib.util.spec_from_file_location('moai_works_sync_skills', os.path.join(HERE, 'sync_skills.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


mcp = _load_mcp()
CLIENT = {'name': 'moai-works-attach-files', 'version': '1'}
TYPES = {'.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.gif': 'image/gif', '.webp': 'image/webp',
         '.pdf': 'application/pdf', '.txt': 'text/plain', '.md': 'text/markdown', '.csv': 'text/csv', '.json': 'application/json'}
DEFAULT_TYPE = 'application/octet-stream'
UPLOAD_TIMEOUT = 120


class FileFailed(Exception):
    """이 파일만 실패 — 다음 파일로 넘어간다. 티켓 · 키는 담지 않는다."""


def content_type(name):
    return TYPES.get(os.path.splitext(name)[1].lower(), DEFAULT_TYPE)


def markdown_line(view, alt):
    """201 응답의 markdown. alt 를 주면 [ ] 안만 바꾼다(서버처럼 ']' 는 '\\]', 줄바꿈은 빈칸 — 한 줄을 지킨다)"""
    markdown = view.get('markdown')
    if not isinstance(markdown, str) or not markdown.strip():
        raise FileFailed('올렸지만 응답에 markdown 이 없음')
    if alt is None or not view.get('id'):
        return markdown
    label = ' '.join(alt.split()).replace(']', '\\]')
    return f'{"!" if markdown.startswith("!") else ""}[{label}](attachment:{view["id"]})'


def upload_url(mcp_url, given):
    """결과의 uploadUrl(상대 주소면 MCP 주소 기준). https 가 아니면 티켓을 보내지 않는다 — MCP 주소부터 http 일 때만(시험) 같은 scheme 을 받는다"""
    if not isinstance(given, str) or not given:
        raise FileFailed('티켓 결과에 uploadUrl 이 없음')
    url = urllib.parse.urljoin(mcp_url, given)
    scheme = urllib.parse.urlsplit(url).scheme
    if scheme != 'https' and scheme != urllib.parse.urlsplit(mcp_url).scheme:
        raise FileFailed('올릴 주소가 https 가 아님 — 티켓을 보내지 않음')
    return url


def put(url, ticket, data, kind, context):
    """PUT 으로 올리고 만든 첨부(JSON). 4xx · 5xx 는 서버의 code 로 FileFailed, 붙지 못하면 SyncError(끝 코드 2)"""
    request = urllib.request.Request(url, data=data, method='PUT', headers={
        'Authorization': f'Bearer {ticket}', 'Content-Type': kind, 'Accept': 'application/json',
        'User-Agent': f'{CLIENT["name"]}/{CLIENT["version"]}'})
    try:
        with urllib.request.urlopen(request, context=context, timeout=UPLOAD_TIMEOUT) as response:
            raw = response.read()
    except urllib.error.HTTPError as e:
        raise FileFailed(f'올리지 못함 — HTTP {e.code} {mcp.problem_code(e)}'.rstrip())
    except urllib.error.URLError as e:
        if isinstance(e.reason, (ssl.SSLError, ConnectionRefusedError, socket.gaierror)):
            hint = ' — 로컬 인증 기관(infra/tls/certs/ca.pem)을 --ca 로 주십시오' if isinstance(e.reason, ssl.SSLError) else ''
            raise mcp.SyncError(f'올리는 곳에 붙지 못했습니다: {e.reason}{hint}')
        raise FileFailed(f'올리지 못함 — {e.reason}')
    except OSError as e:  # 보내는 사이 끊김 · 시간 초과
        raise FileFailed(f'올리지 못함 — {type(e).__name__}')
    try:
        view = json.loads(raw.decode('utf-8'))
    except (UnicodeDecodeError, json.JSONDecodeError):
        raise FileFailed('올렸지만 응답을 읽을 수 없음')
    if not isinstance(view, dict):
        raise FileFailed('올렸지만 응답을 읽을 수 없음')
    return view


def attach_one(client, mcp_url, context, path, number, workspace, project, alt):
    """파일 하나 — 티켓을 받고 바로 올린다(10분 안에 쓰도록 파일마다 새로). @return markdown 한 줄"""
    try:
        with open(path, 'rb') as f:
            data = f.read()
    except OSError as e:
        raise FileFailed(f'파일을 읽을 수 없음 — {e.strerror or type(e).__name__}')
    name = os.path.basename(path)
    kind = content_type(name)
    arguments = {'number': number, 'workspace': workspace, 'fileName': name, 'size': len(data), 'contentType': kind}
    if project:
        arguments['project'] = project
    got = client.call('request_attachment_upload', arguments)
    if not isinstance(got, dict):
        raise FileFailed('티켓 결과를 읽을 수 없음')
    if 'error' in got:
        error = got['error'] if isinstance(got['error'], dict) else {}
        raise FileFailed(f'티켓을 받지 못함 — {error.get("code", "")} {error.get("message", "")}'.rstrip())
    ticket = got.get('ticket')
    if not isinstance(ticket, str) or not ticket:
        raise FileFailed('티켓 결과에 ticket 이 없음')
    view = put(upload_url(mcp_url, got.get('uploadUrl')), ticket, data, kind, context)
    return markdown_line(view, alt)


def attach(client, mcp_url, context, number, files, workspace, project=None, alts=None):
    """파일들을 차례대로 — 성공한 markdown 은 stdout 에 바로(인자 차례), 실패는 stderr 에. @return 실패한 파일 수"""
    failed = 0
    for i, path in enumerate(files):
        alt = (alts[0] if len(alts) == 1 else alts[i]) if alts else None
        try:
            line = attach_one(client, mcp_url, context, path, number, workspace, project, alt)
        except FileFailed as e:
            print(f'{path}: {e}', file=sys.stderr)
            failed += 1
            continue
        print(line, flush=True)
    return failed


def positive(text):
    value = int(text)
    if value < 1:
        raise argparse.ArgumentTypeError('1 이상이어야 합니다')
    return value


def main(argv=None):
    parser = argparse.ArgumentParser(description='moai-works 일감에 파일을 첨부하고 markdown 을 한 줄씩 찍는다.')
    parser.add_argument('number', type=positive, help='일감 번호(워크스페이스 번호)')
    parser.add_argument('files', nargs='+', help='올릴 파일들 — markdown 이 이 차례로 나온다')
    parser.add_argument('--workspace', required=True, help='워크스페이스 주소(list_workspaces 의 slug)')
    parser.add_argument('--project', help='프로젝트 key(비워도 된다 — 번호는 워크스페이스에서 하나)')
    parser.add_argument('--alt', action='append', help='markdown 의 [ ] 안 글 — 한 번이면 모두, 파일 수만큼이면 차례대로')
    parser.add_argument('--server', default=mcp.SERVER, help=f'~/.claude.json 의 MCP 서버 이름 (기본 {mcp.SERVER})')
    parser.add_argument('--config', default=os.path.join(os.path.expanduser('~'), '.claude.json'), help='Claude Code 설정 파일')
    parser.add_argument('--ca', default=mcp.DEFAULT_CA, help='로컬 인증 기관 (기본 infra/tls/certs/ca.pem)')
    args = parser.parse_args(argv)
    if args.alt and len(args.alt) not in (1, len(args.files)):
        parser.error(f'--alt 는 한 번이거나 파일 수({len(args.files)})만큼 줍니다')
    try:
        url, headers = mcp.load_server(args.config, args.server)
        context = mcp.tls_context(args.ca)
        client = mcp.McpClient(url, headers, context, client=CLIENT)
        client.open()
        try:
            failed = attach(client, url, context, args.number, args.files, args.workspace, args.project, args.alt)
        finally:
            client.close()
    except mcp.SyncError as e:
        print(f'오류: {e}', file=sys.stderr)
        return 2
    if failed:
        rest = '(나머지는 올림)' if failed < len(args.files) else ''
        print(f'{len(args.files)}개 가운데 {failed}개를 올리지 못했습니다{rest}.', file=sys.stderr)
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
