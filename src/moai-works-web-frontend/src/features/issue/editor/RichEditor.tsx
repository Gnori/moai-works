import type { Crepe } from '@milkdown/crepe';
import { editorViewCtx, parserCtx } from '@milkdown/kit/core';
import { redoCommand, undoCommand } from '@milkdown/kit/plugin/history';
import {
  createCodeBlockCommand,
  insertHrCommand,
  toggleEmphasisCommand,
  toggleInlineCodeCommand,
  toggleLinkCommand,
  toggleStrongCommand,
  turnIntoTextCommand,
  wrapInBlockquoteCommand,
  wrapInBulletListCommand,
  wrapInHeadingCommand,
  wrapInOrderedListCommand,
} from '@milkdown/kit/preset/commonmark';
import { insertTableCommand, toggleStrikethroughCommand } from '@milkdown/kit/preset/gfm';
import { Slice } from '@milkdown/kit/prose/model';
import { Plugin, PluginKey, TextSelection } from '@milkdown/kit/prose/state';
import { $prose, callCommand, outline } from '@milkdown/kit/utils';
import Box from '@mui/material/Box';
import { useEffect, useImperativeHandle, useRef, type Ref } from 'react';

import { createCrepe, startCrepe } from './crepe-setup';
import type { EditorAction, EditorActive, EditorHeading, EditorMark, RichEditorApi } from './editor-types';

export interface RichEditorProps {
  /** 처음 넣을 Markdown. 밖에서 글을 통째로 바꾸면 감싸는 쪽이 편집기를 다시 만든다(key) */
  value: string;
  /** 편집기를 만든 직후 편집기가 내놓은 글(원문 보존의 baseline) */
  onReady?: (baseline: string) => void;
  /** 편집기가 내놓은 Markdown(원문 보존 전) — 바뀔 때마다, 그리고 편집 칸을 떠날 때 바로 */
  onMarkdown: (markdown: string) => void;
  placeholder?: string;
  linkPlaceholder?: string;
  ariaLabel: string;
  /** 그림 주소 → 보이는 주소(attachment:<id> → 이 일감의 첨부 내용 주소). null 이면 이름만 보인다 */
  resolveImage: (src: string) => string | null;
  /** 붙여넣거나 끌어 놓은 파일 */
  onFiles?: (files: File[], pos: number | null) => void;
  /** 제목 목록(목차) — 바뀔 때마다 */
  onOutline?: (headings: EditorHeading[]) => void;
  /** 커서 자리의 서식(도구 막대의 눌림 표시) — 바뀔 때마다 */
  onActive?: (active: EditorActive) => void;
  ref?: Ref<RichEditorApi>;
}

/** 올리는 동안 둔 자리 표시 — 글이 바뀌어도 따라가는 자리와 넣은 표시(대체 글) */
interface Marker {
  pos: number;
  alt: string;
}

/** `![대체 글](주소)` 의 대체 글 */
function altOf(markdown: string): string {
  const match = /^!\[([^\]]*)\]/.exec(markdown.trim());
  return match?.[1] ?? '';
}

/**
 * 쓰면서 바로 그려지는 Markdown 편집기 — Milkdown(Crepe). 위 도구 막대 · 목차 · 원문 보기는 감싸는 MarkdownEditor 가 맡고,
 * 여기서는 편집기를 만들고 · 명령을 돌리고 · Markdown 을 내놓는다. 화면 코드가 커서 따로 받는다(lazy).
 * 색은 화면 테마(theme.vars)를 따르고, 글 모양은 보기(MarkdownView)와 같게 둔다.
 */
export default function RichEditor({
  value,
  onReady,
  onMarkdown,
  placeholder,
  linkPlaceholder,
  ariaLabel,
  resolveImage,
  onFiles,
  onOutline,
  onActive,
  ref,
}: RichEditorProps) {
  const host = useRef<HTMLDivElement>(null);
  const crepeRef = useRef<Crepe | null>(null);
  const ready = useRef(false);
  const latest = useRef({ onReady, onMarkdown, onFiles, onOutline, onActive, resolveImage });
  useEffect(() => {
    latest.current = { onReady, onMarkdown, onFiles, onOutline, onActive, resolveImage };
  });
  const markers = useRef(new Map<number, Marker>());
  const nextMarker = useRef(1);
  const valueRef = useRef(value);

  /** 지금 편집기 보기(ProseMirror) — 아직 없으면 null */
  const view = () => {
    const crepe = crepeRef.current;
    if (!crepe || !ready.current) {
      return null;
    }
    return crepe.editor.ctx.get(editorViewCtx);
  };
  const action = <T,>(fn: Parameters<Crepe['editor']['action']>[0]): T | undefined => {
    const crepe = crepeRef.current;
    return crepe && ready.current ? (crepe.editor.action(fn) as T) : undefined;
  };
  /**
   * Markdown 을 커서(또는 pos) 자리에 넣는다 — 해석기로 바로 노드를 만든다.
   * Milkdown 의 insert(inline) · insertPos 는 HTML DOM 을 거쳐 노드를 만드는데, 그때 만든 <img src> 가 문서 밖에서도 받기를 시작하고
   * (attachment: 은 CSP 가 막지만 바깥 주소라면 남의 서버로 요청이 간다) 그림의 제목에 이름을 덧붙인다. 그래서 DOM 을 거치지 않는다.
   * 한 문단이면 줄 안 글로(지금 문단에 이어서), 여러 덩어리면 덩어리째 넣는다.
   */
  const insertMarkdown = (markdown: string, pos?: number) => {
    const crepe = crepeRef.current;
    if (!crepe || !ready.current) {
      return;
    }
    crepe.editor.action((ctx) => {
      const current = ctx.get(editorViewCtx);
      const doc = ctx.get(parserCtx)(markdown);
      if (!doc) {
        return;
      }
      const { tr } = current.state;
      const single = doc.childCount === 1 && doc.firstChild?.type.name === 'paragraph' ? doc.firstChild : null;
      if (pos === undefined) {
        tr.replaceSelection(single ? new Slice(single.content, 0, 0) : new Slice(doc.content, 0, 0));
      } else {
        const at = Math.max(0, Math.min(pos, tr.doc.content.size));
        if (single) {
          tr.insert(at, single.content);
        } else {
          tr.replaceRange(at, at, new Slice(doc.content, 0, 0));
        }
      }
      current.dispatch(tr.scrollIntoView());
    });
  };
  const emitOutline = () => {
    const items = action<{ text: string; level: number }[]>(outline()) ?? [];
    latest.current.onOutline?.(items.map((item) => ({ text: item.text, level: item.level })));
  };
  /** 커서 자리의 서식을 읽어 알린다 — 고른 글이 없으면 다음에 칠 글자의 서식(storedMarks)까지 본다 */
  const readActive = () => {
    const current = view();
    if (!current) {
      return;
    }
    const { $from, $to, empty } = current.state.selection;
    const marks = empty ? (current.state.storedMarks ?? $from.marks()) : ($from.marksAcross($to) ?? []);
    const has = (name: string) => marks.some((mark) => mark.type.name === name);
    const parent = $from.parent;
    latest.current.onActive?.({
      bold: has('strong'),
      italic: has('emphasis'),
      strike: has('strike_through'),
      inlineCode: has('inlineCode'),
      heading: parent.type.name === 'heading' ? Number(parent.attrs.level) : 0,
    });
  };
  const emitMarkdown = () => {
    const crepe = crepeRef.current;
    if (crepe && ready.current) {
      latest.current.onMarkdown(crepe.getMarkdown());
    }
  };

  useImperativeHandle(
    ref,
    (): RichEditorApi => ({
      run: (name: EditorAction, payload?: unknown) => {
        const current = view();
        if (!current) {
          return;
        }
        current.focus();
        switch (name) {
          case 'paragraph':
            action(callCommand(turnIntoTextCommand.key));
            break;
          case 'heading1':
          case 'heading2':
          case 'heading3':
            action(callCommand(wrapInHeadingCommand.key, Number(name.slice(-1))));
            break;
          case 'bold':
            action(callCommand(toggleStrongCommand.key));
            break;
          case 'italic':
            action(callCommand(toggleEmphasisCommand.key));
            break;
          case 'strike':
            action(callCommand(toggleStrikethroughCommand.key));
            break;
          case 'inlineCode':
            action(callCommand(toggleInlineCodeCommand.key));
            break;
          case 'link': {
            const { href, text } = (payload ?? {}) as { href: string; text?: string };
            if (current.state.selection.empty) {
              insertMarkdown(`[${(text || href).replace(/[[\]]/g, '')}](${href})`);
            } else {
              action(callCommand(toggleLinkCommand.key, { href }));
            }
            break;
          }
          case 'bulletList':
            action(callCommand(wrapInBulletListCommand.key));
            break;
          case 'orderedList':
            action(callCommand(wrapInOrderedListCommand.key));
            break;
          case 'taskList': {
            // 글머리 목록으로 감싼 뒤 지금 항목을 할 일(checked=false)로
            action(callCommand(wrapInBulletListCommand.key));
            const after = view();
            if (after) {
              const { $from } = after.state.selection;
              for (let depth = $from.depth; depth > 0; depth -= 1) {
                const node = $from.node(depth);
                if (node.type.name === 'list_item') {
                  after.dispatch(after.state.tr.setNodeMarkup($from.before(depth), undefined, { ...node.attrs, checked: false }));
                  break;
                }
              }
            }
            break;
          }
          case 'table':
            action(callCommand(insertTableCommand.key, { row: 3, col: 3 }));
            break;
          case 'quote':
            action(callCommand(wrapInBlockquoteCommand.key));
            break;
          case 'codeBlock':
            action(callCommand(createCodeBlockCommand.key));
            break;
          case 'hr':
            action(callCommand(insertHrCommand.key));
            break;
          case 'undo':
            action(callCommand(undoCommand.key));
            break;
          case 'redo':
            action(callCommand(redoCommand.key));
            break;
        }
        // 글자를 고르지 않고 굵게를 켜면 글도 선택도 바뀌지 않아 알림이 오지 않는다 — 여기서 바로 읽는다(moai-wiki 와 같다)
        readActive();
      },
      focus: () => view()?.focus(),
      placeCursor: (pos: number) => {
        const current = view();
        if (current) {
          const at = Math.max(0, Math.min(pos, current.state.doc.content.size));
          current.dispatch(current.state.tr.setSelection(TextSelection.near(current.state.doc.resolve(at))));
          current.focus();
        }
      },
      insertMarker: (markdown: string) => {
        const current = view();
        if (!current) {
          return null;
        }
        const pos = current.state.selection.from;
        insertMarkdown(markdown.trim());
        const id = nextMarker.current++;
        markers.current.set(id, { pos, alt: altOf(markdown) });
        return { id };
      },
      replaceMarker: (mark: EditorMark | null, markdown: string) => {
        const marker = mark && markers.current.get(mark.id);
        const current = view();
        if (!mark || !marker || !current) {
          return;
        }
        markers.current.delete(mark.id);
        const node = current.state.doc.nodeAt(marker.pos);
        if (node && node.type.name === 'image' && node.attrs.alt === marker.alt && !node.attrs.src) {
          current.dispatch(current.state.tr.delete(marker.pos, marker.pos + node.nodeSize));
        }
        insertMarkdown(markdown.trim(), marker.pos);
      },
      removeMarker: (mark: EditorMark | null) => {
        const marker = mark && markers.current.get(mark.id);
        const current = view();
        if (!mark || !marker || !current) {
          return;
        }
        markers.current.delete(mark.id);
        const node = current.state.doc.nodeAt(marker.pos);
        if (node && node.type.name === 'image' && node.attrs.alt === marker.alt && !node.attrs.src) {
          current.dispatch(current.state.tr.delete(marker.pos, marker.pos + node.nodeSize));
        }
      },
      flush: () => emitMarkdown(),
      scrollToHeading: (index: number) => {
        const headings = host.current?.querySelectorAll('.ProseMirror h1, .ProseMirror h2, .ProseMirror h3, .ProseMirror h4, .ProseMirror h5, .ProseMirror h6');
        headings?.[index]?.scrollIntoView({ block: 'start', behavior: 'smooth' });
      },
    }),
    // 손잡이의 함수들은 ref(편집기 · 알림 받을 곳)만 읽어 늘 같은 일을 한다 — 한 번만 만든다
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  // 편집기는 한 번만 만든다 — 값은 아래 effect 가 version 으로 맞춘다
  useEffect(() => {
    const root = host.current;
    if (!root) {
      return;
    }
    let disposed = false;
    const crepe = createCrepe({
      root,
      value: valueRef.current,
      placeholder,
      linkPlaceholder,
      resolveImage: (src) => latest.current.resolveImage(src),
      onFiles: (files, pos) => latest.current.onFiles?.(files, pos),
    });
    // 올리는 동안 둔 자리를 글이 바뀔 때마다 옮긴다
    crepe.editor.use(
      $prose(
        () =>
          new Plugin({
            key: new PluginKey('moai-works-upload-markers'),
            appendTransaction: (transactions) => {
              for (const tr of transactions) {
                if (tr.docChanged) {
                  for (const marker of markers.current.values()) {
                    marker.pos = tr.mapping.map(marker.pos, -1);
                  }
                }
              }
              return null;
            },
          }),
      ),
    );
    // updated · selectionUpdated 는 편집기가 글을 바꾸는 도중에 온다(그때 보기 상태를 읽으면 만들어지는 중에는 없어서 예외가 나고,
    // create() 가 거절돼 "준비됨"이 영영 안 된다) — 다음 화면 그리기 때 읽는다(moai-wiki 와 같다)
    let frame = 0;
    const scheduleRead = () => {
      if (frame || disposed) {
        return;
      }
      frame = requestAnimationFrame(() => {
        frame = 0;
        if (!disposed && ready.current) {
          readActive();
        }
      });
    };
    crepe.on((api) => {
      api.markdownUpdated(() => {
        if (!disposed) {
          emitMarkdown();
          emitOutline();
        }
      });
      api.updated(scheduleRead);
      api.selectionUpdated(scheduleRead);
    });
    crepeRef.current = crepe;
    void startCrepe(crepe).then(() => {
      if (disposed) {
        void crepe.destroy();
        return;
      }
      ready.current = true;
      // 원문 보존의 baseline — 이 원문을 편집기에 넣자마자 편집기가 내놓은 글(source-blocks.preserveSource)
      latest.current.onReady?.(crepe.getMarkdown());
      const editable = root.querySelector('.ProseMirror');
      editable?.setAttribute('aria-label', ariaLabel);
      editable?.setAttribute('aria-multiline', 'true');
      editable?.setAttribute('role', 'textbox');
      emitOutline();
      readActive();
    });
    return () => {
      disposed = true;
      if (frame) {
        cancelAnimationFrame(frame);
      }
      ready.current = false;
      crepeRef.current = null;
      void crepe.destroy();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 처음 한 번만(안내 글 · 이름은 처음 값)
  }, []);

  // 편집 칸을 떠날 때(저장 단추를 누르기 직전) 마지막 글을 바로 내놓는다 — 편집기의 알림은 조금 늦게 온다
  return <Box ref={host} onBlur={emitMarkdown} sx={{ height: '100%' }} />;
}
