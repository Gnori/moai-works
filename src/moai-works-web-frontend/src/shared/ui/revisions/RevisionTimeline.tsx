import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import ListItemButton from '@mui/material/ListItemButton';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useCallback, useRef, type ReactNode, type RefObject } from 'react';
import { useTranslation } from 'react-i18next';

import type { RevisionInfo } from '@/shared/api/types';
import { dateTimeMs, fromNow } from '@/shared/lib/format';
import { layout } from '@/theme/tokens';

/**
 * 리비전 화면의 공통 조각 — 일감 보기의 리비전 탭(IssueRevisions)과 스킬의 리비전 탭(SkillRevisions)이 함께 쓴다.
 * 무엇이 바뀌었는지(칩) · 차이의 내용 · 리비전 전체는 쓰는 쪽이 그린다. 여기는 리비전 번호 · 누가 · 밀리초 시각 · MCP · 지금 리비전과 배치만.
 */

/** 이 너비(리비전 탭이 받은 너비)부터 타임라인과 차이를 나란히 둔다 — 주 메뉴를 접고 펴도 맞게 창이 아니라 탭 너비로 */
export const SIDE_BY_SIDE = '@container (min-width: 760px)';
/** 리비전 탭의 최대 폭 — 일감 보기의 속성 · 설명 · 댓글과 같은 1200px */
const MAX_WIDTH = 1200;
/** 화면 위에 붙는 것(좁은 화면의 위 막대) 아래로 띄우는 거리 — 고른 차이로 내려 줄 때 · 타임라인을 붙여 둘 때 */
const STICK_TOP = { xs: `${layout.topbarHeight + 16}px`, md: '32px' } as const;

/** 타임라인 · 카드의 작은 칩 모양 */
export const revisionChipSx = { height: 20, fontSize: 11 } as const;

/**
 * 타임라인(왼쪽 · 위)과 고른 리비전(오른쪽 · 아래)의 배치. 탭이 받은 너비가 760px 이상이면 나란히(타임라인 300px, 붙어 따라옴),
 * 아래면 쌓는다. 좁은 화면에서 리비전을 고르면 그 차이로 내려 주려면 {@link useRevisionReveal} 의 ref 를 넘긴다.
 */
export function RevisionSplit({ timeline, children, timelineRef, panelRef }: {
  timeline: ReactNode;
  children: ReactNode;
  timelineRef?: RefObject<HTMLDivElement | null>;
  panelRef?: RefObject<HTMLDivElement | null>;
}) {
  return (
    <Box sx={{ containerType: 'inline-size', maxWidth: MAX_WIDTH }}>
      <Box
        sx={{
          display: 'grid',
          gap: 3,
          alignItems: 'start',
          gridTemplateColumns: 'minmax(0, 1fr)',
          [SIDE_BY_SIDE]: { gridTemplateColumns: '300px minmax(0, 1fr)' },
        }}
      >
        <Box ref={timelineRef} sx={{ minWidth: 0, top: STICK_TOP, [SIDE_BY_SIDE]: { position: 'sticky' } }}>
          {timeline}
        </Box>
        <Box ref={panelRef} sx={{ minWidth: 0, scrollMarginTop: STICK_TOP }}>
          {children}
        </Box>
      </Box>
    </Box>
  );
}

/** 좁은 화면(차이가 타임라인 아래)에서 리비전을 고르면 그 차이로 내려 준다 — {@link RevisionSplit} 에 ref 를 넘긴다 */
export function useRevisionReveal() {
  const timelineRef = useRef<HTMLDivElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const reveal = useCallback(() => {
    const panel = panelRef.current;
    const timeline = timelineRef.current;
    if (panel && timeline && panel.getBoundingClientRect().top >= timeline.getBoundingClientRect().bottom - 1) {
      panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }, []);
  return { timelineRef, panelRef, reveal };
}

/**
 * 리비전 타임라인 — 새 리비전부터. 리비전 번호 · 누가 · 언제(밀리초까지) · 어느 길로(MCP) · 무엇을 바꿨나(tags) · 지금 리비전, 더 보기.
 *
 * @param tags  리비전 아래의 칩들(바꾼 필드 · 만듦 · 되돌림 …) — 쓰는 쪽이 정한다
 * @param extra 칩 아래 한 줄(바꾼 까닭 등) — 없으면 그리지 않는다
 */
export function RevisionTimeline<T extends RevisionInfo>({ items, selected, onSelect, hasMore, loadingMore, onMore, tags, extra }: {
  items: T[];
  selected: number;
  onSelect: (no: number) => void;
  hasMore: boolean;
  loadingMore: boolean;
  onMore: () => void;
  tags: (item: T) => ReactNode;
  extra?: (item: T) => ReactNode;
}) {
  const { t } = useTranslation();
  return (
    <Paper
      variant="outlined"
      sx={{
        borderRadius: 2.5,
        overflowY: 'auto',
        maxHeight: 360,
        [SIDE_BY_SIDE]: { maxHeight: 'calc(100dvh - 96px)' },
      }}
    >
      <Box component="ol" aria-label={t('revisions.listLabel')} sx={{ listStyle: 'none', m: 0, p: 0, py: 0.5 }}>
        {items.map((item, index) => (
          <TimelineItem
            key={item.no}
            item={item}
            selected={item.no === selected}
            first={index === 0}
            last={index === items.length - 1 && !hasMore}
            onSelect={() => onSelect(item.no)}
            tags={tags(item)}
            extra={extra?.(item)}
          />
        ))}
      </Box>
      {hasMore && (
        <Box sx={{ p: 1, textAlign: 'center', borderTop: 1, borderColor: 'divider' }}>
          <Button size="small" color="inherit" onClick={onMore} loading={loadingMore}>
            {t('revisions.more')}
          </Button>
        </Box>
      )}
    </Paper>
  );
}

function TimelineItem({ item, selected, first, last, onSelect, tags, extra }: {
  item: RevisionInfo;
  selected: boolean;
  first: boolean;
  last: boolean;
  onSelect: () => void;
  tags: ReactNode;
  extra: ReactNode;
}) {
  const { t } = useTranslation();
  return (
    <Box component="li">
      <ListItemButton
        selected={selected}
        aria-current={selected ? 'true' : undefined}
        onClick={onSelect}
        sx={{ alignItems: 'stretch', gap: 1.25, py: 1.25, pl: 1.5, pr: 1.5 }}
      >
        {/* 타임라인의 줄과 점 — 지금 리비전은 채운 점, 고른 리비전은 주 색 테두리(뜻은 옆의 글 "현재 리비전" · 선택 표시가 전한다) */}
        <Box aria-hidden sx={{ position: 'relative', width: 14, flexShrink: 0 }}>
          <Box
            sx={{
              position: 'absolute',
              left: 6,
              width: 2,
              top: first ? 12 : -10,
              bottom: last ? 'calc(100% - 12px)' : -10,
              bgcolor: 'divider',
            }}
          />
          <Box
            sx={{
              position: 'absolute',
              left: 1,
              top: 6,
              width: 12,
              height: 12,
              borderRadius: '50%',
              border: 2,
              borderColor: selected || item.current ? 'primary.main' : 'text.disabled',
              bgcolor: item.current ? 'primary.main' : 'background.paper',
            }}
          />
        </Box>
        <Box sx={{ minWidth: 0, flex: 1 }}>
          <RevisionHead info={item} />
          <Typography variant="body2" sx={{ mt: 0.25, overflowWrap: 'anywhere' }}>
            {revisionActor(item, t('revisions.service'), t('revisions.unknownPerson'))}
          </Typography>
          <RevisionTime createdAt={item.createdAt} />
          <Stack direction="row" spacing={0.5} useFlexGap aria-label={t('revisions.changedFields')} sx={{ flexWrap: 'wrap', mt: 0.75 }}>
            {tags}
          </Stack>
          {extra && (
            <Typography variant="caption" component="p" sx={{ mt: 0.5, color: 'text.secondary', overflowWrap: 'anywhere' }}>
              {extra}
            </Typography>
          )}
        </Box>
      </ListItemButton>
    </Box>
  );
}

/** 리비전 번호 · 현재 리비전 · MCP · 서비스 칩 한 줄 */
function RevisionHead({ info }: { info: RevisionInfo }) {
  const { t } = useTranslation();
  return (
    <Stack direction="row" spacing={0.75} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
      <Typography variant="subtitle2" sx={{ fontVariantNumeric: 'tabular-nums' }}>
        {t('revisions.revisionNo', { no: info.no })}
      </Typography>
      {info.current && <Chip size="small" color="primary" label={t('revisions.current')} sx={revisionChipSx} />}
      {info.via === 'MCP' && <Chip size="small" variant="outlined" label={t('revisions.viaMcp')} title={t('revisions.viaMcpHelp')} sx={revisionChipSx} />}
      {info.via === 'API' && <Chip size="small" variant="outlined" label={t('revisions.viaApi')} title={t('revisions.viaApiHelp')} sx={revisionChipSx} />}
      {info.actorType === 'SERVICE' && <Chip size="small" variant="outlined" label={t('revisions.service')} sx={revisionChipSx} />}
    </Stack>
  );
}

function RevisionTime({ createdAt }: { createdAt: string }) {
  return (
    <Typography variant="caption" component="time" dateTime={createdAt} sx={{ display: 'block', color: 'text.secondary', fontVariantNumeric: 'tabular-nums' }}>
      {dateTimeMs(createdAt)} · {fromNow(createdAt)}
    </Typography>
  );
}

/** 한 리비전의 머리 — 리비전 번호 · 현재 리비전 · MCP · 누가 · 언제(밀리초까지) */
export function RevisionMeta({ info }: { info: RevisionInfo }) {
  const { t } = useTranslation();
  return (
    <>
      <RevisionHead info={info} />
      <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
        {revisionActor(info, t('revisions.service'), t('revisions.unknownPerson'))}
      </Typography>
      <RevisionTime createdAt={info.createdAt} />
    </>
  );
}

/**
 * 견주는 리비전 하나(옛 쪽 · 새 쪽) — 리비전 머리. info 가 없으면 empty(빈 일감 · 없음)를 보인다.
 */
export function RevisionCard({ label, info, empty }: { label: string; info: RevisionInfo | null; empty: string }) {
  return (
    <Paper variant="outlined" sx={{ borderRadius: 2, px: 1.5, py: 1 }}>
      <Typography variant="overline" sx={{ color: 'text.secondary', lineHeight: 1.6, display: 'block' }}>
        {label}
      </Typography>
      {info ? <RevisionMeta info={info} /> : <Typography variant="subtitle2">{empty}</Typography>}
    </Paper>
  );
}

/** 리비전을 남긴 사람 이름 — 없으면 서비스 · 알 수 없음 */
export function revisionActor(info: Pick<RevisionInfo, 'actorType' | 'actorName'>, service: string, unknown: string): string {
  return info.actorName || (info.actorType === 'SERVICE' ? service : unknown);
}
