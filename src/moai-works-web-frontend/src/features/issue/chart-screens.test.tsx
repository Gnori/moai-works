import { useColorScheme } from '@mui/material/styles';
import { render, screen, waitFor, within } from '@testing-library/react';
import { useEffect, type ReactNode } from 'react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';

import '@/shared/i18n/i18n';
import { AppearanceProvider } from '@/theme/AppearanceProvider';
import { defaultSkin, skins } from '@/theme/skins';

import { ChartBlock } from './ChartBlock';
import { MarkdownView } from './MarkdownView';

/**
 * 본문 차트(```chart) — ChartBlock 이 종류마다 MUI X Charts 로 그리는지, 틀린 꼴은 mermaid 실패와 같은 모양으로 보이는지,
 * MarkdownView 가 chart 블록을 ChartBlock 으로 넘기는지. 차트 라이브러리는 따로 받는 조각이라 "그리는 중" 이 사라질 때까지 기다린다.
 */

function Providers({ children }: { children: ReactNode }) {
  return (
    <AppearanceProvider>
      <MemoryRouter>{children}</MemoryRouter>
    </AppearanceProvider>
  );
}

/** 차트 조각을 받아 그릴 때까지 */
async function drawn(name: string | RegExp) {
  const figure = await screen.findByRole('img', { name });
  await waitFor(() => expect(within(figure).queryByText('차트를 그리는 중입니다…')).not.toBeInTheDocument());
  return figure;
}

const examples: Record<string, string> = {
  bar: '{"type":"bar","title":"막대","labels":["월","화","수"],"series":[{"name":"완료","data":[3,5,null]}],"stacked":true,"horizontal":true,"xLabel":"요일","yLabel":"처리","unit":"건"}',
  mixed: '{"type":"bar","title":"섞기","labels":["월","화"],"series":[{"name":"완료","data":[3,5]},{"name":"새로","data":[1,2],"type":"line","color":"#c33"}],"unit":"건"}',
  line: '{"type":"line","title":"선","labels":["1월","2월","3월"],"series":[{"name":"열림","data":[4,null,6]},{"name":"닫힘","data":[1,2,3]}]}',
  area: '{"type":"area","title":"영역","labels":["1월","2월"],"series":[{"name":"쌓임","data":[4,6]}],"stacked":true}',
  pie: '{"type":"pie","title":"파이","data":[{"label":"버그","value":12},{"label":"새기능","value":20,"color":"#2a9"}]}',
  donut: '{"type":"donut","title":"도넛","data":[{"label":"버그","value":12},{"label":"테스트","value":0}]}',
  scatter: '{"type":"scatter","title":"흩어짐","series":[{"name":"일감","data":[[1,2],[3,4]]}],"xLabel":"예상","yLabel":"실제","unit":"h"}',
  radar: '{"type":"radar","title":"레이더","labels":["속도","품질","비용"],"series":[{"name":"이번 달","data":[3,4,5]}],"max":5}',
  gauge: '{"type":"gauge","title":"게이지","value":63,"unit":"%"}',
  sparkline: '{"type":"sparkline","title":"스파크","data":[1,4,2,5],"labels":["a","b","c","d"]}',
};

describe('차트 그리기 (ChartBlock)', () => {
  it.each(Object.entries(examples))('%s — 제목이 접근성 이름이고, 그리다 멈추지 않는다', async (_, code) => {
    const title = (JSON.parse(code) as { title: string }).title;
    render(
      <Providers>
        <ChartBlock code={code} />
      </Providers>,
    );
    const figure = await drawn(title);
    expect(within(figure).getByText(title)).toBeInTheDocument();
    expect(within(figure).queryByText('이 차트를 그리다가 문제가 생겼습니다.')).not.toBeInTheDocument();
    expect(figure.querySelector('svg')).not.toBeNull();
  });

  it('막대 + 선 — 범례에 series 이름, 단위는 값 축 이름에', async () => {
    render(
      <Providers>
        <ChartBlock code={examples.mixed ?? ''} />
      </Providers>,
    );
    const figure = await drawn('섞기');
    expect(within(figure).getByText('완료')).toBeInTheDocument();
    expect(within(figure).getByText('새로')).toBeInTheDocument();
    expect(within(figure).getByText('(건)')).toBeInTheDocument();
  });

  it('도넛 — 제목이 없으면 이름은 "차트", 범례에 조각 이름', async () => {
    render(
      <Providers>
        <ChartBlock code='{"type":"donut","data":[{"label":"버그","value":12},{"label":"새기능","value":20}]}' />
      </Providers>,
    );
    const figure = await drawn('차트');
    expect(within(figure).getByText('버그')).toBeInTheDocument();
    expect(within(figure).getByText('새기능')).toBeInTheDocument();
  });

  it('게이지 — 값과 단위를 가운데에', async () => {
    render(
      <Providers>
        <ChartBlock code={examples.gauge ?? ''} />
      </Providers>,
    );
    const figure = await drawn('게이지');
    expect(within(figure).getByText('63%')).toBeInTheDocument();
  });

  it('글은 글자로만 — 제목 · 이름의 HTML 은 실행되지 않는다', async () => {
    const evil = '<img src=x onerror=alert(1)>';
    const { container } = render(
      <Providers>
        <ChartBlock code={JSON.stringify({ type: 'bar', title: evil, labels: [evil], series: [{ name: evil, data: [1] }] })} />
      </Providers>,
    );
    const figure = await drawn(evil);
    expect(within(figure).getAllByText(evil).length).toBeGreaterThan(0);
    expect(container.querySelector('img')).toBeNull();
  });

  it('다크에서는 다크의 주 색으로 — 화면 테마(CSS 변수)의 theme.palette 는 다크에서도 라이트 값이다', async () => {
    function Dark({ children }: { children: ReactNode }) {
      const { mode, setMode } = useColorScheme();
      useEffect(() => setMode('dark'), [setMode]);
      return mode === 'dark' ? children : null;
    }
    render(
      <Providers>
        <Dark>
          <ChartBlock code={examples.line ?? ''} />
        </Dark>
      </Providers>,
    );
    const figure = await drawn('선');
    const marks = [...figure.querySelectorAll('.MuiChartsLabelMark-fill')].map((mark) => mark.getAttribute('stroke') ?? mark.getAttribute('fill'));
    expect(marks[0]).toBe(skins[defaultSkin].dark.primary.main);
    expect(marks[0]).not.toBe(skins[defaultSkin].light.primary.main);
  });

  it('꼴이 틀리면 mermaid 실패와 같은 모양 — 머리(chart · 그릴 수 없는 차트) · 적힌 글 · 까닭 한 줄', () => {
    const code = '{"type":"bar","labels":["월","화"],"series":[{"name":"<b>완료</b>","data":[1]}]}';
    const { container } = render(
      <Providers>
        <ChartBlock code={code} />
      </Providers>,
    );
    const box = container.querySelector('.diagram-failed') as HTMLElement;
    expect(within(box).getByText('chart')).toBeInTheDocument();
    expect(within(box).getByText('그릴 수 없는 차트')).toBeInTheDocument();
    expect(within(box).getByText(code)).toBeInTheDocument();
    expect(within(box).getByText('series[0].data 의 길이가 labels 와(과) 다릅니다.')).toBeInTheDocument();
    expect(container.querySelector('b')).toBeNull();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  it('영어 화면 — 까닭도 영어', async () => {
    const { default: i18n } = await import('@/shared/i18n/i18n');
    await i18n.changeLanguage('en');
    try {
      render(
        <Providers>
          <ChartBlock code='{"type":"gauge","value":120}' />
        </Providers>,
      );
      expect(screen.getByText('Chart cannot be drawn')).toBeInTheDocument();
      expect(screen.getByText('value must be between min and max.')).toBeInTheDocument();
    } finally {
      await i18n.changeLanguage('ko');
    }
  });
});

describe('본문의 chart 블록 (MarkdownView)', () => {
  it('```chart 블록은 차트로, 틀린 블록은 실패 상자로 — 언어 이름은 대소문자를 가리지 않는다', async () => {
    const body = [
      '앞 문단',
      '',
      '```chart',
      '{"type":"pie","title":"유형별","data":[{"label":"버그","value":12}]}',
      '```',
      '',
      '```Chart',
      '{"type":"line"}',
      '```',
      '',
      '```json',
      '{"type":"pie"}',
      '```',
    ].join('\n');
    const { container } = render(
      <Providers>
        <MarkdownView content={body} />
      </Providers>,
    );
    const figure = await drawn('유형별');
    expect(within(figure).getByText('버그')).toBeInTheDocument();
    const failed = container.querySelector('.diagram-failed') as HTMLElement;
    expect(within(failed).getByText('labels 이(가) 없습니다.')).toBeInTheDocument();
    // 다른 언어의 코드 블록은 그대로 코드 상자
    expect(screen.getByText('json')).toBeInTheDocument();
    expect(screen.getAllByRole('img')).toHaveLength(1);
  });
});
