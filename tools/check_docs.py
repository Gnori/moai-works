"""moai-works 문서 검사: 상대 링크·앵커, 요구사항 앵커, 위키 이동(이전/다음) 연결.

사용법: python3 -I tools/check_docs.py docs
- 이동 순서: README → 01 → 02 → requirements → design → modules → plan → wbs → README.
- 링크·앵커는 docs/ 아래에 더해 저장소 맨 위의 규칙 문서(*.md)와 모듈 폴더의 문서(src/*/*.md)도 본다.
- 요구사항 진행: 요구사항마다 진행 줄이 규칙에 맞는지, 진행 현황 쪽(requirements/14-progress.md)이 최신인지 본다
  (tools/req_progress.py --check 와 같다).
- 코드 표기 안의 예시 링크(`[텍스트](URL)`, `[홈](...)`)는 알려진 오탐으로 걸러서 보고합니다.
"""
import collections
import os
import re
import subprocess
import sys
import unicodedata

ROOT = sys.argv[1] if len(sys.argv) > 1 else 'docs'
LINK = re.compile(r'(?<!!)\[([^\]]*)\]\(([^)\s]+)\)')
HEAD = re.compile(r'^(#{1,6})\s+(.*?)\s*#*\s*$')
AID = re.compile(r'<a\s+(?:id|name)="([^"]+)"')
FENCE = re.compile(r'^(```|~~~)')
KNOWN_FALSE_POSITIVE = {'...', '…', 'URL'}


def slug(text):
    """GitHub 제목 앵커 규칙."""
    text = re.sub(r'`([^`]*)`', r'\1', text)
    text = re.sub(r'\[([^\]]*)\]\([^)]*\)', r'\1', text)
    text = re.sub(r'<[^>]+>', '', text).strip().lower()
    out = [ch for ch in text if ch in ' -_' or unicodedata.category(ch)[0] in ('L', 'N') or unicodedata.category(ch) == 'Mn']
    return ''.join(out).replace(' ', '-')


def anchors_of(path):
    res, counts, in_fence = set(), collections.defaultdict(int), False
    with open(path, encoding='utf-8') as f:
        for line in f:
            if FENCE.match(line.strip()):
                in_fence = not in_fence
                continue
            if in_fence:
                continue
            res.update(AID.findall(line))
            m = HEAD.match(line)
            if m:
                s = slug(m.group(2))
                res.add(s if counts[s] == 0 else f'{s}-{counts[s]}')
                counts[s] += 1
    return res


def check_links(md_files):
    cache, problems = {}, []
    for path in md_files:
        in_fence = False
        with open(path, encoding='utf-8') as f:
            for no, line in enumerate(f, 1):
                if FENCE.match(line.strip()):
                    in_fence = not in_fence
                    continue
                if in_fence:
                    continue
                for m in LINK.finditer(line):
                    target = m.group(2)
                    if re.match(r'^[a-z]+:', target) or target in KNOWN_FALSE_POSITIVE:
                        continue
                    file_part, _, anchor = target.partition('#')
                    dest = os.path.normpath(os.path.join(os.path.dirname(path), file_part)) if file_part else path
                    rel = os.path.relpath(path, ROOT)
                    if not os.path.exists(dest):
                        problems.append(f'{rel}:{no}: 파일 없음 -> {target}')
                    elif anchor and dest.endswith('.md'):
                        cache.setdefault(dest, anchors_of(dest))
                        if anchor not in cache[dest]:
                            problems.append(f'{rel}:{no}: 앵커 없음 -> {target}')
    return problems


def check_catalog():
    req = os.path.join(ROOT, 'requirements')
    cat = open(os.path.join(req, '00-overview.md'), encoding='utf-8').read()
    ids = re.findall(r'^\| \[((?:FR|NFR)-[A-Z0-9]+-\d+)\]\(([^)#]+)#', cat, re.M)
    found = collections.Counter()
    for f in os.listdir(req):
        for a in AID.findall(open(os.path.join(req, f), encoding='utf-8').read()):
            found[(f, a)] += 1
    bad = [f'{i} ({f})' for i, f in ids if found[(f, i.lower())] != 1]
    prio = collections.Counter(re.findall(r'^\| \[(?:FR|NFR)-[A-Z0-9]+-\d+\]\([^)]*\) \| .*? \| ([MSCW]) \|$', cat, re.M))
    return len(ids), dict(prio), bad


def check_nav():
    def listing(sub):
        d = os.path.join(ROOT, sub)
        return [f'{sub}/{f}' for f in sorted(os.listdir(d)) if f.endswith('.md')]
    order = (['README.md', '01-overview.md', '02-glossary.md'] + listing('requirements') + listing('design')
             + listing('modules') + listing('plan') + listing('wbs'))
    problems = []
    for i, f in enumerate(order):
        if f == 'README.md':
            continue
        lines = open(os.path.join(ROOT, f), encoding='utf-8').read().rstrip().split('\n')
        if not lines[0].startswith('[홈]'):
            problems.append(f'{f}: 첫 줄 경로(breadcrumb) 없음')
        links = re.findall(r'\[([←다][^\]]*)\]\(([^)]+)\)', lines[-1])
        got = [os.path.normpath(os.path.join(os.path.dirname(f), t)) for _, t in links]
        want = [order[i - 1], order[i + 1] if i + 1 < len(order) else 'README.md']
        if got != want:
            problems.append(f'{f}: 이전/다음 {got} → 기대값 {want}')
    return problems


if __name__ == '__main__':
    md = sorted(os.path.join(d, f) for d, _, fs in os.walk(ROOT) for f in fs if f.endswith('.md'))
    repo = os.path.dirname(os.path.abspath(ROOT))
    rules = sorted(os.path.join(repo, f) for f in os.listdir(repo) if f.endswith('.md'))
    src = os.path.join(repo, 'src')
    modules = sorted(os.path.join(src, m, f) for m in os.listdir(src) if os.path.isdir(os.path.join(src, m))
                     for f in os.listdir(os.path.join(src, m)) if f.endswith('.md'))
    md += [os.path.relpath(p) for p in rules + modules]
    link_problems = check_links(md)
    n, prio, bad = check_catalog()
    nav_problems = check_nav()
    print(f'문서 {len(md)}개 검사')
    print(f'링크·앵커 문제 {len(link_problems)}건')
    for p in link_problems:
        print('  ', p)
    print(f'요구사항 {n}개 {prio}, 앵커 누락·중복 {len(bad)}건 {bad[:5]}')
    print(f'이동 연결 문제 {len(nav_problems)}건')
    for p in nav_problems:
        print('  ', p)
    # 요구사항 진행 — 진행 줄의 규칙과 현황 쪽이 최신인지 (저장소 맨 위에서 돈다)
    progress = subprocess.run([sys.executable, '-I', os.path.join(repo, 'tools', 'req_progress.py'), '--check'],
                              cwd=repo, capture_output=True, text=True)
    print(progress.stdout.rstrip())
    sys.exit(1 if (link_problems or bad or nav_problems or progress.returncode) else 0)
