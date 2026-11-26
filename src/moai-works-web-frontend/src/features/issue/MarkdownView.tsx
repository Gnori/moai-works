import AttachFileRounded from '@mui/icons-material/AttachFileRounded';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import { alpha, type SxProps, type Theme } from '@mui/material/styles';
import { useMemo, useState, type ComponentProps, type MouseEvent } from 'react';
import { useTranslation } from 'react-i18next';
import Markdown from 'react-markdown';
import { Link as RouterLink } from 'react-router';
import rehypeHighlight from 'rehype-highlight';
import remarkGfm from 'remark-gfm';

import { useAttachmentLinks, type AttachmentLinks } from './attachment-context';
import { isPlainClick } from './attachments';
import { ChartBlock } from './ChartBlock';
import { CodeBlock } from './CodeBlock';
import { CHART_LANGUAGE, DIAGRAM_LANGUAGE, codeLanguage, codeText, type CodeNode } from './code-blocks';
import { highlightAliases, useHighlightLanguages } from './code-languages';
import { COLLAPSED_LINES, rehypeCodeLines } from './code-lines';
import { codeMeta } from './code-meta';
import { remarkIssueText, type IssueMarkdownOptions } from './markdown-plugins';
import { attachmentIdOf, markdownUrlTransform, resolveAttachmentUrl } from './markdown-urls';
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
 * HTML 은 그리지 않는다(글자로 보인다) — 본문에 든 태그가 화면에서 실행되지 않는다.
 * 주소는 react-markdown 의 기본 거르기(javascript: 같은 것을 버림)에 첨부 표시 attachment:<id> 하나만 더 살린다(markdown-urls).
 * 첨부 표시는 이 일감의 첨부 내용 주소로 바꾼다 — 감싼 IssueAttachmentsProvider 가 어느 일감인지 알려 준다.
 *   ![이름](attachment:<id>) → 그 자리에 이미지(창 너비까지 · 보일 때 받음 · 설명 글 유지). 열 수 없으면 설명 글만. 누르면 첨부 보기
 *   [이름](attachment:<id>)  → 첨부 링크 — 누르면 첨부 보기(일감 #310). 목록에 없는 첨부면 내려받기, 가운데 누르기 · ⌘ 누르기는 링크 그대로
 * 그 밖의 이미지(바깥 주소)는 그리지 않고 설명 글만 보인다 — 남의 서버에서 그림을 받아 오지 않는다.
 * 코드 블록은 언어에 맞춰 색을 입히고(적힌 언어만 — 짐작하지 않는다), 줄로 나눠 줄 번호 · 강조한 줄 · diff 의 더한 줄 / 뺀 줄을 보인다
 * (CodeBlock · code-lines). ```mermaid 블록은 다이어그램으로 그린다.
 * 넓은 표 · 긴 코드 · 큰 다이어그램은 그 상자 안에서만 가로로 밀려 페이지 전체가 밀리지 않는다.
 */
/** 색을 입히지 않고 그림으로 그리는 블록(흐름도 · 차트) — 줄로 나누지도 않는다 */
const plainText = [DIAGRAM_LANGUAGE, CHART_LANGUAGE];

/** 주소에 적힌 한글(%EC%96…)을 읽을 수 있게 푼다. 풀 수 없으면 적힌 대로 */
function readablePath(href: string): string {
  try {
    return decodeURI(href);
  } catch {
    return href;
  }
}

export function MarkdownView({ content, issuePath, dense = false }: Props) {
  const { t } = useTranslation('issue');
  const attachments = useAttachmentLinks();
  const remarkPlugins = useMemo<ComponentProps<typeof Markdown>['remarkPlugins']>(
    () => [remarkGfm, [remarkIssueText, { issuePath } satisfies IssueMarkdownOptions]],
    [issuePath],
  );
  // 코드에 색 입히기 — 적힌 언어만 쓴다(짐작하면 글이 엉뚱하게 칠해진다). 흔하지 않은 언어는 이 글이 쓸 때 받아 와 다시 칠한다(code-languages).
  // 그다음 코드 블록을 줄마다 나눈다(줄 번호 · 강조한 줄 · diff) — 색 입힌 span 을 줄 너머로 이어 주므로 반드시 강조 뒤에 돈다
  const languages = useHighlightLanguages(content);
  const rehypePlugins = useMemo<ComponentProps<typeof Markdown>['rehypePlugins']>(
    () => [
      [rehypeHighlight, { detect: false, plainText, languages, aliases: highlightAliases }],
      [rehypeCodeLines, { skip: plainText }],
    ],
    [languages],
  );
  // 그리는 부품은 첨부 주소가 바뀔 때만 새로 만든다 — 다시 그릴 때마다 새 함수면 이미지 · 다이어그램이 통째로 다시 만들어진다
  const components = useMemo<ComponentProps<typeof Markdown>['components']>(
    () => ({
      a: ({ href, children, className, title }) => {
        const file = resolveAttachmentUrl(href, attachments?.contentUrl);
        if (file) {
          // 첨부 파일 — 누르면 첨부 보기. 보기로 열 수 없으면(목록에 없음) 이 일감의 내용 주소에서 내려받는다(서버가 attachment 로 준다)
          return (
            <Link
              href={file}
              download
              className="attachment-link"
              title={title ?? t('attachments.fileLink')}
              onClick={(event) => openViewer(event, attachments, href)}
            >
              <AttachFileRounded aria-hidden fontSize="inherit" />
              {children}
            </Link>
          );
        }
        return href?.startsWith('attachment:') ? (
          // 어느 일감의 첨부인지 모른다(새 일감 미리보기) — 글로만
          <span>{children}</span>
        ) : href?.startsWith('/') ? (
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
        );
      },
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
        ) : language === CHART_LANGUAGE ? (
          <ChartBlock code={codeText(block)} />
        ) : (
          <CodeBlock language={language} title={codeMeta(block).title} text={codeText(block)}>
            {children}
          </CodeBlock>
        );
      },
      img: ({ src, alt, title }) => {
        const url = resolveAttachmentUrl(typeof src === 'string' ? src : null, attachments?.contentUrl);
        // 이 일감의 첨부 이미지만 그린다. 바깥 주소 · 모르는 첨부는 설명 글만
        return url ? (
          <AttachmentImage key={url} src={url} alt={alt ?? ''} title={title} onOpen={(event) => openViewer(event, attachments, src as string)} />
        ) : (
          <em>{alt}</em>
        );
      },
    }),
    [attachments, t],
  );
  return (
    <Box sx={dense ? denseBodySx : bodySx}>
      <Markdown remarkPlugins={remarkPlugins} rehypePlugins={rehypePlugins} urlTransform={markdownUrlTransform} components={components}>
        {content}
      </Markdown>
    </Box>
  );
}

/** 보통 누르기면 첨부 보기로 연다 — 열었으면 링크가 하던 일(내려받기 · 새 탭)을 막는다 */
function openViewer(event: MouseEvent, attachments: AttachmentLinks | null, href: string | null | undefined) {
  const id = attachmentIdOf(href);
  if (id && attachments && isPlainClick(event) && attachments.view(id)) {
    event.preventDefault();
  }
}

/**
 * 본문 속 첨부 이미지 — 보일 때 받는다. 지웠거나 열 수 없으면 설명 글만 남긴다.
 * 원본으로 가는 링크로 감싼다 — 누르면 첨부 보기, 가운데 누르기는 새 탭에서 원본(키보드로도 열린다)
 */
function AttachmentImage({ src, alt, title, onOpen }: {
  src: string;
  alt: string;
  title: string | undefined;
  onOpen: (event: MouseEvent) => void;
}) {
  const { t } = useTranslation('issue');
  const [failed, setFailed] = useState(false);
  if (failed) {
    return (
      <em className="attachment-missing" title={t('attachments.imageMissing')}>
        {alt || t('attachments.imageMissing')}
      </em>
    );
  }
  return (
    <a href={src} target="_blank" rel="noopener noreferrer" className="attachment-image" title={title ?? t('attachments.imageView', { name: alt })} onClick={onOpen}>
      <img src={src} alt={alt} title={title} loading="lazy" decoding="async" onError={() => setFailed(true)} />
    </a>
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
  // 코드 상자 — 머리(파일 이름 · 언어 이름 · 단추)와 줄로 나뉜 본문(codeViewSx). 긴 줄은 상자 안에서만 밀린다
  '& .code-block': { my: 2, bgcolor: 'background.subtle', borderRadius: 1.5, border: 1, borderColor: 'divider', overflow: 'hidden' },
  '& .code-head': {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 1,
    minHeight: dense ? 28 : 32,
    pl: dense ? 1.5 : 2,
    pr: 0.5,
    borderBottom: 1,
    borderColor: 'divider',
    color: 'text.secondary',
    fontSize: '0.75rem',
    fontFamily: "'JetBrains Mono', 'SFMono-Regular', Menlo, Consolas, monospace",
  },
  ...codeViewSx(theme, dense),
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
  // 첨부 이미지 — 글 폭을 넘지 않게. 첨부 파일 링크 — 앞에 클립 표시
  '& img': { maxWidth: '100%', height: 'auto', verticalAlign: 'middle', borderRadius: 1 },
  '& .attachment-link svg': { verticalAlign: '-0.15em', mr: 0.25 },
  '& .attachment-image': { cursor: 'zoom-in' },
  '& .attachment-missing': { color: 'text.secondary' },
});

const bodySx = makeBodySx(false);
const denseBodySx = makeBodySx(true);

/**
 * 본문 모양 — 편집기(MarkdownEditor)도 같은 모양으로 그린다(보기와 편집이 같게).
 * scope 를 주면 모든 규칙을 그 선택자 아래로 옮긴다 — 편집기는 Crepe 의 `.milkdown .ProseMirror h2` 같은 규칙보다 앞서야 해서
 * `& .milkdown .ProseMirror` 아래에 둔다.
 */
export function markdownBodySx(dense: boolean, scope?: string): SxProps<Theme> {
  if (!scope) {
    return dense ? denseBodySx : bodySx;
  }
  return (theme: Theme) => {
    const base = (makeBodySx(dense) as (t: Theme) => Record<string, unknown>)(theme);
    const scoped: Record<string, unknown> = {};
    const own: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(base)) {
      if (key.startsWith('&')) {
        scoped[`${scope}${key.slice(1)}`] = value;
      } else {
        own[key] = value;
      }
    }
    return { [scope]: own, ...scoped };
  };
}

/**
 * 코드 보기 — 머리의 이름 · 단추, 줄(줄 번호 | 글), 강조한 줄, diff 의 더한 줄 · 뺀 줄, 줄 바꿈, 접기.
 * 상자의 class(code-numbered · code-wrap · code-collapsed)는 CodeBlock 이, 줄 마디는 rehypeCodeLines 가 만든다.
 */
function codeViewSx(theme: Theme, dense: boolean) {
  const palette = (theme.vars ?? theme).palette;
  const markBar = `inset 3px 0 0 ${palette.primary.main}`;
  // diff 바탕 — 상태색을 옅게 겹친다(라이트 · 다크 모두 그 위 글자가 AA 를 넘는다)
  const tint = (color: 'success' | 'error') =>
    theme.vars ? `rgba(${theme.vars.palette[color].mainChannel} / 0.14)` : alpha(theme.palette[color].main, 0.14);
  const added = tint('success');
  const removed = tint('error');
  /** 줄 번호 칸은 가로로 밀 때 글 위에 떠 있으므로 비치지 않게 — 상자 바탕 위에 겹쳐 칠한다 */
  const solid = (overlay: string) => `linear-gradient(${overlay}, ${overlay}), ${palette.background.subtle}`;
  return {
    '& .code-name': { display: 'flex', alignItems: 'center', gap: 1, minWidth: 0 },
    '& .code-file-icon': { flex: 'none', fontSize: '0.875rem' },
    '& .code-title': { minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: 'text.primary', fontWeight: 600 },
    '& .code-lang': { flex: 'none', whiteSpace: 'nowrap' },
    '& .code-tools': { display: 'flex', flex: 'none', alignItems: 'center', gap: dense ? 0 : 0.25 },
    '& .code-tools .MuiIconButton-root': dense ? { p: 0.5 } : {},
    '& .code-tools [aria-pressed="true"]': { color: 'primary.main', bgcolor: 'action.selected' },
    // 본문 — 바깥 여백은 줄이 갖는다(강조 바탕이 상자 끝까지 가게). 줄 바꿈을 끄면 가장 긴 줄만큼 넓어져 가로로 민다
    '& .code-block pre': { px: 0, py: dense ? 1 : 1.5 },
    // 그릴 수 없는 흐름도 · 차트의 상자는 줄로 나누지 않은 글 그대로다 — 여백을 상자가 준다
    '& .diagram-failed pre': { px: dense ? 1.5 : 2 },
    '& .code-block pre > code': { display: 'block', width: 'max-content', minWidth: '100%' },
    '& .code-wrap pre > code': { width: 'auto' },
    '& .code-line': { display: 'flex' },
    '& .code-src': { flex: '1 0 auto', px: dense ? 1.5 : 2 },
    '& .code-wrap .code-src': { flex: '1 1 auto', minWidth: 0, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' },
    // 줄 번호 — 글자가 아니라 data-line 을 그린다(복사 · 화면 읽기에 들지 않는다). 가로로 밀어도 왼쪽에 붙어 있다
    '& .code-ln': { display: 'none' },
    '& .code-numbered .code-ln': {
      display: 'block',
      flex: 'none',
      position: 'sticky',
      left: 0,
      boxSizing: 'content-box',
      width: 'calc(var(--code-digits, 1) * 1ch)',
      px: dense ? 1 : 1.5,
      textAlign: 'right',
      color: 'text.secondary',
      bgcolor: 'background.subtle',
      borderRight: 1,
      borderColor: 'divider',
      userSelect: 'none',
      '&::before': { content: 'attr(data-line)' },
    },
    '& .code-numbered .code-src': { pl: dense ? 1 : 1.5 },
    // 강조한 줄({2,4-6}) — 바탕과 왼쪽 막대(색만으로 전하지 않게). 줄 번호 칸은 겹쳐도 비치지 않게 바탕을 겹쳐 칠한다
    '& .code-line-mark': { bgcolor: 'action.selected', boxShadow: markBar },
    '& .code-numbered .code-line-mark .code-ln': { background: solid(palette.action.selected), boxShadow: markBar },
    // diff — 줄 맨 앞의 + · - 글자가 그대로 보이고(색만으로 전하지 않는다), 바탕을 옅게 칠한다
    '& .code-line-add': { bgcolor: added },
    '& .code-line-del': { bgcolor: removed },
    '& .code-numbered .code-line-add .code-ln': { background: solid(added) },
    '& .code-numbered .code-line-del .code-ln': { background: solid(removed) },
    '& .code-line .hljs-addition, & .code-line .hljs-deletion': { bgcolor: 'transparent' },
    // 접기 — 앞 COLLAPSED_LINES 줄만 보인다
    [`& .code-collapsed .code-line:nth-of-type(n+${COLLAPSED_LINES + 1})`]: { display: 'none' },
    '& .code-more': {
      borderTop: 1,
      borderColor: 'divider',
      borderRadius: 0,
      py: dense ? 0.25 : 0.5,
      color: 'text.secondary',
      fontSize: '0.75rem',
      fontWeight: 600,
    },
  };
}

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
