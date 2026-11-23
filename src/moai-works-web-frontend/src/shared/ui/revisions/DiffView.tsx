import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import type { SxProps, Theme } from '@mui/material/styles';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';

import type { DiffLine } from '@/shared/api/types';

import { collapseSame } from './diff-lines';

/**
 * 두 리비전의 줄 단위 차이(git 의 unified diff 처럼) — 옛 줄 번호 · 새 줄 번호 · 표시 · 글.
 * 지운 줄은 빨강, 넣은 줄은 초록이고, 색만으로 전하지 않게 줄 앞에 − · + 를 함께 적는다.
 * 같은 줄이 길게 이어지는 곳은 접어 두고 누르면 편다. 긴 줄은 그 칸 안에서 접어 보인다(가로로 밀리지 않는다).
 * moai-wiki 의 DiffView 와 같은 모양이다. 일감 설명 · 스킬 본문의 리비전 차이가 함께 쓴다.
 *
 * @param label 표의 이름(화면 읽기용) — 예: "두 리비전의 설명 차이"
 */
export function DiffView({ lines, label }: { lines: DiffLine[]; label: string }) {
  const { t } = useTranslation();
  const rows = useMemo(() => collapseSame(lines), [lines]);
  const [opened, setOpened] = useState<ReadonlySet<number>>(new Set());

  return (
    <Box role="table" aria-label={label} sx={tableSx}>
      {rows.map((row, index) => {
        if (row.kind === 'line') {
          return <Row key={index} line={row.line} />;
        }
        if (opened.has(row.id)) {
          return row.lines.map((line, inner) => <Row key={`${index}:${inner}`} line={line} />);
        }
        return (
          <Box key={index} role="row" className="gap">
            <Link
              component="button"
              type="button"
              underline="hover"
              onClick={() => setOpened((before) => new Set([...before, row.id]))}
            >
              {t('revisions.sameLines', { count: row.lines.length })}
            </Link>
          </Box>
        );
      })}
    </Box>
  );
}

function Row({ line }: { line: DiffLine }) {
  const { t } = useTranslation();
  const mark = line.op === 'ADD' ? '+' : line.op === 'DEL' ? '−' : '';
  return (
    <Box role="row" className={line.op.toLowerCase()}>
      <Box role="cell" className="no" aria-hidden>
        {line.oldLine ?? ''}
      </Box>
      <Box role="cell" className="no" aria-hidden>
        {line.newLine ?? ''}
      </Box>
      <Box
        role="cell"
        className="mark"
        aria-label={line.op === 'ADD' ? t('revisions.added') : line.op === 'DEL' ? t('revisions.removed') : undefined}
      >
        {mark}
      </Box>
      <Box role="cell" className="text">
        {line.text || ' '}
      </Box>
    </Box>
  );
}

const tableSx: SxProps<Theme> = {
  border: 1,
  borderColor: 'divider',
  borderRadius: 2.5,
  overflow: 'hidden',
  fontFamily: "'JetBrains Mono', 'SFMono-Regular', Menlo, Consolas, monospace",
  fontSize: 13,
  lineHeight: 1.65,
  bgcolor: 'background.paper',
  '& [role=row]': { display: 'grid', gridTemplateColumns: '44px 44px 22px minmax(0, 1fr)' },
  '& .no': { textAlign: 'right', pr: 1, color: 'text.disabled', userSelect: 'none', fontSize: 12 },
  '& .mark': { textAlign: 'center', fontWeight: 700, userSelect: 'none' },
  '& .text': { whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', pr: 1.5 },
  '& .add': { bgcolor: 'success.light' },
  '& .add .mark': { color: 'success.main' },
  '& .del': { bgcolor: 'error.light' },
  '& .del .mark': { color: 'error.main' },
  '& .gap': {
    display: 'block',
    textAlign: 'center',
    py: 0.5,
    bgcolor: 'background.subtle',
    fontFamily: (theme) => theme.typography.fontFamily,
    fontSize: 12,
  },
  // 좁은 화면에서는 줄 번호 칸을 줄인다
  '@media (max-width: 599.95px)': {
    fontSize: 12,
    '& [role=row]': { gridTemplateColumns: '30px 30px 16px minmax(0, 1fr)' },
    '& .no': { pr: 0.5, fontSize: 11 },
  },
};
