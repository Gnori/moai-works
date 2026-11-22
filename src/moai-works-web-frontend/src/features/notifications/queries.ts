import { useInfiniteQuery, useQuery } from '@tanstack/react-query';

import { notificationApi } from '@/shared/api/endpoints';

/** 알림 화면이 쓰는 서버 상태의 키 (moai-wiki 와 같은 모양에 받기 설정을 더했다) */
export const notificationKeys = {
  /** 알림에 관한 것 전부 — 모두 읽음 뒤 한 번에 다시 읽는다 */
  all: ['notifications'] as const,
  unread: ['notifications', 'unread-count'] as const,
  /** 알림함 두 탭(전체 · 읽지 않음) — 읽음으로 한 뒤 함께 다시 읽는다 */
  inboxAll: ['notifications', 'inbox'] as const,
  inbox: (unreadOnly: boolean) => ['notifications', 'inbox', unreadOnly] as const,
  preferences: ['notifications', 'preferences'] as const,
};

/** 읽지 않은 수를 이만큼마다 다시 읽는다(창으로 돌아올 때도) */
export const UNREAD_REFRESH_MS = 60_000;
/** 알림함 한 쪽의 줄 수 (api 최대) */
export const INBOX_PAGE_SIZE = 30;

/**
 * 읽지 않은 알림의 수 — 종 모양 옆의 숫자. 1분마다, 그리고 창으로 돌아올 때 다시 읽는다.
 * 시스템 관리자 계정에는 알림함이 없다(403) — 부르는 쪽이 종 모양을 그리지 않아 묻지도 않는다.
 */
export function useUnreadCountQuery(enabled = true) {
  return useQuery({
    queryKey: notificationKeys.unread,
    queryFn: notificationApi.unreadCount,
    enabled,
    staleTime: 15_000,
    refetchInterval: UNREAD_REFRESH_MS,
    refetchOnWindowFocus: true,
    retry: false,
  });
}

/** 알림함 — 30개씩 이어 받는다. 다음 쪽은 앞 쪽의 next 를 그대로 before 로 */
export function useInboxQuery(unreadOnly: boolean) {
  return useInfiniteQuery({
    queryKey: notificationKeys.inbox(unreadOnly),
    queryFn: ({ pageParam }) => notificationApi.inbox({ unread: unreadOnly || undefined, before: pageParam, limit: INBOX_PAGE_SIZE }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.next ?? undefined,
    staleTime: 10_000,
    retry: false,
  });
}

/** 알림 받기 설정 — 모든 종류와 켬 · 끔 */
export function useNotificationPreferencesQuery() {
  return useQuery({
    queryKey: notificationKeys.preferences,
    queryFn: notificationApi.preferences,
    staleTime: 60_000,
    retry: false,
  });
}
