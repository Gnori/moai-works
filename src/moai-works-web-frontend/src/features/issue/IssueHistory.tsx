import DeleteOutlineRounded from '@mui/icons-material/DeleteOutlineRounded';
import EditRounded from '@mui/icons-material/EditRounded';
import MoreVertRounded from '@mui/icons-material/MoreVertRounded';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink } from 'react-router';
import { toast } from 'sonner';

import { useMe } from '@/features/auth/session';
import { issueApi } from '@/shared/api/endpoints';
import { errorMessage, isApiError } from '@/shared/api/http';
import type { IssueDetail, IssueJournal } from '@/shared/api/types';
import { dateTimeMs, fromNow, initials } from '@/shared/lib/format';
import { useConfirm } from '@/shared/ui/ConfirmDialog';

import { UploadProgressLine } from './AttachmentControls';
import { useChangeText } from './change-text';
import { commentActions, type CommentRights } from './comments';
import { personName } from './issue-labels';
import { readChange } from './journal';
import { MarkdownEditor } from './MarkdownEditor';
import { MarkdownView } from './MarkdownView';
import { refreshIssues, storeIssue } from './queries';
import { useEditorUpload } from './use-attachment-upload';

/** 댓글 고치기 · 지우기에 필요한 것 — 넘기지 않으면 이력은 보기만 한다 */
export interface CommentControls extends CommentRights {
  slug: string;
  projectKey: string;
  /** 고치는 편집기에서도 이미지 · 파일을 올릴 수 있다 (canUploadAttachment) */
  canAttach: boolean;
}

interface HistoryProps {
  issue: IssueDetail;
  issuePath: (number: number) => string;
  /** 판 N 의 차이를 여는 주소 */
  revisionPath: (no: number) => string;
  /** 댓글 고치기 · 지우기(FR-ISS-019) — 단추는 서버가 준 내 권한과 쓴 사람 · 나로 가리기만 한다(판정은 api) */
  comments?: CommentControls;
}

/**
 * 이력 — 필드 변경과 댓글을 한 줄기로(오래된 것부터). 사람 · 서비스 이름, MCP 로 바꾼 것은 그렇다고 적는다.
 * 지운 줄 · 넣은 줄 같은 색 구분 없이, 바뀐 것을 말로 적는다(색만으로 전하지 않는다).
 * 판을 만든 줄(필드를 바꾼 줄)에는 "판 N" 표를 달고, 누르면 리비전 화면에서 그 판의 차이를 연다.
 * 바뀐 때는 밀리초까지 보인다(곁에 "3분 전").
 * 댓글은 제자리에서 고치고 지운다 — 내 댓글의 ⋮ 에서 고치기(같은 Markdown 편집기, 저장 · 취소) · 지우기(확인 대화 상자).
 * 고친 댓글은 "고침 3분 전"(밀리초는 도구 설명), 지운 댓글은 "지운 댓글"로 남는다(글은 서버가 더 주지 않는다).
 */
export function IssueHistory(props: HistoryProps) {
  const { t } = useTranslation('issue');
  if (props.issue.journal.length === 0) {
    return (
      <Typography variant="body2" sx={{ color: 'text.secondary', py: 1 }}>
        {t('view.historyEmpty')}
      </Typography>
    );
  }
  return props.comments ? <HistoryWithMe {...props} comments={props.comments} /> : <HistoryList {...props} meId={null} />;
}

/** 댓글을 고칠 수 있는 화면 — 쓴 사람이 나인지 보려고 내 정보를 읽는다 */
function HistoryWithMe(props: HistoryProps & { comments: CommentControls }) {
  const me = useMe();
  return <HistoryList {...props} meId={me.id} />;
}

function HistoryList({ issue, issuePath, revisionPath, comments, meId }: HistoryProps & { meId: string | null }) {
  return (
    <Box component="ol" sx={{ listStyle: 'none', m: 0, p: 0 }}>
      {issue.journal.map((entry) => (
        <JournalEntry
          key={entry.id}
          entry={entry}
          issueNumber={issue.number}
          issuePath={issuePath}
          revisionPath={revisionPath}
          comments={comments}
          meId={meId}
        />
      ))}
    </Box>
  );
}

function JournalEntry({ entry, issueNumber, issuePath, revisionPath, comments, meId }: {
  entry: IssueJournal;
  issueNumber: number;
  issuePath: (number: number) => string;
  revisionPath: (no: number) => string;
  comments?: CommentControls;
  meId: string | null;
}) {
  const { t } = useTranslation('issue');
  const changeText = useChangeText();
  const [editing, setEditing] = useState(false);
  const name = entry.actorName || (entry.actorType === 'SERVICE' ? t('journal.service') : t('view.unknownPerson'));
  const actions = comments && meId !== null ? commentActions(entry, meId, comments) : { edit: false, remove: false };
  // 그 사이 권한을 잃거나 지워졌으면 편집기를 닫는다
  const isEditing = editing && actions.edit && !!comments;
  const live = entry.notes !== null && !entry.deleted;
  return (
    <Stack component="li" direction="row" spacing={1.5} sx={{ py: 1.5, borderTop: 1, borderColor: 'divider', '&:first-of-type': { borderTop: 0 } }}>
      <Avatar sx={{ width: 32, height: 32, fontSize: 13, fontWeight: 700, flexShrink: 0 }}>{initials(name)}</Avatar>
      <Box sx={{ minWidth: 0, flex: 1 }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
          <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap', flex: 1, minWidth: 0 }}>
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
            {live && entry.editedAt && <EditedMark editedAt={entry.editedAt} />}
          </Stack>
          {comments && (actions.edit || actions.remove) && !isEditing && (
            <CommentMenu
              entry={entry}
              issueNumber={issueNumber}
              controls={comments}
              canEdit={actions.edit}
              canRemove={actions.remove}
              onEdit={() => setEditing(true)}
            />
          )}
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
        {entry.deleted && <DeletedComment entry={entry} />}
        {live &&
          (isEditing && comments ? (
            <CommentEditor entry={entry} issueNumber={issueNumber} controls={comments} onDone={() => setEditing(false)} />
          ) : (
            <Paper variant="outlined" sx={{ mt: 1, px: 2, py: 1.5, borderRadius: 2 }}>
              <MarkdownView content={entry.notes ?? ''} issuePath={issuePath} dense />
            </Paper>
          ))}
      </Box>
    </Stack>
  );
}

/** 고친 댓글 표시 — "고침 3분 전", 밀리초 시각은 도구 설명에 */
function EditedMark({ editedAt }: { editedAt: string }) {
  const { t } = useTranslation('issue');
  return (
    <Tooltip title={t('comment.editedAt', { time: dateTimeMs(editedAt) })}>
      <Typography variant="caption" component="time" dateTime={editedAt} sx={{ color: 'text.secondary', fontVariantNumeric: 'tabular-nums' }}>
        · {t('comment.edited')} {fromNow(editedAt)}
      </Typography>
    </Tooltip>
  );
}

/** 지운 댓글 — 제자리에 남고 글은 없다. 누가 · 언제 지웠는지(밀리초는 도구 설명) */
function DeletedComment({ entry }: { entry: IssueJournal }) {
  const { t } = useTranslation('issue');
  const by = personName(entry.deletedBy);
  return (
    <Box
      sx={{
        mt: 1,
        px: 2,
        py: 1.25,
        borderRadius: 2,
        border: 1,
        borderStyle: 'dashed',
        borderColor: 'divider',
        color: 'text.secondary',
        display: 'flex',
        flexWrap: 'wrap',
        alignItems: 'baseline',
        columnGap: 1,
        rowGap: 0.25,
      }}
    >
      <Typography variant="body2">{t('comment.deleted')}</Typography>
      {entry.deletedAt && (
        <Tooltip title={t('comment.deletedAt', { time: dateTimeMs(entry.deletedAt) })}>
          <Typography variant="caption" component="time" dateTime={entry.deletedAt} sx={{ fontVariantNumeric: 'tabular-nums', overflowWrap: 'anywhere' }}>
            {by ? `${t('comment.deletedBy', { name: by })} · ` : ''}
            {fromNow(entry.deletedAt)}
          </Typography>
        </Tooltip>
      )}
    </Box>
  );
}

/** 댓글을 고치거나 지우지 못했을 때 — 이미 지워졌으면 지금 이력을 다시 읽는다 */
function useCommentFailure(controls: CommentControls) {
  const { t } = useTranslation('issue');
  const queryClient = useQueryClient();
  return (e: unknown): 'conflict' | 'gone' | 'other' => {
    if (isApiError(e, 'CONCURRENT_UPDATE')) {
      // 고치던 글은 그대로 두고 최신 판을 받는다 — 다시 저장하면 그 판 위에 고친다
      toast.warning(t('comment.conflict'));
      void refreshIssues(queryClient, controls.slug, controls.projectKey);
      return 'conflict';
    }
    if (isApiError(e, 'COMMENT_DELETED') || isApiError(e, 'COMMENT_NOT_FOUND')) {
      toast.warning(t('comment.gone'));
      void refreshIssues(queryClient, controls.slug, controls.projectKey);
      return 'gone';
    }
    toast.error(errorMessage(e));
    return 'other';
  };
}

/** 내 댓글(또는 project.manage 면 남의 댓글)의 ⋮ — 고치기 · 지우기 */
function CommentMenu({ entry, issueNumber, controls, canEdit, canRemove, onEdit }: {
  entry: IssueJournal;
  issueNumber: number;
  controls: CommentControls;
  canEdit: boolean;
  canRemove: boolean;
  onEdit: () => void;
}) {
  const { t } = useTranslation('issue');
  const queryClient = useQueryClient();
  const confirm = useConfirm();
  const failed = useCommentFailure(controls);
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);

  const remove = async () => {
    setAnchor(null);
    const ok = await confirm({
      title: t('comment.removeTitle'),
      description: t('comment.removeBody'),
      confirmLabel: t('comment.removeConfirm'),
      destructive: true,
    });
    if (!ok) {
      return;
    }
    try {
      const saved = await issueApi.deleteComment(controls.slug, controls.projectKey, issueNumber, entry.id);
      storeIssue(queryClient, controls.slug, controls.projectKey, saved);
      toast.success(t('comment.removed'));
    } catch (e) {
      failed(e);
    }
  };

  return (
    <>
      <Tooltip title={t('comment.actions')}>
        <IconButton size="small" aria-label={t('comment.actions')} aria-haspopup="menu" onClick={(event) => setAnchor(event.currentTarget)} sx={{ mt: -0.5 }}>
          <MoreVertRounded fontSize="small" />
        </IconButton>
      </Tooltip>
      <Menu anchorEl={anchor} open={!!anchor} onClose={() => setAnchor(null)}>
        {[
          canEdit && (
            <MenuItem
              key="edit"
              onClick={() => {
                setAnchor(null);
                onEdit();
              }}
            >
              <ListItemIcon>
                <EditRounded fontSize="small" />
              </ListItemIcon>
              {t('comment.edit')}
            </MenuItem>
          ),
          canRemove && (
            <MenuItem key="remove" onClick={() => void remove()} sx={{ color: 'error.main' }}>
              <ListItemIcon sx={{ color: 'inherit' }}>
                <DeleteOutlineRounded fontSize="small" />
              </ListItemIcon>
              {t('comment.remove')}
            </MenuItem>
          ),
        ].filter(Boolean)}
      </Menu>
    </>
  );
}

/**
 * 댓글 고치기 — 댓글 쓰기와 같은 Markdown 편집기를 그 자리에 연다(저장 · 취소). 읽을 때 받은 댓글 판(rowVersion)을 함께 보내고,
 * 그 사이 다른 곳에서 고쳤으면(409) 고치던 글은 남긴 채 최신 판을 다시 읽는다.
 */
function CommentEditor({ entry, issueNumber, controls, onDone }: {
  entry: IssueJournal;
  issueNumber: number;
  controls: CommentControls;
  onDone: () => void;
}) {
  const { t } = useTranslation('issue');
  const queryClient = useQueryClient();
  const failed = useCommentFailure(controls);
  const [notes, setNotes] = useState(entry.notes ?? '');
  const [busy, setBusy] = useState(false);
  const { editorRef, addFiles, progress, pending, onPending, flush } = useEditorUpload(
    controls.canAttach ? { slug: controls.slug, projectKey: controls.projectKey, number: issueNumber, refreshIssue: true } : null,
  );

  const save = async () => {
    // 방금 친 글은 아직 올라오지 않았을 수 있다 — 바로 받는다
    const body = flush() ?? notes;
    if (!body.trim()) {
      return;
    }
    if (body === entry.notes) {
      onDone();
      return;
    }
    setBusy(true);
    try {
      const saved = await issueApi.editComment(controls.slug, controls.projectKey, issueNumber, entry.id, body, entry.rowVersion ?? 0);
      storeIssue(queryClient, controls.slug, controls.projectKey, saved);
      toast.success(t('comment.updated'));
      onDone();
    } catch (e) {
      if (failed(e) === 'gone') {
        onDone();
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <Paper variant="outlined" sx={{ mt: 1, borderRadius: 2, overflow: 'hidden' }}>
      <Box sx={{ height: { xs: 220, sm: 260 } }}>
        <MarkdownEditor
          ref={editorRef}
          value={notes}
          onChange={setNotes}
          onPending={onPending}
          ariaLabel={t('comment.editLabel')}
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
        sx={{ alignItems: 'center', justifyContent: 'flex-end', px: 1.5, py: 1, borderTop: progress ? 0 : 1, borderColor: 'divider' }}
      >
        <Button color="inherit" onClick={onDone} disabled={busy}>
          {t('comment.cancel')}
        </Button>
        {/* 올리는 동안에는 저장하지 않는다 — 본문에 "업로드 중…" 자리가 그대로 남는다 */}
        <Button variant="contained" onClick={() => void save()} loading={busy} disabled={(!notes.trim() && !pending) || !!progress}>
          {t('comment.save')}
        </Button>
      </Stack>
    </Paper>
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
