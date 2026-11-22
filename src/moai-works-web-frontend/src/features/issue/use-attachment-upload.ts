import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';

import { attachmentApi } from '@/shared/api/endpoints';
import { errorMessage, isApiError } from '@/shared/api/http';
import type { AttachmentView } from '@/shared/api/types';

import { uploadPlaceholder } from './attachments';
import type { MarkdownEditorHandle } from './MarkdownEditor';
import { issueKeys } from './queries';

/** 어느 일감에 올리나 */
export interface UploadTarget {
  slug: string;
  projectKey: string;
  number: number;
  /**
   * 다 올린 뒤 일감 상세(이력)를 바로 다시 읽나 — 보기 화면은 그렇다(이력에 "첨부 추가" 줄이 생긴다).
   * 고치기 화면은 아니다 — 고치는 동안 바탕 일감이 바뀌면 남이 고친 것을 모르고 덮어쓸 수 있다(올리기는 판을 올리지 않는다).
   * 그때는 낡았다는 표시만 해 두어, 보기 화면으로 돌아가면 다시 읽는다.
   */
  refreshIssue: boolean;
}

/** 올리는 중 — 지금 파일 · 몇 번째 · 모두 몇 개(기다리는 것까지) · 지금 파일의 보낸 몫(0 ~ 1) */
export interface UploadProgress {
  name: string;
  index: number;
  total: number;
  fraction: number;
}

interface UploadCallbacks {
  /** 파일 하나를 다 올렸다 (index = 넘긴 목록에서의 자리) */
  onUploaded?: (attachment: AttachmentView, index: number) => void;
  /** 파일 하나를 올리지 못했다 — 알림은 이미 띄웠다 */
  onFailed?: (index: number) => void;
}

/** 다음 파일도 똑같이 막힐 오류 — 남은 파일은 올리지 않는다 */
function stopsBatch(e: unknown): boolean {
  return (
    isApiError(e) &&
    (e.code === 'WORKSPACE_STORAGE_FULL' || e.code === 'PROJECT_NOT_WRITABLE' || e.status === 401 || e.status === 403 || e.status === 404)
  );
}

/**
 * 첨부 올리기 — 여러 파일을 하나씩 차례로 올린다(나중에 고른 묶음은 앞 묶음이 끝난 뒤에). 올리는 동안 진행을 알린다.
 * 실패한 파일은 서버 오류 문장으로 알린다(errorMessage). target 이 없으면(새 일감) 아무것도 하지 않는다.
 */
export function useAttachmentUpload(target: UploadTarget | null) {
  const { t } = useTranslation('issue');
  const queryClient = useQueryClient();
  const [progress, setProgress] = useState<UploadProgress | null>(null);
  const queue = useRef<Promise<unknown>>(Promise.resolve());
  const counts = useRef({ started: 0, total: 0 });
  const slug = target?.slug;
  const projectKey = target?.projectKey;
  const number = target?.number;
  const refreshIssue = target?.refreshIssue ?? false;

  const upload = useCallback(
    (files: File[], callbacks: UploadCallbacks = {}): Promise<AttachmentView[]> => {
      if (slug === undefined || projectKey === undefined || number === undefined || files.length === 0) {
        return Promise.resolve([]);
      }
      counts.current.total += files.length;

      const run = async () => {
        const uploaded: AttachmentView[] = [];
        for (let index = 0; index < files.length; index += 1) {
          const file = files[index] as File;
          counts.current.started += 1;
          const position = counts.current.started;
          setProgress({ name: file.name, index: position, total: counts.current.total, fraction: 0 });
          try {
            const saved = await attachmentApi.upload(slug, projectKey, number, file, {
              onProgress: (fraction) => setProgress((p) => (p && p.index === position ? { ...p, fraction } : p)),
            });
            uploaded.push(saved);
            callbacks.onUploaded?.(saved, index);
            void queryClient.invalidateQueries({ queryKey: issueKeys.attachments(slug, projectKey, number) });
          } catch (e) {
            callbacks.onFailed?.(index);
            toast.error(t('attachments.uploadFailed', { name: file.name, message: errorMessage(e) }));
            if (stopsBatch(e) && index < files.length - 1) {
              const rest = files.length - index - 1;
              for (let skipped = index + 1; skipped < files.length; skipped += 1) {
                callbacks.onFailed?.(skipped);
              }
              counts.current.started += rest;
              toast.warning(t('attachments.skipped', { count: rest }));
              break;
            }
          }
        }
        if (uploaded.length > 0) {
          // 이력에 "첨부 추가" 줄이 생겼다. 고치기 화면은 지금 읽지 않고 낡았다는 표시만 한다(보기 화면으로 돌아가면 다시 읽는다)
          void queryClient.invalidateQueries({
            queryKey: issueKeys.detail(slug, projectKey, number),
            refetchType: refreshIssue ? 'active' : 'none',
          });
          const first = uploaded[0];
          toast.success(
            uploaded.length === 1 && first
              ? t('attachments.uploaded', { name: first.fileName })
              : t('attachments.uploadedMany', { count: uploaded.length }),
          );
        }
        // 뒤에 기다리는 묶음이 없으면 진행 표시를 거둔다
        if (counts.current.started >= counts.current.total) {
          counts.current = { started: 0, total: 0 };
          setProgress(null);
        }
        return uploaded;
      };

      const result = queue.current.then(run, run);
      queue.current = result.catch(() => undefined);
      return result;
    },
    [slug, projectKey, number, refreshIssue, queryClient, t],
  );

  return { upload, progress, enabled: target !== null };
}

/**
 * 편집 칸에 이미지 · 파일 넣기 — 붙여넣기 · 끌어다 놓기 · 단추로 고른 파일을 이 일감에 올리고, 커서 자리에 받은 표시(markdown)를 넣는다.
 * 올리는 동안 그 자리에 "![업로드 중… 이름]()" 를 두었다가, 다 올리면 받은 표시로 바꾸고 실패하면 지운다.
 * target 이 없으면(새 일감) addFiles 가 없다 — 편집 칸은 기본 동작 그대로.
 */
export function useEditorUpload(target: UploadTarget | null) {
  const { t } = useTranslation('issue');
  const editorRef = useRef<MarkdownEditorHandle>(null);
  const { upload, progress, enabled } = useAttachmentUpload(target);

  const addFiles = useCallback(
    (files: File[]) => {
      const editor = editorRef.current;
      if (!editor || files.length === 0) {
        return;
      }
      // 여러 개면 한 줄에 하나씩
      const lead = (index: number) => (index > 0 ? '\n' : '');
      const marks = files.map((file, index) =>
        editor.insert(lead(index) + uploadPlaceholder(t('attachments.placeholder', { name: file.name }))),
      );
      void upload(files, {
        onUploaded: (attachment, index) => editorRef.current?.replace(marks[index] ?? null, lead(index) + attachment.markdown),
        onFailed: (index) => editorRef.current?.remove(marks[index] ?? null),
      });
    },
    [upload, t],
  );

  return { editorRef, addFiles: enabled ? addFiles : undefined, progress };
}

/** 편집 칸에 이미지 · 파일 넣기 손잡이 — 양식(IssueForm)에 넘긴다 */
export type EditorUpload = ReturnType<typeof useEditorUpload>;
