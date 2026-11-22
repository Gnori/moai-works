import { defaultKeymap, history, historyKeymap } from '@codemirror/commands';
import { markdown } from '@codemirror/lang-markdown';
import { HighlightStyle, syntaxHighlighting } from '@codemirror/language';
import { Compartment, EditorState } from '@codemirror/state';
import { EditorView, keymap, placeholder as placeholderText } from '@codemirror/view';
import { tags } from '@lezer/highlight';
import Box from '@mui/material/Box';
import { useTheme, type Theme } from '@mui/material/styles';
import { useEffect, useMemo, useRef } from 'react';

interface Props {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  ariaLabel: string;
}

/**
 * Markdown 편집 칸(CodeMirror) — 일감 설명 · 댓글을 쓴다. moai-wiki 의 페이지 편집 칸과 같은 모양이다.
 * Tab 은 다음 칸으로 넘어간다(키보드만 쓰는 사람이 편집 칸에 갇히지 않게) — 들여쓰기는 공백으로 한다.
 * 높이는 감싸는 상자가 정한다(칸이 그 높이를 채우고 안에서 밀린다).
 */
export function MarkdownEditor({ value, onChange, placeholder, ariaLabel }: Props) {
  const theme = useTheme();
  const host = useRef<HTMLDivElement>(null);
  const view = useRef<EditorView | null>(null);
  const onChangeRef = useRef(onChange);
  useEffect(() => {
    onChangeRef.current = onChange;
  });
  const look = useRef(new Compartment());
  const lookExtension = useMemo(() => editorLook(theme), [theme]);

  // 편집 칸은 한 번만 만든다 — 값 · 테마는 아래 effect 들이 맞춘다
  useEffect(() => {
    if (!host.current) {
      return;
    }
    const created = new EditorView({
      parent: host.current,
      state: EditorState.create({
        doc: value,
        extensions: [
          history(),
          keymap.of([...defaultKeymap, ...historyKeymap]),
          markdown(),
          EditorView.lineWrapping,
          placeholderText(placeholder ?? ''),
          EditorView.contentAttributes.of({ 'aria-label': ariaLabel, spellcheck: 'false' }),
          EditorView.updateListener.of((update) => {
            if (update.docChanged) {
              onChangeRef.current(update.state.doc.toString());
            }
          }),
          look.current.of(lookExtension),
        ],
      }),
    });
    view.current = created;
    return () => {
      created.destroy();
      view.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 처음 한 번만
  }, []);

  // 밖에서 값이 바뀌었을 때만(다시 불러옴 · 댓글을 단 뒤 비움) 칸의 글을 바꾼다. 사람이 친 글은 이미 같다
  useEffect(() => {
    const current = view.current;
    if (current && current.state.doc.toString() !== value) {
      current.dispatch({ changes: { from: 0, to: current.state.doc.length, insert: value } });
    }
  }, [value]);

  useEffect(() => {
    view.current?.dispatch({ effects: look.current.reconfigure(lookExtension) });
  }, [lookExtension]);

  return <Box ref={host} sx={{ height: '100%', minHeight: 0, '& .cm-editor': { height: '100%' } }} />;
}

/**
 * 편집 칸의 모양 — 화면 테마(스킨 · 라이트/다크)를 따른다.
 * 색은 값이 아니라 테마의 CSS 변수(theme.vars)로 적는다 — 그래야 다크로 바꿨을 때 글자색이 함께 바뀐다
 * (theme.palette 의 값은 라이트 것이라, 그대로 쓰면 다크에서 글자가 배경에 묻힌다).
 */
function editorLook(theme: Theme) {
  const p = (theme.vars ?? theme).palette;
  const mono = "'JetBrains Mono', 'SFMono-Regular', Menlo, Consolas, monospace";
  return [
    EditorView.theme({
      '&': { fontSize: '14px', backgroundColor: 'transparent', color: p.text.primary },
      '&.cm-focused': { outline: 'none' },
      '.cm-scroller': { fontFamily: mono, lineHeight: '1.7', overflow: 'auto' },
      '.cm-content': { padding: '14px 0', caretColor: p.primary.main },
      '.cm-line': { padding: '0 16px' },
      '.cm-cursor': { borderLeftColor: p.primary.main },
      '&.cm-focused .cm-selectionBackground, .cm-selectionBackground, .cm-content ::selection': {
        backgroundColor: p.action.selected,
      },
      '.cm-placeholder': { color: p.text.disabled },
    }),
    syntaxHighlighting(
      HighlightStyle.define([
        { tag: tags.heading, fontWeight: '700', color: p.primary.main },
        { tag: tags.strong, fontWeight: '700' },
        { tag: tags.emphasis, fontStyle: 'italic' },
        { tag: [tags.link, tags.url], color: p.info.main },
        { tag: tags.monospace, color: p.secondary.main },
        { tag: tags.quote, color: p.text.secondary },
        { tag: [tags.processingInstruction, tags.meta, tags.contentSeparator], color: p.text.disabled },
      ]),
    ),
  ];
}
