import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';

import '@/shared/i18n/i18n';
import { skillApi } from '@/shared/api/endpoints';
import type * as Endpoints from '@/shared/api/endpoints';
import { ApiError } from '@/shared/api/http';
import type { SkillDetail, SkillRevisionDiff, SkillRevisionItem, SkillSummary } from '@/shared/api/types';
import { ConfirmProvider } from '@/shared/ui/ConfirmDialog';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { EditSkillPage, NewSkillPage } from './SkillFormPage';
import { SkillsPage } from './SkillsPage';
import { SkillViewPage } from './SkillViewPage';

/**
 * Agent 스킬 화면(시스템 관리자, 일감 #30) — 목록 · 삭제 확인 · 새 스킬 · 수정의 겹침 · 리비전 탭(타임라인 · 차이) · 되돌리기 확인 · SKILL.md 가져오기.
 * 일감 #183 — 바뀐 점 요약(Agent 에게 보임)은 수정 · 되돌리기 · 가져오기에 꼭 넣는다(새 스킬은 비워도 된다).
 * 서버 호출(skillApi)만 가짜로 두고 화면 · 말은 그대로. 편집기는 시험용 편집기(editor/__mocks__)다.
 */
vi.mock('@/shared/api/endpoints', async (importOriginal) => {
  const actual = await importOriginal<typeof Endpoints>();
  return {
    ...actual,
    skillApi: {
      ...actual.skillApi,
      list: vi.fn(),
      detail: vi.fn(),
      revisions: vi.fn(),
      revision: vi.fn(),
      diff: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      remove: vi.fn(),
      undelete: vi.fn(),
      restoreRevision: vi.fn(),
      importFile: vi.fn(),
    },
  };
});

const ADMIN = { id: 'u0', name: '시스템 관리자' };

function summary(extra: Partial<SkillSummary> = {}): SkillSummary {
  return {
    name: 'moai-works-issues',
    description: 'moai-works 의 일감을 MCP 로 다루는 방법.\n둘째 줄',
    revisionNo: 3,
    updatedAt: '2026-11-24T01:02:03.004Z',
    updatedBy: ADMIN,
    deleted: false,
    deletedAt: null,
    deletedBy: null,
    ...extra,
  };
}

function detail(extra: Partial<SkillDetail> = {}): SkillDetail {
  return {
    ...summary(),
    body: '# 일감 다루기\n\n본문',
    createdAt: '2026-11-20T00:00:00Z',
    createdBy: ADMIN,
    ...extra,
  };
}

function item(no: number, extra: Partial<SkillRevisionItem> = {}): SkillRevisionItem {
  return {
    no,
    kind: no === 1 ? 'CREATE' : 'UPDATE',
    restoredFrom: null,
    changedFields: no === 1 ? ['description', 'body'] : ['body'],
    changeNote: null,
    actorType: 'USER',
    actorId: 'u0',
    actorName: '시스템 관리자',
    via: 'WEB',
    createdAt: `2026-11-2${no}T00:03:04.00${no}Z`,
    current: no === 3,
    ...extra,
  };
}

const items = [item(3, { kind: 'RESTORE', restoredFrom: 1, changeNote: '처음 규칙으로' }), item(2), item(1)];

const NOTE_LABEL = '바뀐 점 요약 (Agent 에게 보임)';
const NOTE_REQUIRED = '바뀐 점 요약을 5자 이상 넣으십시오 — Agent 가 무엇이 바뀌었는지 보는 글입니다.';

function diff(no: number, from: number | null): SkillRevisionDiff {
  return {
    from,
    to: no,
    fromInfo: from ? (items.find((i) => i.no === from) ?? null) : null,
    toInfo: items.find((i) => i.no === no) as SkillRevisionItem,
    fields: [{ field: 'description', before: '옛 설명', after: '새 설명' }],
    bodyLines: [
      { op: 'SAME', text: '# 일감 다루기', oldLine: 1, newLine: 1 },
      { op: 'DEL', text: '옛 줄', oldLine: 2, newLine: null },
      { op: 'ADD', text: '새 줄', oldLine: null, newLine: 2 },
    ],
    added: 1,
    removed: 1,
    coarse: false,
  };
}

function Location() {
  const location = useLocation();
  return <p data-testid="location">{location.pathname + location.search}</p>;
}

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <AppearanceProvider>
      <QueryClientProvider client={client}>
        <ConfirmProvider>
          <MemoryRouter initialEntries={[path]}>
            <Routes>
              <Route path="/admin/skills" element={<SkillsPage />} />
              <Route path="/admin/skills/new" element={<NewSkillPage />} />
              <Route path="/admin/skills/view/:name" element={<SkillViewPage />} />
              <Route path="/admin/skills/edit/:name" element={<EditSkillPage />} />
            </Routes>
            <Location />
          </MemoryRouter>
        </ConfirmProvider>
      </QueryClientProvider>
    </AppearanceProvider>,
  );
}

/** 편집 칸에 글을 붙여넣는다 (시험용 편집기는 붙여넣은 글을 끝에 붙인다) */
function pasteText(target: Element, text: string) {
  const event = new Event('paste', { bubbles: true, cancelable: true });
  Object.defineProperty(event, 'clipboardData', { value: { files: [], types: ['text/plain'], getData: () => text } });
  act(() => {
    target.dispatchEvent(event);
  });
}

beforeAll(() => {
  // jsdom 에는 없다 — 좁은 화면에서 고른 리비전의 차이로 내려 줄 때 부른다
  Element.prototype.scrollIntoView = vi.fn();
});

afterEach(() => {
  vi.clearAllMocks();
});

describe('스킬 목록', () => {
  it('이름 · 설명(한 줄로 줄임) · 리비전 · 수정한 사람을 보이고, 삭제한 스킬도 보기는 주소에 남는다', async () => {
    vi.mocked(skillApi.list).mockImplementation(async (includeDeleted) =>
      includeDeleted ? [summary(), summary({ name: 'old-skill', deleted: true, deletedAt: '2026-11-24T00:00:00Z', deletedBy: ADMIN })] : [summary()],
    );
    renderAt('/admin/skills');

    const table = await screen.findByRole('table');
    expect(skillApi.list).toHaveBeenCalledWith(false);
    const row = within(table).getAllByRole('row')[1] as HTMLElement;
    expect(within(row).getByRole('link', { name: 'moai-works-issues' })).toHaveAttribute('href', '/admin/skills/view/moai-works-issues');
    expect(within(row).getByText('moai-works 의 일감을 MCP 로 다루는 방법. 둘째 줄')).toBeInTheDocument();
    expect(within(row).getByText('3')).toBeInTheDocument();
    expect(within(row).getByText('시스템 관리자')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('switch', { name: '삭제한 스킬도 보기' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/admin/skills?deleted=1');
    await waitFor(() => expect(skillApi.list).toHaveBeenCalledWith(true));
    const deletedRow = (await screen.findByText('old-skill')).closest('tr') as HTMLElement;
    expect(within(deletedRow).getByText('삭제함')).toBeInTheDocument();
    // 삭제한 스킬은 되살리기(수정은 없다)
    fireEvent.click(within(deletedRow).getByRole('button', { name: 'old-skill 동작' }));
    const menu = await screen.findByRole('menu');
    expect(within(menu).queryByRole('menuitem', { name: '수정' })).not.toBeInTheDocument();
    vi.mocked(skillApi.undelete).mockResolvedValue(detail({ name: 'old-skill' }));
    fireEvent.click(within(menu).getByRole('menuitem', { name: '되살리기' }));
    await waitFor(() => expect(skillApi.undelete).toHaveBeenCalledWith('old-skill'));
  });

  it('삭제는 확인을 받고(리비전은 남고 되살릴 수 있다는 안내), 취소하면 부르지 않는다', async () => {
    vi.mocked(skillApi.list).mockResolvedValue([summary()]);
    vi.mocked(skillApi.remove).mockResolvedValue(detail({ deleted: true }));
    renderAt('/admin/skills');

    fireEvent.click(await screen.findByRole('button', { name: 'moai-works-issues 동작' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: '삭제' }));
    let dialog = await screen.findByRole('dialog', { name: 'moai-works-issues 스킬을 삭제할까요?' });
    expect(dialog).toHaveTextContent('리비전은 그대로 남고, 언제든 되살릴 수 있습니다.');
    fireEvent.click(within(dialog).getByRole('button', { name: '취소' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(skillApi.remove).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'moai-works-issues 동작' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: '삭제' }));
    dialog = await screen.findByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: '삭제' }));
    await waitFor(() => expect(skillApi.remove).toHaveBeenCalledWith('moai-works-issues'));
  });

  it('SKILL.md 가져오기 — 바뀐 점 요약을 꼭 받고, 같은 이름이 있으면 한 번 묻고 그 리비전(baseRevisionNo)으로 다시 보낸다', async () => {
    vi.mocked(skillApi.list).mockResolvedValue([summary()]);
    vi.mocked(skillApi.importFile)
      .mockRejectedValueOnce(new ApiError(409, 'SKILL_EXISTS', '같은 이름의 스킬이 있습니다.', [], { name: 'moai-works-issues', revisionNo: 3 }))
      .mockResolvedValueOnce({ created: false, skill: detail({ revisionNo: 4 }) });
    renderAt('/admin/skills');

    fireEvent.click(await screen.findByRole('button', { name: 'SKILL.md 가져오기' }));
    const dialog = await screen.findByRole('dialog', { name: 'SKILL.md 가져오기' });
    const content = '---\nname: moai-works-issues\ndescription: 설명\n---\n\n# 본문\n';
    fireEvent.change(within(dialog).getByRole('textbox', { name: 'SKILL.md 내용' }), { target: { value: content } });
    // 요약 없이는 보내지 않는다(5자 미만도)
    const note = within(dialog).getByRole('textbox', { name: NOTE_LABEL });
    expect(note).toBeRequired();
    fireEvent.click(within(dialog).getByRole('button', { name: '가져오기' }));
    expect(await within(dialog).findByText(NOTE_REQUIRED)).toBeInTheDocument();
    fireEvent.change(note, { target: { value: '짧음' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '가져오기' }));
    expect(await within(dialog).findByText(NOTE_REQUIRED)).toBeInTheDocument();
    expect(skillApi.importFile).not.toHaveBeenCalled();

    fireEvent.change(note, { target: { value: '  파일로 규칙을 고침 ' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '가져오기' }));
    await waitFor(() => expect(skillApi.importFile).toHaveBeenCalledWith(content, undefined, '파일로 규칙을 고침'));
    expect(await within(dialog).findByText(/moai-works-issues 스킬\(리비전 3\)이 이미 있습니다/)).toBeInTheDocument();

    fireEvent.click(within(dialog).getByRole('button', { name: '이 내용으로 수정' }));
    await waitFor(() => expect(skillApi.importFile).toHaveBeenLastCalledWith(content, 3, '파일로 규칙을 고침'));
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/admin/skills/view/moai-works-issues'));
  });
});

describe('새 스킬 · 수정', () => {
  it('이름 규칙은 저장 전에 알리고, 맞으면 이름 · 설명 · 본문 · 바뀐 점 요약으로 만든다(요약은 비워도 된다)', async () => {
    vi.mocked(skillApi.create).mockResolvedValue(detail({ name: 'my-skill', revisionNo: 1 }));
    renderAt('/admin/skills/new');

    const name = await screen.findByRole('textbox', { name: '이름' });
    fireEvent.change(name, { target: { value: 'My_Skill' } });
    fireEvent.change(screen.getByRole('textbox', { name: '설명' }), { target: { value: '언제 쓰는지' } });
    expect(screen.getByText('6 / 1024')).toBeInTheDocument();
    pasteText(await screen.findByTestId('rich-editor'), '# 지침');
    fireEvent.click(screen.getByRole('button', { name: '저장' }));
    expect(await screen.findByText(/영문 소문자 · 숫자 · 하이픈만 씁니다/)).toBeInTheDocument();
    expect(skillApi.create).not.toHaveBeenCalled();

    fireEvent.change(name, { target: { value: 'my-skill' } });
    // 새 스킬의 요약은 비워도 된다(서버가 "처음 만듦") — 넣으면 같은 규칙
    expect(screen.getByRole('textbox', { name: NOTE_LABEL })).not.toBeRequired();
    expect(screen.getByText(/비우면 "처음 만듦"으로 남습니다/)).toBeInTheDocument();
    fireEvent.change(screen.getByRole('textbox', { name: NOTE_LABEL }), { target: { value: '처음 등록' } });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));
    await waitFor(() =>
      expect(skillApi.create).toHaveBeenCalledWith({ name: 'my-skill', description: '언제 쓰는지', body: expect.stringContaining('# 지침'), changeNote: '처음 등록' }),
    );
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/admin/skills/view/my-skill'));
  });

  it('수정은 이름을 바꾸지 않고 읽은 리비전(baseRevisionNo) · 바뀐 점 요약(꼭)과 함께 보내며, 그 사이 다른 사람이 고쳤으면 다시 불러오라고 알린다', async () => {
    vi.mocked(skillApi.detail).mockResolvedValue(detail());
    vi.mocked(skillApi.update).mockRejectedValue(new ApiError(409, 'SKILL_CHANGED', '그 사이 다른 사람이 고쳤습니다.'));
    renderAt('/admin/skills/edit/moai-works-issues');

    expect(await screen.findByText('이름은 만든 뒤 바꿀 수 없습니다.')).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: '이름' })).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole('textbox', { name: '설명' }), { target: { value: '고친 설명' } });
    expect(screen.getByText('저장 전')).toBeInTheDocument();
    // 바뀐 점 요약 없이는 저장하지 않는다 — Agent 에게 보인다는 안내와 함께
    const note = screen.getByRole('textbox', { name: NOTE_LABEL });
    expect(note).toBeRequired();
    expect(screen.getByText(/Agent 가 이 스킬을 새로 받을 때 무엇이 바뀌었는지 이 글로 봅니다/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '저장' }));
    expect(await screen.findByText(NOTE_REQUIRED)).toBeInTheDocument();
    expect(skillApi.update).not.toHaveBeenCalled();

    fireEvent.change(note, { target: { value: '설명을 더 분명히' } });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));
    await waitFor(() =>
      expect(skillApi.update).toHaveBeenCalledWith(
        'moai-works-issues',
        expect.objectContaining({ description: '고친 설명', baseRevisionNo: 3, changeNote: '설명을 더 분명히' }),
      ),
    );
    expect(await screen.findByText('다른 사람이 먼저 수정했습니다')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '저장' })).toBeDisabled();
  });
});

describe('스킬 보기의 리비전 탭', () => {
  it('주소(?tab=revisions&rev=3&from=1)로 열면 타임라인(한 일 · 바꾼 까닭 · 밀리초 시각)과 두 리비전의 차이를 그린다', async () => {
    vi.mocked(skillApi.detail).mockResolvedValue(detail());
    vi.mocked(skillApi.revisions).mockResolvedValue({ items, next: null });
    vi.mocked(skillApi.diff).mockImplementation(async (_name, no, from) => diff(no, from ?? no - 1));
    renderAt('/admin/skills/view/moai-works-issues?tab=revisions&rev=3&from=1');

    const tab = await screen.findByRole('tab', { name: '리비전 3' });
    expect(tab).toHaveAttribute('aria-selected', 'true');
    const timeline = await screen.findByRole('list', { name: '리비전 타임라인' });
    const entries = within(timeline).getAllByRole('listitem');
    expect(entries).toHaveLength(3);
    expect(within(entries[0] as HTMLElement).getByText('현재 리비전')).toBeInTheDocument();
    expect(within(entries[0] as HTMLElement).getByText('리비전 1(으)로 되돌림')).toBeInTheDocument();
    expect(within(entries[0] as HTMLElement).getByText('바뀐 점: 처음 규칙으로')).toBeInTheDocument();
    expect(within(entries[1] as HTMLElement).getByText('본문')).toBeInTheDocument();
    expect(within(entries[2] as HTMLElement).getByText('만듦')).toBeInTheDocument();
    expect(within(entries[2] as HTMLElement).getByText(/2026-11-21 09:03:04\.001/)).toBeInTheDocument();

    await waitFor(() => expect(skillApi.diff).toHaveBeenCalledWith('moai-works-issues', 3, 1));
    expect(await screen.findByText('리비전 1 → 리비전 3 · 바뀐 항목 1 · 본문 넣은 줄 1 · 지운 줄 1')).toBeInTheDocument();
    expect(screen.getByText('옛 설명')).toBeInTheDocument();
    expect(screen.getByText('새 설명')).toBeInTheDocument();
    const table = screen.getByRole('table', { name: '두 리비전의 본문 차이' });
    expect(within(table).getByLabelText('지운 줄')).toHaveTextContent('−');
    expect(within(table).getByLabelText('넣은 줄')).toHaveTextContent('+');
    // 지금 리비전은 되돌릴 것이 없다
    expect(screen.queryByRole('button', { name: '이 리비전으로 되돌리기' })).not.toBeInTheDocument();
  });

  it('옛 리비전을 고르면 "이 리비전으로 되돌리기" — 확인을 받고 읽은 리비전 · 바뀐 점 요약(꼭)과 함께 보낸다', async () => {
    vi.mocked(skillApi.detail).mockResolvedValue(detail());
    vi.mocked(skillApi.revisions).mockResolvedValue({ items, next: null });
    vi.mocked(skillApi.diff).mockImplementation(async (_name, no, from) => diff(no, from ?? no - 1));
    vi.mocked(skillApi.restoreRevision).mockResolvedValue(detail({ revisionNo: 4 }));
    renderAt('/admin/skills/view/moai-works-issues?tab=revisions');

    const timeline = await screen.findByRole('list', { name: '리비전 타임라인' });
    fireEvent.click(within(timeline).getByText('리비전 2'));
    expect(screen.getByTestId('location')).toHaveTextContent('rev=2');
    fireEvent.click(await screen.findByRole('button', { name: '이 리비전으로 되돌리기' }));
    const dialog = await screen.findByRole('dialog', { name: '리비전 2(으)로 되돌릴까요?' });
    expect(dialog).toHaveTextContent('새 리비전 4을(를) 만듭니다');
    fireEvent.click(within(dialog).getByRole('button', { name: '취소' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(skillApi.restoreRevision).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: '이 리비전으로 되돌리기' }));
    const again = await screen.findByRole('dialog');
    const note = within(again).getByRole('textbox', { name: NOTE_LABEL });
    expect(note).toBeRequired();
    fireEvent.click(within(again).getByRole('button', { name: '되돌리기' }));
    expect(await within(again).findByText(NOTE_REQUIRED)).toBeInTheDocument();
    expect(skillApi.restoreRevision).not.toHaveBeenCalled();
    fireEvent.change(note, { target: { value: '앞 규칙이 맞음' } });
    fireEvent.click(within(again).getByRole('button', { name: '되돌리기' }));
    await waitFor(() => expect(skillApi.restoreRevision).toHaveBeenCalledWith('moai-works-issues', 2, 3, '앞 규칙이 맞음'));
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('rev=4'));
  });

  it('삭제한 스킬은 안내와 되살리기를 보이고, 수정 · 되돌리기는 없다', async () => {
    vi.mocked(skillApi.detail).mockResolvedValue(detail({ deleted: true, deletedAt: '2026-11-24T00:00:00Z', deletedBy: ADMIN }));
    renderAt('/admin/skills/view/moai-works-issues');

    expect(await screen.findByText(/목록과 MCP 에 보이지 않습니다/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '되살리기' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '수정' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '삭제' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'SKILL.md 내려받기' })).toHaveAttribute('href', '/api/v1/skills/moai-works-issues/SKILL.md');
  });
});
