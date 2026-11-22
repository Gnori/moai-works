/**
 * 유형별 일감 템플릿의 항목 읽기 — api 의 IssueTemplateService.sections · missing 과 같은 규칙.
 * 화면이 저장 전에 빠진 항목을 경고하는 데 쓴다(저장은 막지 않는다 — 2026-11-23 사용자 결정 "알려만 줌").
 *
 * - 항목 = 코드 블록(``` · ~~~) 밖의 제목(# ~ ######)
 * - 제목 끝이 "(선택)" · "(optional)" 이면 비워도 되는 항목
 * - 설명에 같은 제목이 있는지는 제목 수준 · 대소문자 · 공백을 따지지 않는다
 */

export interface TemplateSection {
  title: string;
  optional: boolean;
}

const HEADING = /^ {0,3}#{1,6}[ \t]+(.*?)[ \t]*#*[ \t]*$/;
const FENCE = /^ {0,3}(```|~~~)/;
const OPTIONAL = /\s*\((선택|optional)\)\s*$/i;

function headings(text: string | null | undefined): string[] {
  const out: string[] = [];
  if (!text || !text.trim()) {
    return out;
  }
  let inFence = false;
  for (const line of text.split(/\r\n|\r|\n/)) {
    if (FENCE.test(line)) {
      inFence = !inFence;
      continue;
    }
    if (inFence) {
      continue;
    }
    const m = HEADING.exec(line);
    if (m) {
      out.push(m[1] ?? '');
    }
  }
  return out;
}

const norm = (title: string) => title.trim().replace(/\s+/g, ' ').toLowerCase();

/** 템플릿의 항목 */
export function templateSections(body: string | null | undefined): TemplateSection[] {
  const out: TemplateSection[] = [];
  for (const title of headings(body)) {
    const optional = OPTIONAL.test(title);
    const plain = (optional ? title.replace(OPTIONAL, '') : title).trim();
    if (plain) {
      out.push({ title: plain, optional });
    }
  }
  return out;
}

/** 설명에 빠진 필수 항목 제목 */
export function missingSections(templateBody: string | null | undefined, description: string | null | undefined): string[] {
  const present = new Set(headings(description).map((t) => norm(t.replace(OPTIONAL, ''))));
  return templateSections(templateBody)
    .filter((s) => !s.optional && !present.has(norm(s.title)))
    .map((s) => s.title);
}

/** 설명이 비었거나 공백뿐이면 템플릿으로 채워도 된다(사람이 쓴 글을 덮지 않는다) */
export function canPrefill(description: string | null | undefined): boolean {
  return !description || !description.trim();
}
