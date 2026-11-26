import { describe, expect, it } from 'vitest';

import {
  extensionOf,
  isTextKind,
  isZoomable,
  looksLikeText,
  parseDelimited,
  prettyJson,
  viewerType,
  zoomIn,
  zoomOut,
  MAX_ZOOM,
  MIN_ZOOM,
} from './attachment-kinds';
import { baseLanguages, extraLanguageNames } from './code-languages';

/** 첨부 보기의 형식 고르기 · 글 판별 · CSV 읽기 · 배율 단계 (일감 #310) */

const file = (fileName: string, contentType = 'application/octet-stream', image = false) => ({ fileName, contentType, image });

describe('viewerType — 확장자 → 받은 형식 → 앞부분 보기', () => {
  it.each([
    ['화면.png', 'image/png', true, 'image'],
    ['스티커.webp', 'image/webp', true, 'image'],
    ['보고서.pdf', 'application/octet-stream', false, 'pdf'],
    ['README.md', 'application/octet-stream', false, 'markdown'],
    ['노트.markdown', '', false, 'markdown'],
    ['data.json', 'application/json', false, 'json'],
    ['표.csv', 'text/csv', false, 'csv'],
    ['표.tsv', '', false, 'tsv'],
    ['메모.txt', 'text/plain', false, 'text'],
    ['서버.log', '', false, 'text'],
    ['녹화.mp4', 'video/mp4', false, 'video'],
    ['녹화.webm', '', false, 'video'],
    ['음성.mp3', 'audio/mpeg', false, 'audio'],
    ['음성.wav', '', false, 'audio'],
    ['음성.ogg', '', false, 'audio'],
    ['음성.m4a', '', false, 'audio'],
    ['문서.docx', '', false, 'unsupported'],
    ['표.xlsx', '', false, 'unsupported'],
    ['보고서.hwp', '', false, 'unsupported'],
    ['묶음.zip', 'application/zip', false, 'unsupported'],
    ['영상.mov', 'video/quicktime', false, 'unsupported'],
    ['아무거나', 'application/octet-stream', false, 'sniff'],
    ['.gitignore', '', false, 'sniff'],
  ])('%s (%s) → %s', (name, type, image, kind) => {
    expect(viewerType(file(name, type, image)).kind).toBe(kind);
  });

  it('SVG 는 이미지 형식이어도 보지 않는다 — 스크립트를 품을 수 있다', () => {
    expect(viewerType(file('그림.svg', 'image/svg+xml')).kind).toBe('unsupported');
    expect(viewerType(file('그림', 'image/svg+xml')).kind).toBe('unsupported');
    expect(viewerType(file('그림.svg', 'image/svg+xml', true)).kind).toBe('unsupported');
  });

  it('코드는 확장자로 언어를 고른다 — 브라우저가 붙인 엉뚱한 형식(.ts → video/mp2t)보다 앞선다', () => {
    expect(viewerType(file('main.ts', 'video/mp2t'))).toEqual({ kind: 'code', language: 'typescript' });
    expect(viewerType(file('App.TSX'))).toEqual({ kind: 'code', language: 'typescript' });
    expect(viewerType(file('run.py'))).toEqual({ kind: 'code', language: 'python' });
    expect(viewerType(file('index.html', 'text/html'))).toEqual({ kind: 'code', language: 'xml' });
    expect(viewerType(file('build.gradle'))).toEqual({ kind: 'code', language: 'gradle' });
    expect(viewerType(file('Dockerfile'))).toEqual({ kind: 'code', language: 'dockerfile' });
    expect(viewerType(file('Makefile'))).toEqual({ kind: 'code', language: 'makefile' });
    expect(viewerType(file('CMakeLists.txt'))).toEqual({ kind: 'code', language: 'cmake' });
    expect(viewerType(file('fix.patch'))).toEqual({ kind: 'code', language: 'diff' });
    expect(viewerType(file('settings.jsonc'))).toEqual({ kind: 'code', language: 'json' });
  });

  it('고르는 코드 언어는 모두 색을 입힐 수 있다(처음부터 있거나 쓸 때 받는 언어)', () => {
    const known = new Set([...Object.keys(baseLanguages), ...extraLanguageNames]);
    const languages = ['js', 'ts', 'py', 'rb', 'rs', 'go', 'java', 'kt', 'scala', 'groovy', 'gradle', 'c', 'cpp', 'cs', 'm', 'swift', 'dart', 'php', 'pl', 'lua', 'r', 'sql', 'pgsql', 'sh', 'ps1', 'bat', 'yml', 'toml', 'properties', 'xml', 'css', 'scss', 'less', 'gql', 'diff', 'mk', 'dockerfile', 'proto', 'ex', 'erl', 'hs', 'clj', 'ml', 'fs', 'jl', 'tex', 'vim', 'nix', 'hbs', 'j2', 'v', 'vhd', 'asm', 'http', 'cmake', 'vb', 'ino']
      .map((ext) => viewerType(file(`a.${ext}`)).language ?? '');
    // ini 는 toml 을 별칭으로 갖는다 — 이름은 ini
    expect(languages.filter((name) => !known.has(name))).toEqual([]);
  });

  it('형식만 있을 때 — text/* 는 글, 오피스 · 그 밖의 그림 형식은 볼 수 없음', () => {
    expect(viewerType(file('a', 'application/pdf')).kind).toBe('pdf');
    expect(viewerType(file('a', 'text/markdown')).kind).toBe('markdown');
    expect(viewerType(file('a', 'application/ld+json')).kind).toBe('json');
    expect(viewerType(file('a', 'text/x-anything; charset=utf-8')).kind).toBe('text');
    expect(viewerType(file('a', 'audio/x-m4a'))).toEqual({ kind: 'audio', mime: 'audio/mp4' });
    expect(viewerType(file('a', 'application/vnd.ms-excel')).kind).toBe('unsupported');
    expect(viewerType(file('a', 'image/bmp')).kind).toBe('unsupported');
  });

  it('동영상 · 소리는 blob 에 붙일 형식을 준다', () => {
    expect(viewerType(file('a.mp4'))).toEqual({ kind: 'video', mime: 'video/mp4' });
    expect(viewerType(file('a.mp3'))).toEqual({ kind: 'audio', mime: 'audio/mpeg' });
  });

  it('글로 보는 형식 · 확대할 수 있는 형식', () => {
    expect(['markdown', 'code', 'json', 'csv', 'tsv', 'text', 'sniff'].every((kind) => isTextKind(kind as never))).toBe(true);
    expect(isTextKind('pdf')).toBe(false);
    expect(isZoomable('image')).toBe(true);
    expect(isZoomable('pdf')).toBe(true);
    expect(isZoomable('video')).toBe(false);
    expect(isZoomable('unsupported')).toBe(false);
  });
});

describe('extensionOf', () => {
  it('마지막 점 뒤(소문자). 점으로 시작하는 이름 · 점이 없으면 빈 글', () => {
    expect(extensionOf('a.tar.GZ')).toBe('gz');
    expect(extensionOf('.env')).toBe('');
    expect(extensionOf('README')).toBe('');
    expect(extensionOf('끝점.')).toBe('');
  });
});

describe('looksLikeText — NUL 이 없고 UTF-8 로 읽히나', () => {
  const utf8 = (text: string) => new TextEncoder().encode(text);
  it('한글 · 영어 글은 글', () => {
    expect(looksLikeText(utf8('안녕하세요 hello\n'))).toBe(true);
  });
  it('NUL 이 있거나 UTF-8 이 아니면 글이 아니다', () => {
    expect(looksLikeText(new Uint8Array([0x50, 0x4b, 0x03, 0x04, 0x00, 0x00]))).toBe(false);
    expect(looksLikeText(new Uint8Array([0xff, 0xfe, 0x41]))).toBe(false);
  });
  it('끝에서 잘린 여러 바이트 글자는 틀린 것으로 보지 않는다', () => {
    const bytes = utf8('가나다');
    expect(looksLikeText(bytes.subarray(0, bytes.length - 1))).toBe(true);
  });
});

describe('parseDelimited — CSV · TSV', () => {
  it('따옴표 칸 — 안의 쉼표 · 줄바꿈 · "" 는 따옴표 하나', () => {
    const { rows, more } = parseDelimited('이름,메모\r\n"김, 철수","말하길 ""안녕""\n둘째 줄"\n이영희,\n', ',');
    expect(rows).toEqual([
      ['이름', '메모'],
      ['김, 철수', '말하길 "안녕"\n둘째 줄'],
      ['이영희', ''],
    ]);
    expect(more).toBe(false);
  });
  it('TSV · 끝에 줄바꿈이 없어도', () => {
    expect(parseDelimited('a\tb\n1\t2', '\t').rows).toEqual([
      ['a', 'b'],
      ['1', '2'],
    ]);
  });
  it('한도를 넘으면 그 줄까지만 · more', () => {
    const text = Array.from({ length: 10 }, (_, i) => `${i},x`).join('\n');
    const table = parseDelimited(text, ',', 3);
    expect(table.rows).toHaveLength(3);
    expect(table.more).toBe(true);
    expect(parseDelimited('a\nb\nc\n', ',', 3).more).toBe(false);
  });
});

describe('prettyJson', () => {
  it('들여쓰기 2칸, JSON 이 아니면 null', () => {
    expect(prettyJson('{"a":[1,2]}')).toBe('{\n  "a": [\n    1,\n    2\n  ]\n}');
    expect(prettyJson('{"a": ')).toBeNull();
  });
});

describe('배율 단계 — 25% ~ 400%', () => {
  it('한 단계씩, 끝에서 멈춘다', () => {
    expect(zoomIn(1)).toBe(1.1);
    expect(zoomOut(1)).toBe(0.9);
    expect(zoomIn(0.23)).toBe(0.25);
    expect(zoomIn(MAX_ZOOM)).toBe(MAX_ZOOM);
    expect(zoomOut(MIN_ZOOM)).toBe(MIN_ZOOM);
    // 맞춤 배율(단계 사이 값)에서도 다음 단계로
    expect(zoomIn(0.42)).toBe(0.5);
    expect(zoomOut(0.42)).toBe(0.33);
  });
});
