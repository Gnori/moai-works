import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import LinearProgress from '@mui/material/LinearProgress';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import Stack from '@mui/material/Stack';
import { useTheme, type Theme } from '@mui/material/styles';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { lazy, Suspense, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState, type FormEvent, type Ref } from 'react';
import { useTranslation } from 'react-i18next';

import { useElementWidth } from '@/shared/lib/use-element-width';

import { useAttachmentLinks } from './attachment-context';
import { AttachFilesButton } from './AttachmentControls';
import { EditorToolbar } from './editor/EditorToolbar';
import { noActive, type EditorAction, type EditorActive, type EditorHeading, type EditorMark, type RichEditorApi } from './editor/editor-types';
import { linkProblem } from './editor/link-rules';
import { MarkdownSourceEditor, type SourceEditorHandle } from './editor/MarkdownSourceEditor';
import { preserveSource } from './editor/source-blocks';
import { resolveAttachmentUrl } from './markdown-urls';
import { markdownBodySx } from './MarkdownView';

/** 쓰면서 바로 그려지는 편집기(Milkdown) — 화면 코드가 커서 편집할 때 따로 받는다 */
const RichEditor = lazy(() => import('./editor/RichEditor'));

export type { EditorMark } from './editor/editor-types';

/** 편집 칸을 밖에서 다루는 손잡이 — 올린 첨부의 표시를 커서 자리에 넣을 때 쓴다(useEditorUpload) */
export interface MarkdownEditorHandle {
  /** 지금 커서(고른 글이 있으면 그 자리)에 글을 넣고, 넣은 자리를 돌려준다 */
  insert: (text: string) => EditorMark | null;
  /** 넣은 자리의 글을 바꾼다. 사람이 그 글을 고쳤으면 그 자리에 덧붙인다 */
  replace: (mark: EditorMark | null, text: string) => void;
  /** 넣은 자리의 글을 지운다. 사람이 그 글을 고쳤으면 그대로 둔다 */
  remove: (mark: EditorMark | null) => void;
}

interface Props {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  ariaLabel: string;
  /** 파일을 붙여넣거나 끌어다 놓거나 단추로 골랐을 때 — 주면 도구 막대에 "이미지 · 파일 넣기"가 생긴다 */
  onFiles?: (files: File[]) => void;
  /** document = 일감 설명(넓으면 목차를 연다), comment = 댓글(목차 없음) */
  variant?: 'document' | 'comment';
  ref?: Ref<MarkdownEditorHandle>;
}

/** 목차를 처음부터 열어 두는 편집기 너비 */
const OUTLINE_FROM = 900;

/**
 * 일감 설명 · 댓글 편집기 — 쓰면서 바로 그려지는 Markdown 편집(Milkdown Crepe)과 위 고정 도구 막대 · 왼쪽 목차 · "Markdown 원문" 보기.
 * (2026-11-23 사용자: "이렇게 md 편집기를 만들고 이슈의 본문이나 덧글은 모두 md 로 작성할 수 있게 해 줘")
 *
 * 저장하는 값은 늘 Markdown 이다. 편집기는 글 전체를 다시 적으므로, 손대지 않은 덩어리는 원문 조각 그대로 둔다(source-blocks — moai-wiki 와 같은 구조):
 *   편집기를 만든 직후 내놓은 글을 baseline 으로 잡고, 바뀔 때마다 preserveSource(원문, baseline, 지금) 을 바깥 값으로 올린다.
 *   열자마자 저장하면 한 글자도 바뀌지 않고, 리비전 차이에는 고친 덩어리만 나온다. 밖에서 글을 통째로 바꾸면(원문 보기에서 돌아옴 ·
 *   다시 불러옴 · 댓글을 단 뒤 비움) 편집기를 다시 만들고 baseline 을 새로 잡는다.
 *   한계: 덩어리 단위라 긴 목록 · 표의 한 칸을 고치면 그 덩어리 전체가 편집기 모양이 된다.
 * 첨부 그림(attachment:<id>)은 편집 중에도 그 일감의 첨부 내용 주소로 보이고, 바깥 주소 그림은 받지 않는다(보기와 같은 규칙).
 * 높이는 감싸는 상자가 정한다(도구 막대 아래 칸이 그 높이를 채우고 안에서 밀린다).
 */
export function MarkdownEditor({ value, onChange, placeholder, ariaLabel, onFiles, variant = 'document', ref }: Props) {
  const { t } = useTranslation('issue');
  const theme = useTheme();
  const attachments = useAttachmentLinks();
  const [source, setSource] = useState(false);
  const [outlineChoice, setOutlineChoice] = useState<boolean | null>(null);
  const [headings, setHeadings] = useState<EditorHeading[]>([]);
  const [active, setActive] = useState<EditorActive>(noActive);
  const [linkOpen, setLinkOpen] = useState(false);
  const [boxRef, boxWidth] = useElementWidth<HTMLDivElement>();
  const rich = useRef<RichEditorApi>(null);
  const sourceEditor = useRef<SourceEditorHandle>(null);

  /** 편집기에 넣은 원문 · 그 원문을 넣자마자 편집기가 내놓은 글(baseline) · 마지막으로 내보낸 값 · 편집기를 다시 만드는 판 번호 */
  const original = useRef(value);
  const baseline = useRef<string | null>(null);
  const emitted = useRef(value);
  const [version, setVersion] = useState(0);
  const [loadValue, setLoadValue] = useState(value);

  // 밖에서 값이 바뀌었을 때만(다시 불러옴 · 댓글을 단 뒤 비움) 원문 · 편집기를 새로 맞춘다
  useEffect(() => {
    if (value !== emitted.current) {
      original.current = value;
      baseline.current = null;
      emitted.current = value;
      setLoadValue(value);
      setVersion((v) => v + 1);
    }
  }, [value]);

  const emit = useCallback(
    (next: string) => {
      if (next !== emitted.current) {
        emitted.current = next;
        onChange(next);
      }
    },
    [onChange],
  );

  /** 편집기가 내놓은 글 — 손대지 않은 덩어리는 원문 그대로. baseline 을 잡기 전에 온 알림은 버린다 */
  const fromRich = useCallback(
    (markdown: string) => {
      if (baseline.current !== null) {
        emit(preserveSource(original.current, baseline.current, markdown));
      }
    },
    [emit],
  );
  const onReady = useCallback((ready: string) => {
    baseline.current = ready;
  }, []);

  const toggleSource = () => {
    if (!source) {
      // 편집기의 바뀜 알림은 늦게 온다 — 원문으로 바꾸기 전에 지금 글을 바로 받아 둔다(마지막에 친 글을 잃지 않게)
      rich.current?.flush();
    }
    if (source) {
      // 원문에서 돌아오면 지금 글이 새 원문이다 — 편집기를 다시 만들고 baseline 을 새로 잡는다
      original.current = emitted.current;
      baseline.current = null;
      setLoadValue(emitted.current);
      setVersion((v) => v + 1);
    }
    setSource((s) => !s);
  };

  const resolveImage = useCallback((src: string) => resolveAttachmentUrl(src, attachments?.contentUrl), [attachments]);

  /** 넣은 자리 — 어느 편집 칸에서 넣었는지와 그 칸의 자리 */
  const marks = useRef(new Map<number, { source: boolean; mark: EditorMark }>());
  const nextMark = useRef(1);
  useImperativeHandle(
    ref,
    () => ({
      insert: (text) => {
        const inner = source ? sourceEditor.current?.insert(text) : rich.current?.insertMarker(text);
        if (!inner) {
          return null;
        }
        const id = nextMark.current++;
        marks.current.set(id, { source, mark: inner });
        return { id };
      },
      replace: (mark, text) => {
        const entry = mark && marks.current.get(mark.id);
        if (!mark || !entry) {
          return;
        }
        marks.current.delete(mark.id);
        if (entry.source) {
          sourceEditor.current?.replace(entry.mark, text);
        } else {
          rich.current?.replaceMarker(entry.mark, text);
        }
      },
      remove: (mark) => {
        const entry = mark && marks.current.get(mark.id);
        if (!mark || !entry) {
          return;
        }
        marks.current.delete(mark.id);
        if (entry.source) {
          sourceEditor.current?.remove(entry.mark);
        } else {
          rich.current?.removeMarker(entry.mark);
        }
      },
    }),
    [source],
  );

  const richFiles = useMemo(
    () =>
      onFiles
        ? (files: File[], pos: number | null) => {
            if (pos !== null) {
              rich.current?.placeCursor(pos);
            }
            onFiles(files);
          }
        : undefined,
    [onFiles],
  );

  const showOutline = variant === 'document';
  const outlineOpen = showOutline && !source && (outlineChoice ?? (boxWidth !== null && boxWidth >= OUTLINE_FROM));

  return (
    <Box
      ref={boxRef}
      sx={{
        height: '100%',
        minHeight: 0,
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <EditorToolbar
        source={source}
        onToggleSource={toggleSource}
        active={source ? noActive : active}
        onAction={(action: EditorAction) => rich.current?.run(action)}
        onLink={() => setLinkOpen(true)}
        outlineOpen={outlineOpen}
        onToggleOutline={showOutline ? () => setOutlineChoice(!outlineOpen) : undefined}
        extra={
          onFiles && (
            <Tooltip title={t('attachments.insertHelp')}>
              <span>
                <AttachFilesButton onFiles={onFiles} size="small" color="inherit" sx={{ whiteSpace: 'nowrap', mr: 0.5 }} />
              </span>
            </Tooltip>
          )
        }
      />
      <Box sx={{ flex: 1, minHeight: 0, display: 'flex' }}>
        {outlineOpen && (
          <Box
            component="nav"
            aria-label={t('editor.outline')}
            sx={{
              width: 220,
              flexShrink: 0,
              borderRight: 1,
              borderColor: 'divider',
              overflowY: 'auto',
              py: 1,
            }}
          >
            {headings.length === 0 ? (
              <Typography variant="caption" sx={{ display: 'block', px: 2, py: 1, color: 'text.secondary' }}>
                {t('editor.outlineEmpty')}
              </Typography>
            ) : (
              <List dense disablePadding>
                {headings.map((heading, index) => (
                  <ListItemButton
                    key={`${index}-${heading.text}`}
                    onClick={() => rich.current?.scrollToHeading(index)}
                    sx={{
                      pl: 1.5 + (heading.level - 1) * 1.5,
                      py: 0.5,
                      fontSize: 13,
                      color: 'text.secondary',
                      borderRadius: 1,
                      mx: 0.5,
                    }}
                  >
                    <Box
                      component="span"
                      sx={{
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                      title={heading.text}
                    >
                      {heading.text || '—'}
                    </Box>
                  </ListItemButton>
                ))}
              </List>
            )}
          </Box>
        )}
        <Box sx={{ flex: 1, minWidth: 0, overflowY: source ? 'hidden' : 'auto' }}>
          {source ? (
            <MarkdownSourceEditor
              ref={sourceEditor}
              value={value}
              onChange={emit}
              placeholder={placeholder}
              ariaLabel={ariaLabel}
              onFiles={onFiles}
            />
          ) : (
            // 글 모양은 보기와 같게(바깥 상자), 편집기 색 · 여백은 안 상자
            <Box sx={markdownBodySx(variant === 'comment', '& .milkdown .ProseMirror')}>
              <Box sx={richEditorSx(theme)}>
                <Suspense fallback={<LinearProgress aria-label={t('editor.loading')} sx={{ m: 2 }} />}>
                  <RichEditor
                    key={version}
                    ref={rich}
                    value={loadValue}
                    onReady={onReady}
                    onMarkdown={fromRich}
                    placeholder={placeholder}
                    linkPlaceholder={t('editor.linkUrl')}
                    ariaLabel={ariaLabel}
                    resolveImage={resolveImage}
                    onFiles={richFiles}
                    onOutline={setHeadings}
                  onActive={setActive}
                  />
                </Suspense>
              </Box>
            </Box>
          )}
        </Box>
      </Box>
      {linkOpen && (
        <LinkDialog
          onClose={() => setLinkOpen(false)}
          onSubmit={(href, text) => {
            setLinkOpen(false);
            rich.current?.run('link', { href, text });
          }}
        />
      )}
    </Box>
  );
}

/** 링크 넣기 — 주소(http · https · mailto · 이 화면 안의 경로)와 보일 글(고른 글이 없을 때) */
function LinkDialog({ onClose, onSubmit }: { onClose: () => void; onSubmit: (href: string, text: string) => void }) {
  const { t } = useTranslation(['issue', 'common']);
  const [href, setHref] = useState('');
  const [text, setText] = useState('');
  const [error, setError] = useState<string>();
  const submit = (event: FormEvent) => {
    event.preventDefault();
    const problem = linkProblem(href);
    if (problem) {
      setError(t(`editor.${problem}`));
      return;
    }
    onSubmit(href.trim(), text.trim());
  };
  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
      <Box component="form" noValidate onSubmit={submit} sx={{ display: 'contents' }}>
        <DialogTitle>{t('editor.linkTitle')}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              autoFocus
              required
              label={t('editor.linkUrl')}
              placeholder="https://"
              value={href}
              onChange={(event) => {
                setHref(event.target.value);
                setError(undefined);
              }}
              error={!!error}
              helperText={error}
            />
            <TextField
              label={t('editor.linkText')}
              helperText={t('editor.linkTextHelp')}
              value={text}
              onChange={(event) => setText(event.target.value)}
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={onClose} color="inherit">
            {t('common:actions.cancel')}
          </Button>
          <Button type="submit" variant="contained">
            {t('editor.linkInsert')}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}

/**
 * 편집기 모양 — 글 모양은 보기(markdownBodySx)와 같고, Crepe 의 색 변수는 화면 테마의 CSS 변수(theme.vars)로 채운다
 * (값으로 적으면 다크로 바꿨을 때 글자가 배경에 묻힌다).
 */
function richEditorSx(theme: Theme) {
  const p = (theme.vars ?? theme).palette;
  return {
    height: '100%',
    '& .milkdown': {
      '--crepe-color-background': 'transparent',
      '--crepe-color-on-background': p.text.primary,
      '--crepe-color-surface': p.background.paper,
      '--crepe-color-surface-low': p.action.hover,
      '--crepe-color-on-surface': p.text.primary,
      '--crepe-color-on-surface-variant': p.text.secondary,
      '--crepe-color-outline': p.divider,
      '--crepe-color-primary': p.primary.main,
      '--crepe-color-secondary': p.action.selected,
      '--crepe-color-on-secondary': p.text.primary,
      '--crepe-color-inverse': p.text.primary,
      '--crepe-color-on-inverse': p.background.paper,
      '--crepe-color-inline-code': p.text.primary,
      '--crepe-color-error': p.error.main,
      '--crepe-color-hover': p.action.hover,
      '--crepe-color-selected': p.action.selected,
      '--crepe-color-inline-area': p.action.selected,
      '--crepe-font-title': 'inherit',
      '--crepe-font-default': 'inherit',
      '--crepe-font-code': "'JetBrains Mono', 'SFMono-Regular', Menlo, Consolas, monospace",
      '--crepe-shadow-1': theme.shadows[2],
      '--crepe-shadow-2': theme.shadows[4],
      height: '100%',
      color: p.text.primary,
      background: 'transparent',
    },
    '& .milkdown .ProseMirror': {
      minHeight: '100%',
      px: { xs: 2, sm: 3 },
      py: 2,
      outline: 'none',
      caretColor: p.primary.main,
    },
    '& .milkdown .ProseMirror:focus-visible': { outline: 'none' },
    // 첨부 그림 — 글 폭까지. 받지 않는 그림(바깥 주소 · 올리는 중)은 이름만 흐리게
    '& .md-image img': {
      maxWidth: '100%',
      height: 'auto',
      borderRadius: 1,
      verticalAlign: 'middle',
    },
    '& .md-image-missing': { color: p.text.secondary, fontStyle: 'italic' },
    '& .milkdown .ProseMirror a': { color: p.primary.main, cursor: 'text' },
    // 목록 — Crepe 의 항목 블록(기호 · 체크 칸)을 보기의 목록 간격에 맞춘다
    '& .milkdown .ProseMirror ul, & .milkdown .ProseMirror ol': { pl: 0.5 },
    '& .milkdown .milkdown-list-item-block li': { gap: '6px', my: 0.25 },
    '& .milkdown .milkdown-list-item-block li .label-wrapper, & .milkdown .milkdown-list-item-block li .label-wrapper .label': {
      height: '1.75em',
      width: 22,
      py: 0,
    },
    // 항목 안 문단 — Crepe 는 .children > .content-dom > p 로 그린다. 보기의 `li > p` 처럼 위아래를 좁힌다
    '& .milkdown .milkdown-list-item-block .children p': { my: 0.25, py: 0 },
  } as const;
}
