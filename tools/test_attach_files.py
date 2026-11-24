"""tools/attach_files.py 시험 — 가짜 MCP 서버 + 올리는 곳(평문 HTTP, 이 프로세스 안)에 붙어 markdown 이 인자 차례대로 나오는지 ·
정확한 크기 · 형식 짐작 · 한 파일이 실패해도 나머지를 올리고 끝 코드 1 · 설정이 없으면 2 · 키와 티켓을 찍지 않음을 본다(일감 #179).
리다이렉트(3xx)를 따르지 않아 키 · 티켓이 새 주소로 가지 않음도 본다(일감 #188). 덧붙는 모듈 없이 돈다.

    python3 -I -m unittest discover -s tools -p "test_*.py"
"""
import contextlib
import importlib.util
import io
import json
import os
import socket
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location('attach_files', os.path.join(HERE, 'attach_files.py'))
attach_files = importlib.util.module_from_spec(spec)
spec.loader.exec_module(attach_files)

KEY = 'mwk-test-SECRET-key-0123456789'


def problem(handler, status, code, detail):
    payload = json.dumps({'status': status, 'code': code, 'detail': detail}, ensure_ascii=False).encode('utf-8')
    handler.send_response(status)
    handler.send_header('Content-Type', 'application/problem+json')
    handler.send_header('Content-Length', str(len(payload)))
    handler.end_headers()
    handler.wfile.write(payload)


class FakeServer:
    """MCP Streamable HTTP(initialize · 알림 · tools/call 을 SSE 로) + 첨부 티켓으로 올리는 곳(PUT /mcp/files/upload, 201)"""

    def __init__(self):
        self.tickets = {}        # 티켓 → {fileName, size, contentType, used}
        self.calls = []          # request_attachment_upload 인자
        self.puts = []           # (Authorization, Content-Type, 본문 길이, 맡긴 티켓의 파일 이름)
        self.mcp_auth = []
        self.expire = set()      # 이 파일 이름의 티켓은 쓰기 전에 지난 것으로(401 TICKET_INVALID)
        self.size_skew = {}      # 파일 이름 → 티켓에 적을 크기의 차이(바뀐 파일 흉내 — 본문이 크면 413)
        self.upload_url = None   # 결과의 uploadUrl 을 바꿀 때
        server = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_DELETE(self):
                self.send_response(200)
                self.end_headers()

            def do_PUT(self):
                body = self.rfile.read(int(self.headers['Content-Length']))
                auth = self.headers.get('Authorization') or ''
                ticket = server.tickets.get(auth.removeprefix('Bearer '))
                server.puts.append((auth, self.headers.get('Content-Type'), len(body), ticket and ticket['fileName']))
                if self.path != '/mcp/files/upload' or ticket is None or ticket['used']:
                    return problem(self, 401, 'TICKET_INVALID', '첨부 티켓이 없거나, 이미 썼거나, 기간이 지났습니다.')
                ticket['used'] = True
                if len(body) > ticket['size']:
                    return problem(self, 413, 'FILE_TOO_LARGE', '파일이 허용된 크기를 넘습니다.')
                if len(body) != ticket['size']:
                    return problem(self, 400, 'SIZE_MISMATCH', '알린 크기와 다릅니다(가짜 서버만).')
                image = (ticket['contentType'] or '').startswith('image/')
                aid = f'att-{len(server.puts)}'
                view = {'id': aid, 'fileName': ticket['fileName'], 'contentType': ticket['contentType'], 'size': len(body),
                        'image': image, 'markdown': ('!' if image else '') + f'[{ticket["fileName"]}](attachment:{aid})'}
                payload = json.dumps(view, ensure_ascii=False).encode('utf-8')
                self.send_response(201)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)

            def do_POST(self):
                server.mcp_auth.append(self.headers.get('Authorization'))
                body = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
                if self.headers.get('Authorization') != f'Bearer {KEY}':
                    return problem(self, 401, 'MCP_KEY_INVALID', 'bad key')
                if 'id' not in body:
                    self.send_response(202)
                    self.end_headers()
                    return
                if body['method'] == 'initialize':
                    return self.reply(body['id'], {'protocolVersion': '2025-06-18', 'capabilities': {'tools': {}}}, sse=False, session=True)
                args = body['params']['arguments']
                server.calls.append(args)
                if args['size'] <= 0:
                    result = {'error': {'status': 400, 'code': 'EMPTY_FILE', 'message': '빈 파일은 올릴 수 없습니다.'}}
                else:
                    ticket = f'mwt_test-ticket-{len(server.tickets) + 1}'
                    server.tickets[ticket] = {'fileName': args['fileName'], 'contentType': args.get('contentType'),
                                              'size': args['size'] + server.size_skew.get(args['fileName'], 0),
                                              'used': args['fileName'] in server.expire}
                    result = {'number': args['number'], 'uploadUrl': server.upload_url or server.base + '/mcp/files/upload',
                              'method': 'PUT', 'ticket': ticket, 'headers': {'Authorization': f'Bearer {ticket}'},
                              'maxBytes': args['size'], 'expiresAt': 'x', 'note': '…'}
                self.reply(body['id'], {'content': [{'type': 'text', 'text': json.dumps(result, ensure_ascii=False)}]}, sse=True)

            def reply(self, rid, result, sse, session=False):
                message = json.dumps({'jsonrpc': '2.0', 'id': rid, 'result': result}, ensure_ascii=False)
                self.send_response(200)
                if session:
                    self.send_header('Mcp-Session-Id', 'session-1')
                if sse:
                    self.send_header('Content-Type', 'text/event-stream')
                    payload = f'id: 1\nevent: message\ndata: {message}\n\n'.encode('utf-8')
                else:
                    self.send_header('Content-Type', 'application/json')
                    payload = message.encode('utf-8')
                self.send_header('Content-Length', str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)

        self.httpd = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        self.base = f'http://127.0.0.1:{self.httpd.server_address[1]}'
        self.url = self.base + '/mcp/issue'
        threading.Thread(target=self.httpd.serve_forever, daemon=True).start()

    def stop(self):
        self.httpd.shutdown()
        self.httpd.server_close()


class Recorder:
    """아무 요청이나 받아 적고(메서드 · 경로 · 머리글) status 로 답한다 — location 을 주면 Location 머리글(리다이렉트 흉내)"""

    def __init__(self, status=404, location=None):
        self.status, self.location, self.requests = status, location, []
        recorder = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def handle_any(self):
                self.rfile.read(int(self.headers.get('Content-Length') or 0))
                recorder.requests.append((self.command, self.path, dict(self.headers)))
                self.send_response(recorder.status)
                if recorder.location:
                    self.send_header('Location', recorder.location)
                self.send_header('Content-Length', '0')
                self.end_headers()

            do_GET = do_POST = do_PUT = do_DELETE = handle_any

        self.httpd = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        self.base = f'http://127.0.0.1:{self.httpd.server_address[1]}'
        threading.Thread(target=self.httpd.serve_forever, daemon=True).start()

    def stop(self):
        self.httpd.shutdown()
        self.httpd.server_close()


class AttachFilesTest(unittest.TestCase):

    def setUp(self):
        self.server = FakeServer()
        self.dir = tempfile.TemporaryDirectory()
        self.config = os.path.join(self.dir.name, 'claude.json')
        self.write_config(KEY)

    def tearDown(self):
        self.server.stop()
        self.dir.cleanup()

    def write_config(self, key, servers=None):
        with open(self.config, 'w', encoding='utf-8') as f:
            json.dump({'mcpServers': servers if servers is not None else {
                'moai-works-issue': {'type': 'http', 'url': self.server.url, 'headers': {'Authorization': f'Bearer {key}'}}}}, f)

    def file(self, name, content=b'\x89PNG\r\n\x1a\n' + b'x' * 40):
        path = os.path.join(self.dir.name, name)
        with open(path, 'wb') as f:
            f.write(content)
        return path

    def run_tool(self, *args):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = attach_files.main(['--config', self.config, '--ca', '/nonexistent', '--workspace', 'ws-a', *args])
        text = out.getvalue() + err.getvalue()
        self.assertNotIn(KEY, text)  # 키는 찍지 않는다
        for ticket in self.server.tickets:
            self.assertNotIn(ticket, text)  # 티켓도
        return code, out.getvalue(), err.getvalue()

    def test_markdown_을_인자_차례대로_한_줄씩_찍는다(self):
        files = [self.file('b-after.png'), self.file('a-before.JPG', b'\xff\xd8\xff' + b'y' * 100), self.file('notes.md', '# 메모\n'.encode()),
                 self.file('dump.bin', b'\x00\x01'), self.file('data.json', b'{}')]
        code, out, err = self.run_tool('179', *files)
        self.assertEqual((code, err), (0, ''))
        self.assertEqual(out.splitlines(), ['![b-after.png](attachment:att-1)', '![a-before.JPG](attachment:att-2)',
                                            '[notes.md](attachment:att-3)', '[dump.bin](attachment:att-4)', '[data.json](attachment:att-5)'])
        # 크기는 정확한 바이트 수, 형식은 확장자로, 번호 · 워크스페이스를 주고 프로젝트는 비운다
        self.assertEqual([(c['fileName'], c['size'], c['contentType']) for c in self.server.calls],
                         [('b-after.png', 48, 'image/png'), ('a-before.JPG', 103, 'image/jpeg'), ('notes.md', len('# 메모\n'.encode()), 'text/markdown'),
                          ('dump.bin', 2, 'application/octet-stream'), ('data.json', 2, 'application/json')])
        self.assertTrue(all(c['number'] == 179 and c['workspace'] == 'ws-a' and 'project' not in c for c in self.server.calls))
        # 올릴 때는 티켓만 보낸다(MCP 키는 MCP 에만), Content-Type 은 짐작한 형식
        tickets = list(self.server.tickets)
        self.assertEqual([(a, t) for a, t, _, _ in self.server.puts],
                         [(f'Bearer {tickets[0]}', 'image/png'), (f'Bearer {tickets[1]}', 'image/jpeg'), (f'Bearer {tickets[2]}', 'text/markdown'),
                          (f'Bearer {tickets[3]}', 'application/octet-stream'), (f'Bearer {tickets[4]}', 'application/json')])
        self.assertTrue(all(a == f'Bearer {KEY}' for a in self.server.mcp_auth))

    def test_한_파일이_실패해도_나머지는_올리고_끝_코드_1(self):
        self.server.expire.add('expired.png')         # 티켓이 지남 → 401 TICKET_INVALID
        self.server.size_skew['grown.txt'] = -1        # 크기가 맞지 않음 → 413 FILE_TOO_LARGE
        files = [self.file('one.png'), self.file('expired.png'), self.file('grown.txt', b'hello'), self.file('empty.png', b''),
                 os.path.join(self.dir.name, 'missing.png'), self.file('two.gif', b'GIF89a')]
        code, out, err = self.run_tool('7', *files)
        self.assertEqual(code, 1)
        self.assertEqual(out.splitlines(), ['![one.png](attachment:att-1)', '![two.gif](attachment:att-4)'])
        self.assertIn('expired.png: 올리지 못함 — HTTP 401 TICKET_INVALID', err)
        self.assertIn('grown.txt: 올리지 못함 — HTTP 413 FILE_TOO_LARGE', err)
        self.assertIn('empty.png: 티켓을 받지 못함 — EMPTY_FILE', err)
        self.assertIn('missing.png: 파일을 읽을 수 없음', err)
        self.assertIn('6개 가운데 4개를 올리지 못했습니다', err)
        self.assertEqual([name for _, _, _, name in self.server.puts], ['one.png', 'expired.png', 'grown.txt', 'two.gif'])

    def test_alt_와_project(self):
        files = [self.file('x.png'), self.file('y.pdf', b'%PDF-1.7')]
        code, out, _ = self.run_tool('3', *files, '--project', 'moai-works', '--alt', '고친 뒤 ]')
        self.assertEqual(code, 0)
        self.assertEqual(out.splitlines(), ['![고친 뒤 \\]](attachment:att-1)', '[고친 뒤 \\]](attachment:att-2)'])
        self.assertTrue(all(c['project'] == 'moai-works' for c in self.server.calls))
        self.server.upload_url = '/mcp/files/upload'  # 상대 주소는 MCP 주소 기준
        code, out, _ = self.run_tool('3', *files, '--alt', '고치기\n전', '--alt', '설명서')
        self.assertEqual((code, out.splitlines()), (0, ['![고치기 전](attachment:att-3)', '[설명서](attachment:att-4)']))
        with self.assertRaises(SystemExit) as stop, contextlib.redirect_stderr(io.StringIO()):
            attach_files.main(['--config', self.config, '--workspace', 'ws-a', '3', *files, '--alt', 'a', '--alt', 'b', '--alt', 'c'])
        self.assertEqual(stop.exception.code, 2)

    def test_올릴_주소가_https_가_아니면_티켓을_보내지_않는다(self):
        self.assertEqual(attach_files.upload_url('https://localhost:11002/mcp/issue', '/mcp/files/upload'),
                         'https://localhost:11002/mcp/files/upload')
        with self.assertRaises(attach_files.FileFailed):
            attach_files.upload_url('https://localhost:11002/mcp/issue', 'http://localhost:11002/mcp/files/upload')
        with self.assertRaises(attach_files.FileFailed):
            attach_files.upload_url('https://localhost:11002/mcp/issue', None)

    def test_설정에_서버가_없으면_끝_코드_2_와_등록_방법(self):
        self.write_config(KEY, servers={})
        code, out, err = self.run_tool('1', self.file('a.png'))
        self.assertEqual((code, out), (2, ''))
        self.assertIn('claude mcp add --scope user', err)
        self.assertEqual(self.server.calls, [])

    def test_키가_틀리면_끝_코드_2_와_서버의_code_만(self):
        self.write_config(KEY + '-wrong')
        code, out, err = self.run_tool('1', self.file('a.png'))
        self.assertEqual((code, out), (2, ''))
        self.assertIn('MCP_KEY_INVALID', err)
        self.assertNotIn(KEY, err)

    def test_올리는_곳에_붙지_못하면_끝_코드_2(self):
        with socket.socket() as s:  # 닫힌 포트
            s.bind(('127.0.0.1', 0))
            port = s.getsockname()[1]
        self.server.upload_url = f'http://127.0.0.1:{port}/mcp/files/upload'
        code, out, err = self.run_tool('1', self.file('a.png'), self.file('b.png'))
        self.assertEqual((code, out), (2, ''))
        self.assertIn('올리는 곳에 붙지 못했습니다', err)
        self.assertEqual(len(self.server.calls), 1)  # 첫 파일에서 멈춘다

    # ── 리다이렉트를 따르지 않는다 (일감 #188) ──────────────────────────────

    def test_올리는_곳이_다른_주소로_보내면_티켓을_다시_보내지_않는다(self):
        elsewhere = Recorder(status=201)
        try:
            for status in (301, 302, 303, 307, 308):
                redirector = Recorder(status, location=elsewhere.base + '/mcp/files/upload')
                try:
                    self.server.upload_url = redirector.base + '/mcp/files/upload'
                    calls = len(self.server.calls)
                    code, out, err = self.run_tool('1', self.file('a.png'), self.file('b.png'))
                    self.assertEqual((code, out), (2, ''), status)
                    self.assertIn(f'올리는 곳이 다른 주소로 보냈습니다(HTTP {status})', err)
                    self.assertNotIn(elsewhere.base, err)
                    self.assertEqual(len(self.server.calls) - calls, 1, status)  # 첫 파일에서 멈춘다
                    # 티켓은 받은 올릴 주소에 한 번만 갔다
                    ticket = list(self.server.tickets)[-1]
                    self.assertEqual([(m, h.get('Authorization')) for m, _, h in redirector.requests], [('PUT', f'Bearer {ticket}')])
                finally:
                    redirector.stop()
            # 새 주소에는 아무 요청도 — 티켓이 실린 요청은 더욱 — 가지 않았다
            self.assertFalse(any('Authorization' in h for _, _, h in elsewhere.requests))
            self.assertEqual(elsewhere.requests, [])
        finally:
            elsewhere.stop()

    def test_MCP_주소가_다른_곳으로_보내도_키를_다시_보내지_않는다(self):
        elsewhere = Recorder(status=200)
        redirector = Recorder(302, location=elsewhere.base + '/mcp/issue')
        try:
            self.write_config(KEY, servers={'moai-works-issue': {'type': 'http', 'url': redirector.base + '/mcp/issue',
                                                                 'headers': {'Authorization': f'Bearer {KEY}'}}})
            code, out, err = self.run_tool('1', self.file('a.png'))
            self.assertEqual((code, out), (2, ''))
            self.assertIn('MCP 서버가 다른 주소로 보냈습니다(HTTP 302)', err)
            self.assertEqual(len(redirector.requests), 1)
            self.assertEqual(elsewhere.requests, [])
        finally:
            redirector.stop()
            elsewhere.stop()


if __name__ == '__main__':
    unittest.main(verbosity=2)
