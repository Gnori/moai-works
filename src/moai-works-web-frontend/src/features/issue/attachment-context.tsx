import { createContext, useContext, useMemo, type ReactNode } from 'react';

import { attachmentApi } from '@/shared/api/endpoints';

/** 본문의 첨부 표시(attachment:<id>)를 그릴 때 쓰는 것 — 어느 일감의 첨부인지 */
export interface AttachmentLinks {
  /** 첨부 id → 이 일감의 첨부 내용 주소 */
  contentUrl: (id: string) => string;
}

const AttachmentLinksContext = createContext<AttachmentLinks | null>(null);

/**
 * 이 아래의 본문 보기(MarkdownView)가 attachment:<id> 를 이 일감의 첨부 내용 주소로 바꿔 그린다.
 * 일감 보기 · 고치기(미리보기) · 리비전 화면이 감싼다. 감싸지 않은 곳(새 일감 미리보기)에서는 첨부 표시를 글로만 보인다.
 */
export function IssueAttachmentsProvider({ slug, number, children }: {
  slug: string;
  number: number;
  children: ReactNode;
}) {
  const value = useMemo<AttachmentLinks>(
    () => ({ contentUrl: (id) => attachmentApi.contentUrl(slug, number, id) }),
    [slug, number],
  );
  return <AttachmentLinksContext.Provider value={value}>{children}</AttachmentLinksContext.Provider>;
}

/** 지금 일감의 첨부 주소 — 일감 밖이면 null */
export function useAttachmentLinks(): AttachmentLinks | null {
  return useContext(AttachmentLinksContext);
}
