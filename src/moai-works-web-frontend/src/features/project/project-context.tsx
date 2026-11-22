import { createContext, useContext, type ReactNode } from 'react';

import type { ProjectDetail } from '@/shared/api/types';

const ProjectContext = createContext<ProjectDetail | null>(null);

/** 프로젝트 화면(/w/:slug/projects/:projectKey 아래)에 지금 프로젝트를 내려준다 — ProjectLayout 이 만든다. */
export function ProjectProvider({ project, children }: { project: ProjectDetail; children: ReactNode }) {
  return <ProjectContext.Provider value={project}>{children}</ProjectContext.Provider>;
}

/** 지금 보고 있는 프로젝트 (내 권한 · 할 수 있는 일 포함) — 프로젝트 화면에서만 */
export function useProject(): ProjectDetail {
  const project = useContext(ProjectContext);
  if (!project) {
    throw new Error('useProject 는 프로젝트 화면(/w/:slug/projects/:projectKey 아래)에서만 쓸 수 있습니다');
  }
  return project;
}
