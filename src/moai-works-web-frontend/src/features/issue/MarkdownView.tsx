import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import type { SxProps, Theme } from '@mui/material/styles';
import { useMemo, type ComponentProps } from 'react';
import Markdown from 'react-markdown';
import { Link as RouterLink } from 'react-router';
import rehypeHighlight from 'rehype-highlight';
import remarkGfm from 'remark-gfm';

import { CodeBlock } from './CodeBlock';
import { DIAGRAM_LANGUAGE, codeLanguage, codeText, type CodeNode } from './code-blocks';
import { remarkIssueText, type IssueMarkdownOptions } from './markdown-plugins';
import { MermaidDiagram } from './MermaidDiagram';

interface Props {
  content: string;
  /** 본문의 #123 을 같은 프로젝트의 일감 주소로 바꾼다. 주지 않으면 글자 그대로 */
  issuePath?: IssueMarkdownOptions['issuePath'];
  /** 댓글처럼 작은 글 — 제목 · 문단 간격을 줄인다 */
  dense?: boolean;
}

/**
 * 일감 설명 · 댓글 보기 — Markdown(GFM 표 포함)을 그린다. 보기 화면과 편집기 미리보기가 같이 쓴다(moai-wiki 의 페이지 본문 보기와 같다).
 *
 * HTML 은 그리지 않는다(글자로 보인다) — 본문에 든 태그가 화면에서 실행되지 않는다. 이미지는 아직 그리지 않는다.
 * 코드 블록은 언어에 맞춰 색을 입히고(적힌 언어만 — 짐작하지 않는다), ```mermaid 블록은 다이어그램으로 그린다.
 * 넓은 표 · 긴 코드 · 큰 다이어그램은 그 상자 안에서만 가로로 밀려 페이지 전체가 밀리지 않는다.
 */
/** 코드에 색 입히기 — 적힌 언어만 쓴다(짐작하면 글이 엉뚱하게 칠해진다). 다이어그램 블록은 그대로 둔다 */
const rehypePlugins: ComponentProps<typeof Markdown>['rehypePlugins'] = [
  [rehypeHighlight, { detect: false, plainText: [DIAGRAM_LANGUAGE] }],
];

/** 주소에 적힌 한글(%EC%96…)을 읽을 수 있게 푼다. 풀 수 없으면 적힌 대로 */
function readablePath(href: string): string {
  try {
    return decodeURI(href);
  } catch {
    return href;
  }
}

export function MarkdownView({ content, issuePath, dense = false }: Props) {
  const remarkPlugins = useMemo<ComponentProps<typeof Markdown>['remarkPlugins']>(
    () => [remarkGfm, [remarkIssueText, { issuePath } satisfies IssueMarkdownOptions]],
    [issuePath],
  );
  return (
    <Box sx={dense ? denseBodySx : bodySx}>
      <Markdown
        remarkPlugins={remarkPlugins}
        rehypePlugins={rehypePlugins}
        components={{
          a: ({ href, children, className, title }) =>
            href?.startsWith('/') ? (
              <Link component={RouterLink} to={href} className={className} title={title}>
                {children}
              </Link>
            ) : href?.startsWith('#') ? (
              <Link href={href}>{children}</Link>
            ) : !href || !/^([a-z][a-z0-9+.-]*:|\/\/)/i.test(href) ? (
              // 상대 경로(./다른문서.md) — 원래 문서가 있던 폴더에서만 뜻이 있다. 여기서 열면 엉뚱한 주소가 되므로 글로만 보인다
              <span title={href ? readablePath(href) : undefined}>{children}</span>
            ) : (
              <Link href={href} target="_blank" rel="noopener noreferrer">
                {children}
              </Link>
            ),
          table: ({ children }) => (
            <Box className="table-scroll">
              <table>{children}</table>
            </Box>
          ),
          pre: ({ node, children }) => {
            const block = node as CodeNode | undefined;
            const language = codeLanguage(block);
            return language === DIAGRAM_LANGUAGE ? (
              <MermaidDiagram code={codeText(block)} />
            ) : (
              <CodeBlock language={language} text={codeText(block)}>
                {children}
              </CodeBlock>
            );
          },
          // 이미지는 아직 넣을 수 없다(첨부는 나중) — 설명 글만 보인다
          img: ({ alt }) => <em>{alt}</em>,
        }}
      >
        {content}
      </Markdown>
    </Box>
  );
}

/** 본문 모양. dense = 댓글 — 글자를 조금 작게, 제목 · 문단 간격을 좁게 */
const makeBodySx = (dense: boolean): SxProps<Theme> => (theme) => ({
  fontSize: dense ? '0.9063rem' : '0.9688rem',
  lineHeight: dense ? 1.7 : 1.75,
  color: 'text.primary',
  overflowWrap: 'anywhere',
  '& > :first-of-type': { mt: 0 },
  '& > :last-child': { mb: 0 },
  '& h2': {
    ...(dense ? theme.typography.h6 : theme.typography.h5),
    mt: dense ? 2 : 4.5,
    mb: dense ? 1 : 1.5,
    pb: dense ? 0.5 : 0.75,
    borderBottom: 1,
    borderColor: 'divider',
    scrollMarginTop: 80,
  },
  '& h3': dense ? { ...theme.typography.subtitle1, mt: 1.5, mb: 0.75 } : { ...theme.typography.h6, mt: 3, mb: 1 },
  '& h4, & h5, & h6': { ...theme.typography.subtitle1, mt: 2.5, mb: 0.75 },
  '& p': { my: dense ? 1 : 1.5 },
  '& ul, & ol': { pl: 3, my: 1.5 },
  '& li': { my: 0.5 },
  '& li > p': { my: 0.5 },
  '& blockquote': {
    m: 0,
    my: 2,
    px: 2,
    py: 0.5,
    borderLeft: 3,
    borderColor: 'primary.main',
    bgcolor: 'background.subtle',
    borderRadius: 1,
    color: 'text.secondary',
  },
  '& code': {
    fontFamily: "'JetBrains Mono', 'SFMono-Regular', Menlo, Consolas, monospace",
    fontSize: '0.85em',
    bgcolor: 'background.subtle',
    borderRadius: 0.75,
    px: 0.6,
    py: 0.2,
  },
  '& pre': {
    m: 0,
    p: 2,
    overflowX: 'auto',
    lineHeight: 1.6,
    '& code': { bgcolor: 'transparent', p: 0, fontSize: '0.8125rem', overflowWrap: 'normal' },
  },
  // 코드 상자 — 머리(언어 이름 · 복사)와 본문. 긴 줄은 상자 안에서만 밀린다
  '& .code-block': { my: 2, bgcolor: 'background.subtle', borderRadius: 1.5, border: 1, borderColor: 'divider', overflow: 'hidden' },
  '& .code-head': {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 1,
    minHeight: 32,
    pl: 2,
    pr: 0.5,
    borderBottom: 1,
    borderColor: 'divider',
    color: 'text.secondary',
    fontSize: '0.75rem',
    fontFamily: "'JetBrains Mono', 'SFMono-Regular', Menlo, Consolas, monospace",
  },
  // 다이어그램 — 넓으면 상자 안에서 가로로 민다
  '& .diagram': { my: 2, p: 2, border: 1, borderColor: 'divider', borderRadius: 1.5, overflowX: 'auto', textAlign: 'center' },
  '& .diagram-canvas svg': { maxWidth: '100%', height: 'auto' },
  ...codeColors,
  '& .table-scroll': { overflowX: 'auto', my: 2 },
  '& table': { borderCollapse: 'collapse', minWidth: '100%', fontSize: '0.875rem', overflowWrap: 'normal' },
  '& th, & td': { border: 1, borderColor: 'divider', px: 1.5, py: 1, textAlign: 'left', verticalAlign: 'top' },
  '& th': { bgcolor: 'background.subtle', fontWeight: 700, whiteSpace: 'nowrap' },
  '& hr': { border: 0, borderTop: 1, borderColor: 'divider', my: 3 },
  // 일감 번호 링크(#123) — 글자처럼 이어 읽히게 굵기만 조금
  '& .issue-ref': { fontWeight: 600, whiteSpace: 'nowrap' },
  '& input[type="checkbox"]': { mr: 1 },
});

const bodySx = makeBodySx(false);
const denseBodySx = makeBodySx(true);

/** 코드 강조의 글자 색 — theme 의 `code.*`(라이트 · 다크가 따로 정해져 있다, theme/tokens.ts). 색만으로 뜻을 전하지 않는다 */
const codeColors = {
  '& .hljs-comment, & .hljs-quote': { color: 'code.comment', fontStyle: 'italic' },
  '& .hljs-keyword, & .hljs-selector-tag, & .hljs-doctag, & .hljs-template-tag, & .hljs-type': { color: 'code.keyword' },
  '& .hljs-string, & .hljs-regexp': { color: 'code.string' },
  '& .hljs-number, & .hljs-literal, & .hljs-variable, & .hljs-template-variable, & .hljs-attr, & .hljs-attribute, & .hljs-meta, & .hljs-selector-class, & .hljs-selector-id, & .hljs-selector-attr':
    { color: 'code.value' },
  '& .hljs-title, & .hljs-section': { color: 'code.title' },
  '& .hljs-built_in, & .hljs-symbol, & .hljs-bullet': { color: 'code.builtin' },
  '& .hljs-name, & .hljs-selector-pseudo': { color: 'code.tag' },
  '& .hljs-addition': { color: 'code.added', bgcolor: 'code.addedBg' },
  '& .hljs-deletion': { color: 'code.removed', bgcolor: 'code.removedBg' },
  '& .hljs-emphasis': { fontStyle: 'italic' },
  '& .hljs-strong': { fontWeight: 700 },
} as const;
