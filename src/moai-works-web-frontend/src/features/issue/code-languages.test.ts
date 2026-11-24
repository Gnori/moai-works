import hljs from 'highlight.js';
import { createLowlight } from 'lowlight';
import { beforeAll, describe, expect, it } from 'vitest';

import {
  baseLanguages,
  extraLanguageAliases,
  extraLanguageNames,
  extraLanguagesIn,
  highlightAliases,
  highlightLanguages,
  labeledLanguages,
  languageLabel,
  loadLanguages,
} from './code-languages';

/** 흔한 언어(common) 밖에서 더한 언어 */
const EXTRAS = [
  'dockerfile', 'nginx', 'apache', 'powershell', 'dos', 'gradle', 'groovy', 'scala', 'dart', 'elixir',
  'erlang', 'haskell', 'clojure', 'protobuf', 'http', 'properties', 'cmake', 'latex', 'matlab', 'julia',
  'ocaml', 'fsharp', 'vim', 'x86asm', 'nix', 'pgsql', 'handlebars', 'django', 'verilog', 'vhdl',
];

/** 본문 보기와 같은 설정의 강조기(rehype-highlight 가 안에서 만드는 것과 같다) — 쓸 때 받는 언어까지 모두 받은 뒤 */
function highlighter() {
  const lowlight = createLowlight(highlightLanguages());
  lowlight.registerAlias(highlightAliases);
  return lowlight;
}

describe('코드 블록의 언어', () => {
  beforeAll(async () => {
    await loadLanguages(extraLanguageNames);
  });

  it('흔한 언어 37개는 처음부터, 더한 30개는 받은 뒤 — 모두 67개', () => {
    expect(Object.keys(baseLanguages)).toHaveLength(37);
    expect([...extraLanguageNames].sort()).toEqual([...EXTRAS].sort());
    const lowlight = highlighter();
    expect(lowlight.listLanguages()).toHaveLength(67);
    for (const name of EXTRAS) {
      expect(lowlight.registered(name), name).toBe(true);
    }
  });

  it('흔히 적는 별칭으로도 찾는다', () => {
    const lowlight = highlighter();
    const aliases = ['ts', 'tsx', 'js', 'jsx', 'sh', 'zsh', 'yml', 'html', 'py', 'rb', 'rs', 'kt', 'docker', 'ps1', 'golang', 'cs', 'md', 'patch', 'proto', 'tex', 'jl', 'asm', 'psql', 'bat', 'hbs', 'jinja'];
    for (const alias of aliases) {
      expect(lowlight.registered(alias), alias).toBe(true);
    }
  });

  it('```shell 은 bash 로 칠하고, 명령 기록은 ```console', () => {
    const lowlight = highlighter();
    expect(JSON.stringify(lowlight.highlight('shell', 'echo "hi" | grep h'))).toContain('hljs-built_in');
    expect(JSON.stringify(lowlight.highlight('console', '$ npm test\nok'))).toContain('hljs-meta');
  });

  it('이름을 정해 둔 말은 모두 칠할 수 있다', () => {
    const lowlight = highlighter();
    for (const name of labeledLanguages) {
      expect(lowlight.registered(name), name).toBe(true);
    }
  });

  it('쓸 때 받는 언어는 받기 전에도 제 별칭을 다 안다(글에서 찾을 수 있게)', () => {
    const languages = highlightLanguages();
    for (const name of extraLanguageNames) {
      const own = languages[name]?.(hljs).aliases ?? [];
      expect(extraLanguageAliases(name), name).toEqual(expect.arrayContaining(own));
    }
  });

  it('글의 코드 블록에서 받을 언어를 찾는다 — 별칭 · 대소문자 · 목록 · 인용 · ~~~ 안에서도', () => {
    const markdown = [
      '```dockerfile',
      'FROM node:22',
      '```',
      '- ```PS1 title="run.ps1"',
      '  Get-Item .',
      '  ```',
      '> ~~~ jinja',
      '> {{ x }}',
      '> ~~~',
      '```ts',
      'const a = 1;',
      '```',
      '```terraform',
      '```',
    ].join('\n');
    expect(extraLanguagesIn(markdown)).toEqual(['dockerfile', 'powershell', 'django']);
    expect(extraLanguagesIn('```ts\n```\n그냥 글')).toEqual([]);
  });

  it('머리에는 읽기 좋은 이름을, 모르는 말은 적힌 그대로', () => {
    expect(languageLabel('ts')).toBe('TypeScript');
    expect(languageLabel('TS')).toBe('TypeScript');
    expect(languageLabel('tsx')).toBe('TSX');
    expect(languageLabel('sh')).toBe('Shell');
    expect(languageLabel('yml')).toBe('YAML');
    expect(languageLabel('docker')).toBe('Dockerfile');
    expect(languageLabel('ps1')).toBe('PowerShell');
    expect(languageLabel('pgsql')).toBe('PostgreSQL');
    expect(languageLabel('csharp')).toBe('C#');
    expect(languageLabel('terraform')).toBe('terraform');
    expect(languageLabel(null)).toBeNull();
  });
});
