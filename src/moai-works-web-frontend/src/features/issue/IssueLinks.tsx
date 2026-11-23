import AddRounded from '@mui/icons-material/AddRounded';
import CloseRounded from '@mui/icons-material/CloseRounded';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import IconButton from '@mui/material/IconButton';
import Link from '@mui/material/Link';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink } from 'react-router';
import { toast } from 'sonner';

import { issuePath } from '@/app/routes';
import { issueApi } from '@/shared/api/endpoints';
import { errorMessage, isApiError } from '@/shared/api/http';
import { issueLinkTypes, type IssueDetail, type IssueLinkType, type IssueRef } from '@/shared/api/types';
import { useLabels } from '@/shared/lib/labels';

import { useIssueTypeName } from './issue-labels';
import { IssuePicker, type IssueOption } from './IssuePicker';
import { IssueStatusChip } from './IssueStatusChip';
import { storeIssue } from './queries';

/**
 * 다른 일감 한 줄 — 번호 · 제목(링크) · 유형 · 상태. 앞에 붙일 것(연결 종류)과 뒤에 둘 것(빼기)을 받는다.
 * 그 일감이 지금 프로젝트(currentProjectKey)와 다른 프로젝트에 있으면 프로젝트 이름을 함께 적는다(연결 일감은 다른 프로젝트일 수 있다 — 2026-11-24).
 */
export function IssueRefRow({ slug, currentProjectKey, issue, lead, trail, divider }: {
  slug: string;
  currentProjectKey: string;
  issue: IssueRef;
  lead?: ReactNode;
  trail?: ReactNode;
  divider?: boolean;
}) {
  const typeName = useIssueTypeName();
  return (
    <Stack
      component="li"
      direction="row"
      spacing={1.5}
      sx={{ alignItems: 'center', px: 2, py: 1, borderBottom: divider ? 1 : 0, borderColor: 'divider', minHeight: 48 }}
    >
      <Box sx={{ minWidth: 0, flex: 1 }}>
        {lead}
        <Link
          component={RouterLink}
          to={issuePath(slug, issue.number)}
          underline="hover"
          color="text.primary"
          sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}
        >
          <Box component="span" sx={{ fontFamily: 'monospace', color: 'text.secondary', mr: 0.75, fontWeight: 400 }}>
            #{issue.number}
          </Box>
          {issue.title}
        </Link>
        <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', overflowWrap: 'anywhere' }}>
          {issue.project && issue.project.key !== currentProjectKey ? `${issue.project.name} · ${typeName(issue.type)}` : typeName(issue.type)}
        </Typography>
      </Box>
      <IssueStatusChip status={issue.status} />
      {trail}
    </Stack>
  );
}

/**
 * 연결 일감 — 이 일감 쪽에서 읽은 연결 종류("차단함" · "차단됨")와 대상. 넣고 빼기는 issue.edit 가 있을 때만(표시만 — 판정은 api).
 */
export function IssueLinks({ slug, issue, canEdit }: { slug: string; issue: IssueDetail; canEdit: boolean }) {
  const { t } = useTranslation('issue');
  const labels = useLabels();
  const queryClient = useQueryClient();
  const [adding, setAdding] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);

  const unlink = async (linkId: string) => {
    setBusy(linkId);
    try {
      const saved = await issueApi.unlink(slug, issue.number, linkId);
      storeIssue(queryClient, slug, saved);
      toast.success(t('view.linkRemoved'));
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(null);
    }
  };

  return (
    <Box component="section" sx={{ mt: 3.5 }}>
      <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between', gap: 2, mb: issue.links.length === 0 ? 0 : 1 }}>
        {/* 비었으면 머리 한 줄로 끝낸다 — 가운데 본문을 길게 밀지 않게 */}
        <Stack direction="row" useFlexGap sx={{ alignItems: 'baseline', flexWrap: 'wrap', columnGap: 1.5, minWidth: 0 }}>
          <Typography variant="h6" component="h2">
            {t('fields.link')}
          </Typography>
          {issue.links.length === 0 && (
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              {t('view.linksEmpty')}
            </Typography>
          )}
        </Stack>
        {canEdit && (
          <Button size="small" startIcon={<AddRounded />} onClick={() => setAdding(true)}>
            {t('view.addLink')}
          </Button>
        )}
      </Stack>
      {issue.links.length > 0 && (
        <Paper variant="outlined" sx={{ borderRadius: 2.5, overflow: 'hidden' }}>
          <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
            {issue.links.map((link, index) => (
              <IssueRefRow
                key={link.id}
                slug={slug}
                currentProjectKey={issue.project.key}
                issue={link.issue}
                divider={index < issue.links.length - 1}
                lead={<Chip size="small" variant="outlined" label={labels.issueLink(link)} sx={{ mb: 0.5, mr: 1 }} />}
                trail={
                  canEdit && (
                    <Tooltip title={t('view.removeLink', { number: link.issue.number })}>
                      <IconButton
                        size="small"
                        aria-label={t('view.removeLink', { number: link.issue.number })}
                        onClick={() => void unlink(link.id)}
                        disabled={busy === link.id}
                      >
                        <CloseRounded fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  )
                }
              />
            ))}
          </Box>
        </Paper>
      )}
      {adding && <AddLinkDialog slug={slug} issue={issue} onClose={() => setAdding(false)} />}
    </Box>
  );
}

/** 연결 넣기 — 종류(설명과 함께)와 일감을 고른다. 워크스페이스에서 내가 읽을 수 있는 일감이면 다른 프로젝트의 것도 된다(2026-11-24) */
function AddLinkDialog({ slug, issue, onClose }: { slug: string; issue: IssueDetail; onClose: () => void }) {
  const { t } = useTranslation(['issue', 'common']);
  const labels = useLabels();
  const queryClient = useQueryClient();
  const fullScreen = useMediaQuery((theme) => theme.breakpoints.down('sm'));
  const [type, setType] = useState<IssueLinkType>('RELATES');
  const [target, setTarget] = useState<IssueOption | null>(null);
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!target) {
      setError(t('link.targetRequired'));
      return;
    }
    setBusy(true);
    try {
      const saved = await issueApi.link(slug, issue.number, target.number, type);
      storeIssue(queryClient, slug, saved);
      toast.success(t('link.added', { number: target.number }));
      onClose();
    } catch (e) {
      setBusy(false);
      if (
        isApiError(e, 'LINK_SELF') ||
        isApiError(e, 'INVALID_ISSUE_LINK') ||
        isApiError(e, 'ISSUE_LINK_EXISTS') ||
        isApiError(e, 'ISSUE_NOT_FOUND') ||
        isApiError(e, 'LINK_TARGET_NOT_FOUND')
      ) {
        setError(e.message);
      } else {
        toast.error(errorMessage(e));
      }
    }
  };

  return (
    <Dialog open onClose={busy ? undefined : onClose} maxWidth="sm" fullWidth fullScreen={fullScreen}>
      <Box component="form" noValidate onSubmit={(event) => void submit(event)} sx={{ display: 'contents' }}>
        <DialogTitle sx={{ fontWeight: 700 }}>{t('link.addTitle')}</DialogTitle>
        <DialogContent>
          <Stack spacing={2.5} sx={{ pt: 1 }}>
            <TextField
              select
              label={t('link.typeLabel')}
              value={type}
              onChange={(event) => setType(event.target.value as IssueLinkType)}
              helperText={t(`links.help.${type}`)}
            >
              {issueLinkTypes.map((value) => (
                <MenuItem key={value} value={value}>
                  {labels.issueLinkType(value)}
                </MenuItem>
              ))}
            </TextField>
            <IssuePicker
              slug={slug}
              projectKey={null}
              label={t('link.targetLabel')}
              helperText={t('link.targetHelp')}
              value={target}
              onChange={(next) => {
                setTarget(next);
                setError(undefined);
              }}
              exclude={issue.number}
              error={error}
              autoFocus
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button color="inherit" onClick={onClose} disabled={busy}>
            {t('common:actions.cancel')}
          </Button>
          <Button type="submit" variant="contained" loading={busy}>
            {t('link.submit')}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}
