"""tools/sync_skills.py 시험 — 가짜 MCP 서버(평문 HTTP, 이 프로세스 안)에 붙어 받기 · 같음 · 새 리비전 · 그 자리에서 고친 사본 건너뛰기 ·
--force · 출처 표시 줄이 없는 파일 · 폴더 밖 경로 거절 · 키를 찍지 않음 · 바뀐 점 요약(sinceRevision · latestChange · 줄이기 ·
옛 서버)을 본다. 받는 쪽 보호(일감 #188): 끝의 줄바꿈 거절 · 리다이렉트를 따르지 않음(키가 새 주소로 가지 않음) · 스킬 폴더 안의 링크 ·
대소문자만 다른 경로. 덧붙는 모듈 없이 돈다.

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
        self.calls = []  # (도구, 인자)
        # legacy: sinceRevision · latestChange 를 모르는 옛 서버(인자를 무시). strict: 모르는 인자를 JSON-RPC 오류로 거절하는 옛 서버
        self.legacy, self.strict = False, False
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
                server.calls.append((body['params']['name'], dict(args)))
                if body['params']['name'] == 'list_skills':
                    result = {'skills': [server.listed(n, s) for n, s in sorted(server.skills.items())], 'notice': '…'}
                elif 'sinceRevision' in args and server.strict:
                    self.reply_error(body['id'], -32602, 'Unknown argument: sinceRevision')
                    return
                else:
                    s = server.skills.get(args['name'])
                    if s is None:
                        result = {'error': {'status': 404, 'code': 'SKILL_NOT_FOUND', 'message': '없음'}}
                    else:
                        files = [{'path': 'SKILL.md', 'content': assemble(args['name'], s['description'], s['body'], s['revision'])}]
                        files += server.extra_files.get(args['name'], [])
                        result = {'name': args['name'], 'description': s['description'], 'revisionNo': s['revision'], 'updatedAt': 'x'}
                        if 'sinceRevision' in args and not server.legacy:
                            result['changes'] = [server.change(s, r) for r in range(args['sinceRevision'] + 1, s['revision'] + 1)]
                        result.update(files=files, notice='…')
                self.reply(body['id'], {'content': [{'type': 'text', 'text': json.dumps(result, ensure_ascii=False)}]}, sse=True)

            def reply_error(self, rid, code, message):
                payload = json.dumps({'jsonrpc': '2.0', 'id': rid, 'error': {'code': code, 'message': message}}).encode('utf-8')
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)

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
        self.handler = Handler
        self.url = f'http://127.0.0.1:{self.httpd.server_address[1]}/mcp/issue'
        threading.Thread(target=self.httpd.serve_forever, daemon=True).start()

    def change(self, s, revision):
        """get_skill 의 changes[] 한 줄 — 요약은 history 에서(없으면 요약 없는 옛 리비전)"""
        return {'revisionNo': revision, 'at': 'x', 'by': '관리자', 'kind': 'UPDATE', 'summary': s.get('history', {}).get(revision)}

    def listed(self, name, s):
        item = {'name': name, 'description': s['description'], 'revisionNo': s['revision'], 'updatedAt': 'x'}
        if not self.legacy:
            item['latestChange'] = {'revisionNo': s['revision'], 'at': 'x', 'summary': s.get('history', {}).get(s['revision'])}
        return item

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
        self.stdout = out.getvalue()
        text = out.getvalue() + err.getvalue()
        self.assertNotIn(KEY, text)  # 키는 찍지 않는다
        return code, text

    def write_config(self, url):
        with open(self.config, 'w', encoding='utf-8') as f:
            json.dump({'mcpServers': {'moai-works-issue': {'type': 'http', 'url': url, 'headers': {'Authorization': f'Bearer {KEY}'}}}}, f)

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
        self.server.skills['moai-works-issues']['history'] = {3: '지금 리비전의 요약'}
        code, text = self.run_tool()
        self.assertEqual(code, 0)
        self.assertIn('출처 표시 줄을 붙임', text)
        self.assertNotIn('지금 리비전의 요약', text)  # 새로 받은 것도 리비전을 견준 것도 아니다
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

    # ── 바뀐 점 요약 (일감 #183) ─────────────────────────────────────────

    def history(self, revision, **summaries):
        """서버의 스킬을 revision 으로 올리고 리비전마다 요약을 둔다(r3='…' 처럼)"""
        skill = self.server.skills['moai-works-issues']
        skill.setdefault('history', {}).update({int(k[1:]): v for k, v in summaries.items()})
        skill.update(body=f'# 일감\n\n리비전 {revision} 본문', revision=revision)

    def test_고쳐_받으면_사본의_리비전부터_바뀐_점을_찍는다(self):
        self.run_tool()
        self.history(5, r4='보류 · 취소 상태를 더함', r5='첨부 도구 attach_files.py 안내')
        code, text = self.run_tool()
        self.assertEqual(code, 0)
        self.assertIn('moai-works-issues: 리비전 3 → 5\n      [4] 보류 · 취소 상태를 더함\n      [5] 첨부 도구 attach_files.py 안내\n', text)
        self.assertIn(('get_skill', {'name': 'moai-works-issues', 'sinceRevision': 3}), self.server.calls)
        self.assertIn('리비전 5 본문', self.skill_md())
        # 시험 실행도 바뀐 점을 보인다(쓰지 않는다)
        self.history(6, r6='작업 시간 규칙')
        code, text = self.run_tool('--dry-run')
        self.assertIn('리비전 5 → 6 (시험 — 쓰지 않음)\n      [6] 작업 시간 규칙', text)
        self.assertIn('리비전 5 본문', self.skill_md())

    def test_새로_받으면_지금_리비전의_요약만_찍는다(self):
        self.history(3, r2='옛 요약', r3='처음 가져옴')
        code, text = self.run_tool()
        self.assertEqual(code, 0)
        self.assertIn('moai-works-issues: 새로 받음 (리비전 3)\n      [3] 처음 가져옴\n', text)
        self.assertNotIn('옛 요약', text)
        self.assertNotIn('sinceRevision', json.dumps(self.server.calls))

    def test_훅_모드는_바뀐_점을_한_줄로_줄여_찍는다(self):
        sync_skills.STAMP_DIR = os.path.join(self.dir.name, 'stamps')
        self.run_tool()
        self.history(3, r3='처음')
        long = '가' * 300
        self.history(10, r4='넷', r5='다섯', r6='여섯\n둘째 줄\x1b[31m빨강\u202e숨김', r7=long, r8='여덟', r10='열')
        code, text = self.run_tool('--hook', '--min-interval', '0')
        self.assertEqual(code, 0)
        self.assertEqual(text.count('\n'), 1)  # 한 줄
        self.assertTrue(text.startswith('moai-works 스킬이 바뀌어 새로 받았습니다 — moai-works-issues 리비전 3 → 10: [6] 여섯 둘째 줄 [31m빨강 숨김; '),
                        text)
        self.assertIn(f'[7] {"가" * 199}…; [8] 여덟; [9] (요약 없음); [10] 열; 외 앞선 2개 — 바뀐 점을 보고 SKILL.md 를 다시 읽으십시오.\n', text)
        self.assertNotIn('\x1b', text)
        self.assertNotIn('\u202e', text)
        # 새로 받는 스킬은 지금 리비전의 요약 하나
        self.server.skills['other-skill'] = {'description': '다른 스킬', 'body': '# 다른', 'revision': 2, 'history': {2: '규칙 정리'}}
        code, text = self.run_tool('--hook', '--min-interval', '0')
        self.assertEqual(text, 'moai-works 스킬이 바뀌어 새로 받았습니다 — other-skill 새로 받음(리비전 2): [2] 규칙 정리'
                               ' — 바뀐 점을 보고 SKILL.md 를 다시 읽으십시오.\n')

    def test_sinceRevision_을_모르는_옛_서버면_번호만_찍는다(self):
        sync_skills.STAMP_DIR = os.path.join(self.dir.name, 'stamps')
        self.run_tool()
        # 인자를 무시하는 옛 서버 — changes · latestChange 가 없다
        self.server.legacy = True
        self.history(4, r4='보이지 않음')
        code, text = self.run_tool('--hook', '--min-interval', '0')
        self.assertEqual(text, 'moai-works 스킬이 바뀌어 새로 받았습니다 — moai-works-issues 리비전 3 → 4'
                               ' — 바뀐 점을 보고 SKILL.md 를 다시 읽으십시오.\n')
        # 모르는 인자를 거절하는 옛 서버 — sinceRevision 없이 다시 묻고 받는다
        self.server.legacy, self.server.strict = True, True
        self.history(5, r5='보이지 않음')
        code, text = self.run_tool()
        self.assertEqual(code, 0)
        self.assertIn('moai-works-issues: 리비전 4 → 5\n', text)
        self.assertNotIn('보이지 않음', text)
        self.assertIn('리비전 5 본문', self.skill_md())
        self.assertEqual([args for tool, args in self.server.calls if tool == 'get_skill'][-2:],
                         [{'name': 'moai-works-issues', 'sinceRevision': 4}, {'name': 'moai-works-issues'}])

    def test_훅_모드는_받지_않은_스킬을_따로_알린다(self):
        sync_skills.STAMP_DIR = os.path.join(self.dir.name, 'stamps')
        self.run_tool()
        self.write_local(self.skill_md().replace('본문', '손으로 고침'))
        self.history(4, r4='서버에서 고침')
        code, text = self.run_tool('--hook', '--min-interval', '0')
        self.assertEqual(code, 0)
        self.assertNotIn('새로 받았습니다', text)
        self.assertIn('moai-works 스킬 중 받지 않은 것 — moai-works-issues: 건너뜀 — 그 자리에서 고친 사본', text)

    # ── 받는 쪽 보호 (일감 #188 — moai-wiki 세션이 찾음) ──────────────────────

    def folder(self, *parts):
        return os.path.join(self.target, 'moai-works-issues', *parts)

    def test_이름이나_경로_끝의_줄바꿈은_거절한다(self):
        # 규칙은 fullmatch — re.match 와 '$' 는 끝의 "\n" 을 통과시켰다
        for bad in ['a.md\n', 'scripts/run.sh\n', 'SKILL.md\n', 'scripts\n/run.sh']:
            self.server.extra_files['moai-works-issues'] = [{'path': bad, 'content': 'x'}]
            code, text = self.run_tool()
            self.assertEqual(code, 2, repr(bad))
            self.assertIn('받을 수 없는 파일 경로', text)
            self.assertFalse(os.path.exists(self.target), repr(bad))
        self.server.extra_files.clear()
        self.server.skills = {'moai-works-issues\n': self.server.skills['moai-works-issues']}
        code, text = self.run_tool()
        self.assertEqual(code, 2)
        self.assertIn("스킬 이름이 규칙에 맞지 않습니다: 'moai-works-issues\\n'", text)
        self.assertFalse(os.path.exists(self.target))
        # 출처 표시 줄은 CRLF 로 바뀐 파일에서도 읽는다
        marker, _ = sync_skills.split_marker(assemble('moai-works-issues', '설명', '# 본문', 3).replace('\n', '\r\n'))
        self.assertEqual(marker['revision'], 3)

    def test_MCP_주소가_다른_곳으로_보내도_따르지_않는다(self):
        sync_skills.STAMP_DIR = os.path.join(self.dir.name, 'stamps')
        elsewhere = Recorder(status=200)
        try:
            for status in (301, 302, 303, 307, 308):
                redirector = Recorder(status, location=elsewhere.base + '/mcp/issue')
                try:
                    self.write_config(redirector.base + '/mcp/issue')
                    code, text = self.run_tool()
                    self.assertEqual(code, 2, status)
                    self.assertIn(f'MCP 서버가 다른 주소로 보냈습니다(HTTP {status})', text)
                    self.assertNotIn(elsewhere.base, text)  # 새 주소도 찍지 않는다
                    # 훅은 조용히(stdout 비움) 끝 코드 0
                    code, text = self.run_tool('--hook', '--min-interval', '0')
                    self.assertEqual((code, self.stdout), (0, ''), status)
                    self.assertIn('다음에 다시', text)
                    # 등록한 주소(리다이렉트한 곳)만 키를 받았다 — 한 번 실행에 initialize 한 번
                    self.assertEqual([m for m, _, _ in redirector.requests], ['POST', 'POST'], status)
                finally:
                    redirector.stop()
            # 새 주소에는 아무 요청도 — 키가 실린 요청은 더욱 — 가지 않았다
            self.assertFalse(any('Authorization' in h for _, _, h in elsewhere.requests))
            self.assertEqual(elsewhere.requests, [])
            self.assertFalse(os.path.exists(self.target))
        finally:
            elsewhere.stop()

    def test_스킬_폴더_안의_링크는_따라가지_않고_그_스킬을_건너뛴다(self):
        sync_skills.STAMP_DIR = os.path.join(self.dir.name, 'stamps')
        outside = os.path.join(self.dir.name, 'outside')
        os.makedirs(outside)
        os.makedirs(self.folder())
        os.symlink(outside, self.folder('scripts'))
        self.server.extra_files['moai-works-issues'] = [{'path': 'scripts/run.sh', 'content': 'echo pwned\n'}]
        code, text = self.run_tool()
        self.assertEqual(code, 1)
        self.assertIn('moai-works-issues: 건너뜀 — 스킬 폴더 안의 링크 scripts', text)
        self.assertEqual(os.listdir(outside), [])
        self.assertFalse(os.path.exists(self.folder('SKILL.md')))  # 하나라도 막히면 SKILL.md 도 쓰지 않는다
        code, text = self.run_tool('--hook', '--min-interval', '0')  # 훅도 막지 않고 받지 않은 것으로 알린다
        self.assertEqual(code, 0)
        self.assertIn('받지 않은 것 — moai-works-issues: 건너뜀 — 스킬 폴더 안의 링크 scripts', text)
        self.assertEqual(os.listdir(outside), [])

        # SKILL.md 자체가 바깥 파일로 가는 링크 — 읽지도 쓰지도 않는다(--force 여도)
        os.unlink(self.folder('scripts'))
        victim = os.path.join(outside, 'victim.md')
        with open(victim, 'w', encoding='utf-8') as f:
            f.write('keep\n')
        os.symlink(victim, self.folder('SKILL.md'))
        self.server.extra_files.clear()
        code, text = self.run_tool('--force')
        self.assertEqual(code, 1)
        self.assertIn('스킬 폴더 안의 링크 SKILL.md', text)
        with open(victim, encoding='utf-8') as f:
            self.assertEqual(f.read(), 'keep\n')
        self.assertTrue(os.path.islink(self.folder('SKILL.md')))

        # SKILL.md 가 FIFO — 읽으면 훅이 멈춘다
        os.unlink(self.folder('SKILL.md'))
        os.mkfifo(self.folder('SKILL.md'))
        code, text = self.run_tool('--hook', '--min-interval', '0')
        self.assertEqual(code, 0)
        self.assertIn('SKILL.md 이 보통 파일이 아님', text)

    def test_스킬_폴더_자체가_링크면_따라간_폴더에_쓴다(self):
        # 전역 등록(~/.claude/skills/<name> → 저장소의 스킬 폴더)은 그대로 된다
        repo = os.path.join(self.dir.name, 'repo-skill')
        os.makedirs(repo)
        os.makedirs(self.target)
        os.symlink(repo, self.folder())
        self.server.extra_files['moai-works-issues'] = [{'path': 'scripts/run.sh', 'content': 'echo hi\n'}]
        code, _ = self.run_tool()
        self.assertEqual(code, 0)
        self.assertTrue(os.path.islink(self.folder()))
        self.assertTrue(os.path.isfile(os.path.join(repo, 'SKILL.md')))
        self.assertTrue(os.path.isfile(os.path.join(repo, 'scripts', 'run.sh')))
        # 끊긴 링크면 따라가 만들지 않는다
        os.unlink(self.folder())
        os.symlink(os.path.join(self.dir.name, 'gone'), self.folder())
        code, text = self.run_tool()
        self.assertEqual(code, 1)
        self.assertIn('끊긴 링크', text)
        self.assertFalse(os.path.exists(os.path.join(self.dir.name, 'gone')))

    def test_미리_본_뒤_링크로_바꿔치기해도_쓰기가_따라가지_않는다(self):
        # 검사(unsafe_destination)와 쓰기 사이에 링크가 생긴 경우 — write_skill 만으로 막는다(O_NOFOLLOW)
        outside = os.path.join(self.dir.name, 'outside')
        os.makedirs(outside)
        folder = self.folder()
        os.makedirs(folder)
        os.symlink(outside, os.path.join(folder, 'scripts'))
        refused = sync_skills.write_skill(folder, [('SKILL.md', 'x\n'), ('new/a.md', 'a\n'), ('scripts/run.sh', 'echo\n')])
        self.assertIn('scripts 이 링크이거나 폴더가 아님', refused)
        self.assertEqual(os.listdir(outside), [])
        self.assertEqual(os.listdir(folder), ['scripts'])  # SKILL.md 도, 임시 파일도, 새로 만든 폴더(new)도 남지 않는다
        victim = os.path.join(outside, 'victim.md')
        with open(victim, 'w', encoding='utf-8') as f:
            f.write('keep\n')
        os.symlink(victim, os.path.join(folder, 'SKILL.md'))
        refused = sync_skills.write_skill(folder, [('SKILL.md', 'x\n')])
        self.assertIn('SKILL.md 이 링크이거나 보통 파일이 아님', refused)
        with open(victim, encoding='utf-8') as f:
            self.assertEqual(f.read(), 'keep\n')
        # 새 스킬 폴더를 만들다 막히면(UTF-8 로 쓸 수 없는 글) 그 폴더도 치운다
        fresh = os.path.join(self.target, 'fresh')
        self.assertIsNotNone(sync_skills.write_skill(fresh, [('SKILL.md', 'ok\n'), ('b.md', '\ud800')]))
        self.assertFalse(os.path.exists(fresh))
        # 되면 None — 파일은 보통 권한(0644 에서 umask)
        self.assertIsNone(sync_skills.write_skill(fresh, [('SKILL.md', 'ok\n'), ('d/e.md', 'e\n')]))
        self.assertEqual(sorted(os.listdir(fresh)), ['SKILL.md', 'd'])

    def test_대소문자만_다른_경로와_skill_md_는_거절한다(self):
        # macOS(대소문자를 가리지 않는 파일 시스템)에서는 같은 파일이라 서로 덮어쓴다 — 그 스킬을 건너뛴다
        for extra in (['a.md', 'A.md'], ['Docs/a.md', 'docs/A.MD'], ['docs', 'docs/a.md'], ['scripts/run.sh', 'Scripts']):
            self.server.extra_files['moai-works-issues'] = [{'path': p, 'content': p} for p in extra]
            code, text = self.run_tool()
            self.assertEqual(code, 1, extra)
            self.assertIn('같은 경로가 둘(대소문자만 다른 것', text)
            self.assertFalse(os.path.exists(self.folder('SKILL.md')), extra)
        # 대소문자만 다른 SKILL.md 는 본 파일 자리 — 받을 수 없는 경로
        for bad in ['skill.md', 'Skill.MD', 'SKILL.MD']:
            self.server.extra_files['moai-works-issues'] = [{'path': bad, 'content': 'x'}]
            code, text = self.run_tool()
            self.assertEqual(code, 2, bad)
            self.assertIn(f'받을 수 없는 파일 경로 {bad!r}', text)
            self.assertFalse(os.path.exists(self.folder('SKILL.md')), bad)
        sync_skills.check_file_path('x', 'sub/skill.md')  # 하위 폴더의 것은 본 파일 자리가 아니다
        self.assertTrue(sync_skills.overlapping(['é.md', 'é.md']))  # 유니코드 정규화만 다름
        self.assertFalse(sync_skills.overlapping(['a/b.md', 'A/c.md']))  # 같은 폴더의 다른 파일
        # 겹치지 않으면 그대로 받는다
        self.server.extra_files['moai-works-issues'] = [{'path': 'a.md', 'content': 'a'}, {'path': 'b/A.md', 'content': 'b'}]
        code, _ = self.run_tool()
        self.assertEqual(code, 0)
        self.assertTrue(os.path.isfile(self.folder('b', 'A.md')))


if __name__ == '__main__':
    unittest.main(verbosity=2)
