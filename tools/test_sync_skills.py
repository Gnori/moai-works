"""tools/sync_skills.py 시험 — 가짜 MCP 서버(평문 HTTP, 이 프로세스 안)에 붙어 받기 · 같음 · 새 리비전 · 그 자리에서 고친 사본 건너뛰기 ·
--force · 출처 표시 줄이 없는 파일 · 폴더 밖 경로 거절 · 키를 찍지 않음을 본다. 덧붙는 모듈 없이 돈다.

    python3 -I tools/test_sync_skills.py
"""
import contextlib
import hashlib
import importlib.util
import io
import json
import os
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location('sync_skills', os.path.join(HERE, 'sync_skills.py'))
sync_skills = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync_skills)

KEY = 'mwk-test-SECRET-key-0123456789'


def assemble(name, description, body, revision):
    """서버(SkillFile.assemble)와 같은 모양 — 설명은 늘 큰따옴표 한 줄, 지문은 출처 표시 줄을 뺀 파일의 sha256"""
    head = f'---\nname: {name}\ndescription: {json.dumps(description, ensure_ascii=False)}\n---\n'
    plain = head + '\n' + body + '\n'
    sha = hashlib.sha256(plain.encode('utf-8')).hexdigest()
    marker = (f'<!-- moai-works-skill name={name} revision={revision} sha256={sha} · 원본은 moai-works 시스템 관리 › 스킬입니다. '
              '이 파일을 고치지 말고 거기서 수정한 뒤 python3 -I tools/sync_skills.py 로 받으십시오 -->')
    return head + marker + '\n\n' + body + '\n'


class FakeServer:
    """MCP Streamable HTTP 흉내 — initialize(JSON) · 알림(202) · tools/call(SSE) · 세션 끝(DELETE)"""

    def __init__(self):
        self.skills = {}
        self.extra_files = {}
        self.authorizations = []
        server = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_DELETE(self):
                self.send_response(200)
                self.end_headers()

            def do_POST(self):
                server.authorizations.append(self.headers.get('Authorization'))
                body = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
                if self.headers.get('Authorization') != f'Bearer {KEY}':
                    self.send_response(401)
                    self.send_header('Content-Type', 'application/json')
                    self.end_headers()
                    self.wfile.write(b'{"code":"MCP_KEY_INVALID","detail":"bad key"}')
                    return
                if 'id' not in body:
                    self.send_response(202)
                    self.end_headers()
                    return
                if body['method'] == 'initialize':
                    self.reply(body['id'], {'protocolVersion': '2025-06-18', 'capabilities': {'tools': {}}}, sse=False, session=True)
                    return
                args = body['params']['arguments']
                if body['params']['name'] == 'list_skills':
                    result = {'skills': [{'name': n, 'description': s['description'], 'revisionNo': s['revision'], 'updatedAt': 'x'}
                                         for n, s in sorted(server.skills.items())], 'notice': '…'}
                else:
                    s = server.skills.get(args['name'])
                    if s is None:
                        result = {'error': {'status': 404, 'code': 'SKILL_NOT_FOUND', 'message': '없음'}}
                    else:
                        files = [{'path': 'SKILL.md', 'content': assemble(args['name'], s['description'], s['body'], s['revision'])}]
                        files += server.extra_files.get(args['name'], [])
                        result = {'name': args['name'], 'description': s['description'], 'revisionNo': s['revision'], 'updatedAt': 'x',
                                  'files': files, 'notice': '…'}
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
        self.url = f'http://127.0.0.1:{self.httpd.server_address[1]}/mcp/issue'
        threading.Thread(target=self.httpd.serve_forever, daemon=True).start()

    def stop(self):
        self.httpd.shutdown()
        self.httpd.server_close()


class SyncSkillsTest(unittest.TestCase):

    def setUp(self):
        self.server = FakeServer()
        self.server.skills['moai-works-issues'] = {'description': '일감 다루기', 'body': '# 일감\n\n본문', 'revision': 3}
        self.dir = tempfile.TemporaryDirectory()
        self.target = os.path.join(self.dir.name, 'skills')
        self.config = os.path.join(self.dir.name, 'claude.json')
        with open(self.config, 'w', encoding='utf-8') as f:
            json.dump({'mcpServers': {'moai-works-issue': {'type': 'http', 'url': self.server.url,
                                                           'headers': {'Authorization': f'Bearer {KEY}'}}}}, f)

    def tearDown(self):
        self.server.stop()
        self.dir.cleanup()

    def run_tool(self, *extra):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = sync_skills.main(['--config', self.config, '--target', self.target, '--ca', '/nonexistent', *extra])
        text = out.getvalue() + err.getvalue()
        self.assertNotIn(KEY, text)  # 키는 찍지 않는다
        return code, text

    def skill_md(self, name='moai-works-issues'):
        with open(os.path.join(self.target, name, 'SKILL.md'), encoding='utf-8') as f:
            return f.read()

    def write_local(self, text, name='moai-works-issues'):
        os.makedirs(os.path.join(self.target, name), exist_ok=True)
        with open(os.path.join(self.target, name, 'SKILL.md'), 'w', encoding='utf-8') as f:
            f.write(text)

    def test_받고_같으면_그대로_새_리비전이면_바꾼다(self):
        code, text = self.run_tool()
        self.assertEqual(code, 0)
        self.assertIn('새로 받음', text)
        self.assertEqual(self.skill_md(), assemble('moai-works-issues', '일감 다루기', '# 일감\n\n본문', 3))
        self.assertTrue(all(a == f'Bearer {KEY}' for a in self.server.authorizations))

        code, text = self.run_tool()
        self.assertEqual(code, 0)
        self.assertIn('리비전 3 그대로', text)

        self.server.skills['moai-works-issues'].update(body='# 일감\n\n고친 본문', revision=4)
        code, text = self.run_tool()
        self.assertEqual(code, 0)
        self.assertIn('리비전 3 → 4', text)
        self.assertIn('고친 본문', self.skill_md())

    def test_그_자리에서_고친_사본은_force_없이_덮어쓰지_않는다(self):
        self.run_tool()
        edited = self.skill_md().replace('본문', '손으로 고침')
        self.write_local(edited)
        self.server.skills['moai-works-issues'].update(body='# 일감\n\n서버에서 고침', revision=4)
        code, text = self.run_tool()
        self.assertEqual(code, 1)
        self.assertIn('그 자리에서 고친 사본', text)
        self.assertEqual(self.skill_md(), edited)
        # 시험 실행은 쓰지 않는다
        code, text = self.run_tool('--dry-run', '--force')
        self.assertEqual(self.skill_md(), edited)
        code, text = self.run_tool('--force')
        self.assertEqual(code, 0)
        self.assertIn('서버에서 고침', self.skill_md())

    def test_출처_표시_줄이_없는_파일은_내용이_같을_때만_받아_쓴다(self):
        # 손으로 만든 파일(설명에 따옴표 없음 · 빈 줄 · 끝 줄바꿈이 조금 다름) — 내용이 같으면 출처 표시 줄을 붙인다
        self.write_local('---\nname: moai-works-issues\ndescription: 일감 다루기\n---\n\n\n# 일감\n\n본문\n\n')
        code, text = self.run_tool()
        self.assertEqual(code, 0)
        self.assertIn('출처 표시 줄을 붙임', text)
        self.assertIn('<!-- moai-works-skill name=moai-works-issues revision=3 ', self.skill_md())
        # 내용이 다르면 건너뛴다
        self.write_local('---\nname: moai-works-issues\ndescription: 일감 다루기\n---\n\n# 다른 본문\n')
        code, text = self.run_tool()
        self.assertEqual(code, 1)
        self.assertIn('손으로 고친 파일', text)

    def test_리비전이_서버보다_앞선_사본과_서버에_없는_스킬(self):
        self.write_local(assemble('moai-works-issues', '일감 다루기', '# 일감\n\n본문', 9))
        self.write_local(assemble('gone-skill', '옛 스킬', '# 옛', 2), name='gone-skill')
        code, text = self.run_tool()
        self.assertEqual(code, 1)
        self.assertIn('사본의 리비전 9', text)
        self.assertIn('gone-skill: 서버에 없는 사본(리비전 2) — 지우지 않음', text)
        self.assertTrue(os.path.exists(os.path.join(self.target, 'gone-skill', 'SKILL.md')))
        code, text = self.run_tool('--name', 'no-such')
        self.assertEqual(code, 1)
        self.assertIn('no-such: 서버에 없음', text)

    def test_딸린_파일은_경로_규칙을_지키면_함께_쓴다(self):
        self.server.extra_files['moai-works-issues'] = [{'path': 'scripts/run.sh', 'content': 'echo hi\n'}]
        code, _ = self.run_tool()
        self.assertEqual(code, 0)
        with open(os.path.join(self.target, 'moai-works-issues', 'scripts', 'run.sh'), encoding='utf-8') as f:
            self.assertEqual(f.read(), 'echo hi\n')
        # SKILL.md 가 둘이면 건너뛴다
        self.server.extra_files['moai-works-issues'] = [{'path': 'SKILL.md', 'content': 'x'}]
        self.server.skills['moai-works-issues']['revision'] = 4
        code, text = self.run_tool()
        self.assertEqual(code, 1)
        self.assertIn('SKILL.md 가 하나가 아니거나', text)

    def test_moai_wiki_의_경로_규칙에_어긋나면_아무것도_쓰지_않는다(self):
        for bad in ['/abs.md', 'a/../b.md', './a.md', 'a b.md', 'a/b/c/d/e.md', 'x' * 201, 'dir\\win.md', 'a//b.md', '한글.md']:
            self.server.extra_files['moai-works-issues'] = [{'path': bad, 'content': 'x'}]
            code, text = self.run_tool()
            self.assertEqual(code, 2, bad)
            self.assertIn('받을 수 없는 파일 경로', text)
            self.assertFalse(os.path.exists(os.path.join(self.target, 'moai-works-issues', 'SKILL.md')), bad)

    def test_폴더_밖을_가리키는_파일_경로는_거절한다(self):
        self.server.extra_files['moai-works-issues'] = [{'path': '../escape.md', 'content': 'x'}]
        code, text = self.run_tool()
        self.assertEqual(code, 2)
        self.assertIn('받을 수 없는 파일 경로', text)
        self.assertFalse(os.path.exists(os.path.join(self.target, 'escape.md')))
        # 하나라도 폴더 밖이면 SKILL.md 도 쓰지 않는다
        self.assertFalse(os.path.exists(os.path.join(self.target, 'moai-works-issues', 'SKILL.md')))

    def test_키가_틀리면_서버의_code_만_알린다(self):
        with open(self.config, 'w', encoding='utf-8') as f:
            json.dump({'mcpServers': {'moai-works-issue': {'type': 'http', 'url': self.server.url,
                                                           'headers': {'Authorization': f'Bearer {KEY}-wrong'}}}}, f)
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = sync_skills.main(['--config', self.config, '--target', self.target, '--ca', '/nonexistent'])
        self.assertEqual(code, 2)
        self.assertIn('MCP_KEY_INVALID', err.getvalue())
        self.assertNotIn(KEY, out.getvalue() + err.getvalue())

    def test_설정에_서버가_없으면_등록_방법을_알린다(self):
        with open(self.config, 'w', encoding='utf-8') as f:
            json.dump({'mcpServers': {}}, f)
        code, text = self.run_tool()
        self.assertEqual(code, 2)
        self.assertIn('claude mcp add --scope user', text)


    def test_훅_모드는_바뀐_것만_한_줄로_알리고_창_안에서는_묻지_않는다(self):
        sync_skills.STAMP_DIR = os.path.join(self.dir.name, 'stamps')
        code, text = self.run_tool('--hook', '--min-interval', '0')
        self.assertEqual(code, 0)
        self.assertIn('새로 받았습니다', text)
        self.assertIn('moai-works-issues', text)
        code, text = self.run_tool('--hook', '--min-interval', '0')
        self.assertEqual((code, text), (0, ''))  # 그대로면 아무것도 찍지 않는다
        self.server.skills['moai-works-issues'].update(body='# 일감\n\n새 본문', revision=4)
        calls = len(self.server.authorizations)
        code, text = self.run_tool('--hook', '--min-interval', '600')  # 처음 보는 창 — 묻고 받는다
        self.assertIn('리비전 3 → 4', text)
        self.server.skills['moai-works-issues'].update(body='# 일감\n\n더 새 본문', revision=5)
        calls = len(self.server.authorizations)
        code, text = self.run_tool('--hook', '--min-interval', '600')  # 10분 안 — 서버에 묻지 않는다
        self.assertEqual((code, text), (0, ''))
        self.assertEqual(len(self.server.authorizations), calls)

    def test_훅_모드는_연결이_안_돼도_막지_않는다(self):
        sync_skills.STAMP_DIR = os.path.join(self.dir.name, 'stamps')
        self.server.stop()
        code, text = self.run_tool('--hook', '--min-interval', '0')
        self.assertEqual(code, 0)
        self.assertIn('다음에 다시', text)  # stderr 한 줄 — 모델 문맥(stdout)에는 들어가지 않는다
        self.server = FakeServer()


if __name__ == '__main__':
    unittest.main(verbosity=2)
