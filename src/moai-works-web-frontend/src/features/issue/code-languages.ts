/**
 * 본문 코드 블록이 색을 입히는 언어와 머리에 보일 이름.
 *
 * highlight.js 의 흔한 언어(lowlight `common`, 37개)는 처음부터 들어 있고, 개발 문서에 자주 나오는 30개는 그 언어의 코드 블록이
 * 있는 글을 볼 때만 받는다(언어마다 작은 조각 — 다 넣으면 본문 보기 조각이 gzip 107 → 151KB 로 커진다). 받기 전에는 색 없이 보이다가
 * 받으면 칠해진다. 적힌 언어만 쓴다 — 짐작하지 않는다(rehype-highlight `detect: false`). 여기 없는 언어는 색 없이 글자 그대로 보인다.
 */
import { common, type LanguageFn } from 'lowlight';
import { useEffect, useMemo, useSyncExternalStore } from 'react';

/** 흔한 언어 가운데 `shell` 은 "명령 기록"($ 프롬프트 뒤만 색칠)이다. ```shell 은 보통 그냥 명령이라 bash 로 읽고, 기록은 ```console 로 쓴다 */
const { shell: shellSession, ...commonLanguages } = common;

/** 처음부터 있는 언어 — 이 이름과 각 언어가 스스로 가진 별칭(ts · js · sh · yml · py …)으로 찾는다 */
export const baseLanguages: Readonly<Record<string, LanguageFn>> = { ...commonLanguages, console: shellSession as LanguageFn };

type Loader = () => Promise<{ default: LanguageFn }>;

/**
 * 쓸 때 받는 언어 — 받는 법과 적는 말(언어가 스스로 가진 별칭 + highlightAliases). 받기 전에 글에서 찾아야 하므로 별칭을 여기 다 적는다
 * (시험이 언어의 별칭과 견준다).
 */
const EXTRA_LANGUAGES: Readonly<Record<string, { load: Loader; aliases: readonly string[] }>> = {
  apache: { load: () => import('highlight.js/lib/languages/apache'), aliases: ['apacheconf'] },
  clojure: { load: () => import('highlight.js/lib/languages/clojure'), aliases: ['clj', 'edn'] },
  cmake: { load: () => import('highlight.js/lib/languages/cmake'), aliases: ['cmake.in'] },
  dart: { load: () => import('highlight.js/lib/languages/dart'), aliases: [] },
  django: { load: () => import('highlight.js/lib/languages/django'), aliases: ['jinja'] },
  dockerfile: { load: () => import('highlight.js/lib/languages/dockerfile'), aliases: ['docker', 'containerfile'] },
  dos: { load: () => import('highlight.js/lib/languages/dos'), aliases: ['bat', 'cmd', 'batch'] },
  elixir: { load: () => import('highlight.js/lib/languages/elixir'), aliases: ['ex', 'exs'] },
  erlang: { load: () => import('highlight.js/lib/languages/erlang'), aliases: ['erl'] },
  fsharp: { load: () => import('highlight.js/lib/languages/fsharp'), aliases: ['fs', 'f#'] },
  gradle: { load: () => import('highlight.js/lib/languages/gradle'), aliases: [] },
  groovy: { load: () => import('highlight.js/lib/languages/groovy'), aliases: [] },
  handlebars: {
    load: () => import('highlight.js/lib/languages/handlebars'),
    aliases: ['hbs', 'html.hbs', 'html.handlebars', 'htmlbars'],
  },
  haskell: { load: () => import('highlight.js/lib/languages/haskell'), aliases: ['hs'] },
  http: { load: () => import('highlight.js/lib/languages/http'), aliases: ['https'] },
  julia: { load: () => import('highlight.js/lib/languages/julia'), aliases: ['jl'] },
  latex: { load: () => import('highlight.js/lib/languages/latex'), aliases: ['tex'] },
  matlab: { load: () => import('highlight.js/lib/languages/matlab'), aliases: [] },
  nginx: { load: () => import('highlight.js/lib/languages/nginx'), aliases: ['nginxconf'] },
  nix: { load: () => import('highlight.js/lib/languages/nix'), aliases: ['nixos'] },
  ocaml: { load: () => import('highlight.js/lib/languages/ocaml'), aliases: ['ml'] },
  pgsql: { load: () => import('highlight.js/lib/languages/pgsql'), aliases: ['postgres', 'postgresql', 'psql'] },
  powershell: { load: () => import('highlight.js/lib/languages/powershell'), aliases: ['pwsh', 'ps', 'ps1'] },
  properties: { load: () => import('highlight.js/lib/languages/properties'), aliases: [] },
  protobuf: { load: () => import('highlight.js/lib/languages/protobuf'), aliases: ['proto'] },
  scala: { load: () => import('highlight.js/lib/languages/scala'), aliases: [] },
  verilog: { load: () => import('highlight.js/lib/languages/verilog'), aliases: ['v', 'sv', 'svh'] },
  vhdl: { load: () => import('highlight.js/lib/languages/vhdl'), aliases: [] },
  vim: { load: () => import('highlight.js/lib/languages/vim'), aliases: ['vimscript', 'viml'] },
  x86asm: { load: () => import('highlight.js/lib/languages/x86asm'), aliases: ['asm', 'nasm'] },
};

/** 쓸 때 받는 언어의 이름들 */
export const extraLanguageNames: readonly string[] = Object.keys(EXTRA_LANGUAGES);

/** 쓸 때 받는 언어가 받기 전에 스스로 밝히는 별칭 — 시험용 */
export function extraLanguageAliases(name: string): readonly string[] {
  return EXTRA_LANGUAGES[name]?.aliases ?? [];
}

/** 언어가 스스로 갖지 않은 별칭 — rehype-highlight 의 `aliases`. 아직 받지 않은 언어를 가리켜도 된다(받으면 통한다) */
export const highlightAliases: Readonly<Record<string, readonly string[]>> = {
  bash: ['shell'],
  dockerfile: ['containerfile'],
  dos: ['batch'],
  julia: ['jl'],
  pgsql: ['psql'],
  vim: ['vimscript', 'viml'],
  x86asm: ['asm', 'nasm'],
};

/** 적는 말 → 쓸 때 받는 언어 */
const extraByName = new Map(
  Object.entries(EXTRA_LANGUAGES).flatMap(([name, { aliases }]) => [name, ...aliases].map((alias) => [alias, name] as const)),
);

/** 코드 블록 여는 줄의 언어(```dockerfile · ~~~ ps1). 목록 · 인용 안의 것도 — 더 찾는 것은 괜찮다(받기만 한다) */
const FENCE_LANGUAGE = /(?:`{3,}|~{3,})[ \t]*([^\s`{}]+)/g;

/** 이 Markdown 의 코드 블록이 쓰는, 쓸 때 받는 언어 */
export function extraLanguagesIn(markdown: string): string[] {
  const names = new Set<string>();
  for (const match of markdown.matchAll(FENCE_LANGUAGE)) {
    const name = extraByName.get((match[1] ?? '').toLowerCase());
    if (name) {
      names.add(name);
    }
  }
  return [...names];
}

/** 지금 쓸 수 있는 언어 — 처음부터 있는 것 + 받은 것. 새로 받을 때마다 새 객체다(본문 보기가 다시 그린다) */
let available: Readonly<Record<string, LanguageFn>> = baseLanguages;
const pending = new Map<string, Promise<void>>();
const listeners = new Set<() => void>();

/** 언어를 받는다. 받았거나 받는 중이면 그것을 기다린다. 못 받으면(연결 끊김 · 새 판) 색 없이 두고 다음에 다시 해 본다 */
export function loadLanguages(names: readonly string[]): Promise<void> {
  return Promise.all(
    names.map((name) => {
      const extra = EXTRA_LANGUAGES[name];
      if (!extra || available[name]) {
        return undefined;
      }
      let job = pending.get(name);
      if (!job) {
        job = extra
          .load()
          .then((module) => {
            available = { ...available, [name]: module.default };
            listeners.forEach((listener) => listener());
          })
          .catch(() => undefined)
          .finally(() => pending.delete(name));
        pending.set(name, job);
      }
      return job;
    }),
  ).then(() => undefined);
}

/** 지금 쓸 수 있는 언어 */
export function highlightLanguages(): Readonly<Record<string, LanguageFn>> {
  return available;
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** 본문 보기 — 이 글이 쓰는 언어를 받고, 받으면 다시 그린다. 돌려준 값을 rehype-highlight 의 `languages` 로 쓴다 */
export function useHighlightLanguages(markdown: string): Readonly<Record<string, LanguageFn>> {
  const wanted = useMemo(() => extraLanguagesIn(markdown), [markdown]);
  const languages = useSyncExternalStore(subscribe, highlightLanguages);
  useEffect(() => {
    if (wanted.length > 0) {
      void loadLanguages(wanted);
    }
  }, [wanted]);
  return languages;
}

/** 머리에 보일 이름 — [이름, 적는 말…]. 없으면 적힌 그대로 보인다 */
const LABELS: readonly (readonly [string, ...string[]])[] = [
  ['TypeScript', 'typescript', 'ts', 'mts', 'cts'],
  ['TSX', 'tsx'],
  ['JavaScript', 'javascript', 'js', 'mjs', 'cjs'],
  ['JSX', 'jsx'],
  ['JSON', 'json', 'jsonc', 'json5'],
  ['Shell', 'sh', 'shell'],
  ['Bash', 'bash'],
  ['Zsh', 'zsh'],
  ['Console', 'console', 'shellsession'],
  ['PowerShell', 'powershell', 'pwsh', 'ps', 'ps1'],
  ['Batch', 'dos', 'bat', 'cmd', 'batch'],
  ['YAML', 'yaml', 'yml'],
  ['TOML', 'toml'],
  ['INI', 'ini'],
  ['Properties', 'properties'],
  ['HTML', 'html', 'xhtml'],
  ['XML', 'xml'],
  ['SVG', 'svg'],
  ['CSS', 'css'],
  ['SCSS', 'scss'],
  ['Less', 'less'],
  ['Python', 'python', 'py'],
  ['Python REPL', 'python-repl', 'pycon'],
  ['Ruby', 'ruby', 'rb'],
  ['Rust', 'rust', 'rs'],
  ['Go', 'go', 'golang'],
  ['Java', 'java'],
  ['Kotlin', 'kotlin', 'kt', 'kts'],
  ['Groovy', 'groovy'],
  ['Gradle', 'gradle'],
  ['Scala', 'scala'],
  ['C', 'c', 'h'],
  ['C++', 'cpp', 'cc', 'c++', 'hpp', 'cxx'],
  ['C#', 'csharp', 'cs', 'c#'],
  ['Objective-C', 'objectivec', 'objc'],
  ['Swift', 'swift'],
  ['Dart', 'dart'],
  ['PHP', 'php'],
  ['PHP template', 'php-template'],
  ['Perl', 'perl', 'pl'],
  ['Lua', 'lua'],
  ['R', 'r'],
  ['SQL', 'sql'],
  ['PostgreSQL', 'pgsql', 'postgres', 'postgresql', 'psql'],
  ['GraphQL', 'graphql', 'gql'],
  ['Markdown', 'markdown', 'md'],
  ['Diff', 'diff', 'patch'],
  ['Makefile', 'makefile', 'make', 'mk'],
  ['CMake', 'cmake'],
  ['Dockerfile', 'dockerfile', 'docker', 'containerfile'],
  ['Nginx', 'nginx', 'nginxconf'],
  ['Apache', 'apache', 'apacheconf'],
  ['HTTP', 'http', 'https'],
  ['Protocol Buffers', 'protobuf', 'proto'],
  ['Elixir', 'elixir', 'ex', 'exs'],
  ['Erlang', 'erlang', 'erl'],
  ['Haskell', 'haskell', 'hs'],
  ['Clojure', 'clojure', 'clj', 'edn'],
  ['OCaml', 'ocaml', 'ml'],
  ['F#', 'fsharp', 'fs', 'f#'],
  ['Julia', 'julia', 'jl'],
  ['MATLAB', 'matlab'],
  ['LaTeX', 'latex', 'tex'],
  ['Vim script', 'vim', 'vimscript', 'viml'],
  ['x86 Assembly', 'x86asm', 'asm', 'nasm'],
  ['Nix', 'nix'],
  ['Handlebars', 'handlebars', 'hbs'],
  ['Django', 'django'],
  ['Jinja', 'jinja'],
  ['Verilog', 'verilog', 'v'],
  ['SystemVerilog', 'sv'],
  ['VHDL', 'vhdl'],
  ['Arduino', 'arduino', 'ino'],
  ['VB.NET', 'vbnet', 'vb'],
  ['WebAssembly', 'wasm'],
  ['Text', 'plaintext', 'text', 'txt'],
];

const labels = new Map(LABELS.flatMap(([label, ...names]) => names.map((name) => [name, label] as const)));

/** 이름을 정해 둔 적는 말(소문자) — 시험이 모두 색을 입힐 수 있는지 본다 */
export const labeledLanguages: readonly string[] = [...labels.keys()];

/** 머리에 보일 언어 이름(ts → TypeScript). 모르는 말은 적힌 그대로, 적지 않았으면 null */
export function languageLabel(language: string | null): string | null {
  if (!language) {
    return null;
  }
  return labels.get(language.toLowerCase()) ?? language;
}
