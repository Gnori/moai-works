import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import i18n from '@/shared/i18n/i18n';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { MarkdownView } from './MarkdownView';

/**
 * 본문 코드 보기 — Markdown 그대로 그려(react-markdown · rehype-highlight · rehypeCodeLines · CodeBlock) 머리 · 줄 · 단추를 본다.
 * 모양(CSS)은 jsdom 이 그리지 않으므로 상자의 class · 단추의 상태로 본다.
 */

function view(content: string, dense = false) {
  return render(
    <AppearanceProvider>
      <MemoryRouter>
        <MarkdownView content={content} dense={dense} />
      </MemoryRouter>
    </AppearanceProvider>,
  );
}

const fence = (info: string, lines: string[]) => ['```' + info, ...lines, '```'].join('\n');
const numbered = (count: number) => Array.from({ length: count }, (_, i) => `line ${i + 1}`);

let writeText: ReturnType<typeof vi.fn>;

beforeEach(() => {
  writeText = vi.fn().mockResolvedValue(undefined);
  Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
});

afterEach(() => {
  vi.clearAllMocks();
});

describe('본문 코드 보기', () => {
  it('파일 이름 · 언어 이름 · 강조한 줄 · diff 의 더한 줄 / 뺀 줄을 그린다', () => {
    const source = ['--- a/src/app.ts', '+++ b/src/app.ts', '-const a = 1;', '+const a = 2;', ' const b = 3;'];
    const { container } = view(fence('diff title="src/app.ts" {3}', source));

    const block = container.querySelector('.code-block') as HTMLElement;
    expect(within(block).getByText('src/app.ts')).toHaveAttribute('title', 'src/app.ts');
    expect(within(block).getByText('Diff')).toBeInTheDocument();

    const lines = [...block.querySelectorAll('.code-line')];
    expect(lines).toHaveLength(5);
    expect(lines.map((line) => [...line.classList].filter((name) => name !== 'code-line'))).toEqual([
      [],
      [],
      ['code-line-mark', 'code-line-del'],
      ['code-line-add'],
      [],
    ]);
    // 색은 그대로 입혀져 있다
    expect(lines[2]?.querySelector('.hljs-deletion')).not.toBeNull();
    expect(lines[3]?.querySelector('.hljs-addition')).not.toBeNull();

    // 줄 번호 — 4줄 이상이라 처음부터 보이고, 번호는 글자가 아니다(aria-hidden · data-line)
    expect(block).toHaveClass('code-numbered');
    const gutter = lines[0]?.querySelector('.code-ln');
    expect(gutter).toHaveAttribute('aria-hidden', 'true');
    expect(gutter).toHaveAttribute('data-line', '1');
    expect(gutter?.textContent).toBe('');
    // <code> 의 글은 원래 글 그대로(골라서 복사해도 번호가 끼지 않는다)
    expect(block.querySelector('code')?.textContent).toBe(`${source.join('\n')}\n`);
  });

  it('여러 줄 주석은 줄마다 주석 색을 유지하고, 별칭(ts)으로 적어도 칠한다', () => {
    const { container } = view(fence('ts', ['/* 하나', '둘 */', 'const a = 1;']));
    const lines = [...container.querySelectorAll('.code-line')];
    expect(lines[0]?.querySelector('.hljs-comment')?.textContent).toBe('/* 하나');
    expect(lines[1]?.querySelector('.hljs-comment')?.textContent).toBe('둘 */');
    expect(lines[2]?.querySelector('.hljs-keyword')?.textContent).toBe('const');
    expect(screen.getByText('TypeScript')).toBeInTheDocument();
  });

  it('흔하지 않은 언어(```docker)는 그 언어를 받은 뒤 다시 칠한다 — 그동안에도 줄 · 이름은 그대로', async () => {
    const { container } = view(fence('docker', ['FROM node:22', 'RUN npm ci']));
    expect(screen.getByText('Dockerfile')).toBeInTheDocument();
    expect(container.querySelectorAll('.code-line')).toHaveLength(2);
    await waitFor(() => expect(container.querySelector('.code-line .hljs-keyword')?.textContent).toBe('FROM'));
    expect(container.querySelectorAll('.code-line')).toHaveLength(2);
  });

  it('그림 블록(mermaid)은 코드 상자로 그리지 않는다', () => {
    const { container } = view(fence('mermaid', ['flowchart TD', 'A-->B']));
    expect(container.querySelector('.code-block')).toBeNull();
    expect(container.querySelector('.code-line')).toBeNull();
  });

  it('긴 줄 바꿈 · 줄 번호 단추 — 누르면 켜고 끄고, 3줄 이하는 번호가 꺼진 채 시작한다', () => {
    const { container } = view(fence('js', ['a();', 'b();', 'c();']));
    const block = container.querySelector('.code-block') as HTMLElement;

    const wrap = screen.getByRole('button', { name: '긴 줄 바꿈' });
    expect(wrap).toHaveAttribute('aria-pressed', 'false');
    fireEvent.click(wrap);
    expect(wrap).toHaveAttribute('aria-pressed', 'true');
    expect(block).toHaveClass('code-wrap');
    fireEvent.click(wrap);
    expect(block).not.toHaveClass('code-wrap');

    const numbers = screen.getByRole('button', { name: '줄 번호' });
    expect(numbers).toHaveAttribute('aria-pressed', 'false');
    expect(block).not.toHaveClass('code-numbered');
    fireEvent.click(numbers);
    expect(numbers).toHaveAttribute('aria-pressed', 'true');
    expect(block).toHaveClass('code-numbered');
    // 번호 칸 너비는 가장 긴 번호의 자릿수
    expect(block.style.getPropertyValue('--code-digits')).toBe('1');
  });

  it('30줄이 넘으면 20줄만 보이고 "N줄 더 보기"로 편다 — 30줄은 접지 않는다', () => {
    const { container, unmount } = view(fence('', numbered(35)));
    const block = container.querySelector('.code-block') as HTMLElement;
    expect(block).toHaveClass('code-collapsed');
    expect(block.style.getPropertyValue('--code-digits')).toBe('2');

    const more = screen.getByRole('button', { name: '15줄 더 보기' });
    expect(more).toHaveAttribute('aria-expanded', 'false');
    expect(more).toHaveAttribute('aria-controls', block.querySelector('pre')?.id);
    fireEvent.click(more);
    expect(block).not.toHaveClass('code-collapsed');
    const less = screen.getByRole('button', { name: '접기' });
    expect(less).toHaveAttribute('aria-expanded', 'true');
    fireEvent.click(less);
    expect(block).toHaveClass('code-collapsed');
    unmount();

    const short = view(fence('', numbered(30)));
    expect(short.container.querySelector('.code-block')).not.toHaveClass('code-collapsed');
    expect(screen.queryByRole('button', { name: /더 보기/ })).not.toBeInTheDocument();
  });

  it('복사는 줄 번호 없이 글 그대로', async () => {
    view(fence('py title="run.py"', ['def main():', '    print("hi")', '', 'main()']));
    fireEvent.click(screen.getByRole('button', { name: '코드 복사' }));
    await waitFor(() => expect(writeText).toHaveBeenCalledWith('def main():\n    print("hi")\n\nmain()'));
    expect(await screen.findByRole('button', { name: '복사했습니다' })).toBeInTheDocument();
  });

  it('영어 화면의 단추 이름', async () => {
    await act(() => i18n.changeLanguage('en'));
    try {
      view(fence('sh', numbered(32)), true);
      expect(screen.getByRole('button', { name: 'Wrap long lines' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Line numbers' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Show 12 more lines' })).toBeInTheDocument();
      expect(screen.getByText('Shell')).toBeInTheDocument();
    } finally {
      await act(() => i18n.changeLanguage('ko'));
    }
  });
});
