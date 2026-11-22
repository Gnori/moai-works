import { imageSchema } from '@milkdown/kit/preset/commonmark';

/**
 * 편집기 안에서 바깥 주소의 `<img>` 를 **만들지도 않는다** — 그림 마디가 HTML 로 적히는 모양(schema 의 toDOM · parseDOM) 자체를 바꾼다.
 * (moai-wiki 의 safe-image.ts 와 같은 방식. 위키는 그림을 모두 글자로 두고, 일감은 자기 일감의 첨부 그림만 그린다)
 *
 * 왜 nodeView 로는 모자라나: 편집기는 글자로 붙여 넣은 Markdown(클립보드에 text/plain 만 있을 때) · insert 같은 길에서
 * 마디를 한 번 HTML 로 적었다가(DOMSerializer — nodeView 가 아니라 schema 의 toDOM 을 쓴다) 다시 읽는다. 그때 그림 마디가
 * `<img src="바깥 주소">` 로 적히면, 화면에 붙지 않은 요소라도 브라우저가 그 주소를 불러오기 시작한다(바깥으로 요청이 나간다).
 * 화면 보안 정책(img-src)이 막아 주지만 거기에 기대지 않는다.
 *
 *   toDOM   — src 가 `attachment:<uuid>` 이고 이 일감의 첨부 주소로 풀리면 그 같은 출처 주소의 `<img>`, 아니면
 *             `<span data-image-src data-image-alt data-image-title>설명 글</span>`(불러오지 않는다).
 *             어느 쪽이든 원래 주소 · 설명 · 제목을 data 속성에 들고 있다.
 *   parseDOM — `[data-image-src]`(우리가 적은 것, 우선) → data 속성 그대로(같은 출처 주소가 아니라 `attachment:<id>` 로 돌아온다).
 *             `img[src]`(다른 곳에서 복사해 온 HTML) → 주소 · 설명만, 제목은 title 속성만(설명을 제목으로 베끼지 않는다).
 *
 * @param resolve 그림 주소 → 보이는 같은 출처 주소. 첨부 표시가 아니거나 어느 일감인지 모르면 null
 */
export function safeImageSchema(resolve: (src: string) => string | null) {
  return imageSchema.extendSchema((prev) => (ctx) => ({
    ...prev(ctx),
    draggable: false,
    toDOM: (node) => {
      const src = String(node.attrs.src ?? '');
      const alt = String(node.attrs.alt ?? '');
      const title = String(node.attrs.title ?? '');
      const data = { 'data-image-src': src, 'data-image-alt': alt, 'data-image-title': title };
      const url = resolve(src);
      if (url) {
        return ['img', { ...data, class: 'md-image', src: url, alt, ...(title ? { title } : {}), loading: 'lazy', decoding: 'async' }];
      }
      // 바깥 주소 · 모르는 첨부 · 올리는 중 표시 — 불러오지 않고 설명 글만(보기와 같다)
      return ['span', { ...data, class: 'md-image-missing', title: src }, alt || src];
    },
    parseDOM: [
      {
        tag: '[data-image-src]',
        priority: 60,
        getAttrs: (dom) => {
          const set = (dom as HTMLElement).dataset;
          return { src: set.imageSrc ?? '', alt: set.imageAlt ?? '', title: set.imageTitle ?? '' };
        },
      },
      {
        // 다른 곳에서 복사해 온 HTML 의 그림 — 주소 · 설명 · 제목 속성만 받는다(보이는 것은 위 toDOM 이 정한다)
        tag: 'img[src]',
        getAttrs: (dom) => {
          const element = dom as HTMLElement;
          return { src: element.getAttribute('src') ?? '', alt: element.getAttribute('alt') ?? '', title: element.getAttribute('title') ?? '' };
        },
      },
    ],
  }));
}
