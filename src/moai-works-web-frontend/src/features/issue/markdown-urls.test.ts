import { describe, expect, it } from 'vitest';

import { attachmentIdOf, markdownUrlTransform, resolveAttachmentUrl } from './markdown-urls';

const ID = '0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b';
const contentUrl = (id: string) => `/api/v1/workspaces/a/projects/web/issues/7/attachments/${id}/content`;

describe('본문 주소 거르기 (urlTransform)', () => {
  it('attachment:<uuid> 를 살린다', () => {
    expect(markdownUrlTransform(`attachment:${ID}`)).toBe(`attachment:${ID}`);
    expect(markdownUrlTransform(`attachment:${ID.toUpperCase()}`)).toBe(`attachment:${ID.toUpperCase()}`);
  });

  it('javascript: · data: · vbscript: 는 여전히 버린다', () => {
    for (const url of ['javascript:alert(1)', 'JavaScript:alert(1)', 'data:text/html,<script>alert(1)</script>', 'vbscript:msgbox(1)']) {
      expect(markdownUrlTransform(url), url).toBe('');
    }
  });

  it('uuid 가 아닌 attachment: 는 버린다 (경로 · 덧붙인 글)', () => {
    for (const url of ['attachment:../../etc/passwd', 'attachment:', `attachment:${ID}/x`, `attachment:${ID}?a=1`, `attachment: ${ID}`, 'attachment:javascript:alert(1)']) {
      expect(markdownUrlTransform(url), url).toBe('');
    }
  });

  it('원래 살리던 것(http(s) · mailto · 상대 주소 · #조각)은 그대로', () => {
    for (const url of ['https://example.com/a?b=1', 'http://x', 'mailto:a@b.c', '/w/a/projects/web/issues/3', '#steps', './doc.md']) {
      expect(markdownUrlTransform(url), url).toBe(url);
    }
  });
});

describe('첨부 표시 → 내용 주소', () => {
  it('attachment:<uuid> 에서 id 를 읽는다(소문자로)', () => {
    expect(attachmentIdOf(`attachment:${ID}`)).toBe(ID);
    expect(attachmentIdOf(`attachment:${ID.toUpperCase()}`)).toBe(ID);
    expect(attachmentIdOf('https://example.com')).toBeNull();
    expect(attachmentIdOf(undefined)).toBeNull();
  });

  it('이 일감의 첨부 내용 주소로 바꾸고, 일감을 모르면 null', () => {
    expect(resolveAttachmentUrl(`attachment:${ID}`, contentUrl)).toBe(`/api/v1/workspaces/a/projects/web/issues/7/attachments/${ID}/content`);
    expect(resolveAttachmentUrl(`attachment:${ID}`, null)).toBeNull();
    expect(resolveAttachmentUrl('https://example.com/a.png', contentUrl)).toBeNull();
    expect(resolveAttachmentUrl('attachment:nope', contentUrl)).toBeNull();
  });
});
