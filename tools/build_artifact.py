"""docs/architecture.html(완전한 HTML 문서)에서 Artifact 게시용 본문 조각을 만든다.

Artifact 게시는 <!doctype>/<html>/<head>/<body>를 직접 감싸므로, 그 껍데기를 빼고
<title>·<link>·<style>과 body 안쪽만 남긴다.

사용법: python3 -I tools/build_artifact.py docs/architecture.html <출력 경로>
"""
import sys

src, out = sys.argv[1], sys.argv[2]
t = open(src, encoding='utf-8').read()
head = t[t.index('<title>'):t.index('</head>')]
body = t[t.index('<body>') + len('<body>'):t.index('</body>')]
head = head.replace('</title>', '</title>\n<meta name="claude:viewport-fit-sides" content="cover">', 1)
open(out, 'w', encoding='utf-8').write(head + body)
print(f'wrote {out}')
