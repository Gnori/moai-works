import AddRounded from '@mui/icons-material/AddRounded';
import AutoStoriesRounded from '@mui/icons-material/AutoStoriesRounded';
import FileUploadRounded from '@mui/icons-material/FileUploadRounded';
import MoreVertRounded from '@mui/icons-material/MoreVertRounded';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Chip from '@mui/material/Chip';
import FormControlLabel from '@mui/material/FormControlLabel';
import IconButton from '@mui/material/IconButton';
import Link from '@mui/material/Link';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Switch from '@mui/material/Switch';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router';
import { toast } from 'sonner';

import { newSkillPath, skillEditPath, skillPath } from '@/app/routes';
import { skillApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import type { SkillSummary } from '@/shared/api/types';
import { dateTimeMs, fromNow } from '@/shared/lib/format';
import { useConfirm } from '@/shared/ui/ConfirmDialog';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';
import { VisuallyHidden } from '@/shared/ui/VisuallyHidden';

import { ImportSkillDialog } from './ImportSkillDialog';
import { skillKeys, storeSkill, useSkillsQuery } from './queries';
import { oneLine } from './skill-rules';

/** 휴대폰에서 열을 넘겨도 어느 줄인지 잃지 않게 이름 칸을 붙여 둔다 (WEB-DESIGN §4) */
const stickyCell = {
  position: 'sticky',
  left: 0,
  zIndex: 1,
  bgcolor: 'background.paper',
  borderRight: { xs: 1, md: 0 },
  borderRightColor: 'divider',
} as const;

/**
 * Agent 스킬 목록(시스템 관리자, 일감 #30) — 이름 · 설명(줄임) · 리비전 · 수정한 사람과 때. "삭제한 스킬도 보기"는 주소에 남는다(?deleted=1).
 * 새 스킬 · SKILL.md 가져오기 · 줄마다 보기 · 수정 · 내려받기 · 삭제(확인) · 되살리기. 경로 가드가 system-admin 을 확인한다(표시만 — 판정은 api).
 */
export function SkillsPage() {
  const { t } = useTranslation('admin');
  const [params, setParams] = useSearchParams();
  const showDeleted = params.get('deleted') === '1';
  const list = useSkillsQuery(showDeleted);
  const [importing, setImporting] = useState(false);
  const navigate = useNavigate();

  const toggleDeleted = (next: boolean) => {
    const copy = new URLSearchParams(params);
    if (next) {
      copy.set('deleted', '1');
    } else {
      copy.delete('deleted');
    }
    setParams(copy, { replace: true });
  };

  return (
    <>
      <PageHeader
        eyebrow={t('eyebrow')}
        title={t('skills.title')}
        description={t('skills.description')}
        actions={
          <>
            <Button variant="outlined" startIcon={<FileUploadRounded />} onClick={() => setImporting(true)}>
              {t('skills.import.open')}
            </Button>
            <Button variant="contained" startIcon={<AddRounded />} component={RouterLink} to={newSkillPath}>
              {t('skills.new')}
            </Button>
          </>
        }
      />
      <Card>
        <Box sx={{ px: 2, py: 1, borderBottom: 1, borderColor: 'divider' }}>
          <FormControlLabel
            control={<Switch checked={showDeleted} onChange={(event) => toggleDeleted(event.target.checked)} />}
            label={t('skills.showDeleted')}
          />
        </Box>
        {list.isPending ? (
          <LoadingState />
        ) : list.isError ? (
          <ErrorState error={list.error} onRetry={() => list.refetch()} />
        ) : list.data.length === 0 ? (
          <EmptyState
            icon={<AutoStoriesRounded />}
            title={t('skills.emptyTitle')}
            description={t('skills.emptyBody')}
            action={
              <Button variant="contained" component={RouterLink} to={newSkillPath}>
                {t('skills.new')}
              </Button>
            }
          />
        ) : (
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell sx={{ ...stickyCell, minWidth: 150 }}>{t('skills.colName')}</TableCell>
                  <TableCell sx={{ minWidth: 220 }}>{t('skills.colDescription')}</TableCell>
                  <TableCell align="right">{t('skills.colRevision')}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' }, minWidth: 180 }}>{t('skills.colUpdated')}</TableCell>
                  <TableCell align="right">
                    <VisuallyHidden>{t('skills.colActions')}</VisuallyHidden>
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {list.data.map((skill) => (
                  <SkillRow key={skill.name} skill={skill} onOpen={() => void navigate(skillPath(skill.name))} />
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      {importing && <ImportSkillDialog onClose={() => setImporting(false)} />}
    </>
  );
}

function SkillRow({ skill, onOpen }: { skill: SkillSummary; onOpen: () => void }) {
  const { t } = useTranslation('admin');
  const confirm = useConfirm();
  const queryClient = useQueryClient();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const close = () => setAnchor(null);

  const remove = async () => {
    close();
    const ok = await confirm({
      title: t('skills.delete.title', { name: skill.name }),
      description: t('skills.delete.body'),
      confirmLabel: t('skills.delete.confirm'),
      destructive: true,
    });
    if (!ok) {
      return;
    }
    try {
      storeSkill(queryClient, await skillApi.remove(skill.name));
      toast.success(t('skills.delete.done', { name: skill.name }));
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  const undelete = async () => {
    close();
    try {
      storeSkill(queryClient, await skillApi.undelete(skill.name));
      toast.success(t('skills.undelete.done', { name: skill.name }));
    } catch (e) {
      toast.error(errorMessage(e));
      void queryClient.invalidateQueries({ queryKey: skillKeys.all });
    }
  };

  const updated = skill.deleted && skill.deletedAt ? skill.deletedAt : skill.updatedAt;
  const by = skill.deleted ? skill.deletedBy : skill.updatedBy;
  return (
    <TableRow hover sx={skill.deleted ? { '& td': { color: 'text.secondary' } } : undefined}>
      <TableCell sx={{ ...stickyCell, verticalAlign: 'top' }}>
        <Link component={RouterLink} to={skillPath(skill.name)} underline="hover" sx={{ fontFamily: 'monospace', fontWeight: 600, overflowWrap: 'anywhere' }}>
          {skill.name}
        </Link>
        {skill.deleted && <Chip size="small" label={t('skills.deletedChip')} sx={{ ml: 1, height: 20, fontSize: 11 }} />}
      </TableCell>
      <TableCell sx={{ verticalAlign: 'top' }}>
        <Typography
          variant="body2"
          title={skill.description}
          onClick={onOpen}
          sx={{
            cursor: 'pointer',
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
            overflowWrap: 'anywhere',
            maxWidth: 560,
          }}
        >
          {oneLine(skill.description)}
        </Typography>
      </TableCell>
      <TableCell align="right" sx={{ verticalAlign: 'top', fontVariantNumeric: 'tabular-nums' }}>
        {skill.revisionNo}
      </TableCell>
      <TableCell sx={{ display: { xs: 'none', md: 'table-cell' }, verticalAlign: 'top' }}>
        <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
          {skill.deleted ? t('skills.deletedBy', { name: by?.name ?? '—' }) : (by?.name ?? '—')}
        </Typography>
        <Typography variant="caption" component="time" dateTime={updated} title={dateTimeMs(updated)} sx={{ color: 'text.secondary' }}>
          {fromNow(updated)}
        </Typography>
      </TableCell>
      <TableCell align="right" sx={{ verticalAlign: 'top', whiteSpace: 'nowrap' }}>
        <IconButton size="small" aria-label={t('skills.rowMenu', { name: skill.name })} onClick={(event) => setAnchor(event.currentTarget)}>
          <MoreVertRounded fontSize="small" />
        </IconButton>
        <Menu anchorEl={anchor} open={anchor !== null} onClose={close}>
          <MenuItem component={RouterLink} to={skillPath(skill.name)} onClick={close}>
            {t('skills.actions.view')}
          </MenuItem>
          {!skill.deleted && (
            <MenuItem component={RouterLink} to={skillEditPath(skill.name)} onClick={close}>
              {t('skills.actions.edit')}
            </MenuItem>
          )}
          <MenuItem component="a" href={skillApi.fileUrl(skill.name)} download={`${skill.name}-SKILL.md`} onClick={close}>
            {t('skills.actions.download')}
          </MenuItem>
          {skill.deleted ? (
            <MenuItem onClick={() => void undelete()}>{t('skills.actions.undelete')}</MenuItem>
          ) : (
            <MenuItem onClick={() => void remove()} sx={{ color: 'error.main' }}>
              {t('skills.actions.delete')}
            </MenuItem>
          )}
        </Menu>
      </TableCell>
    </TableRow>
  );
}
