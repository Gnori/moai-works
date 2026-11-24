import { importWithReload } from '@/shared/lib/chunk-reload';

/**
 * ```mermaid 글을 그림(SVG)으로 그린다 — 보기 화면(MermaidDiagram)과 편집기의 코드 블록 미리보기가 같이 쓴다(moai-wiki 의 mermaid.ts 와 같다).
 *
 * - mermaid 는 크다 — 다이어그램이 있을 때만 그때 받아 온다(동적 import).
 * - 본문은 남이 쓴 글이다 — securityLevel 'strict' 로 그린다(글자 안의 HTML · 누르면 실행되는 것을 막는다).
 * - 문법이 틀리면 그리지 않고 이유(첫 줄)를 돌려준다.
 */
export type Drawing = { svg: string } | { error: string };

let round = 0;

export async function drawMermaid(code: string, dark: boolean, invalidMessage: string): Promise<Drawing> {
  try {
    const mermaid = (await importWithReload(() => import('mermaid'))).default;
    mermaid.initialize({
      startOnLoad: false,
      securityLevel: 'strict',
      theme: dark ? 'dark' : 'default',
      fontFamily: window.getComputedStyle(document.body).fontFamily,
    });
    const valid = await mermaid.parse(code, { suppressErrors: false });
    if (!valid) {
      return { error: invalidMessage };
    }
    round += 1;
    const { svg } = await mermaid.render(`diagram-${round}`, code);
    return { svg };
  } catch (e) {
    const reason = e instanceof Error ? e.message.split('\n')[0] : String(e);
    return { error: reason || invalidMessage };
  }
}
