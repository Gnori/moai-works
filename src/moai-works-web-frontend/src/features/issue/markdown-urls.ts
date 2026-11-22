import { defaultUrlTransform } from 'react-markdown';

/**
 * 본문(Markdown)의 주소 거르기 — 일감 설명 · 댓글을 그릴 때 react-markdown 의 urlTransform 으로 쓴다.
 *
 * 기본 규칙(react-markdown 의 defaultUrlTransform: http(s) · mailto · irc · xmpp 와 상대 주소만, javascript: · data: 같은 것은 버림)을
 * 그대로 두고, 첨부 표시 `attachment:<uuid>` 하나만 더 살린다. 그 주소는 그릴 때 이 일감의 첨부 내용 주소로 바꾼다.
 * attachment: 뒤가 uuid 가 아니면(attachment:../x 등) 기본 규칙대로 버린다.
 */

const ATTACHMENT_URL = /^attachment:([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/i;

/** 첨부 표시의 첨부 id — `attachment:<uuid>` 가 아니면 null */
export function attachmentIdOf(url: string | null | undefined): string | null {
  if (!url) {
    return null;
  }
  const match = ATTACHMENT_URL.exec(url);
  return match?.[1] ? match[1].toLowerCase() : null;
}

/** react-markdown 의 urlTransform — 기본 거르기 + attachment:<uuid> */
export function markdownUrlTransform(url: string): string {
  return attachmentIdOf(url) ? url : defaultUrlTransform(url);
}

/**
 * 첨부 표시 → 이 일감의 첨부 내용 주소. 첨부 표시가 아니거나, 어느 일감인지 모르면(새 일감 미리보기 등) null.
 *
 * @param contentUrl 첨부 id → 내용 주소 (/api/v1/workspaces/{slug}/projects/{key}/issues/{number}/attachments/{id}/content)
 */
export function resolveAttachmentUrl(url: string | null | undefined, contentUrl: ((id: string) => string) | null | undefined): string | null {
  const id = attachmentIdOf(url);
  return id && contentUrl ? contentUrl(id) : null;
}
