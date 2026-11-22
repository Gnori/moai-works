import CloudUploadOutlined from '@mui/icons-material/CloudUploadOutlined';
import ContentCopyRounded from '@mui/icons-material/ContentCopyRounded';
import DeleteOutlineRounded from '@mui/icons-material/DeleteOutlineRounded';
import DownloadRounded from '@mui/icons-material/DownloadRounded';
import InsertDriveFileOutlined from '@mui/icons-material/InsertDriveFileOutlined';
import OpenInNewRounded from '@mui/icons-material/OpenInNewRounded';
import UploadFileRounded from '@mui/icons-material/UploadFileRounded';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useQueryClient } from '@tanstack/react-query';
import { useId, useRef, useState, type DragEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';

import { useMe } from '@/features/auth/session';
import { attachmentApi } from '@/shared/api/endpoints';
import { errorMessage, isApiError } from '@/shared/api/http';
import type { AttachmentView } from '@/shared/api/types';
import { dateTimeMs, fileSize } from '@/shared/lib/format';
import { useConfirm } from '@/shared/ui/ConfirmDialog';
import { ErrorState, LoadingState } from '@/shared/ui/States';

import { AttachFilesButton, UploadProgressLine } from './AttachmentControls';
import { canRemoveAttachment } from './attachments';
import { personName } from './issue-labels';
import { issueKeys, useAttachmentsQuery } from './queries';
import { useAttachmentUpload, type UploadProgress } from './use-attachment-upload';

interface Props {
  slug: string;
  projectKey: string;
  number: number;
  /** 올릴 수 있다 — 프로젝트가 열려 있고 issue.comment (canUploadAttachment) */
  canUpload: boolean;
  /** 남이 올린 것도 지울 수 있다 — project.manage */
  canManage: boolean;
}

/**
 * 첨부파일 탭 — 올리기(단추 · 끌어다 놓기, 여러 개를 하나씩)와 목록.
 * 줄마다 미리보기(이미지) 또는 파일 표시 · 이름 · 크기 · 올린 사람 · 올린 때(밀리초) · MCP 로 올렸으면 그 표시,
 * 그리고 열기(이미지) · 내려받기(파일) · 본문에 넣을 표시 복사 · 지우기(내가 올렸거나 project.manage).
 * 단추는 서버가 준 이 프로젝트의 내 권한으로 가리기만 한다(판정은 api).
 */
export function IssueAttachments({ slug, projectKey, number, canUpload, canManage }: Props) {
  const { t } = useTranslation('issue');
  const me = useMe();
  const queryClient = useQueryClient();
  const confirm = useConfirm();
  const titleId = useId();
  const query = useAttachmentsQuery(slug, projectKey, number);
  const { upload, progress } = useAttachmentUpload(canUpload ? { slug, projectKey, number, refreshIssue: true } : null);
  const [removing, setRemoving] = useState<string | null>(null);
  const listKey = issueKeys.attachments(slug, projectKey, number);

  const remove = async (attachment: AttachmentView) => {
    const ok = await confirm({
      title: t('attachments.removeTitle'),
      description: t('attachments.removeBody', { name: attachment.fileName, size: fileSize(attachment.size) }),
      confirmLabel: t('attachments.removeConfirm'),
      destructive: true,
    });
    if (!ok) {
      return;
    }
    setRemoving(attachment.id);
    try {
      await attachmentApi.remove(slug, projectKey, number, attachment.id);
      queryClient.setQueryData<AttachmentView[]>(listKey, (list) => list?.filter((a) => a.id !== attachment.id));
      void queryClient.invalidateQueries({ queryKey: listKey });
      // 이력에 "첨부 삭제" 줄이 생긴다
      void queryClient.invalidateQueries({ queryKey: issueKeys.detail(slug, projectKey, number) });
      toast.success(t('attachments.removed'));
    } catch (e) {
      toast.error(errorMessage(e));
      if (isApiError(e, 'ATTACHMENT_NOT_FOUND')) {
        // 그 사이 누군가 지웠다 — 지금 목록을 다시 읽는다
        void queryClient.invalidateQueries({ queryKey: listKey });
      }
    } finally {
      setRemoving(null);
    }
  };

  const copy = async (attachment: AttachmentView) => {
    try {
      await navigator.clipboard.writeText(attachment.markdown);
      toast.success(t('attachments.copied'));
    } catch {
      toast.error(t('attachments.copyFailed'));
    }
  };

  const items = query.data ?? [];
  const total = items.reduce((sum, a) => sum + a.size, 0);

  return (
    <Box component="section" aria-labelledby={titleId}>
      <Stack direction="row" sx={{ alignItems: 'baseline', justifyContent: 'space-between', flexWrap: 'wrap', columnGap: 2, rowGap: 0.5, mb: 1.5 }}>
        <Typography variant="h6" component="h2" id={titleId}>
          {t('attachments.title')}
        </Typography>
        {items.length > 0 && (
          <Typography variant="body2" sx={{ color: 'text.secondary', fontVariantNumeric: 'tabular-nums' }}>
            {t('attachments.summary', { count: items.length, size: fileSize(total) })}
          </Typography>
        )}
      </Stack>

      {canUpload && <DropZone onFiles={(files) => void upload(files)} progress={progress} />}

      {query.isPending ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      ) : items.length === 0 ? (
        <Typography variant="body2" sx={{ color: 'text.secondary', py: 1 }}>
          {canUpload ? t('attachments.emptyHelp') : t('attachments.empty')}
        </Typography>
      ) : (
        <Paper variant="outlined" sx={{ borderRadius: 2.5, overflow: 'hidden' }}>
          <Box component="ul" aria-label={t('attachments.listLabel')} sx={{ listStyle: 'none', m: 0, p: 0 }}>
            {items.map((attachment, index) => (
              <AttachmentRow
                key={attachment.id}
                attachment={attachment}
                url={attachmentApi.contentUrl(slug, projectKey, number, attachment.id)}
                divider={index > 0}
                mine={attachment.uploadedBy.id === me.id}
                removable={canRemoveAttachment(attachment, me.id, { canUpload, canManage })}
                busy={removing === attachment.id}
                onCopy={() => void copy(attachment)}
                onRemove={() => void remove(attachment)}
              />
            ))}
          </Box>
        </Paper>
      )}
    </Box>
  );
}

/** 끌어다 놓는 자리 — 파일을 끌고 들어오면 테두리를 칠한다. 터치 화면에서는 단추로 고른다 */
function DropZone({ onFiles, progress }: { onFiles: (files: File[]) => void; progress: UploadProgress | null }) {
  const { t } = useTranslation('issue');
  const [over, setOver] = useState(false);
  /** 안쪽 요소를 지날 때도 dragenter · dragleave 가 오므로 깊이를 센다 */
  const depth = useRef(0);
  const hasFiles = (event: DragEvent) => Array.from(event.dataTransfer?.types ?? []).includes('Files');

  return (
    <Box
      onDragEnter={(event) => {
        if (hasFiles(event)) {
          event.preventDefault();
          depth.current += 1;
          setOver(true);
        }
      }}
      onDragOver={(event) => {
        if (hasFiles(event)) {
          event.preventDefault();
          event.dataTransfer.dropEffect = 'copy';
        }
      }}
      onDragLeave={() => {
        depth.current = Math.max(0, depth.current - 1);
        if (depth.current === 0) {
          setOver(false);
        }
      }}
      onDrop={(event) => {
        event.preventDefault();
        depth.current = 0;
        setOver(false);
        const files = Array.from(event.dataTransfer?.files ?? []);
        if (files.length > 0) {
          onFiles(files);
        }
      }}
      sx={{
        mb: 2,
        px: { xs: 2, sm: 3 },
        py: 2.5,
        border: 1,
        borderStyle: 'dashed',
        borderColor: over ? 'primary.main' : 'divider',
        bgcolor: over ? 'action.hover' : 'transparent',
        borderRadius: 2.5,
        textAlign: 'center',
        transition: 'border-color 120ms, background-color 120ms',
      }}
    >
      <Stack spacing={1} sx={{ alignItems: 'center' }}>
        <CloudUploadOutlined aria-hidden sx={{ color: over ? 'primary.main' : 'text.secondary' }} />
        <Typography variant="body2">{over ? t('attachments.dropActive') : t('attachments.dropHere')}</Typography>
        <AttachFilesButton onFiles={onFiles} variant="outlined" size="small" icon={<UploadFileRounded />}>
          {t('attachments.choose')}
        </AttachFilesButton>
        <Typography variant="caption" sx={{ color: 'text.secondary', maxWidth: 520 }}>
          {t('attachments.dropHelp')}
        </Typography>
      </Stack>
      {progress && (
        <Box sx={{ mt: 2, textAlign: 'left' }}>
          <UploadProgressLine progress={progress} />
        </Box>
      )}
    </Box>
  );
}

function AttachmentRow({ attachment, url, divider, mine, removable, busy, onCopy, onRemove }: {
  attachment: AttachmentView;
  url: string;
  divider: boolean;
  mine: boolean;
  removable: boolean;
  busy: boolean;
  onCopy: () => void;
  onRemove: () => void;
}) {
  const { t } = useTranslation('issue');
  const [broken, setBroken] = useState(false);
  const name = attachment.fileName;
  const uploader = personName(attachment.uploadedBy) ?? t('view.unknownPerson');
  const thumbSx = {
    gridArea: 'thumb',
    width: 48,
    height: 48,
    borderRadius: 1.5,
    border: 1,
    borderColor: 'divider',
    bgcolor: 'background.subtle',
    display: 'grid',
    placeItems: 'center',
    overflow: 'hidden',
    color: 'text.secondary',
    flexShrink: 0,
  } as const;

  return (
    <Box
      component="li"
      sx={{
        display: 'grid',
        gridTemplateColumns: '48px minmax(0, 1fr) auto',
        gridTemplateAreas: { xs: '"thumb main main" "thumb actions actions"', sm: '"thumb main actions"' },
        columnGap: 1.5,
        rowGap: 0.5,
        alignItems: 'center',
        px: 2,
        py: 1.25,
        borderTop: divider ? 1 : 0,
        borderColor: 'divider',
      }}
    >
      {attachment.image && !broken ? (
        // 미리보기는 마우스로 누르기 편하게 둔 것 — 키보드 · 화면 읽기는 오른쪽 "열기" 단추로
        <Box component="a" href={url} target="_blank" rel="noopener noreferrer" tabIndex={-1} aria-hidden sx={thumbSx}>
          <Box
            component="img"
            src={url}
            alt=""
            loading="lazy"
            decoding="async"
            width={48}
            height={48}
            onError={() => setBroken(true)}
            sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
          />
        </Box>
      ) : (
        <Box sx={thumbSx} aria-hidden>
          <InsertDriveFileOutlined />
        </Box>
      )}

      <Box sx={{ gridArea: 'main', minWidth: 0 }}>
        <Typography variant="body2" noWrap title={name} sx={{ fontWeight: 600 }}>
          {name}
        </Typography>
        <Stack direction="row" spacing={0.75} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap', color: 'text.secondary' }}>
          <Typography variant="caption" sx={{ fontVariantNumeric: 'tabular-nums' }}>
            {fileSize(attachment.size)}
          </Typography>
          <Typography variant="caption" aria-hidden>
            ·
          </Typography>
          <Typography variant="caption" sx={{ overflowWrap: 'anywhere' }}>
            {mine ? `${uploader} (${t('attachments.me')})` : uploader}
          </Typography>
          {attachment.via === 'MCP' && (
            <Tooltip title={t('attachments.viaMcpHelp')}>
              <Chip size="small" variant="outlined" label={t('journal.viaMcp')} sx={{ height: 20, fontSize: 11 }} />
            </Tooltip>
          )}
          <Typography variant="caption" aria-hidden>
            ·
          </Typography>
          <Typography variant="caption" component="time" dateTime={attachment.createdAt} sx={{ fontVariantNumeric: 'tabular-nums' }}>
            {dateTimeMs(attachment.createdAt)}
          </Typography>
        </Stack>
      </Box>

      <Stack direction="row" spacing={0.25} sx={{ gridArea: 'actions', alignItems: 'center', justifySelf: { xs: 'start', sm: 'end' }, ml: { xs: -1, sm: 0 } }}>
        {attachment.image ? (
          <Tooltip title={t('attachments.openTooltip')}>
            <IconButton size="small" component="a" href={url} target="_blank" rel="noopener noreferrer" aria-label={t('attachments.open', { name })}>
              <OpenInNewRounded fontSize="small" />
            </IconButton>
          </Tooltip>
        ) : (
          <Tooltip title={t('attachments.downloadTooltip')}>
            <IconButton size="small" component="a" href={url} download aria-label={t('attachments.download', { name })}>
              <DownloadRounded fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
        <Tooltip title={t('attachments.copyTooltip')}>
          <IconButton size="small" onClick={onCopy} aria-label={t('attachments.copy', { name })}>
            <ContentCopyRounded fontSize="small" />
          </IconButton>
        </Tooltip>
        {removable && (
          <IconButton size="small" onClick={onRemove} loading={busy} aria-label={t('attachments.remove', { name })}>
            <DeleteOutlineRounded fontSize="small" />
          </IconButton>
        )}
      </Stack>
    </Box>
  );
}
