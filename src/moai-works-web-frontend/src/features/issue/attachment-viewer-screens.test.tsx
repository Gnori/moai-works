import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import i18n from '@/shared/i18n/i18n';
import { attachmentApi } from '@/shared/api/endpoints';
import type * as Endpoints from '@/shared/api/endpoints';
import type { AttachmentView } from '@/shared/api/types';
import { ConfirmProvider } from '@/shared/ui/ConfirmDialog';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { IssueAttachmentsProvider } from './attachment-context';
import { TEXT_LIMIT } from './attachment-kinds';
import { IssueAttachments } from './IssueAttachments';
import { MarkdownView } from './MarkdownView';
import { openPdf } from './pdf';

/**
 * 첨부 보기(일감 #310) — 열기 · 닫기 · 이전 / 다음(단추 · ← →) · 배율(단추 · + - 0) · 전체 화면, 형식마다 그리기(글 · 코드 · 표 · Markdown ·
 * JSON · 동영상 · PDF · 볼 수 없음), 2 MB 자르기, blob 주소 버리기, 본문 첨부 링크 · 그림에서 열기.
 * 서버 호출(attachmentApi.list · fetch)과 pdf.js(./pdf)만 가짜로 둔다.
 */

vi.mock('@/shared/api/endpoints', async (importOriginal) => {
  const actual = await importOriginal<typeof Endpoints>();
  return { ...actual, attachmentApi: { ...actual.attachmentApi, list: vi.fn() } };
});

vi.mock('@/features/auth/session', () => ({
  useMe: () => ({ id: 'aaaaaaaa-0000-0000-0000-000000000001', username: 'kim', displayName: '김철수' }),
}));

const render0 = vi.fn();
vi.mock('./pdf', () => ({ openPdf: vi.fn() }));

const ME = 'aaaaaaaa-0000-0000-0000-000000000001';
const id = (n: number) => `0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a${n.toString(16).padStart(2, '0')}`;
const content = (attachmentId: string) => `/api/v1/workspaces/a/issues/7/attachments/${attachmentId}/content`;

function attachment(n: number, fileName: string, extra: Partial<AttachmentView> = {}): AttachmentView {
  return {
    id: id(n),
    fileName,
    contentType: 'application/octet-stream',
    size: 1024,
    image: false,
    sha256: 'a'.repeat(64),
    uploadedBy: { id: ME, username: 'kim', displayName: '김철수' },
    via: 'WEB',
    createdAt: '2026-11-26T10:00:00.000',
    markdown: `[${fileName}](attachment:${id(n)})`,
    preview: false,
    agentSession: null,
    ...extra,
  };
}

const image = attachment(1, '화면.png', { contentType: 'image/png', image: true, preview: true, markdown: `![화면.png](attachment:${id(1)})` });
const notes = attachment(2, '메모.txt', { contentType: 'text/plain' });
const code = attachment(3, 'main.ts', { contentType: 'video/mp2t' });
const table = attachment(4, '표.csv');
const readme = attachment(5, 'README.md');
const data = attachment(6, 'data.json');
const movie = attachment(7, '녹화.mp4');
const report = attachment(8, '보고서.pdf');
const office = attachment(9, '문서.docx');
const binary = attachment(10, '덩어리.bin2');

/** 내용 주소 → 줄 바이트. 없는 주소는 404 problem+json */
let bodies: Record<string, BodyInit> = {};
const fetchMock = vi.fn(async (url: string) =>
  url in bodies
    ? new Response(bodies[url])
    : new Response(JSON.stringify({ code: 'ATTACHMENT_NOT_FOUND', detail: '첨부를 찾을 수 없습니다.' }), { status: 404 }),
);

function Providers({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <AppearanceProvider>
      <QueryClientProvider client={client}>
        <ConfirmProvider>
          <MemoryRouter>{children}</MemoryRouter>
        </ConfirmProvider>
      </QueryClientProvider>
    </AppearanceProvider>
  );
}

function renderList(items: AttachmentView[]) {
  vi.mocked(attachmentApi.list).mockResolvedValue(items);
  return render(
    <Providers>
      <IssueAttachments slug="a" number={7} canUpload={false} canManage={false} />
    </Providers>,
  );
}

async function openViewer(name: string) {
  fireEvent.click(await screen.findByRole('button', { name: `${name} 보기` }));
  return screen.findByRole('dialog', { name });
}

const createObjectURL = vi.fn(() => 'blob:moai/1');
const revokeObjectURL = vi.fn();

beforeEach(() => {
  bodies = {};
  vi.stubGlobal('fetch', fetchMock);
  Object.assign(URL, { createObjectURL, revokeObjectURL });
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe('첨부 보기 — 열기 · 닫기 · 이전 / 다음 · 배율', () => {
  it('보기 단추 · 작은 그림으로 열고, 머리에 이름 · 크기 · n / 전체, Esc 로 닫는다', async () => {
    const { container } = renderList([image, notes]);
    const dialog = await openViewer('화면.png');
    expect(dialog).toHaveTextContent('1.0 KB · 1 / 2');
    // 이미지는 내용 주소를 그대로(서버가 inline 으로 준다)
    expect(within(dialog).getByRole('img', { name: '화면.png' })).toHaveAttribute('src', content(image.id));
    expect(within(dialog).getByRole('link', { name: '화면.png 내려받기' })).toHaveAttribute('href', content(image.id));
    fireEvent.keyDown(dialog, { key: 'Escape' });
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    // 작은 그림(마우스용 단추)을 눌러도 연다
    fireEvent.click(container.querySelector('li button[aria-hidden]') as HTMLElement);
    expect(await screen.findByRole('dialog', { name: '화면.png' })).toBeInTheDocument();
  });

  it('이전 · 다음 단추와 ← → 로 넘기고, 끝에서는 누를 수 없다. 닫기 단추로 닫는다', async () => {
    bodies[content(notes.id)] = '첫 줄\n둘째 줄';
    renderList([image, notes]);
    let dialog = await openViewer('화면.png');
    expect(within(dialog).getByRole('button', { name: '이전 첨부 없음' })).toBeDisabled();
    fireEvent.keyDown(dialog, { key: 'ArrowRight' });
    dialog = await screen.findByRole('dialog', { name: '메모.txt' });
    expect(dialog).toHaveTextContent('2 / 2');
    expect(await within(dialog).findByText(/첫 줄/)).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: '다음 첨부 없음' })).toBeDisabled();
    fireEvent.click(within(dialog).getByRole('button', { name: '이전 첨부: 화면.png' }));
    dialog = await screen.findByRole('dialog', { name: '화면.png' });
    fireEvent.click(within(dialog).getByRole('button', { name: '닫기' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('배율 — 축소 · 확대 · 100% 단추와 + - 0 키, 25% ~ 400%. 글자 크기(zoom)에 쓰인다', async () => {
    bodies[content(notes.id)] = '가나다';
    renderList([notes]);
    const dialog = await openViewer('메모.txt');
    const pre = await within(dialog).findByText('가나다');
    const level = () => within(dialog).getByLabelText(/^배율 \d+%$/);
    expect(level()).toHaveTextContent('100%');
    fireEvent.click(within(dialog).getByRole('button', { name: '확대' }));
    expect(level()).toHaveTextContent('110%');
    fireEvent.keyDown(dialog, { key: '+' });
    expect(level()).toHaveTextContent('125%');
    expect(pre).toHaveStyle({ zoom: '1.25' });
    fireEvent.keyDown(dialog, { key: '-' });
    fireEvent.keyDown(dialog, { key: '-' });
    fireEvent.click(within(dialog).getByRole('button', { name: '축소' }));
    expect(level()).toHaveTextContent('90%');
    fireEvent.keyDown(dialog, { key: '0' });
    expect(level()).toHaveTextContent('100%');
    for (let i = 0; i < 20; i += 1) {
      fireEvent.keyDown(dialog, { key: '+' });
    }
    expect(level()).toHaveTextContent('400%');
    expect(within(dialog).getByRole('button', { name: '확대' })).toBeDisabled();
    fireEvent.click(within(dialog).getByRole('button', { name: '실제 크기(100%)' }));
    for (let i = 0; i < 20; i += 1) {
      fireEvent.keyDown(dialog, { key: '-' });
    }
    expect(level()).toHaveTextContent('25%');
    expect(within(dialog).getByRole('button', { name: '축소' })).toBeDisabled();
  });

  it('이미지는 처음에 화면에 맞춤 — 확대하면 맞춤이 꺼지고, 맞춤 단추로 되돌린다', async () => {
    renderList([image]);
    const dialog = await openViewer('화면.png');
    const fit = within(dialog).getByRole('button', { name: '화면에 맞춤' });
    expect(fit).toHaveAttribute('aria-pressed', 'true');
    expect(within(dialog).getByLabelText(/^배율/)).toHaveTextContent('맞춤 100%');
    fireEvent.click(within(dialog).getByRole('button', { name: '확대' }));
    expect(fit).toHaveAttribute('aria-pressed', 'false');
    expect(within(dialog).getByLabelText(/^배율/)).toHaveTextContent('110%');
    fireEvent.click(fit);
    expect(fit).toHaveAttribute('aria-pressed', 'true');
  });

  it('전체 화면을 켜고 끈다', async () => {
    renderList([image]);
    const dialog = await openViewer('화면.png');
    const toggle = within(dialog).getByRole('button', { name: '전체 화면' });
    expect(toggle).toHaveAttribute('aria-pressed', 'false');
    fireEvent.click(toggle);
    expect(within(dialog).getByRole('button', { name: '전체 화면 끄기' })).toHaveAttribute('aria-pressed', 'true');
    expect(document.querySelector('.MuiDialog-paperFullScreen')).not.toBeNull();
  });

  it('영어로도', async () => {
    await act(() => i18n.changeLanguage('en'));
    try {
      renderList([image, notes]);
      fireEvent.click(await screen.findByRole('button', { name: 'View 화면.png' }));
      const dialog = await screen.findByRole('dialog', { name: '화면.png' });
      expect(within(dialog).getByRole('button', { name: 'Zoom in' })).toBeInTheDocument();
      expect(within(dialog).getByRole('button', { name: 'Next attachment: 메모.txt' })).toBeInTheDocument();
      expect(within(dialog).getByRole('button', { name: 'Full screen' })).toBeInTheDocument();
    } finally {
      await act(() => i18n.changeLanguage('ko'));
    }
  });
});

describe('첨부 보기 — 형식마다', () => {
  it('코드 — 확장자로 언어를 골라 색 · 줄 번호(받은 형식 video/mp2t 는 믿지 않는다), 줄 바꿈 켜기', async () => {
    bodies[content(code.id)] = 'const a: number = 1;\nexport default a;\n';
    renderList([code]);
    const dialog = await openViewer('main.ts');
    await waitFor(() => expect(dialog.querySelectorAll('.code-line')).toHaveLength(2));
    expect(dialog.querySelector('.code-block.code-numbered')).not.toBeNull();
    expect(dialog.querySelector('.hljs-keyword')).toHaveTextContent('const');
    expect(dialog.querySelector('[data-line="2"]')).toHaveAttribute('aria-hidden', 'true');
    // 파일 글을 HTML 로 그리지 않는다
    expect(dialog.querySelector('script')).toBeNull();
    fireEvent.click(within(dialog).getByRole('button', { name: '줄 바꿈' }));
    expect(dialog.querySelector('.code-block.code-wrap')).not.toBeNull();
  });

  it('CSV — 따옴표 칸을 읽어 표로, 원문 보기로 바꾼다', async () => {
    bodies[content(table.id)] = '이름,메모\n"김, 철수","한 줄\n두 줄"\n이영희,끝\n';
    renderList([table]);
    const dialog = await openViewer('표.csv');
    const grid = await within(dialog).findByRole('table');
    expect(within(grid).getAllByRole('columnheader').map((th) => th.textContent)).toEqual(['이름', '메모']);
    expect(within(grid).getAllByRole('row')).toHaveLength(3);
    expect(within(grid).getByText('김, 철수')).toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole('button', { name: '원문 보기' }));
    expect(within(dialog).queryByRole('table')).not.toBeInTheDocument();
    expect(within(dialog).getByText(/"김, 철수"/)).toBeInTheDocument();
  });

  it('Markdown — 본문과 같은 보기(HTML 은 글자로), 원문 보기', async () => {
    bodies[content(readme.id)] = '# 제목\n\n**굵게** <img src=x onerror=alert(1)>';
    renderList([readme]);
    const dialog = await openViewer('README.md');
    expect(await within(dialog).findByRole('heading', { name: '제목' })).toBeInTheDocument();
    expect(within(dialog).getByText('굵게').tagName).toBe('STRONG');
    expect(dialog.querySelector('img')).toBeNull();
    fireEvent.click(within(dialog).getByRole('button', { name: '원문 보기' }));
    expect(within(dialog).getByText(/^# 제목/)).toBeInTheDocument();
  });

  it('JSON — 보기 좋게 펴고, 읽을 수 없으면 원문 그대로와 알림', async () => {
    const broken = attachment(11, 'broken.json');
    bodies[content(data.id)] = '{"a":[1,2]}';
    bodies[content(broken.id)] = '{"a": ';
    renderList([data, broken]);
    let dialog = await openViewer('data.json');
    await waitFor(() => expect(dialog.querySelectorAll('.code-line')).toHaveLength(6));
    expect(dialog).not.toHaveTextContent('JSON 으로 읽을 수 없어');
    fireEvent.keyDown(dialog, { key: 'ArrowRight' });
    dialog = await screen.findByRole('dialog', { name: 'broken.json' });
    expect(await within(dialog).findByText('JSON 으로 읽을 수 없어 원문 그대로 보입니다.')).toBeInTheDocument();
  });

  it('글이 2 MB 를 넘으면 앞 2 MB 만 받고 잘랐다고 알린다', async () => {
    bodies[content(notes.id)] = 'a'.repeat(TEXT_LIMIT + 10);
    renderList([notes]);
    const dialog = await openViewer('메모.txt');
    expect(await within(dialog).findByText('파일이 커서 앞 2.0 MB 만 보입니다. 전체는 내려받아 보십시오.')).toBeInTheDocument();
    expect(dialog.querySelector('pre')?.textContent).toHaveLength(TEXT_LIMIT);
    expect(fetchMock).toHaveBeenCalledWith(content(notes.id), expect.objectContaining({ credentials: 'same-origin' }));
  });

  it('모르는 형식은 앞부분을 보고 — 글이 아니면(NUL) 볼 수 없다며 내려받기를 준다. 오피스 문서는 받지도 않는다', async () => {
    bodies[content(binary.id)] = new Uint8Array([0x50, 0x4b, 0x03, 0x04, 0x00, 0x00, 0x08]);
    renderList([binary, office]);
    let dialog = await openViewer('덩어리.bin2');
    expect(await within(dialog).findByText('이 형식은 미리 볼 수 없습니다')).toBeInTheDocument();
    fireEvent.keyDown(dialog, { key: 'ArrowRight' });
    dialog = await screen.findByRole('dialog', { name: '문서.docx' });
    expect(within(dialog).getByText('이 형식은 미리 볼 수 없습니다')).toBeInTheDocument();
    expect(within(dialog).getByRole('link', { name: /내려받기 · 1\.0 KB/ })).toHaveAttribute('href', content(office.id));
    expect(within(dialog).queryByRole('button', { name: '확대' })).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalledWith(content(office.id), expect.anything());
  });

  it('받지 못하면(지웠음) 서버의 말과 다시 시도', async () => {
    renderList([notes]);
    const dialog = await openViewer('메모.txt');
    expect(await within(dialog).findByText('첨부를 찾을 수 없습니다.')).toBeInTheDocument();
    bodies[content(notes.id)] = '이제 있음';
    fireEvent.click(within(dialog).getByRole('button', { name: '다시 시도' }));
    expect(await within(dialog).findByText('이제 있음')).toBeInTheDocument();
  });

  it('동영상 — 형식을 붙인 blob 주소로 재생하고, 닫으면 주소를 버린다', async () => {
    bodies[content(movie.id)] = new Uint8Array([0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70]);
    renderList([movie]);
    const dialog = await openViewer('녹화.mp4');
    await waitFor(() => expect(dialog.querySelector('video')).toHaveAttribute('src', 'blob:moai/1'));
    expect(dialog.querySelector('video')).toHaveAttribute('controls');
    expect((createObjectURL.mock.calls[0] as unknown as [Blob])[0].type).toBe('video/mp4');
    fireEvent.click(within(dialog).getByRole('button', { name: '닫기' }));
    await waitFor(() => expect(revokeObjectURL).toHaveBeenCalledWith('blob:moai/1'));
  });

  it('PDF — 받은 바이트를 pdf.js 로, 쪽을 쌓아 그리고 "쪽 n / N", 확대하면 그 배율로 다시 그린다', async () => {
    bodies[content(report.id)] = new Uint8Array([0x25, 0x50, 0x44, 0x46]);
    const pages = [1, 2].map(() => ({
      getViewport: ({ scale }: { scale: number }) => ({ width: 600 * scale, height: 800 * scale }),
      render: render0.mockImplementation(() => ({ promise: Promise.resolve(), cancel: vi.fn() })),
    }));
    const doc = { numPages: 2, getPage: vi.fn(async (n: number) => pages[n - 1]) };
    vi.mocked(openPdf).mockReturnValue({ promise: Promise.resolve(doc), destroy: vi.fn() } as never);
    renderList([report]);
    const dialog = await openViewer('보고서.pdf');
    expect(await within(dialog).findByText('쪽 1 / 2')).toBeInTheDocument();
    expect(within(dialog).getAllByRole('img', { name: /쪽$/ })).toHaveLength(2);
    expect(vi.mocked(openPdf).mock.calls[0]?.[0]).toEqual(new Uint8Array([0x25, 0x50, 0x44, 0x46]));
    await waitFor(() => expect(render0).toHaveBeenCalledTimes(2));
    const scale = () => (render0.mock.calls.at(-1)?.[0] as { viewport: { width: number } }).viewport.width / 600;
    expect(scale()).toBe(1);
    fireEvent.click(within(dialog).getByRole('button', { name: '확대' }));
    await waitFor(() => expect(render0).toHaveBeenCalledTimes(4));
    expect(scale()).toBeCloseTo(1.1);
    expect(dialog.querySelector('[data-page="1"]')).toHaveStyle({ width: '660px' });
  });

  it('PDF 를 열 수 없으면(깨짐 · 암호) 그렇다고 알린다', async () => {
    bodies[content(report.id)] = new Uint8Array([1, 2, 3]);
    vi.mocked(openPdf).mockImplementation(() => ({ promise: Promise.reject(new Error('Invalid PDF structure')), destroy: vi.fn() }) as never);
    renderList([report]);
    const dialog = await openViewer('보고서.pdf');
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('PDF 를 열 수 없습니다');
  });
});

describe('본문 · 댓글의 첨부 표시에서 열기 (MarkdownView)', () => {
  const missing = '0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4aff';
  function renderBody(body: string) {
    vi.mocked(attachmentApi.list).mockResolvedValue([image, notes]);
    return render(
      <Providers>
        <IssueAttachmentsProvider slug="a" number={7}>
          <MarkdownView content={body} />
        </IssueAttachmentsProvider>
      </Providers>,
    );
  }

  it('파일 링크를 누르면 첨부 보기 — 링크 · 내려받기 주소는 그대로(가운데 누르기), 목록에 없는 첨부는 내려받기', async () => {
    bodies[content(notes.id)] = '본문에서 연 메모';
    renderBody(`[메모](attachment:${notes.id}) · [지운 것](attachment:${missing})`);
    await waitFor(() => expect(attachmentApi.list).toHaveBeenCalled());
    const link = screen.getByRole('link', { name: /메모/ });
    expect(link).toHaveAttribute('href', content(notes.id));
    expect(link).toHaveAttribute('download');
    // 목록을 받은 뒤에 누르면 막고(false) 연다 — 받기 전에는 링크 그대로(내려받기)
    await waitFor(() => expect(fireEvent.click(link)).toBe(false));
    const dialog = await screen.findByRole('dialog', { name: '메모.txt' });
    expect(await within(dialog).findByText('본문에서 연 메모')).toBeInTheDocument();
    expect(dialog).toHaveTextContent('2 / 2');
    fireEvent.keyDown(dialog, { key: 'Escape' });
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    // ⌘ 누르기 · 목록에 없는 첨부 — 막지 않는다(링크가 하던 대로)
    expect(fireEvent.click(link, { metaKey: true })).toBe(true);
    expect(fireEvent.click(screen.getByRole('link', { name: /지운 것/ }))).toBe(true);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('본문 속 첨부 그림을 누르면 크게 본다', async () => {
    renderBody(`![화면 캡처](attachment:${image.id})`);
    await waitFor(() => expect(attachmentApi.list).toHaveBeenCalled());
    const picture = screen.getByRole('img', { name: '화면 캡처' });
    expect(picture.closest('a')).toHaveAttribute('href', content(image.id));
    await waitFor(() => expect(fireEvent.click(picture)).toBe(false));
    const dialog = await screen.findByRole('dialog', { name: '화면.png' });
    expect(within(dialog).getByRole('img', { name: '화면.png' })).toBeInTheDocument();
  });
});
