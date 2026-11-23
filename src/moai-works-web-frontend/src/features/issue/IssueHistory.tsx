import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink } from 'react-router';
import { toast } from 'sonner';

import { issueApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import type { IssueDetail, IssueJournal } from '@/shared/api/types';
import { dateTimeMs, fromNow, initials } from '@/shared/lib/format';

import { UploadProgressLine } from './AttachmentControls';
import { useChangeText } from './change-text';
import { readChange } from './journal';
import { MarkdownEditor } from './MarkdownEditor';
import { MarkdownView } from './MarkdownView';
import { storeIssue } from './queries';
import { useEditorUpload } from './use-attachment-upload';

/**
 * 이력 — 필드 변경과 댓글을 한 줄기로(오래된 것부터). 사람 · 서비스 이름, MCP 로 바꾼 것은 그렇다고 적는다.
 * 지운 줄 · 넣은 줄 같은 색 구분 없이, 바뀐 것을 말로 적는다(색만으로 전하지 않는다).
 * 판을 만든 줄(필드를 바꾼 줄)에는 "판 N" 표를 달고, 누르면 리비전 화면에서 그 판의 차이를 연다.
 * 바뀐 때는 밀리초까지 보인다(곁에 "3분 전").
 */
export function IssueHistory({ issue, issuePath, revisionPath }: {
  issue: IssueDetail;
  issuePath: (number: number) => string;
  /** 판 N 의 차이를 여는 주소 */
  revisionPath: (no: number) => string;
}) {
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
        <JournalEntry key={entry.id} entry={entry} issuePath={issuePath} revisionPath={revisionPath} />
      ))}
    </Box>
  );
}

function JournalEntry({ entry, issuePath, revisionPath }: {
  entry: IssueJournal;
  issuePath: (number: number) => string;
  revisionPath: (no: number) => string;
}) {
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
          {entry.revisionNo !== null && (
            <Tooltip describeChild title={t('journal.revisionHelp', { no: entry.revisionNo })}>
              <Chip
                size="small"
                color="primary"
                variant="outlined"
                clickable
                component={RouterLink}
                to={revisionPath(entry.revisionNo)}
                label={t('journal.revision', { no: entry.revisionNo })}
                sx={{ fontVariantNumeric: 'tabular-nums' }}
              />
            </Tooltip>
          )}
          <Typography variant="caption" component="time" dateTime={entry.createdAt} sx={{ color: 'text.secondary', fontVariantNumeric: 'tabular-nums' }}>
            {dateTimeMs(entry.createdAt)} · {fromNow(entry.createdAt)}
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

/**
 * 댓글 쓰기 — Markdown 편집기(쓰면서 바로 그려지고, 원문으로도 고친다). 단 뒤에는 받은 일감 상세(이력 포함)를 그대로 화면에 넣는다.
 * canAttach 면 이미지 · 파일을 붙여넣기 · 끌어다 놓기 · 단추로 이 일감에 올리고, 커서 자리에 표시를 넣는다.
 */
export function CommentBox({ slug, projectKey, issue, canAttach = false }: {
  slug: string;
  projectKey: string;
  issue: IssueDetail;
  /** 첨부를 올릴 수 있다 (canUploadAttachment) */
  canAttach?: boolean;
}) {
  const { t } = useTranslation('issue');
  const queryClient = useQueryClient();
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);
  const { editorRef, addFiles, progress, pending, onPending, flush } = useEditorUpload(
    canAttach ? { slug, projectKey, number: issue.number, refreshIssue: true } : null,
  );

  const submit = async () => {
    // 방금 친 글은 아직 올라오지 않았을 수 있다 — 바로 받는다
    const body = flush() ?? notes;
    if (!body.trim()) {
      return;
    }
    setBusy(true);
    try {
      const saved = await issueApi.comment(slug, projectKey, issue.number, body);
      storeIssue(queryClient, slug, projectKey, saved);
      setNotes('');
      toast.success(t('comment.added'));
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Paper variant="outlined" sx={{ borderRadius: 2.5, overflow: 'hidden', mt: 2 }}>
      <Box sx={{ height: { xs: 240, sm: 260 } }}>
        <MarkdownEditor
          ref={editorRef}
          value={notes}
          onChange={setNotes}
          onPending={onPending}
          placeholder={t('comment.placeholder')}
          ariaLabel={t('comment.label')}
          onFiles={addFiles}
          variant="comment"
        />
      </Box>
      {progress && (
        <Box sx={{ px: 1.5, pt: 1, borderTop: 1, borderColor: 'divider' }}>
          <UploadProgressLine progress={progress} />
        </Box>
      )}
      <Stack
        direction="row"
        spacing={1}
        sx={{ alignItems: 'center', justifyContent: 'space-between', px: 1.5, py: 1, borderTop: progress ? 0 : 1, borderColor: 'divider' }}
      >
        <span />
        {/* 올리는 동안에는 달지 않는다 — 본문에 "업로드 중…" 자리가 그대로 남는다. 친 순간(pending) 켜진다 */}
        <Button variant="contained" onClick={() => void submit()} loading={busy} disabled={(!notes.trim() && !pending) || !!progress}>
          {t('comment.submit')}
        </Button>
      </Stack>
    </Paper>
  );
}
