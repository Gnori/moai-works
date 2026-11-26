import type { AttachmentView } from '@/shared/api/types';

/**
 * 첨부 보기(AttachmentViewer)가 파일을 어떻게 보일지 — 화면이 쓰는 순수 함수(일감 #310).
 *
 * 형식은 파일 이름의 확장자를 먼저 보고(브라우저가 붙인 형식은 자주 틀린다 — .ts 가 video/mp2t 로 오는 식), 없으면 올릴 때 받은 형식으로 정한다.
 * 이미지만 서버가 내용(첫 바이트)으로 정한 `image` 를 믿는다. SVG 는 스크립트를 품을 수 있어 보지 않고 내려받기만 한다.
 * 글로 보는 형식(코드 · 글 · 표 …)은 받은 뒤에도 글인지(NUL 이 없고 UTF-8 로 읽히는지) 다시 본다 — 아니면 보지 않는다.
 */

export type ViewerKind =
  | 'image'
  | 'pdf'
  | 'markdown'
  | 'code'
  | 'json'
  | 'csv'
  | 'tsv'
  | 'text'
  | 'video'
  | 'audio'
  /** 이름 · 형식으로는 모른다 — 앞부분을 받아 글이면 글로 보인다 */
  | 'sniff'
  | 'unsupported';

export interface ViewerType {
  kind: ViewerKind;
  /** code · json — highlight.js 언어 이름 */
  language?: string;
  /** video · audio — blob 에 붙일 형식(서버는 늘 octet-stream 으로 준다) */
  mime?: string;
}

/** 글로 보는 형식은 앞의 이만큼만 받는다(2 MB) — 넘으면 잘랐다고 알린다 */
export const TEXT_LIMIT = 2 * 1024 * 1024;
/** 코드에 색을 입히는 크기 한도 — 넘으면 색 없이 줄 번호만 */
export const HIGHLIGHT_LIMIT = 512 * 1024;
/** 표(csv · tsv)로 그리는 줄 수 한도 — 넘는 줄은 원문 보기로 */
export const TABLE_ROW_LIMIT = 2000;

/** 확장자 → highlight.js 언어(코드 색 입히기). 쓸 때 받는 언어(code-languages 의 EXTRA)도 이름으로 적는다 */
const CODE_EXTENSIONS: Readonly<Record<string, readonly string[]>> = {
  javascript: ['js', 'mjs', 'cjs', 'jsx'],
  typescript: ['ts', 'mts', 'cts', 'tsx'],
  json: ['jsonc', 'json5'],
  python: ['py', 'pyw'],
  ruby: ['rb'],
  rust: ['rs'],
  go: ['go'],
  java: ['java'],
  kotlin: ['kt', 'kts'],
  scala: ['scala', 'sc'],
  groovy: ['groovy'],
  gradle: ['gradle'],
  c: ['c', 'h'],
  cpp: ['cpp', 'cc', 'cxx', 'hpp', 'hh', 'hxx'],
  csharp: ['cs'],
  objectivec: ['m', 'mm'],
  swift: ['swift'],
  dart: ['dart'],
  php: ['php'],
  perl: ['pl', 'pm'],
  lua: ['lua'],
  r: ['r'],
  sql: ['sql'],
  pgsql: ['pgsql'],
  bash: ['sh', 'bash', 'zsh'],
  powershell: ['ps1', 'psm1'],
  dos: ['bat', 'cmd'],
  yaml: ['yaml', 'yml'],
  ini: ['ini', 'cfg', 'conf', 'toml'],
  properties: ['properties'],
  xml: ['xml', 'xsd', 'xsl', 'xslt', 'plist', 'html', 'htm', 'xhtml', 'vue', 'svelte'],
  css: ['css'],
  scss: ['scss'],
  less: ['less'],
  graphql: ['graphql', 'gql'],
  diff: ['diff', 'patch'],
  makefile: ['mk', 'mak'],
  dockerfile: ['dockerfile'],
  protobuf: ['proto'],
  elixir: ['ex', 'exs'],
  erlang: ['erl', 'hrl'],
  haskell: ['hs'],
  clojure: ['clj', 'cljs', 'edn'],
  ocaml: ['ml', 'mli'],
  fsharp: ['fs', 'fsx'],
  julia: ['jl'],
  latex: ['tex'],
  vim: ['vim'],
  nix: ['nix'],
  handlebars: ['hbs', 'handlebars'],
  django: ['j2', 'jinja', 'jinja2'],
  verilog: ['v', 'sv', 'svh'],
  vhdl: ['vhd', 'vhdl'],
  x86asm: ['asm', 's'],
  http: ['http'],
  cmake: ['cmake'],
  vbnet: ['vb'],
  arduino: ['ino'],
};

/** 확장자 없이 이름으로 아는 코드 파일(소문자) */
const CODE_FILE_NAMES: Readonly<Record<string, string>> = {
  makefile: 'makefile',
  gnumakefile: 'makefile',
  dockerfile: 'dockerfile',
  containerfile: 'dockerfile',
  'cmakelists.txt': 'cmake',
};

const codeByExtension = new Map(
  Object.entries(CODE_EXTENSIONS).flatMap(([language, extensions]) => extensions.map((ext) => [ext, language] as const)),
);

const VIDEO: Readonly<Record<string, string>> = { mp4: 'video/mp4', webm: 'video/webm' };
const AUDIO: Readonly<Record<string, string>> = { mp3: 'audio/mpeg', wav: 'audio/wav', ogg: 'audio/ogg', m4a: 'audio/mp4' };
const VIDEO_TYPES: Readonly<Record<string, string>> = { 'video/mp4': 'video/mp4', 'video/webm': 'video/webm' };
const AUDIO_TYPES: Readonly<Record<string, string>> = {
  'audio/mpeg': 'audio/mpeg',
  'audio/mp3': 'audio/mpeg',
  'audio/wav': 'audio/wav',
  'audio/x-wav': 'audio/wav',
  'audio/wave': 'audio/wav',
  'audio/ogg': 'audio/ogg',
  'audio/mp4': 'audio/mp4',
  'audio/x-m4a': 'audio/mp4',
};
const TEXT_EXTENSIONS = new Set(['txt', 'text', 'log', 'out']);

/** 보지 않는 것이 분명한 확장자 — 받아 보지도 않고 내려받기만(오피스 · 한글 · 압축 · 실행 파일 · 그 밖의 그림 · 소리 · 영상 · 글꼴) */
const UNSUPPORTED_EXTENSIONS = new Set([
  'svg', 'svgz',
  'doc', 'docx', 'xls', 'xlsx', 'xlsm', 'ppt', 'pptx', 'odt', 'ods', 'odp', 'rtf', 'hwp', 'hwpx', 'pages', 'numbers', 'key',
  'zip', '7z', 'rar', 'gz', 'tgz', 'bz2', 'xz', 'tar', 'jar', 'war', 'ear',
  'exe', 'dll', 'so', 'dylib', 'bin', 'dmg', 'iso', 'apk', 'ipa', 'msi', 'class', 'o', 'a', 'wasm',
  'bmp', 'tif', 'tiff', 'heic', 'heif', 'ico', 'psd', 'ai', 'eps', 'raw', 'cr2', 'nef',
  'mov', 'avi', 'mkv', 'wmv', 'flv', 'mpg', 'mpeg', 'm4v',
  'flac', 'aac', 'wma', 'opus', 'mid', 'midi',
  'ttf', 'otf', 'woff', 'woff2', 'eot',
  'sqlite', 'db', 'mdb', 'accdb', 'parquet', 'pkl',
]);

/** 파일 이름의 확장자(소문자, 점 없이). 없거나 점으로 시작하는 이름(.gitignore)만 있으면 '' */
export function extensionOf(fileName: string): string {
  const dot = fileName.lastIndexOf('.');
  return dot > 0 && dot < fileName.length - 1 ? fileName.slice(dot + 1).toLowerCase() : '';
}

/** 첨부를 어떻게 보일까 — 확장자 → 받은 형식 → 모르면 앞부분을 받아 본다(sniff) */
export function viewerType(attachment: Pick<AttachmentView, 'fileName' | 'contentType' | 'image'>): ViewerType {
  const name = attachment.fileName.trim().toLowerCase();
  const ext = extensionOf(name);
  const type = (attachment.contentType ?? '').split(';')[0]?.trim().toLowerCase() ?? '';

  if (ext === 'svg' || ext === 'svgz' || type.includes('svg')) {
    return { kind: 'unsupported' };
  }
  if (attachment.image) {
    return { kind: 'image' };
  }

  // 확장자로
  const byName = CODE_FILE_NAMES[name.split('/').pop() ?? name];
  if (byName) {
    return { kind: 'code', language: byName };
  }
  if (ext === 'pdf') {
    return { kind: 'pdf' };
  }
  if (ext === 'md' || ext === 'markdown') {
    return { kind: 'markdown' };
  }
  if (ext === 'json') {
    return { kind: 'json', language: 'json' };
  }
  if (ext === 'csv') {
    return { kind: 'csv' };
  }
  if (ext === 'tsv') {
    return { kind: 'tsv' };
  }
  if (VIDEO[ext]) {
    return { kind: 'video', mime: VIDEO[ext] };
  }
  if (AUDIO[ext]) {
    return { kind: 'audio', mime: AUDIO[ext] };
  }
  const language = codeByExtension.get(ext);
  if (language) {
    return { kind: 'code', language };
  }
  if (TEXT_EXTENSIONS.has(ext)) {
    return { kind: 'text' };
  }
  if (UNSUPPORTED_EXTENSIONS.has(ext)) {
    return { kind: 'unsupported' };
  }

  // 받은 형식으로
  if (type === 'application/pdf') {
    return { kind: 'pdf' };
  }
  if (type === 'text/markdown' || type === 'text/x-markdown') {
    return { kind: 'markdown' };
  }
  if (type === 'application/json' || type.endsWith('+json')) {
    return { kind: 'json', language: 'json' };
  }
  if (type === 'text/csv') {
    return { kind: 'csv' };
  }
  if (type === 'text/tab-separated-values') {
    return { kind: 'tsv' };
  }
  if (VIDEO_TYPES[type]) {
    return { kind: 'video', mime: VIDEO_TYPES[type] };
  }
  if (AUDIO_TYPES[type]) {
    return { kind: 'audio', mime: AUDIO_TYPES[type] };
  }
  if (type.startsWith('text/')) {
    return { kind: 'text' };
  }
  if (
    /^(image|video|audio|font)\//.test(type) ||
    /^application\/(zip|gzip|x-7z-compressed|x-rar-compressed|vnd\.|msword|x-hwp|haansofthwp|x-msdownload|java-archive)/.test(type)
  ) {
    return { kind: 'unsupported' };
  }
  return { kind: 'sniff' };
}

/** 받아서 글로 읽는 형식 — 앞 2 MB 만 받는다 */
export function isTextKind(kind: ViewerKind): boolean {
  return kind === 'markdown' || kind === 'code' || kind === 'json' || kind === 'csv' || kind === 'tsv' || kind === 'text' || kind === 'sniff';
}

/** 확대 · 축소할 수 있는 형식(이미지 크기 · PDF 배율 · 글자 크기) */
export function isZoomable(kind: ViewerKind): boolean {
  return kind === 'image' || kind === 'pdf' || isTextKind(kind);
}

/**
 * 글인가 — 앞 8 KB 에 NUL 이 없고 UTF-8 로 틀림없이 읽힌다. 끝에서 잘린 여러 바이트 글자는 틀린 것으로 보지 않는다.
 */
export function looksLikeText(bytes: Uint8Array): boolean {
  const sample = bytes.subarray(0, 8192);
  if (sample.includes(0)) {
    return false;
  }
  try {
    new TextDecoder('utf-8', { fatal: true }).decode(sample, { stream: true });
    return true;
  } catch {
    return false;
  }
}

/** 받은 바이트를 글로 — UTF-8, 틀린 바이트는 �로(fatal: false). BOM 은 뗀다 */
export function decodeText(bytes: Uint8Array): string {
  return new TextDecoder('utf-8', { fatal: false }).decode(bytes);
}

/** JSON 을 보기 좋게(들여쓰기 2칸). JSON 이 아니면(잘렸거나 틀림) null — 원문 그대로 보인다 */
export function prettyJson(text: string): string | null {
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return null;
  }
}

export interface Table {
  rows: string[][];
  /** 한도(limit)를 넘어 그리지 않은 줄이 있다 */
  more: boolean;
}

/**
 * CSV · TSV 읽기 — 큰따옴표로 감싼 칸(안의 구분자 · 줄바꿈, "" 는 따옴표 하나), CRLF · LF. 끝의 빈 줄은 줄로 세지 않는다.
 * limit 줄까지만 모은다.
 */
export function parseDelimited(text: string, delimiter: ',' | '\t', limit = Number.POSITIVE_INFINITY): Table {
  const rows: string[][] = [];
  let row: string[] = [];
  let field = '';
  let quoted = false;
  let i = 0;
  const endRow = () => {
    row.push(field);
    rows.push(row);
    row = [];
    field = '';
  };
  while (i < text.length) {
    const ch = text[i];
    if (quoted) {
      if (ch === '"') {
        if (text[i + 1] === '"') {
          field += '"';
          i += 2;
          continue;
        }
        quoted = false;
      } else {
        field += ch;
      }
      i += 1;
      continue;
    }
    if (ch === '"' && field === '') {
      quoted = true;
    } else if (ch === delimiter) {
      row.push(field);
      field = '';
    } else if (ch === '\r' || ch === '\n') {
      endRow();
      if (ch === '\r' && text[i + 1] === '\n') {
        i += 1;
      }
      if (rows.length > limit) {
        rows.pop();
        return { rows, more: true };
      }
    } else {
      field += ch;
    }
    i += 1;
  }
  if (field !== '' || row.length > 0) {
    endRow();
  }
  if (rows.length > limit) {
    rows.length = limit;
    return { rows, more: true };
  }
  return { rows, more: false };
}

/** 확대 단계 — 25% ~ 400% */
export const ZOOM_STEPS: readonly number[] = [0.25, 0.33, 0.5, 0.67, 0.75, 0.8, 0.9, 1, 1.1, 1.25, 1.5, 1.75, 2, 2.5, 3, 4];
export const MIN_ZOOM = 0.25;
export const MAX_ZOOM = 4;

/** 한 단계 크게 — 지금 값보다 큰 첫 단계(이미 끝이면 그대로) */
export function zoomIn(zoom: number): number {
  return ZOOM_STEPS.find((step) => step > zoom + 0.001) ?? MAX_ZOOM;
}

/** 한 단계 작게 */
export function zoomOut(zoom: number): number {
  return [...ZOOM_STEPS].reverse().find((step) => step < zoom - 0.001) ?? MIN_ZOOM;
}
