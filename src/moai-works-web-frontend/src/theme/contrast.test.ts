import { blend, contrast } from './contrast';
import { skinIds, skins } from './skins';
import { code, neutral, status } from './tokens';

const AA = 4.5;
const lightPaper = neutral[0];
const darkPaper = neutral[900];
const darkDefault = neutral[950];

describe.each(skinIds)('스킨 %s 대비 (WCAG AA)', (id) => {
  const skin = skins[id];

  it.each(['primary', 'secondary'] as const)('라이트 %s: 글자 · 색조 위 글자 · 단추 글자', (key) => {
    const tone = skin.light[key];
    expect(contrast(tone.main, lightPaper)).toBeGreaterThanOrEqual(AA);
    expect(contrast(tone.main, tone.light)).toBeGreaterThanOrEqual(AA);
    expect(contrast(tone.contrastText, tone.main)).toBeGreaterThanOrEqual(AA);
    expect(contrast(tone.contrastText, tone.dark)).toBeGreaterThanOrEqual(AA);
  });

  it.each(['primary', 'secondary'] as const)('다크 %s: 글자 · 색조 위 글자 · 단추 글자', (key) => {
    const tone = skin.dark[key];
    expect(contrast(tone.main, darkPaper)).toBeGreaterThanOrEqual(AA);
    expect(contrast(tone.main, darkDefault)).toBeGreaterThanOrEqual(AA);
    expect(contrast(tone.main, blend(tone.main, darkPaper, 0.2))).toBeGreaterThanOrEqual(AA);
    expect(contrast(tone.contrastText, tone.main)).toBeGreaterThanOrEqual(AA);
    expect(contrast(tone.contrastText, tone.dark)).toBeGreaterThanOrEqual(AA);
  });
});

describe('상태색 대비', () => {
  it.each(Object.keys(status.light) as (keyof typeof status.light)[])('라이트 %s', (key) => {
    const tone = status.light[key];
    expect(contrast(tone.main, lightPaper)).toBeGreaterThanOrEqual(AA);
    expect(contrast(tone.main, tone.light)).toBeGreaterThanOrEqual(AA);
  });

  it.each(Object.keys(status.dark) as (keyof typeof status.dark)[])('다크 %s', (key) => {
    const tone = status.dark[key];
    expect(contrast(tone.main, darkPaper)).toBeGreaterThanOrEqual(AA);
    expect(contrast(tone.main, blend(tone.main, darkPaper, 0.2))).toBeGreaterThanOrEqual(AA);
  });

  it.each(Object.keys(status.light) as (keyof typeof status.light)[])('알림 상자 %s: 색조 위 본문 글자', (key) => {
    // 라이트: 본문색(neutral 900) on 색조(light). 다크: 본문색(neutral 50) on 20% 색조 — 종이 · 대화상자(흰 16.5% 덮개) 위 모두
    const dialogPaper = blend('#FFFFFF', darkPaper, 0.165);
    expect(contrast(neutral[900], status.light[key].light)).toBeGreaterThanOrEqual(AA);
    expect(contrast(neutral[50], blend(status.dark[key].main, darkPaper, 0.2))).toBeGreaterThanOrEqual(AA);
    expect(contrast(neutral[50], blend(status.dark[key].main, dialogPaper, 0.2))).toBeGreaterThanOrEqual(AA);
  });

  it('보류 칩: 흐린 글자(text.secondary) on 옅은 바탕(background.subtle)', () => {
    // 일감 상태 "보류" 칩(2026-11-24, 일감 #152) — 라이트 neutral 600 on 100, 다크 neutral 400 on 850
    expect(contrast(neutral[600], neutral[100])).toBeGreaterThanOrEqual(AA);
    expect(contrast(neutral[400], neutral[850])).toBeGreaterThanOrEqual(AA);
  });

  it('확인요청 · 확인완료 칩: 정보 · 성공색 글자 on 바탕(테두리만, 일감 #272)', () => {
    // 칩은 바탕 없이 그려 종이(paper)나 화면 바탕(default) 위에 놓인다 — 라이트 neutral 0 · 50, 다크 neutral 900 · 950
    for (const key of ['info', 'success'] as const) {
      for (const bg of [neutral[0], neutral[50]]) {
        expect(contrast(status.light[key].main, bg)).toBeGreaterThanOrEqual(AA);
      }
      for (const bg of [darkPaper, darkDefault]) {
        expect(contrast(status.dark[key].main, bg)).toBeGreaterThanOrEqual(AA);
      }
    }
  });

  it('본문 글자색', () => {
    expect(contrast(neutral[900], neutral[50])).toBeGreaterThanOrEqual(AA);
    expect(contrast(neutral[600], neutral[0])).toBeGreaterThanOrEqual(AA);
    expect(contrast(neutral[50], neutral[950])).toBeGreaterThanOrEqual(AA);
    expect(contrast(neutral[400], neutral[900])).toBeGreaterThanOrEqual(AA);
  });
});

describe('코드 강조 색 대비', () => {
  const words = ['comment', 'keyword', 'string', 'value', 'title', 'builtin', 'tag'] as const;

  it.each(words)('%s: 코드 상자 바탕 위', (key) => {
    // 코드 상자의 바탕은 background.subtle — 라이트 neutral 100, 다크 neutral 850
    expect(contrast(code.light[key], neutral[100])).toBeGreaterThanOrEqual(AA);
    expect(contrast(code.dark[key], neutral[850])).toBeGreaterThanOrEqual(AA);
  });

  it('더한 줄 · 뺀 줄: 그 바탕 위', () => {
    expect(contrast(code.light.added, code.light.addedBg)).toBeGreaterThanOrEqual(AA);
    expect(contrast(code.light.removed, code.light.removedBg)).toBeGreaterThanOrEqual(AA);
    expect(contrast(code.dark.added, code.dark.addedBg)).toBeGreaterThanOrEqual(AA);
    expect(contrast(code.dark.removed, code.dark.removedBg)).toBeGreaterThanOrEqual(AA);
  });
});
