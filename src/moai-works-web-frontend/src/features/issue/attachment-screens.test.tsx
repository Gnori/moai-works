import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import i18n from '@/shared/i18n/i18n';
import { ProjectProvider } from '@/features/project/project-context';
import { WorkspaceProvider } from '@/features/workspace/workspace-context';
import { attachmentApi, issueApi } from '@/shared/api/endpoints';
import type * as Endpoints from '@/shared/api/endpoints';
import { ApiError } from '@/shared/api/http';
import type { AttachmentView, IssueDetail, PermissionCode, ProjectDetail, ProjectStatus, WorkspaceDetail } from '@/shared/api/types';
import { ConfirmProvider } from '@/shared/ui/ConfirmDialog';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { IssueAttachmentsProvider } from './attachment-context';
import { canUploadAttachment } from './attachments';
import { IssueAttachments } from './IssueAttachments';
import { emptyIssueForm } from './issue-form';
import { IssueForm } from './IssueForm';
import { CommentBox, IssueHistory } from './IssueHistory';
import { IssueViewPage } from './IssueViewPage';
import { MarkdownView } from './MarkdownView';

/**
 * 첨부파일 — 본문 속 첨부 표시 그리기, 이력의 말, 일감 보기의 탭 · 주소, 단추 가리기, 올리기 · 지우기 · 붙여넣기.
 * 서버 호출(issueApi · attachmentApi)만 가짜로 두고 화면 · 주소 · 말 · 편집기(CodeMirror)는 그대로.
 */

vi.mock('@/shared/api/endpoints', async (importOriginal) => {
  const actual = await importOriginal<typeof Endpoints>();
  return {
    ...actual,
    issueApi: { ...actual.issueApi, detail: vi.fn(), comment: vi.fn() },
    attachmentApi: { ...actual.attachmentApi, list: vi.fn(), upload: vi.fn(), remove: vi.fn() },
  };
});

vi.mock('@/features/auth/session', () => ({
  useMe: () => ({ id: 'aaaaaaaa-0000-0000-0000-000000000001', username: 'kim', displayName: '김철수' }),
}));

const ME = 'aaaaaaaa-0000-0000-0000-000000000001';
const LEE = 'aaaaaaaa-0000-0000-0000-000000000002';
const IMG = '0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b';
const PDF = '0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a6c';
const content = (id: string) => `/api/v1/workspaces/a/projects/web/issues/7/attachments/${id}/content`;
const preview = (id: string) => `/api/v1/workspaces/a/projects/web/issues/7/attachments/${id}/preview`;
const WEBP = '0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a7d';

const workspace = { slug: 'a', name: 'A', myPermissions: [] as PermissionCode[] } as unknown as WorkspaceDetail;

function project(permissions: PermissionCode[], status: ProjectStatus = 'ACTIVE'): ProjectDetail {
  return {
    id: 'p1',
    key: 'web',
    name: '웹',
    description: null,
    visibility: 'WORKSPACE',
    status,
    updatedAt: '2026-11-22T09:00:00',
    createdAt: '2026-11-01T09:00:00',
    archivedAt: null,
    issueCount: 1,
    myRoles: [],
    myPermissions: permissions,
    myActions: [],
  };
}

const screenshot: AttachmentView = {
  id: IMG,
  fileName: '화면.png',
  contentType: 'image/png',
  size: 1258291,
  image: true,
  sha256: 'a'.repeat(64),
  uploadedBy: { id: ME, username: 'kim', displayName: '김철수' },
  via: 'WEB',
  createdAt: '2026-11-22T10:00:00.005',
  markdown: `![화면.png](attachment:${IMG})`,
  preview: true,
};

const report: AttachmentView = {
  id: PDF,
  fileName: '보고서.pdf',
  contentType: 'application/pdf',
  size: 2048,
  image: false,
  sha256: 'b'.repeat(64),
  uploadedBy: { id: LEE, username: 'lee', displayName: '이영희' },
  via: 'MCP',
  createdAt: '2026-11-22T11:30:00.120',
  markdown: `[보고서.pdf](attachment:${PDF})`,
  preview: false,
};

/** WebP — 서버에 디코더가 없어 미리보기 그림이 없다. 목록은 원본을 쓴다 */
const sticker: AttachmentView = {
  ...screenshot,
  id: WEBP,
  fileName: '스티커.webp',
  contentType: 'image/webp',
  size: 4096,
  markdown: `![스티커.webp](attachment:${WEBP})`,
  preview: false,
};

const issue: IssueDetail = {
  number: 7,
  type: 'bug',
  title: '로그인 실패',
  description: `재현 화면\n\n![화면.png](attachment:${IMG})`,
  status: 'IN_PROGRESS',
  manager: { id: ME, username: 'kim', displayName: '김철수' },
  assignee: null,
  parent: null,
  dueDate: null,
  startDate: null,
  plannedEndDate: null,
  completedOn: null,
  closedOn: null,
  createdBy: { id: ME, username: 'kim', displayName: '김철수' },
  createdAt: '2026-11-20T09:00:00.001',
  updatedAt: '2026-11-22T09:03:04.007',
  rowVersion: 3,
  overdue: false,
  revisionNo: 3,
  estimatedHours: null,
  spentHours: 0,
  agentSession: null,
  children: [],
  links: [],
  journal: [
    {
      id: 'j1',
      actorType: 'USER',
      actorId: ME,
      actorName: '김철수',
      via: 'WEB',
      createdAt: '2026-11-22T10:00:00.006',
      notes: null,
      changes: [{ field: 'attachment', before: null, after: { id: IMG, name: '화면.png', size: 1258291, image: true } }],
      revisionNo: null,
      rowVersion: null,
      editedAt: null,
      deleted: false,
      deletedAt: null,
      deletedBy: null,
    },
    {
      id: 'j2',
      actorType: 'USER',
      actorId: LEE,
      actorName: '이영희',
      via: 'MCP',
      createdAt: '2026-11-22T11:00:00.000',
      notes: '확인했습니다',
      changes: [],
      revisionNo: null,
      rowVersion: 0,
      editedAt: null,
      deleted: false,
      deletedAt: null,
      deletedBy: null,
    },
    {
      id: 'j3',
      actorType: 'USER',
      actorId: ME,
      actorName: '김철수',
      via: 'WEB',
      createdAt: '2026-11-22T12:00:00.000',
      notes: '고쳤습니다',
      changes: [{ field: 'attachment', before: { id: PDF, name: '옛 보고서.pdf', size: 10, image: false }, after: null }],
      revisionNo: null,
      rowVersion: 0,
      editedAt: null,
      deleted: false,
      deletedAt: null,
      deletedBy: null,
    },
  ],
  workLogs: [],
};

/** 지금 주소의 ?… — 탭이 주소에 남는지 본다 */
function SearchProbe() {
  return <div data-testid="search">{useLocation().search}</div>;
}

function Providers({ children, permissions = ['issue.read'], status = 'ACTIVE', path = '/w/a/projects/web/issues/7' }: {
  children: ReactNode;
  permissions?: PermissionCode[];
  status?: ProjectStatus;
  path?: string;
}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <AppearanceProvider>
      <QueryClientProvider client={client}>
        <ConfirmProvider>
          <WorkspaceProvider workspace={workspace}>
            <ProjectProvider project={project(permissions, status)}>
              <MemoryRouter initialEntries={[path]}>
                <Routes>
                  <Route
                    path="/w/:slug/projects/:projectKey/issues/:number/*"
                    element={
                      <>
                        {children}
                        <SearchProbe />
                      </>
                    }
                  />
                </Routes>
              </MemoryRouter>
            </ProjectProvider>
          </WorkspaceProvider>
        </ConfirmProvider>
      </QueryClientProvider>
    </AppearanceProvider>
  );
}

/** 끝을 밖에서 정하는 약속 — 올리는 동안의 화면을 본다 */
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

/** 편집 칸에 파일 · 글을 붙여넣는다 (jsdom 에는 ClipboardEvent 의 clipboardData 가 없다) */
function pasteFiles(target: Element, files: File[], text = '') {
  const event = new Event('paste', { bubbles: true, cancelable: true });
  Object.defineProperty(event, 'clipboardData', {
    value: { files, types: ['Files'], getData: (type: string) => (type === 'text/plain' ? text : '') },
  });
  act(() => {
    target.dispatchEvent(event);
  });
  return event;
}

beforeAll(() => {
  // jsdom 에는 글자 자리를 재는 함수가 없다 — CodeMirror 가 커서 자리를 잴 때 부른다
  const rects = () => Object.assign([], { item: () => null }) as unknown as DOMRectList;
  Range.prototype.getClientRects = rects;
  Range.prototype.getBoundingClientRect = () => new DOMRect();
  Element.prototype.scrollIntoView = vi.fn();
});

beforeEach(() => {
  vi.mocked(issueApi.detail).mockResolvedValue(issue);
  vi.mocked(attachmentApi.list).mockResolvedValue([screenshot, report]);
});

afterEach(() => {
  vi.clearAllMocks();
});

describe('본문 속 첨부 표시 (MarkdownView)', () => {
  const body = [
    `![화면 캡처](attachment:${IMG})`,
    '',
    `[보고서.pdf](attachment:${PDF})`,
    '',
    '[나쁜 링크](javascript:alert(1)) · ![바깥 그림](https://evil.example/x.png) · [가짜 첨부](attachment:../../secret)',
    '',
    '<img src="x" onerror="alert(1)">',
  ].join('\n');

  it('이미지는 이 일감의 내용 주소로(창 폭까지 · 보일 때 받음 · 설명 글 유지), 파일은 내려받기 링크로 그린다', () => {
    const { container } = render(
      <Providers>
        <IssueAttachmentsProvider slug="a" projectKey="web" number={7}>
          <MarkdownView content={body} />
        </IssueAttachmentsProvider>
      </Providers>,
    );
    const image = screen.getByRole('img', { name: '화면 캡처' });
    expect(image).toHaveAttribute('src', content(IMG));
    expect(image).toHaveAttribute('loading', 'lazy');

    const file = screen.getByRole('link', { name: /보고서\.pdf/ });
    expect(file).toHaveAttribute('href', content(PDF));
    expect(file).toHaveAttribute('download');

    // 기본 거르기는 그대로 — javascript: 는 버리고, 바깥 그림 · 모양이 틀린 첨부는 글로만, HTML 은 글자로
    expect(screen.getByText('나쁜 링크').closest('a')).toBeNull();
    expect(container.querySelector('[href^="javascript"], [src^="javascript"]')).toBeNull();
    expect(screen.queryByRole('img', { name: '바깥 그림' })).not.toBeInTheDocument();
    expect(screen.getByText('바깥 그림')).toBeInTheDocument();
    expect(screen.getByText('가짜 첨부').closest('a')).toBeNull();
    expect(container.querySelectorAll('img')).toHaveLength(1);
    expect(screen.getByText('<img src="x" onerror="alert(1)">')).toBeInTheDocument();
  });

  it('열 수 없는 이미지(지웠음)는 설명 글만 남기고, 어느 일감인지 모르면 글로만 보인다', () => {
    const { unmount } = render(
      <Providers>
        <IssueAttachmentsProvider slug="a" projectKey="web" number={7}>
          <MarkdownView content={`![화면 캡처](attachment:${IMG})`} />
        </IssueAttachmentsProvider>
      </Providers>,
    );
    fireEvent.error(screen.getByRole('img', { name: '화면 캡처' }));
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(screen.getByText('화면 캡처')).toBeInTheDocument();
    unmount();

    render(
      <Providers>
        <MarkdownView content={`![화면 캡처](attachment:${IMG}) [보고서](attachment:${PDF})`} />
      </Providers>,
    );
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(screen.getByText('화면 캡처')).toBeInTheDocument();
    expect(screen.getByText('보고서').closest('a')).toBeNull();
  });
});

describe('이력의 첨부 줄', () => {
  it('"첨부 추가: 이름 (크기)" · "첨부 삭제: 이름"', () => {
    render(
      <Providers>
        <IssueHistory issue={issue} issuePath={(n) => `/issues/${n}`} revisionPath={(no) => `/rev/${no}`} />
      </Providers>,
    );
    expect(screen.getByText('첨부 추가: 화면.png (1.2 MB)')).toBeInTheDocument();
    expect(screen.getByText('첨부 삭제: 옛 보고서.pdf')).toBeInTheDocument();
  });

  it('영어로도', async () => {
    await act(() => i18n.changeLanguage('en'));
    try {
      render(
        <Providers>
          <IssueHistory issue={issue} issuePath={(n) => `/issues/${n}`} revisionPath={(no) => `/rev/${no}`} />
        </Providers>,
      );
      expect(screen.getByText('Attachment added: 화면.png (1.2 MB)')).toBeInTheDocument();
      expect(screen.getByText('Attachment deleted: 옛 보고서.pdf')).toBeInTheDocument();
    } finally {
      await act(() => i18n.changeLanguage('ko'));
    }
  });
});

describe('일감 보기의 탭', () => {
  it('내용 · 댓글 · 첨부파일 — 고르면 주소(?tab=)가 바뀌고 다른 주소 값은 남는다. 탭 이름에 수를 붙인다', async () => {
    render(
      <Providers path="/w/a/projects/web/issues/7?settings=profile">
        <IssueViewPage />
      </Providers>,
    );
    expect(await screen.findByRole('heading', { name: '로그인 실패' })).toBeInTheDocument();
    const contentTab = screen.getByRole('tab', { name: /내용/ });
    const commentsTab = screen.getByRole('tab', { name: /댓글/ });
    const attachmentsTab = screen.getByRole('tab', { name: /첨부파일/ });

    // 처음은 내용 — 설명 속 첨부 이미지가 그 자리에 보인다. 속성 칸은 어느 탭에서든 보인다
    expect(contentTab).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('img', { name: '화면.png' })).toHaveAttribute('src', content(IMG));
    expect(screen.getByRole('complementary', { name: '속성' })).toBeVisible();
    // 댓글 수(글이 있는 이력 2줄) · 첨부 수(2개)
    expect(within(commentsTab).getByText('2')).toBeInTheDocument();
    await waitFor(() => expect(within(attachmentsTab).getByText('2')).toBeInTheDocument());

    fireEvent.click(commentsTab);
    expect(screen.getByTestId('search')).toHaveTextContent('?settings=profile&tab=comments');
    expect(commentsTab).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tabpanel', { name: /댓글/ })).toHaveTextContent('확인했습니다');
    expect(screen.queryByRole('img', { name: '화면.png' })).not.toBeInTheDocument();

    fireEvent.click(attachmentsTab);
    expect(screen.getByTestId('search')).toHaveTextContent('?settings=profile&tab=attachments');
    expect(within(screen.getByRole('tabpanel', { name: /첨부파일/ })).getByRole('list', { name: '첨부파일 목록' })).toBeInTheDocument();
    expect(screen.getByRole('complementary', { name: '속성' })).toBeVisible();

    fireEvent.click(contentTab);
    expect(screen.getByTestId('search').textContent).toBe('?settings=profile');
  });

  it('주소의 탭으로 연다 (새로 고침 · 링크)', async () => {
    render(
      <Providers path="/w/a/projects/web/issues/7?tab=comments">
        <IssueViewPage />
      </Providers>,
    );
    expect(await screen.findByRole('tab', { name: /댓글/ })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByText('첨부 추가: 화면.png (1.2 MB)')).toBeVisible();
  });
});

describe('첨부파일 탭의 단추 (서버가 준 권한으로 가리기만)', () => {
  function renderPanel(permissions: PermissionCode[], status: ProjectStatus = 'ACTIVE') {
    const p = project(permissions, status);
    return render(
      <Providers permissions={permissions} status={status}>
        <IssueAttachments
          slug="a"
          projectKey="web"
          number={7}
          canUpload={canUploadAttachment(p)}
          canManage={p.myPermissions.includes('project.manage')}
        />
      </Providers>,
    );
  }

  it('줄마다 미리보기 · 이름 · 크기 · 올린 사람 · 밀리초 시각 · MCP 표시', async () => {
    const { container } = renderPanel(['issue.read']);
    const rows = within(await screen.findByRole('list', { name: '첨부파일 목록' })).getAllByRole('listitem');
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent('화면.png');
    expect(rows[0]).toHaveTextContent('1.2 MB');
    expect(rows[0]).toHaveTextContent('김철수 (나)');
    expect(rows[0]).toHaveTextContent('2026-11-22 10:00:00.005');
    expect(rows[1]).toHaveTextContent('2.0 KB');
    expect(within(rows[1] as HTMLElement).getByText('MCP')).toBeInTheDocument();
    // 이미지는 서버가 만든 작은 미리보기 그림(보일 때 받음), 파일은 표시만
    const thumbs = container.querySelectorAll('li img');
    expect(thumbs).toHaveLength(1);
    expect(thumbs[0]).toHaveAttribute('src', preview(IMG));
    expect(thumbs[0]).toHaveAttribute('loading', 'lazy');
    // 열기(이미지)는 원본 · 내려받기(파일) · 표시 복사는 독자에게도
    expect(screen.getByRole('link', { name: '화면.png 열기' })).toHaveAttribute('href', content(IMG));
    expect(screen.getByRole('link', { name: '보고서.pdf 내려받기' })).toHaveAttribute('download');
    expect(screen.getByRole('button', { name: '보고서.pdf 본문에 넣을 표시 복사' })).toBeInTheDocument();
    expect(screen.getByText('2개 · 모두 1.2 MB')).toBeInTheDocument();
  });

  it('미리보기 그림이 없는 이미지(WebP)는 원본을, 미리보기를 못 받으면(깨진 그림) 파일 표시를 보인다', async () => {
    vi.mocked(attachmentApi.list).mockResolvedValue([screenshot, sticker, report]);
    const { container } = renderPanel(['issue.read']);
    const rows = within(await screen.findByRole('list', { name: '첨부파일 목록' })).getAllByRole('listitem');
    expect(rows).toHaveLength(3);
    expect(rows[0]?.querySelector('img')).toHaveAttribute('src', preview(IMG));
    expect(rows[1]?.querySelector('img')).toHaveAttribute('src', content(WEBP));
    expect(rows[2]?.querySelector('img')).toBeNull();
    // 서버가 404(만들 수 없는 그림)면 그 줄은 파일 표시로 — 열기는 그대로 원본
    fireEvent.error(rows[0]?.querySelector('img') as HTMLImageElement);
    expect(rows[0]?.querySelector('img')).toBeNull();
    expect(container.querySelectorAll('li img')).toHaveLength(1);
    expect(screen.getByRole('link', { name: '화면.png 열기' })).toHaveAttribute('href', content(IMG));
  });

  it('독자 — 올리기도 지우기도 없다', async () => {
    renderPanel(['issue.read']);
    await screen.findByRole('list', { name: '첨부파일 목록' });
    expect(screen.queryByRole('button', { name: '파일 고르기' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /지우기$/ })).not.toBeInTheDocument();
  });

  it('댓글을 달 수 있으면 — 올리기, 지우기는 내가 올린 것만', async () => {
    renderPanel(['issue.read', 'issue.comment']);
    await screen.findByRole('list', { name: '첨부파일 목록' });
    expect(screen.getByRole('button', { name: '파일 고르기' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '화면.png 지우기' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '보고서.pdf 지우기' })).not.toBeInTheDocument();
  });

  it('project.manage — 남이 올린 것도 지운다', async () => {
    renderPanel(['issue.read', 'issue.comment', 'project.manage']);
    await screen.findByRole('list', { name: '첨부파일 목록' });
    expect(screen.getByRole('button', { name: '화면.png 지우기' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '보고서.pdf 지우기' })).toBeInTheDocument();
  });

  it('보관한 프로젝트 — 권한이 있어도 올리기 · 지우기를 숨긴다', async () => {
    renderPanel(['issue.read', 'issue.comment', 'project.manage'], 'ARCHIVED');
    await screen.findByRole('list', { name: '첨부파일 목록' });
    expect(screen.queryByRole('button', { name: '파일 고르기' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /지우기$/ })).not.toBeInTheDocument();
  });

  it('지우기 전에 묻고, 지우면 목록에서 뺀다', async () => {
    vi.mocked(attachmentApi.remove).mockResolvedValue(undefined);
    renderPanel(['issue.read', 'issue.comment']);
    fireEvent.click(await screen.findByRole('button', { name: '화면.png 지우기' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('화면.png (1.2 MB) 을(를) 지웁니다.');
    expect(attachmentApi.remove).not.toHaveBeenCalled();
    vi.mocked(attachmentApi.list).mockResolvedValue([report]);
    fireEvent.click(within(dialog).getByRole('button', { name: '지우기' }));
    await waitFor(() => expect(attachmentApi.remove).toHaveBeenCalledWith('a', 'web', 7, IMG));
    await waitFor(() => expect(screen.queryByText('화면.png')).not.toBeInTheDocument());
  });

  it('여러 파일을 고르면 하나씩 차례로 올리고 진행을 보인다', async () => {
    const first = deferred<AttachmentView>();
    const second = deferred<AttachmentView>();
    vi.mocked(attachmentApi.upload).mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    const { container } = renderPanel(['issue.read', 'issue.comment']);
    await screen.findByRole('list', { name: '첨부파일 목록' });
    const a = new File(['a'], 'a.txt', { type: 'text/plain' });
    const b = new File(['bb'], 'b.txt', { type: 'text/plain' });
    fireEvent.change(container.querySelector('input[type="file"]') as HTMLInputElement, { target: { files: [a, b] } });

    await waitFor(() => expect(attachmentApi.upload).toHaveBeenCalledTimes(1));
    expect(attachmentApi.upload).toHaveBeenLastCalledWith('a', 'web', 7, a, expect.anything());
    expect(screen.getByRole('status')).toHaveTextContent('올리는 중 1/2 · a.txt');

    await act(async () => first.resolve({ ...report, id: PDF, fileName: 'a.txt' }));
    await waitFor(() => expect(attachmentApi.upload).toHaveBeenCalledTimes(2));
    expect(attachmentApi.upload).toHaveBeenLastCalledWith('a', 'web', 7, b, expect.anything());
    expect(screen.getByRole('status')).toHaveTextContent('올리는 중 2/2 · b.txt');

    await act(async () => second.resolve({ ...report, fileName: 'b.txt' }));
    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument());
  });
});

describe('편집 칸에 붙여넣어 올리기', () => {
  function renderComment() {
    return render(
      <Providers permissions={['issue.read', 'issue.comment']}>
        <IssueAttachmentsProvider slug="a" projectKey="web" number={7}>
          <CommentBox slug="a" projectKey="web" issue={issue} canAttach />
        </IssueAttachmentsProvider>
      </Providers>,
    );
  }

  it('이미지를 붙여넣으면 이 일감에 올리고, 커서 자리의 "업로드 중…" 을 받은 표시로 바꾼다', async () => {
    const upload = deferred<AttachmentView>();
    vi.mocked(attachmentApi.upload).mockReturnValueOnce(upload.promise);
    renderComment();
    const editor = await screen.findByTestId('rich-editor');
    const png = new File(['png'], '화면.png', { type: 'image/png' });

    const event = pasteFiles(editor, [png]);
    expect(event.defaultPrevented).toBe(true);
    await waitFor(() => expect(attachmentApi.upload).toHaveBeenCalledWith('a', 'web', 7, png, expect.anything()));
    expect(editor).toHaveTextContent('![업로드 중… 화면.png]()');
    // 올리는 동안에는 댓글을 달 수 없다
    expect(screen.getByRole('button', { name: '댓글 달기' })).toBeDisabled();

    await act(async () => upload.resolve(screenshot));
    await waitFor(() => expect(editor).toHaveTextContent(`![화면.png](attachment:${IMG})`));
    expect(editor).not.toHaveTextContent('업로드 중');
    expect(screen.getByRole('button', { name: '댓글 달기' })).toBeEnabled();

    // 단 댓글에는 받은 표시(Markdown)가 그대로 들어간다
    vi.mocked(issueApi.comment).mockResolvedValueOnce(issue);
    fireEvent.click(screen.getByRole('button', { name: '댓글 달기' }));
    await waitFor(() => expect(issueApi.comment).toHaveBeenCalledWith('a', 'web', 7, `![화면.png](attachment:${IMG})`));
  });

  it('올리지 못하면 "업로드 중…" 자리를 지운다', async () => {
    vi.mocked(attachmentApi.upload).mockRejectedValueOnce(new ApiError(413, 'FILE_TOO_LARGE', '파일이 허용된 크기를 넘습니다.'));
    renderComment();
    const editor = await screen.findByTestId('rich-editor');
    pasteFiles(editor, [new File(['png'], '큰.png', { type: 'image/png' })]);
    await waitFor(() => expect(attachmentApi.upload).toHaveBeenCalled());
    await waitFor(() => expect(editor).not.toHaveTextContent('업로드 중'));
    expect(editor).not.toHaveTextContent('attachment:');
  });

  it('친 바로 다음(기다리지 않고) 댓글 달기 — 단추가 바로 켜지고 방금 친 글이 들어간다', async () => {
    vi.mocked(issueApi.comment).mockResolvedValueOnce(issue);
    renderComment();
    const button = screen.getByRole('button', { name: '댓글 달기' });
    expect(button).toBeDisabled();
    pasteFiles(await screen.findByTestId('rich-editor'), [], '바로 단 댓글');
    expect(button).toBeEnabled();
    fireEvent.click(button);
    await waitFor(() => expect(issueApi.comment).toHaveBeenCalledWith('a', 'web', 7, '바로 단 댓글'));
  });

  it('글을 붙여넣으면 올리지 않고 글로 넣는다', async () => {
    renderComment();
    const editor = await screen.findByTestId('rich-editor');
    pasteFiles(editor, [], '그냥 글');
    await waitFor(() => expect(editor).toHaveTextContent('그냥 글'));
    expect(attachmentApi.upload).not.toHaveBeenCalled();
  });
});

describe('새 일감의 설명 칸', () => {
  it('아직 일감이 없으니 올리지 않고, 만든 뒤에 넣을 수 있다고 알린다', async () => {
    const p = project(['issue.read', 'issue.create', 'issue.comment']);
    render(
      <Providers permissions={p.myPermissions}>
        <IssueForm
          slug="a"
          project={p}
          value={emptyIssueForm('bug', null)}
          onChange={() => undefined}
          initialLabels={{ manager: null, assignee: null, parent: null }}
          types={[{ key: 'bug', name: '버그', description: null, active: true }]}
          errors={{}}
        />
      </Providers>,
    );
    expect(screen.getByText(/이미지 · 파일은 일감을 만든 뒤에 넣을 수 있습니다/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '이미지 · 파일 넣기' })).not.toBeInTheDocument();
    pasteFiles(await screen.findByTestId('rich-editor'), [new File(['png'], '화면.png', { type: 'image/png' })]);
    expect(attachmentApi.upload).not.toHaveBeenCalled();
  });
});
