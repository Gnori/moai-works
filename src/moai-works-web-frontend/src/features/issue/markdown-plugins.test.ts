import { describe, expect, it } from 'vitest';

import { issuePath } from '@/app/routes';

import { remarkIssueText } from './markdown-plugins';

interface Node {
  type: string;
  value?: string;
  url?: string;
  children?: Node[];
  data?: { hName?: string; hProperties?: Record<string, unknown> };
}

/** 문단 하나를 플러그인에 넣고 그 문단의 마디를 돌려받는다 */
function render(text: string, withLinks = true): Node[] {
  const tree: Node = { type: 'root', children: [{ type: 'paragraph', children: [{ type: 'text', value: text }] }] };
  remarkIssueText(withLinks ? { issuePath: (n) => `/issues/${n}` } : {})(tree);
  return tree.children?.[0]?.children ?? [];
}

describe('일감 본문의 표기', () => {
  it('#123 을 일감 링크로 바꾼다', () => {
    const nodes = render('#3 과 #12 를 먼저 끝낸다');
    expect(nodes.map((n) => n.type)).toEqual(['link', 'text', 'link', 'text']);
    expect(nodes[0]).toMatchObject({ url: '/issues/3', children: [{ value: '#3' }] });
    expect(nodes[2]).toMatchObject({ url: '/issues/12', children: [{ value: '#12' }] });
    expect(nodes[3]?.value).toBe(' 를 먼저 끝낸다');
  });

  it('번호가 아닌 # 는 그대로 둔다 (C#1 · &#123; · URL 조각 · #abc)', () => {
    for (const text of ['C#1 문법', '&#123;', 'a/#1', '#bug', '#12x', '## 아님']) {
      expect(render(text).map((n) => n.type), text).toEqual(['text']);
    }
  });

  it('주소를 모르면 바꾸지 않는다', () => {
    expect(render('#3', false)).toEqual([{ type: 'text', value: '#3' }]);
  });

  it('제목 끝의 {#anchor} 를 떼어 id 로 삼는다', () => {
    const heading: Node = { type: 'heading', children: [{ type: 'text', value: '재현 방법 {#steps}' }] };
    const tree: Node = { type: 'root', children: [heading] };
    remarkIssueText()(tree);
    expect(heading.children?.[0]?.value).toBe('재현 방법');
    expect(heading.data?.hProperties).toMatchObject({ id: 'steps' });
  });

  it('<br> 은 줄바꿈으로, 링크 · 코드 안은 손대지 않는다', () => {
    const tree: Node = {
      type: 'root',
      children: [
        {
          type: 'paragraph',
          children: [
            { type: 'html', value: '<br>' },
            { type: 'inlineCode', value: '#5' },
            { type: 'link', url: 'https://x', children: [{ type: 'text', value: '#6' }] },
          ],
        },
      ],
    };
    remarkIssueText({ issuePath: (n) => `/issues/${n}` })(tree);
    const nodes = tree.children?.[0]?.children ?? [];
    expect(nodes.map((n) => n.type)).toEqual(['break', 'inlineCode', 'link']);
    expect(nodes[2]?.children?.[0]?.value).toBe('#6');
    expect(nodes[2]?.url).toBe('https://x');
  });

  it('#123 은 워크스페이스의 정본 주소로 간다 — 번호는 워크스페이스 안에서 하나라 다른 프로젝트의 일감도 같은 모양이다(2026-11-24)', () => {
    const tree: Node = { type: 'root', children: [{ type: 'paragraph', children: [{ type: 'text', value: 'moai-wiki 쪽 #57 과 함께' }] }] };
    remarkIssueText({ issuePath: (n) => issuePath('workspace-979yhcty6vzd', n) })(tree);
    expect(tree.children?.[0]?.children?.[1]).toMatchObject({ type: 'link', url: '/w/workspace-979yhcty6vzd/issues/57', children: [{ value: '#57' }] });
  });
});
