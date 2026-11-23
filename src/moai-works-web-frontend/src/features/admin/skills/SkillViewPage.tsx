import DeleteOutlineRounded from '@mui/icons-material/DeleteOutlineRounded';
import DownloadRounded from '@mui/icons-material/DownloadRounded';
import EditRounded from '@mui/icons-material/EditRounded';
import RestoreFromTrashRounded from '@mui/icons-material/RestoreFromTrashRounded';
import SearchOffRounded from '@mui/icons-material/SearchOffRounded';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import Typography from '@mui/material/Typography';
import { useQueryClient } from '@tanstack/react-query';
import { useState, type SyntheticEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, useParams, useSearchParams } from 'react-router';
import { toast } from 'sonner';

import { skillEditPath, skillsPath } from '@/app/routes';
import { MarkdownView } from '@/features/issue/MarkdownView';
import { skillApi } from '@/shared/api/endpoints';
import { errorMessage, isApiError } from '@/shared/api/http';
import type { SkillDetail } from '@/shared/api/types';
import { dateTimeMs, fromNow } from '@/shared/lib/format';
import { useConfirm } from '@/shared/ui/ConfirmDialog';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

import { storeSkill, useSkillQuery } from './queries';
import { SkillRevisions } from './SkillRevisions';

type ViewTab = 'content' | 'revisions';

/**
 * 스킬 하나(시스템 관리자) — 머리(이름 · 지금 리비전 · 수정 · SKILL.md 내려받기 · 삭제 또는 되살리기)와 탭 둘: 내용(설명 · 본문을 그린 것) ·
 * 리비전(타임라인 · 차이 · 전체 · 되돌리기 — 일감 리비전 탭과 같은 모양). 탭과 고른 리비전은 주소에 남는다(?tab=revisions&rev=&from=&view=).
 */
export function SkillViewPage() {
  const { t } = useTranslation('admin');
  const name = useParams().name;
  const query = useSkillQuery(name);

  if (query.isError && isApiError(query.error) && query.error.status === 404) {
    return (
      <EmptyState
        icon={<SearchOffRounded />}
        title={t('skills.notFoundTitle')}
        description={t('skills.notFoundBody')}
        action={
          <Button variant="outlined" component={RouterLink} to={skillsPath()}>
            {t('skills.backToList')}
          </Button>
        }
      />
    );
  }
  if (query.isPending) {
    return <LoadingState />;
  }
  if (query.isError) {
    return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  }
  return <SkillView skill={query.data} />;
}

function SkillView({ skill }: { skill: SkillDetail }) {
  const { t } = useTranslation(['admin', 'common']);
  const confirm = useConfirm();
  const queryClient = useQueryClient();
  const [params, setParams] = useSearchParams();
  const tab: ViewTab = params.get('tab') === 'revisions' ? 'revisions' : 'content';
  const [busy, setBusy] = useState(false);

  const changeTab = (_: SyntheticEvent, next: ViewTab) => {
    // 고른 리비전(rev · from · view)은 탭을 옮겨도 남겨 둔다 — 돌아오면 보던 리비전
    const copy = new URLSearchParams(params);
    if (next === 'revisions') {
      copy.set('tab', 'revisions');
    } else {
      copy.delete('tab');
    }
    setParams(copy, { replace: true });
  };

  const remove = async () => {
    const ok = await confirm({
      title: t('skills.delete.title', { name: skill.name }),
      description: t('skills.delete.body'),
      confirmLabel: t('skills.delete.confirm'),
      destructive: true,
    });
    if (!ok) {
      return;
    }
    setBusy(true);
    try {
      storeSkill(queryClient, await skillApi.remove(skill.name));
      toast.success(t('skills.delete.done', { name: skill.name }));
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const undelete = async () => {
    setBusy(true);
    try {
      storeSkill(queryClient, await skillApi.undelete(skill.name));
      toast.success(t('skills.undelete.done', { name: skill.name }));
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Box>
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        spacing={1.5}
        sx={{ alignItems: { xs: 'stretch', md: 'flex-start' }, justifyContent: 'space-between', mb: 2 }}
      >
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="overline" sx={{ color: 'text.secondary', display: 'block' }}>
            <Box component={RouterLink} to={skillsPath({ deleted: skill.deleted })} sx={{ color: 'inherit', textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}>
              {`${t('eyebrow')} › ${t('skills.title')}`}
            </Box>
          </Typography>
          <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
            <Typography variant="h4" component="h1" sx={{ fontFamily: 'monospace', overflowWrap: 'anywhere' }}>
              {skill.name}
            </Typography>
            <Chip size="small" variant="outlined" label={t('skills.revisionNo', { no: skill.revisionNo })} />
            {skill.deleted && <Chip size="small" color="error" label={t('skills.deletedChip')} />}
          </Stack>
          <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mt: 0.5, fontVariantNumeric: 'tabular-nums' }}>
            {t('skills.view.updatedLine', {
              name: skill.updatedBy?.name ?? '—',
              time: dateTimeMs(skill.updatedAt),
              ago: fromNow(skill.updatedAt),
            })}
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap', justifyContent: { xs: 'flex-start', md: 'flex-end' }, flexShrink: 0 }}>
          {!skill.deleted && (
            <Button variant="contained" startIcon={<EditRounded />} component={RouterLink} to={skillEditPath(skill.name)}>
              {t('skills.actions.edit')}
            </Button>
          )}
          <Button variant="outlined" startIcon={<DownloadRounded />} component="a" href={skillApi.fileUrl(skill.name)} download={`${skill.name}-SKILL.md`}>
            {t('skills.actions.download')}
          </Button>
          {skill.deleted ? (
            <Button variant="outlined" startIcon={<RestoreFromTrashRounded />} onClick={() => void undelete()} loading={busy}>
              {t('skills.actions.undelete')}
            </Button>
          ) : (
            <Button variant="outlined" color="error" startIcon={<DeleteOutlineRounded />} onClick={() => void remove()} loading={busy}>
              {t('skills.actions.delete')}
            </Button>
          )}
        </Stack>
      </Stack>

      {skill.deleted && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          {t('skills.view.deletedNotice', {
            name: skill.deletedBy?.name ?? '—',
            time: skill.deletedAt ? dateTimeMs(skill.deletedAt) : '—',
          })}
        </Alert>
      )}

      <Tabs value={tab} onChange={changeTab} aria-label={t('skills.view.tabsLabel')} sx={{ borderBottom: 1, borderColor: 'divider', mb: 2.5 }}>
        <Tab value="content" label={t('skills.view.tabContent')} />
        <Tab
          value="revisions"
          label={
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <span>{t('skills.view.tabRevisions')}</span>
              <Chip size="small" label={skill.revisionNo} sx={{ height: 20, fontSize: 12, fontVariantNumeric: 'tabular-nums' }} />
            </Stack>
          }
        />
      </Tabs>

      {tab === 'content' ? <SkillContent skill={skill} /> : <SkillRevisions skill={skill} />}
    </Box>
  );
}

/** 내용 — 설명(Agent 가 언제 쓸지 고르는 글)과 본문(Markdown 을 그린 것) */
function SkillContent({ skill }: { skill: SkillDetail }) {
  const { t } = useTranslation('admin');
  return (
    <Stack spacing={2.5} sx={{ maxWidth: 1200 }}>
      <Paper variant="outlined" component="section" aria-label={t('skills.form.descriptionLabel')} sx={{ borderRadius: 2.5, p: { xs: 2, sm: 2.5 } }}>
        <Typography variant="overline" sx={{ color: 'text.secondary', display: 'block', lineHeight: 1.6 }}>
          {t('skills.form.descriptionLabel')}
        </Typography>
        <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
          {skill.description}
        </Typography>
      </Paper>
      <Paper variant="outlined" component="section" aria-label={t('skills.form.bodyLabel')} sx={{ borderRadius: 2.5, p: { xs: 2, sm: 3 } }}>
        <MarkdownView content={skill.body} />
      </Paper>
    </Stack>
  );
}
