import { notificationGroups, type NotificationPreference } from '@/shared/api/types';

/** 알림 설정 화면의 묶음 하나 */
export interface PreferenceGroup {
  group: string;
  items: NotificationPreference[];
}

/**
 * 받기 설정을 묶음(group)별로 — 워크스페이스 · 프로젝트 · 일감 · 작업 시간 · 보안 순, 서버가 새로 더한 묶음은 그 뒤에 나온 순서대로.
 * 묶음 안의 순서는 서버가 준 대로(api NotificationKind 순서). 빈 묶음은 빼낸다.
 */
export function groupPreferences(list: readonly NotificationPreference[]): PreferenceGroup[] {
  const byGroup = new Map<string, NotificationPreference[]>();
  for (const group of notificationGroups) {
    byGroup.set(group, []);
  }
  for (const pref of list) {
    const items = byGroup.get(pref.group);
    if (items) {
      items.push(pref);
    } else {
      byGroup.set(pref.group, [pref]);
    }
  }
  return [...byGroup.entries()].filter(([, items]) => items.length > 0).map(([group, items]) => ({ group, items }));
}

/** 끌 수 없는 종류인가 — 서버가 정한다(mandatory). 화면은 잠금 표시만 한다 */
export function preferenceLocked(pref: Pick<NotificationPreference, 'mandatory'>): boolean {
  return pref.mandatory;
}

/** 스위치가 켜져 보이는가 — 끌 수 없는 종류는 늘 켜짐 */
export function preferenceChecked(pref: Pick<NotificationPreference, 'mandatory' | 'enabled'>): boolean {
  return pref.mandatory || pref.enabled;
}

/** 메일 스위치를 고를 수 없는가 — 알림함에서 끈 종류는 알림이 생기지 않아 메일도 가지 않는다(서버와 같은 규칙) */
export function emailLocked(pref: Pick<NotificationPreference, 'mandatory' | 'enabled'>): boolean {
  return !preferenceChecked(pref);
}

/** 메일 스위치가 켜져 보이는가 — 알림함에서 끈 종류는 메일 설정이 남아 있어도 꺼져 보인다 */
export function emailChecked(pref: Pick<NotificationPreference, 'mandatory' | 'enabled' | 'email'>): boolean {
  return preferenceChecked(pref) && pref.email;
}
