import { defaultKeymap, history, historyKeymap } from '@codemirror/commands';
import { markdown } from '@codemirror/lang-markdown';
import { HighlightStyle, syntaxHighlighting } from '@codemirror/language';
import { Compartment, EditorState } from '@codemirror/state';
import { EditorView, keymap, placeholder as placeholderText } from '@codemirror/view';
import { tags } from '@lezer/highlight';
import Box from '@mui/material/Box';
import { useTheme, type Theme } from '@mui/material/styles';
import { useEffect, useImperativeHandle, useMemo, useRef, type Ref } from 'react';

import { pastedFiles } from '../attachments';

import type { EditorMark } from './editor-types';

/** 원문 편집 칸을 밖에서 다루는 손잡이 — 올린 첨부의 표시를 커서 자리에 넣을 때 쓴다 */
export interface SourceEditorHandle {
  /** 지금 커서(고른 글이 있으면 그 자리)에 글을 넣고, 넣은 자리를 돌려준다 */
  insert: (text: string) => EditorMark | null;
  /** 넣은 자리의 글을 바꾼다. 사람이 그 글을 고쳤으면 그 자리 끝에 덧붙인다 */
  replace: (mark: EditorMark | null, text: string) => void;
  /** 넣은 자리의 글을 지운다. 사람이 그 글을 고쳤으면 그대로 둔다 */
  remove: (mark: EditorMark | null) => void;
}

interface Props {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  ariaLabel: string;
  /** 파일을 붙여넣거나 끌어다 놓았을 때 — 주면 그 파일을 넘기고(끌어다 놓은 곳으로 커서를 옮긴 뒤), 주지 않으면 편집기 기본 동작 */
  onFiles?: (files: File[]) => void;
  ref?: Ref<SourceEditorHandle>;
}

interface MarkRange {
  from: number;
  to: number;
  text: string;
}

/**
 * Markdown 원문 편집 칸(CodeMirror) — 편집기의 "Markdown 원문" 보기. 쓰면서 바로 그려지는 편집기(RichEditor) 옆에서 글을 그대로 고친다.
 * moai-wiki 의 페이지 편집 칸과 같은 모양이다.
 * Tab 은 다음 칸으로 넘어간다(키보드만 쓰는 사람이 편집 칸에 갇히지 않게) — 들여쓰기는 공백으로 한다.
 * 높이는 감싸는 상자가 정한다(칸이 그 높이를 채우고 안에서 밀린다).
 * onFiles 를 주면 붙여넣거나 끌어다 놓은 파일을 넘긴다(첨부로 올리고, ref 손잡이로 커서 자리에 표시를 넣는다).
 */
export function MarkdownSourceEditor({ value, onChange, placeholder, ariaLabel, onFiles, ref }: Props) {
  const theme = useTheme();
  const host = useRef<HTMLDivElement>(null);
  const view = useRef<EditorView | null>(null);
  const onChangeRef = useRef(onChange);
  const onFilesRef = useRef(onFiles);
  useEffect(() => {
    onChangeRef.current = onChange;
    onFilesRef.current = onFiles;
  });
  const look = useRef(new Compartment());
  const lookExtension = useMemo(() => editorLook(theme), [theme]);
  /** 넣은 자리들 — 글이 바뀔 때마다 위치를 옮긴다 */
  const marks = useRef(new Map<number, MarkRange>());
  const nextMark = useRef(1);

  useImperativeHandle(
    ref,
    () => ({
      insert: (text) => {
        const current = view.current;
        if (!current) {
          return null;
        }
        const { from, to } = current.state.selection.main;
        current.dispatch({
          changes: { from, to, insert: text },
          selection: { anchor: from + text.length },
          scrollIntoView: true,
          userEvent: 'input.paste',
        });
        const id = nextMark.current++;
        marks.current.set(id, { from, to: from + text.length, text });
        return { id };
      },
      replace: (mark, text) => {
        const range = mark && marks.current.get(mark.id);
        const current = view.current;
        if (!mark || !range || !current) {
          return;
        }
        marks.current.delete(mark.id);
        const intact = current.state.sliceDoc(range.from, range.to) === range.text;
        current.dispatch({ changes: intact ? { from: range.from, to: range.to, insert: text } : { from: range.to, insert: text } });
      },
      remove: (mark) => {
        const range = mark && marks.current.get(mark.id);
        const current = view.current;
        if (!mark || !range || !current) {
          return;
        }
        marks.current.delete(mark.id);
        if (current.state.sliceDoc(range.from, range.to) === range.text) {
          current.dispatch({ changes: { from: range.from, to: range.to } });
        }
      },
    }),
    [],
  );

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
              // 넣은 자리 앞에 넣은 글은 자리 밖, 자리 끝에 붙인 글도 자리 밖
              for (const range of marks.current.values()) {
                range.from = update.changes.mapPos(range.from, 1);
                range.to = Math.max(range.from, update.changes.mapPos(range.to, -1));
              }
              onChangeRef.current(update.state.doc.toString());
            }
          }),
          EditorView.domEventHandlers({
            paste: (event) => {
              const handle = onFilesRef.current;
              const files = handle ? pastedFiles(event.clipboardData) : [];
              if (!handle || files.length === 0) {
                return false;
              }
              event.preventDefault();
              handle(files);
              return true;
            },
            drop: (event, editor) => {
              const handle = onFilesRef.current;
              const files = Array.from(event.dataTransfer?.files ?? []);
              if (!handle || files.length === 0) {
                return false;
              }
              event.preventDefault();
              const at = editor.posAtCoords({ x: event.clientX, y: event.clientY });
              if (at !== null) {
                editor.dispatch({ selection: { anchor: at } });
              }
              editor.focus();
              handle(files);
              return true;
            },
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
