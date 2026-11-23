import type { Crepe } from '@milkdown/crepe';
import { editorViewCtx, parserCtx, serializerCtx } from '@milkdown/kit/core';
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
import { Slice, type Node as ProseNode } from '@milkdown/kit/prose/model';
import { Plugin, PluginKey, TextSelection } from '@milkdown/kit/prose/state';
import { $prose, callCommand, outline } from '@milkdown/kit/utils';
import Box from '@mui/material/Box';
import { useEffect, useImperativeHandle, useRef, type Ref } from 'react';

import { createBlockSerializer } from './block-markdown';
import { createCrepe, startCrepe } from './crepe-setup';
import type { EditorAction, EditorActive, EditorHeading, EditorMark, RichEditorApi } from './editor-types';
import { syncDelay } from './sync-delay';

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
  /** 아직 내놓지 않은 글이 있다(true) · 다 내놓았다(false) — 글이 바뀐 순간 바로(직렬화 없이) */
  onPending?: (pending: boolean) => void;
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
  onPending,
  ref,
}: RichEditorProps) {
  const host = useRef<HTMLDivElement>(null);
  const crepeRef = useRef<Crepe | null>(null);
  const ready = useRef(false);
  const latest = useRef({ onReady, onMarkdown, onFiles, onOutline, onActive, onPending, resolveImage });
  useEffect(() => {
    latest.current = { onReady, onMarkdown, onFiles, onOutline, onActive, onPending, resolveImage };
  });
  const markers = useRef(new Map<number, Marker>());
  /** 아직 내놓지 않은 글을 바로 내놓는다 — 편집기를 만들 때 정해진다 */
  const flushRef = useRef<() => void>(() => undefined);
  const nextMarker = useRef(1);
  const valueRef = useRef(value);
  /** 문서 → Markdown — 바뀐 맨 위 덩어리만 다시 적는다(block-markdown, 일감 #16). 편집기가 준비되면 정해진다 */
  const markdownOf = useRef<((doc: ProseNode) => string) | null>(null);

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
  /** 마지막으로 알린 목차 · 눌림 표시 — 같으면 다시 알리지 않는다(쓰는 동안 도구 막대 · 목차를 다시 그리지 않게) */
  const lastOutline = useRef('');
  const lastActive = useRef('');
  const emitOutline = () => {
    const items = (action<{ text: string; level: number }[]>(outline()) ?? []).map((item) => ({ text: item.text, level: item.level }));
    const key = JSON.stringify(items);
    if (key !== lastOutline.current) {
      lastOutline.current = key;
      latest.current.onOutline?.(items);
    }
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
    const active: EditorActive = {
      bold: has('strong'),
      italic: has('emphasis'),
      strike: has('strike_through'),
      inlineCode: has('inlineCode'),
      heading: parent.type.name === 'heading' ? Number(parent.attrs.level) : 0,
    };
    const key = JSON.stringify(active);
    if (key !== lastActive.current) {
      lastActive.current = key;
      latest.current.onActive?.(active);
    }
  };
  const emitMarkdown = () => {
    const current = view();
    const write = markdownOf.current;
    if (current && write) {
      latest.current.onMarkdown(write(current.state.doc));
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
      flush: () => flushRef.current(),
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
    // 글이 바뀌는 것을 직접 지켜본다(moai-wiki 995b202 와 같다). plugin-listener 의 updated · markdownUpdated 는 둘 다 lodash debounce
    // 200ms 로 묶여 늦게 오고, 신호 안에서 보기 상태를 읽으면 만들어지는 중에는 예외가 난다.
    //   - "바뀜"은 글이 바뀐 순간 값싸게 바로 알린다(직렬화 없이 true) — 치자마자 저장을 누를 수 있게 단추를 켠다
    //   - 글(markdownOf → 원문 보존)은 마지막 입력 뒤 syncDelay(글 크기)에 한 번 내놓는다 — 쓰는 동안 프레임마다 적지 않는다.
    //     적을 때도 바뀐 맨 위 덩어리만 다시 적는다(block-markdown, 일감 #16)
    //   - 바로 내놓기(flush)는 편집기가 사라질 때 · 바깥이 부를 때(저장 · 원문 전환 · 닫기 · 다시 만들기 앞) · 편집 칸을 떠날 때
    //   - 목차 · 눌림 표시는 다음 화면 그리기 때 읽는다
    let frame = 0;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let pending = false;
    let outlineStale = false;
    const sync = () => {
      if (timer) {
        clearTimeout(timer);
        timer = undefined;
      }
      if (pending && ready.current) {
        pending = false;
        emitMarkdown();
        latest.current.onPending?.(false);
      }
    };
    flushRef.current = sync;
    crepe.editor.use(
      $prose(
        () =>
          new Plugin({
            key: new PluginKey('moai-works-watch'),
            view: () => ({
              update: (current, prevState) => {
                if (disposed || !ready.current) {
                  return;
                }
                if (!current.state.doc.eq(prevState.doc)) {
                  if (!pending) {
                    pending = true;
                    latest.current.onPending?.(true);
                  }
                  outlineStale = true;
                  if (timer) {
                    clearTimeout(timer);
                  }
                  timer = setTimeout(() => {
                    timer = undefined;
                    if (!disposed) {
                      sync();
                    }
                  }, syncDelay(current.state.doc.content.size));
                }
                if (!frame) {
                  frame = requestAnimationFrame(() => {
                    frame = 0;
                    if (disposed || !ready.current) {
                      return;
                    }
                    readActive();
                    if (outlineStale) {
                      outlineStale = false;
                      emitOutline();
                    }
                  });
                }
              },
            }),
          }),
      ),
    );
    crepeRef.current = crepe;
    void startCrepe(crepe).then(() => {
      if (disposed) {
        void crepe.destroy();
        return;
      }
      ready.current = true;
      // 원문 보존의 baseline — 이 원문을 편집기에 넣자마자 편집기가 내놓은 글(source-blocks.preserveSource). 문서 전체를 한 번에 적는다(getMarkdown 과 같다)
      const serialize = crepe.editor.ctx.get(serializerCtx);
      const doc = crepe.editor.ctx.get(editorViewCtx).state.doc;
      const baseline = serialize(doc);
      // 이후의 글은 바뀐 맨 위 덩어리만 다시 적는다. 여기서 덩어리마다 한 번 적어 기억을 채우고(쓰다 멈춘 첫 번에도 고친 덩어리만 적게),
      // 통째로 적은 글과 한 글자라도 다르면 이 편집기는 늘 문서 전체를 한 번에 적는다
      const blocks = createBlockSerializer(serialize);
      markdownOf.current = blocks(doc) === baseline ? blocks : serialize;
      latest.current.onReady?.(baseline);
      const editable = root.querySelector('.ProseMirror');
      editable?.setAttribute('aria-label', ariaLabel);
      editable?.setAttribute('aria-multiline', 'true');
      editable?.setAttribute('role', 'textbox');
      emitOutline();
      readActive();
    });
    return () => {
      // 사라지기 전에 마지막 글을 내놓는다(아직 내놓지 않은 것이 있으면)
      sync();
      disposed = true;
      if (frame) {
        cancelAnimationFrame(frame);
      }
      flushRef.current = () => undefined;
      ready.current = false;
      markdownOf.current = null;
      crepeRef.current = null;
      void crepe.destroy();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 처음 한 번만(안내 글 · 이름은 처음 값)
  }, []);

  // 편집 칸을 떠날 때(저장 단추를 누르기 직전) 아직 내놓지 않은 글을 바로 내놓는다
  return <Box ref={host} onBlur={() => flushRef.current()} sx={{ height: '100%' }} />;
}
