import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useDeferredValue, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';

import { issueApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import type { IssueDetail, IssueJournal } from '@/shared/api/types';
import { date, dateTime, fromNow, initials } from '@/shared/lib/format';
import { useLabels } from '@/shared/lib/labels';

import { useIssueTypeName } from './issue-labels';
import { readChange, type ChangeValue, type ReadableChange } from './journal';
import { MarkdownEditor } from './MarkdownEditor';
import { MarkdownView } from './MarkdownView';
import { storeIssue } from './queries';

/** 바뀐 필드 하나를 사람이 읽는 한 줄로 — "상태: 신규 → 진행" · "연결 일감 추가: 차단 #12" */
export function useChangeText() {
  const { t } = useTranslation('issue');
  const labels = useLabels();
  const typeName = useIssueTypeName();
  return useCallback(
    (change: ReadableChange): string => {
      const value = (v: ChangeValue): string => {
        switch (v.kind) {
          case 'empty':
            return t('journal.empty');
          case 'person':
            return v.name;
          case 'issue':
            return `#${v.number}`;
          case 'status':
            return labels.issueStatus(v.status);
          case 'type':
            return typeName(v.key);
          case 'date':
            return date(v.date);
          case 'text':
            return v.text;
        }
      };
      switch (change.kind) {
        case 'description':
          return t('journal.descriptionChanged');
        case 'link':
          return change.type && change.number !== null
            ? t(change.action === 'added' ? 'journal.linkAdded' : 'journal.linkRemoved', {
                link: change.direction ? t(`links.${change.direction}.${change.type}`) : labels.issueLinkType(change.type),
                number: change.number,
              })
            : `${t('fields.link')}: ${change.raw}`;
        case 'field':
          return t('journal.changed', { field: t(`fields.${change.field}`), before: value(change.before), after: value(change.after) });
        case 'unknown':
          return t('journal.changed', { field: change.field, before: value(change.before), after: value(change.after) });
      }
    },
    [t, labels, typeName],
  );
}

/**
 * 이력 — 필드 변경과 댓글을 한 줄기로(오래된 것부터). 사람 · 서비스 이름, MCP 로 바꾼 것은 그렇다고 적는다.
 * 지운 줄 · 넣은 줄 같은 색 구분 없이, 바뀐 것을 말로 적는다(색만으로 전하지 않는다).
 */
export function IssueHistory({ issue, issuePath }: { issue: IssueDetail; issuePath: (number: number) => string }) {
  const { t } = useTranslation('issue');
  if (issue.journal.length === 0) {
    return (
      <Typography variant="body2" sx={{ color: 'text.secondary', py: 1 }}>
        {t('view.historyEmpty')}
      </Typography>
    );
  }
  return (
    <Box component="ol" sx={{ listStyle: 'none', m: 0, p: 0 }}>
      {issue.journal.map((entry) => (
        <JournalEntry key={entry.id} entry={entry} issuePath={issuePath} />
      ))}
    </Box>
  );
}

function JournalEntry({ entry, issuePath }: { entry: IssueJournal; issuePath: (number: number) => string }) {
  const { t } = useTranslation('issue');
  const changeText = useChangeText();
  const name = entry.actorName || (entry.actorType === 'SERVICE' ? t('journal.service') : t('view.unknownPerson'));
  return (
    <Stack component="li" direction="row" spacing={1.5} sx={{ py: 1.5, borderTop: 1, borderColor: 'divider', '&:first-of-type': { borderTop: 0 } }}>
      <Avatar sx={{ width: 32, height: 32, fontSize: 13, fontWeight: 700, flexShrink: 0 }}>{initials(name)}</Avatar>
      <Box sx={{ minWidth: 0, flex: 1 }}>
        <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
          <Typography variant="subtitle2" sx={{ overflowWrap: 'anywhere' }}>
            {name}
          </Typography>
          {entry.actorType === 'SERVICE' && <Chip size="small" variant="outlined" label={t('journal.service')} />}
          {entry.via === 'MCP' && (
            <Tooltip title={t('journal.viaMcpHelp')}>
              <Chip size="small" variant="outlined" label={t('journal.viaMcp')} />
            </Tooltip>
          )}
          <Typography variant="caption" component="time" dateTime={entry.createdAt} title={dateTime(entry.createdAt)} sx={{ color: 'text.secondary' }}>
            {fromNow(entry.createdAt)}
          </Typography>
        </Stack>
        {entry.changes.length > 0 && (
          <Box component="ul" aria-label={t('journal.changesLabel')} sx={{ m: 0, mt: 0.5, pl: 2.25, color: 'text.secondary' }}>
            {entry.changes.map((change, index) => (
              <Typography component="li" variant="body2" key={`${change.field}:${index}`} sx={{ py: 0.125, overflowWrap: 'anywhere' }}>
                {changeText(readChange(change))}
              </Typography>
            ))}
          </Box>
        )}
        {entry.notes && (
          <Paper variant="outlined" sx={{ mt: 1, px: 2, py: 1.5, borderRadius: 2 }}>
            <MarkdownView content={entry.notes} issuePath={issuePath} dense />
          </Paper>
        )}
      </Box>
    </Stack>
  );
}

/** 댓글 쓰기 — Markdown, 쓰기/미리보기. 단 뒤에는 받은 일감 상세(이력 포함)를 그대로 화면에 넣는다 */
export function CommentBox({ slug, projectKey, issue, issuePath }: {
  slug: string;
  projectKey: string;
  issue: IssueDetail;
  issuePath: (number: number) => string;
}) {
  const { t } = useTranslation('issue');
  const queryClient = useQueryClient();
  const [notes, setNotes] = useState('');
  const [tab, setTab] = useState<'write' | 'preview'>('write');
  const [busy, setBusy] = useState(false);
  const preview = useDeferredValue(notes);

  const submit = async () => {
    if (!notes.trim()) {
      return;
    }
    setBusy(true);
    try {
      const saved = await issueApi.comment(slug, projectKey, issue.number, notes);
      storeIssue(queryClient, slug, projectKey, saved);
      setNotes('');
      setTab('write');
      toast.success(t('comment.added'));
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Paper variant="outlined" sx={{ borderRadius: 2.5, overflow: 'hidden', mt: 2 }}>
      <Tabs value={tab} onChange={(_, next: 'write' | 'preview') => setTab(next)} sx={{ px: 1, minHeight: 40, borderBottom: 1, borderColor: 'divider' }}>
        <Tab value="write" label={t('form.tabWrite')} sx={{ minHeight: 40 }} />
        <Tab value="preview" label={t('form.tabPreview')} sx={{ minHeight: 40 }} />
      </Tabs>
      <Box sx={{ height: 160, display: tab === 'write' ? 'block' : 'none' }}>
        <MarkdownEditor value={notes} onChange={setNotes} placeholder={t('comment.placeholder')} ariaLabel={t('comment.label')} />
      </Box>
      {tab === 'preview' && (
        <Box sx={{ minHeight: 160, maxHeight: 360, overflow: 'auto', px: 2, py: 1.5 }}>
          {preview.trim() ? (
            <MarkdownView content={preview} issuePath={issuePath} dense />
          ) : (
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              {t('form.previewEmpty')}
            </Typography>
          )}
        </Box>
      )}
      <Stack direction="row" sx={{ justifyContent: 'flex-end', px: 1.5, py: 1, borderTop: 1, borderColor: 'divider' }}>
        <Button variant="contained" onClick={() => void submit()} loading={busy} disabled={!notes.trim()}>
          {t('comment.submit')}
        </Button>
      </Stack>
    </Paper>
  );
}
