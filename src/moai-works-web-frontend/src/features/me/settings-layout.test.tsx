import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';

import '@/shared/i18n/i18n';
import type { SettingsSection } from '@/app/routes';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { SettingsLayout } from './PersonalSettingsDialog';

/**
 * 개인 설정의 배치 — 창 폭이 휴대폰 ↔ 넓은 화면을 넘나들어도 칸의 내용이 다시 만들어지지 않는다.
 * 2026-11-23 버그: MCP 키를 받은 직후(원문이 한 번만 보이는 때) 휴대폰을 돌리면 내용이 다시 만들어져 원문 창이 닫혔다.
 */

/** 원문 창 대신 — 화면에만 있는 상태 */
function Counter() {
  const [n, setN] = useState(0);
  return (
    <button type="button" onClick={() => setN(n + 1)}>
      눌림 {n}
    </button>
  );
}

const section = { key: 'mcp-keys', element: <Counter />, nav: { label: 'myMcpKeys', icon: null }, legacyPath: '/me/mcp-keys' } as unknown as SettingsSection;

function view(phone: boolean) {
  return (
    <AppearanceProvider>
      <SettingsLayout phone={phone} sections={[section]} current={section} onSelect={() => undefined} onClose={() => undefined} />
    </AppearanceProvider>
  );
}

describe('개인 설정 배치', () => {
  it.each([
    ['넓은 화면 → 휴대폰', false],
    ['휴대폰 → 넓은 화면', true],
  ])('%s 으로 바뀌어도 칸 안의 상태가 남는다', async (_, phone) => {
    const { rerender } = render(view(phone));
    fireEvent.click(await screen.findByRole('button', { name: '눌림 0' }));
    fireEvent.click(screen.getByRole('button', { name: '눌림 1' }));
    rerender(view(!phone));
    expect(screen.getByRole('button', { name: '눌림 2' })).toBeInTheDocument();
    rerender(view(phone));
    expect(screen.getByRole('button', { name: '눌림 2' })).toBeInTheDocument();
  });

  it('휴대폰은 칸 탭, 넓은 화면은 세로 메뉴를 보인다', () => {
    const { rerender } = render(view(true));
    expect(screen.getByRole('tablist')).toBeInTheDocument();
    rerender(view(false));
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
    expect(screen.getByRole('navigation')).toBeInTheDocument();
  });
});
