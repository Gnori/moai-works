"""요구사항 진행 현황(docs/requirements/06-progress.md)을 만든다.

정본은 각 요구사항의 표에 있는 **진행** 줄과 수용 기준의 체크(`- [x]`)다. 이 도구는 그것을 읽어
한 쪽으로 모은다 — 현황 쪽은 손으로 고치지 않는다.

    python3 -I tools/req_progress.py           # 현황 쪽을 다시 만든다
    python3 -I tools/req_progress.py --check   # 규칙에 맞는지, 현황 쪽이 최신인지 확인한다(고치지 않는다)

진행 줄:  | 진행 | 할 일 |
          | 진행 | 일부 — 한 것: …. 남은 것: …. |
          | 진행 | 완료 (2026-11-21) — 메모 |
          | 진행 | 폐기 — 사유 |

규칙: 요구사항마다 진행 줄이 하나 있다 · `완료` 는 수용 기준이 모두 체크돼 있어야 한다 · `할 일` 에는 체크가 없다.
"""
import collections
import glob
import os
import re
import sys

ROOT = 'docs'
OUT = os.path.join(ROOT, 'requirements', '06-progress.md')
STATES = ('완료', '일부', '할 일', '폐기')
REQ = re.compile(r'<a id="((?:fr|nfr)-[a-z0-9]+-\d+)"></a>\n### (\S+) (.*?)\n(.*?)(?=\n<a id="(?:fr|nfr)-|\Z)', re.S)
AREAS = [  # 파일: 01-workspace · 02-project · 03-issue · 04-mcp · 05-non-functional
    ('FR-WSP', '워크스페이스 · 계정'), ('FR-PRJ', '프로젝트'), ('FR-ISS', '일감'), ('FR-MCP', 'MCP'), ('NFR', '비기능'),
]


def area_of(rid):
    return 'NFR' if rid.startswith('NFR') else rid.rsplit('-', 1)[0]


def load():
    """요구사항을 문서에 적힌 순서대로 읽는다."""
    reqs, problems = [], []
    for path in sorted(glob.glob(os.path.join(ROOT, 'requirements', '[0-9][0-9]-*.md'))):
        name = os.path.basename(path)
        if name in ('00-overview.md', os.path.basename(OUT)):
            continue
        for m in REQ.finditer(open(path, encoding='utf-8').read()):
            rid, title, body = m.group(2), m.group(3).strip(), m.group(4)
            prio = re.search(r'^\| 우선순위 \| \*{0,2}([MSCW])', body, re.M)
            rows = re.findall(r'^\| 진행 \| (.*?) \|$', body, re.M)
            crit = re.findall(r'^- \[( |x)\] (.*)$', body, re.M)
            state, when, memo = '할 일', None, ''
            if len(rows) != 1:
                problems.append(f'{rid}: 진행 줄이 {len(rows)}개다(하나여야 한다)')
            else:
                s = re.match(r'^(완료|일부|할 일|폐기)(?: \((\d{4}-\d{2}-\d{2})\))?(?: — (.*))?$', rows[0])
                if not s:
                    problems.append(f'{rid}: 진행 줄을 읽을 수 없다: {rows[0][:40]}')
                else:
                    state, when, memo = s.group(1), s.group(2), s.group(3) or ''
            done = sum(1 for c, _ in crit if c == 'x')
            if state == '완료' and (done != len(crit) or not when):
                problems.append(f'{rid}: 완료인데 수용 기준 {done}/{len(crit)} · 끝난 날 {when}')
            if state == '할 일' and done:
                problems.append(f'{rid}: 할 일인데 체크한 수용 기준이 {done}개 있다')
            if state == '일부' and '남은 것:' not in memo:
                problems.append(f'{rid}: 일부에는 "남은 것:" 을 적는다')
            reqs.append(dict(id=rid, title=title, file=name, prio=prio.group(1) if prio else '?', state=state, when=when,
                             memo=memo, done=done, total=len(crit),
                             left=[text for c, text in crit if c == ' ']))
    return reqs, problems


def wbs_tasks():
    """WBS 작업이 가리키는 요구사항 → [(작업 ID, 상태, 파일#앵커)]"""
    out = collections.defaultdict(list)
    for path in sorted(glob.glob(os.path.join(ROOT, 'wbs', '0[2-7]-*.md'))):
        for line in open(path, encoding='utf-8'):
            m = re.match(r'^\| <a id="(w-[0-9-]+)"></a>([0-9.]+) \| ', line)
            if not m:
                continue
            cells = line.rstrip('\n').split(' | ')
            status = cells[6] if len(cells) > 7 else '?'
            for rid in dict.fromkeys(re.findall(r'\[((?:FR|NFR)-[A-Z0-9]+-\d+)\]', cells[-1])):
                out[rid].append((m.group(2), status, f'../wbs/{os.path.basename(path)}#{m.group(1)}'))
    return out


def cell(text):
    return text.replace('|', '\\|').replace('\n', ' ')


def link(r):
    return f'[{r["id"]}]({r["file"]}#{r["id"].lower()}) {cell(r["title"])}'


def tasks_cell(tasks):
    return ' · '.join(f'[{tid}]({href}) {status}' for tid, status, href in tasks) or '—'


def render(reqs, tasks):
    by_state = collections.Counter(r['state'] for r in reqs)
    live = [r for r in reqs if r['state'] != '폐기']
    lines = [
        '[홈](../README.md) › [요구사항](00-overview.md) › 요구사항 진행 현황',
        '',
        '# 요구사항 진행 현황',
        '',
        '요구사항마다 어디까지 됐는지를 한 쪽에 모은다. **이 쪽은 `python3 -I tools/req_progress.py` 가 만든다 — 손으로 고치지 않는다.** '
        '정본은 각 요구사항의 표에 있는 **진행** 줄과 수용 기준의 체크(`- [x]`)다. 고치는 방법은 [요구사항 개요 §6](00-overview.md#6-진행-관리)에 있다.',
        '',
        '**바로가기:** [한눈에](#1-한눈에) · [하고 있는 요구사항](#2-하고-있는-요구사항) · [끝난 요구사항](#3-끝난-요구사항) · '
        '[아직 시작하지 않은 요구사항](#4-아직-시작하지-않은-요구사항)',
        '',
        '---',
        '',
        '## 1. 한눈에',
        '',
        f'요구사항 {len(live)}개 가운데 **끝난 것 {by_state["완료"]}개 · 하고 있는 것 {by_state["일부"]}개 · 시작하지 않은 것 {by_state["할 일"]}개**다. '
        f'수용 기준은 {sum(r["total"] for r in live)}개 가운데 {sum(r["done"] for r in live)}개를 확인했다.',
        '',
        '| 영역 | 전체 | 완료 | 일부 | 할 일 | 확인한 수용 기준 |',
        '|---|---|---|---|---|---|',
    ]
    for key, name in AREAS:
        rs = [r for r in live if area_of(r['id']) == key]
        c = collections.Counter(r['state'] for r in rs)
        lines.append(f'| {name} (`{key}`) | {len(rs)} | {c["완료"]} | {c["일부"]} | {c["할 일"]} | '
                     f'{sum(r["done"] for r in rs)} / {sum(r["total"] for r in rs)} |')
    c = collections.Counter(r['state'] for r in live)
    lines.append(f'| **합계** | **{len(live)}** | **{c["완료"]}** | **{c["일부"]}** | **{c["할 일"]}** | '
                 f'**{sum(r["done"] for r in live)} / {sum(r["total"] for r in live)}** |')
    lines += ['', '| 우선순위 | 전체 | 완료 | 일부 | 할 일 |', '|---|---|---|---|---|']
    for prio, name in (('M', 'M — 꼭 필요'), ('S', 'S — 있으면 좋음'), ('C', 'C — 나중')):
        rs = [r for r in live if r['prio'] == prio]
        c = collections.Counter(r['state'] for r in rs)
        lines.append(f'| {name} | {len(rs)} | {c["완료"]} | {c["일부"]} | {c["할 일"]} |')
    lines += [
        '',
        '- **완료**: 세부 요구사항을 모두 만들었고 수용 기준을 모두 확인했다. **일부**: 만든 것이 있고 남은 것이 있다. **할 일**: 아직 손대지 않았다.',
        '- 수용 기준은 자동 테스트나 실제 화면 · 흐름으로 확인한 것만 체크한다. 만들었지만 확인하지 못한 것은 체크하지 않는다.',
        '- "작업"은 그 요구사항을 다루는 [WBS](../wbs/00-overview.md) 작업과 그 상태다.',
        '',
        '## 2. 하고 있는 요구사항',
        '',
        '| 요구사항 | 우선 | 한 것 | 남은 것 | 수용 기준 | 작업 |',
        '|---|---|---|---|---|---|',
    ]
    for r in reqs:
        if r['state'] != '일부':
            continue
        did, _, left = r['memo'].partition('남은 것:')
        did = did.replace('한 것:', '').strip().rstrip('.').strip()
        lines.append(f'| {link(r)} | {r["prio"]} | {cell(did) or "—"} | {cell(left.strip().rstrip("."))} | '
                     f'{r["done"]} / {r["total"]} | {tasks_cell(tasks.get(r["id"], []))} |')
    lines += ['', '## 3. 끝난 요구사항', '', '| 요구사항 | 우선 | 끝난 날 | 메모 | 작업 |', '|---|---|---|---|---|']
    done = [r for r in reqs if r['state'] == '완료']
    for r in done:
        lines.append(f'| {link(r)} | {r["prio"]} | {r["when"]} | {cell(r["memo"]) or "—"} | {tasks_cell(tasks.get(r["id"], []))} |')
    if not done:
        lines.append('| — | | | | |')
    lines += ['', '## 4. 아직 시작하지 않은 요구사항', '']
    for key, name in AREAS:
        rs = [r for r in reqs if area_of(r['id']) == key and r['state'] == '할 일']
        if not rs:
            continue
        lines += [f'### {name}', '', '| 요구사항 | 우선 | 작업 |', '|---|---|---|']
        lines += [f'| {link(r)} | {r["prio"]} | {tasks_cell(tasks.get(r["id"], []))} |' for r in rs]
        lines.append('')
    dropped = [r for r in reqs if r['state'] == '폐기']
    if dropped:
        lines += ['## 5. 폐기한 요구사항', '', '| 요구사항 | 사유 |', '|---|---|']
        lines += [f'| {link(r)} | {cell(r["memo"])} |' for r in dropped]
        lines.append('')
    lines += [
        '---',
        '',
        '**관련 문서:** [요구사항 전체 목록](00-overview.md) · [WBS 한눈에](../wbs/00-overview.md) · [구현한 기능](../wbs/01-implemented.md)',
        '',
        '[← 이전: 비기능 요구사항](05-non-functional.md) | [다음: 핵심 설계 결정 →](../design/00-key-decisions.md)',
    ]
    return '\n'.join(lines) + '\n'


def main():
    check = '--check' in sys.argv[1:]
    reqs, problems = load()
    text = render(reqs, wbs_tasks())
    if check:
        current = open(OUT, encoding='utf-8').read() if os.path.exists(OUT) else ''
        if current != text:
            problems.append(f'{OUT} 이 최신이 아니다 — python3 -I tools/req_progress.py 로 다시 만든다')
    else:
        with open(OUT, 'w', encoding='utf-8') as f:
            f.write(text)
    c = collections.Counter(r['state'] for r in reqs)
    print(f'요구사항 진행: 완료 {c["완료"]} · 일부 {c["일부"]} · 할 일 {c["할 일"]}'
          + (f' · 폐기 {c["폐기"]}' if c['폐기'] else '')
          + f', 수용 기준 {sum(r["done"] for r in reqs)}/{sum(r["total"] for r in reqs)}, 문제 {len(problems)}건')
    for p in problems:
        print('  ', p)
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
