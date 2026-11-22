import { Editor, parserCtx, rootCtx, schemaCtx, serializerCtx } from '@milkdown/kit/core';
import { commonmark } from '@milkdown/kit/preset/commonmark';
import { gfm } from '@milkdown/kit/preset/gfm';
import { DOMParser, DOMSerializer } from '@milkdown/kit/prose/model';
import { beforeAll, describe, expect, it } from 'vitest';

import { resolveAttachmentUrl } from '../markdown-urls';
import { safeImageSchema } from './safe-image';

const IMG = '0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b';
const content = (id: string) => `/api/v1/workspaces/a/projects/web/issues/7/attachments/${id}/content`;

let roundTrip: (markdown: string) => string;
/** 글자로 붙여 넣을 때(클립보드에 text/plain 만) 편집기가 하는 일 — Markdown → 마디 → HTML(schema 의 toDOM) → 마디 → Markdown */
let pasteAsText: (markdown: string) => { html: DocumentFragment; markdown: string };

beforeAll(async () => {
  const editor = await Editor.make()
    .config((ctx) => ctx.set(rootCtx, document.createElement('div')))
    .use(commonmark)
    .use(gfm)
    .use(safeImageSchema((src) => resolveAttachmentUrl(src, content)))
    .create();
  roundTrip = (markdown) => editor.action((ctx) => ctx.get(serializerCtx)(ctx.get(parserCtx)(markdown)));
  pasteAsText = (markdown) =>
    editor.action((ctx) => {
      const schema = ctx.get(schemaCtx);
      const html = DOMSerializer.fromSchema(schema).serializeFragment(ctx.get(parserCtx)(markdown).content) as DocumentFragment;
      return { html, markdown: ctx.get(serializerCtx)(DOMParser.fromSchema(schema).parse(html)) };
    });
});

describe('편집기의 그림 마디 — 바깥 주소의 <img> 를 만들지 않는다', () => {
  it('바깥 주소 그림은 HTML 로 적어도 <img> 가 아니라 설명 글이다 — 주소 · 설명은 잃지 않고, 설명을 제목으로 베끼지 않는다', () => {
    const source = '앞 ![회사 로고](https://evil.example/logo.png) 뒤\n';
    const pasted = pasteAsText(source);
    expect(pasted.html.querySelector('img')).toBeNull();
    expect(pasted.html.querySelector('.md-image-missing')?.textContent).toBe('회사 로고');
    expect(pasted.markdown).toBe(source);
    expect(roundTrip('![그림](https://evil.example/a.png "제목")\n')).toBe('![그림](https://evil.example/a.png "제목")\n');
  });

  it('이 일감의 첨부 그림만 같은 출처 주소의 <img> 로 적고, 다시 읽으면 attachment: 로 돌아온다', () => {
    const source = `![화면.png](attachment:${IMG})\n`;
    const pasted = pasteAsText(source);
    const images = [...pasted.html.querySelectorAll('img')];
    expect(images.map((img) => img.getAttribute('src'))).toEqual([content(IMG)]);
    expect(images[0]?.getAttribute('title')).toBeNull();
    expect(pasted.markdown).toBe(source);
  });

  it('모양이 틀린 attachment: · data: · javascript: 그림도 <img> 를 만들지 않는다', () => {
    for (const src of ['attachment:../secret', 'data:image/png;base64,AAAA', 'javascript:alert(1)']) {
      expect(pasteAsText(`![x](${src})\n`).html.querySelector('img')).toBeNull();
    }
  });

  it('다른 곳에서 복사해 온 HTML 의 <img> 는 주소 · 설명만 받는다(제목은 title 속성만)', async () => {
    const holder = document.implementation.createHTMLDocument('x').createElement('div');
    holder.innerHTML = '<p>앞 <img src="https://evil.example/x.png" alt="바깥"> 뒤</p>';
    const editor = await Editor.make()
      .config((ctx) => ctx.set(rootCtx, document.createElement('div')))
      .use(commonmark)
      .use(safeImageSchema(() => null))
      .create();
    const markdown = editor.action((ctx) => ctx.get(serializerCtx)(DOMParser.fromSchema(ctx.get(schemaCtx)).parse(holder)));
    expect(markdown).toBe('앞 ![바깥](https://evil.example/x.png) 뒤\n');
  });
});
