import { useQuery } from '@tanstack/react-query';
import { createContext, useContext, type ReactNode } from 'react';

import { workspaceApi } from '@/shared/api/endpoints';
import type { WorkspaceDetail } from '@/shared/api/types';

import { workspaceKeys } from './queries';

/** 워크스페이스 상세 (내 역할 · 권한 포함). 멤버가 아니면 서버가 404. */
export function useWorkspaceQuery(slug: string | undefined) {
  return useQuery({
    queryKey: workspaceKeys.detail(slug ?? ''),
    queryFn: () => workspaceApi.detail(slug ?? ''),
    enabled: !!slug,
    staleTime: 30_000,
    retry: false,
  });
}

interface WorkspaceValue {
  workspace: WorkspaceDetail;
}

const WorkspaceContext = createContext<WorkspaceValue | null>(null);

/** /w/:slug 아래 화면에 지금 워크스페이스를 내려준다 (WorkspaceLayout 이 만든다). 권한 확인은 features/auth/access 의 useCan. */
export function WorkspaceProvider({ workspace, children }: { workspace: WorkspaceDetail; children: ReactNode }) {
  return <WorkspaceContext.Provider value={{ workspace }}>{children}</WorkspaceContext.Provider>;
}

/** 지금 보고 있는 워크스페이스 — /w/:slug 아래 화면에서만 */
export function useWorkspace(): WorkspaceValue {
  const value = useContext(WorkspaceContext);
  if (!value) {
    throw new Error('useWorkspace 는 /w/:slug 아래 화면에서만 쓸 수 있습니다');
  }
  return value;
}

/** 워크스페이스 밖에서도 부를 수 있는 판 (없으면 null) */
export function useOptionalWorkspace(): WorkspaceValue | null {
  return useContext(WorkspaceContext);
}
