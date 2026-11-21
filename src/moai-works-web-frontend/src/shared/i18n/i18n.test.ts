import { languages, resources } from './i18n';

type Tree = { [key: string]: string | Tree };

function flatten(tree: Tree, prefix = ''): string[] {
  return Object.entries(tree).flatMap(([key, value]) =>
    typeof value === 'string' ? [`${prefix}${key}`] : flatten(value, `${prefix}${key}.`),
  );
}

function placeholders(text: string): string[] {
  return [...text.matchAll(/\{\{(\w+)\}\}|<(\w+)>/g)].map((m) => m[1] ?? m[2] ?? '').sort();
}

/** HTML 빈 요소 이름 — <Trans> 가 여는 즉시 닫아 버려 안의 글이 밖으로 빠진다(<link>회원가입</link> → 빈 링크) */
const voidElements = ['area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input', 'link', 'meta', 'source', 'track', 'wbr'];

function tags(text: string): string[] {
  return [...text.matchAll(/<(\w+)>/g)].map((m) => m[1] ?? '');
}

function lookup(tree: Tree, path: string): string {
  return path.split('.').reduce<string | Tree>((node, part) => (node as Tree)[part] as string | Tree, tree) as string;
}

describe('i18n resources', () => {
  const namespaces = Object.keys(resources.ko) as (keyof typeof resources.ko)[];

  it.each(namespaces)('%s: 한국어와 영어의 키가 같다', (ns) => {
    const ko = flatten(resources.ko[ns] as Tree).sort();
    for (const lang of languages) {
      expect(flatten(resources[lang][ns] as Tree).sort()).toEqual(ko);
    }
  });

  it.each(namespaces)('%s: 자리표시자({{x}} · <cta>)가 언어마다 같다', (ns) => {
    for (const key of flatten(resources.ko[ns] as Tree)) {
      const ko = placeholders(lookup(resources.ko[ns] as Tree, key));
      expect(placeholders(lookup(resources.en[ns] as Tree, key))).toEqual(ko);
    }
  });

  it.each(namespaces)('%s: 빈 문장이 없다', (ns) => {
    for (const lang of languages) {
      for (const key of flatten(resources[lang][ns] as Tree)) {
        expect(lookup(resources[lang][ns] as Tree, key).trim().length).toBeGreaterThan(0);
      }
    }
  });

  it.each(namespaces)('%s: <Trans> 태그 이름이 HTML 빈 요소가 아니다', (ns) => {
    for (const lang of languages) {
      for (const key of flatten(resources[lang][ns] as Tree)) {
        for (const tag of tags(lookup(resources[lang][ns] as Tree, key))) {
          expect(voidElements, `${lang}/${ns}:${key}`).not.toContain(tag);
        }
      }
    }
  });
});
