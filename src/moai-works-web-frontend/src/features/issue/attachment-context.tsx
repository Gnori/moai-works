import { createContext, lazy, Suspense, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

import { attachmentApi } from '@/shared/api/endpoints';
import type { AttachmentView } from '@/shared/api/types';

import { useAttachmentsQuery } from './queries';

/** 첨부 보기는 처음 열 때 받는다 */
const AttachmentViewer = lazy(() => import('./AttachmentViewer'));

/** 본문의 첨부 표시(attachment:<id>)를 그릴 때 쓰는 것 — 어느 일감의 첨부인지 */
export interface AttachmentLinks {
  /** 첨부 id → 이 일감의 첨부 내용 주소 */
  contentUrl: (id: string) => string;
  /**
   * 첨부 보기로 연다(일감 #310) — 이 일감의 첨부 목록에 있으면 열고 true. 목록에 없으면(지웠음 · 목록을 아직 못 받음) false 라
   * 링크가 하던 대로(내려받기) 둔다.
   */
  view: (id: string) => boolean;
}

const AttachmentLinksContext = createContext<AttachmentLinks | null>(null);

/**
 * 이 아래의 본문 보기(MarkdownView)가 attachment:<id> 를 이 일감의 첨부 내용 주소로 바꿔 그리고, 누르면 첨부 보기를 연다.
 * 일감 보기 · 고치기(미리보기) · 리비전 화면이 감싼다. 감싼 곳에서는 첨부 목록(useAttachmentsQuery — 첨부 탭과 같은 것)을 받아 둔다.
 * 감싸지 않은 곳(새 일감 미리보기)에서는 첨부 표시를 글로만 보인다.
 */
export function IssueAttachmentsProvider({ slug, number, children }: {
  slug: string;
  number: number;
  children: ReactNode;
}) {
  const list = useAttachmentsQuery(slug, number);
  const [openId, setOpenId] = useState<string | null>(null);
  const [opened, setOpened] = useState(false);
  // 값(view)은 목록이 바뀌어도 그대로 둔다 — 바뀌면 본문의 그림 · 다이어그램이 통째로 다시 만들어진다
  const items = useRef<readonly AttachmentView[] | undefined>(undefined);
  useEffect(() => {
    items.current = list.data;
  }, [list.data]);

  const value = useMemo<AttachmentLinks>(
    () => ({
      contentUrl: (id) => attachmentApi.contentUrl(slug, number, id),
      view: (id) => {
        const found = items.current?.find((a) => a.id.toLowerCase() === id.toLowerCase());
        if (!found) {
          return false;
        }
        setOpened(true);
        setOpenId(found.id);
        return true;
      },
    }),
    [slug, number],
  );
  return (
    <AttachmentLinksContext.Provider value={value}>
      {children}
      {opened && (
        <Suspense fallback={null}>
          <AttachmentViewer slug={slug} number={number} items={list.data ?? []} openId={openId} onOpen={setOpenId} onClose={() => setOpenId(null)} />
        </Suspense>
      )}
    </AttachmentLinksContext.Provider>
  );
}

/** 지금 일감의 첨부 주소 — 일감 밖이면 null */
export function useAttachmentLinks(): AttachmentLinks | null {
  return useContext(AttachmentLinksContext);
}
