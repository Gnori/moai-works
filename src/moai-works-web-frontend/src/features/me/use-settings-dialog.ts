import { useLocation, useNavigate } from 'react-router';

import { readSettingsSection, withSettings, type SettingsSectionKey } from '@/app/routes';

/** 대화상자를 연 이동에 남기는 표시 — 닫을 때 뒤로 가면 연 화면으로 돌아간다 */
interface SettingsState {
  settingsDialog?: boolean;
}

/**
 * 개인 설정 대화상자의 상태 — 주소의 ?settings=<칸> 이 정본이다(어느 화면에서든 같은 화면 위에 열린다).
 * - 열기: 주소에 넣는다(새 기록 — 뒤로 가기로 닫힌다)
 * - 칸 바꾸기: 주소의 값만 바꾼다(기록을 늘리지 않는다 — 뒤로 가기가 칸마다 걸리지 않게)
 * - 닫기: 앱 안에서 열었으면 뒤로(연 화면 그대로), 주소로 바로 들어왔으면 값만 뺀다
 */
export function useSettingsDialog() {
  const location = useLocation();
  const navigate = useNavigate();
  const section = readSettingsSection(new URLSearchParams(location.search));
  const openedHere = (location.state as SettingsState | null)?.settingsDialog === true;
  const to = (next: SettingsSectionKey | null) => ({ pathname: location.pathname, search: withSettings(location.search, next), hash: location.hash });

  return {
    /** 열린 칸 — 닫혀 있으면 null */
    section,
    open: (next: SettingsSectionKey = 'profile') => {
      void navigate(to(next), { state: { settingsDialog: true } satisfies SettingsState });
    },
    select: (next: SettingsSectionKey) => {
      void navigate(to(next), { replace: true, state: location.state });
    },
    close: () => {
      if (openedHere) {
        void navigate(-1);
      } else {
        void navigate(to(null), { replace: true });
      }
    },
  };
}
