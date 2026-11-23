import { act, fireEvent, render, screen } from '@testing-library/react';
import { createRef, useState } from 'react';
import { beforeAll, describe, expect, it, vi } from 'vitest';

import '@/shared/i18n/i18n';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { MarkdownEditor, type MarkdownEditorHandle } from './MarkdownEditor';

/**
 * 편집기가 글을 바깥 값으로 올리는 때(moai-wiki 995b202 와 같은 방식) — 쓰는 동안은 마지막 입력 뒤 120ms 에 한 번 올리고,
 * "바뀜"(onPending)은 친 순간 바로 알린다. 지금 글이 필요한 곳(flush · 원문 전환 · 편집기가 사라질 때)은 기다리지 않고 바로 올린다.
 * 편집기는 시험용 편집기(editor/__mocks__)다 — 올리는 때를 진짜와 같게 흉내 낸다.
 */
beforeAll(() => {
  // jsdom 에는 글자 자리를 재는 함수가 없다 — CodeMirror(원문 보기)가 커서 자리를 잴 때 부른다
  const rects = () => Object.assign([], { item: () => null }) as unknown as DOMRectList;
  Range.prototype.getClientRects = rects;
  Range.prototype.getBoundingClientRect = () => new DOMRect();
  Element.prototype.scrollIntoView = vi.fn();
});

function paste(target: Element, text: string) {
  const event = new Event('paste', { bubbles: true, cancelable: true });
  Object.defineProperty(event, 'clipboardData', { value: { files: [], types: ['text/plain'], getData: () => text } });
  act(() => {
    target.dispatchEvent(event);
  });
}

/** 시험용 편집기는 붙여넣은 글을 끝에 붙인다 — 원문 보존(preserveSource)은 끝 줄바꿈을 원문처럼 둔다 */
const written = (added: string) => `# 제목\n\n본문\n${added}\n`;

function setup(value = '# 제목\n\n본문\n') {
  const onChange = vi.fn();
  const onPending = vi.fn();
  const ref = createRef<MarkdownEditorHandle>();
  // 바깥 값을 들고 있는 쪽(양식)처럼 — 원문 보기는 바깥 값을 그린다
  function Harness() {
    const [text, setText] = useState(value);
    return (
      <MarkdownEditor
        ref={ref}
        value={text}
        onChange={(next) => {
          onChange(next);
          setText(next);
        }}
        onPending={onPending}
        ariaLabel="설명"
      />
    );
  }
  const view = render(
    <AppearanceProvider>
      <Harness />
    </AppearanceProvider>,
  );
  return { onChange, onPending, ref, ...view };
}

describe('편집기가 글을 올리는 때', () => {
  it('친 순간 "바뀜"만 바로 알리고, 글은 마지막 입력 뒤 120ms 에 한 번 올린다', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    try {
      const { onChange, onPending } = setup();
      const editor = await vi.waitFor(() => screen.getByTestId('rich-editor'));
      paste(editor, '하나');
      paste(editor, '둘');
      expect(onPending).toHaveBeenCalledTimes(1);
      expect(onPending).toHaveBeenLastCalledWith(true);
      expect(onChange).not.toHaveBeenCalled();
      act(() => {
        vi.advanceTimersByTime(119);
      });
      expect(onChange).not.toHaveBeenCalled();
      act(() => {
        vi.advanceTimersByTime(1);
      });
      expect(onChange).toHaveBeenCalledTimes(1);
      expect(onChange).toHaveBeenLastCalledWith(written('하나둘'));
      expect(onPending).toHaveBeenLastCalledWith(false);
    } finally {
      vi.useRealTimers();
    }
  });

  it('flush 는 기다리지 않고 지금 글을 올리고 돌려준다', async () => {
    const { onChange, onPending, ref } = setup();
    paste(await screen.findByTestId('rich-editor'), '바로');
    let flushed = '';
    act(() => {
      flushed = ref.current!.flush();
    });
    expect(flushed).toBe(written('바로'));
    expect(onChange).toHaveBeenCalledWith(written('바로'));
    expect(onPending).toHaveBeenLastCalledWith(false);
    // 바뀐 것이 없으면 다시 올리지 않고 지금 글만 돌려준다
    expect(ref.current!.flush()).toBe(written('바로'));
    expect(onChange).toHaveBeenCalledTimes(1);
  });

  it('친 바로 다음 원문으로 바꿔도 방금 친 글이 원문에 있다', async () => {
    const { onChange } = setup();
    paste(await screen.findByTestId('rich-editor'), '원문으로');
    fireEvent.click(screen.getByRole('button', { name: 'Markdown 원문' }));
    expect(onChange).toHaveBeenCalledWith(written('원문으로'));
    // 원문 편집기는 따로 받아 온다(lazy)
    await vi.waitFor(() => expect(document.querySelector('.cm-content')).toHaveTextContent('원문으로'));
  });

  it('편집기가 사라질 때(닫기) 남은 글을 올린다', async () => {
    const { onChange, unmount } = setup();
    paste(await screen.findByTestId('rich-editor'), '닫기 전');
    unmount();
    expect(onChange).toHaveBeenCalledWith(written('닫기 전'));
  });
});
