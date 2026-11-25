import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';

import { notificationApi } from '@/shared/api/endpoints';
import { useUserIdle } from '@/shared/lib/use-user-idle';

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
 * 사람이 2분 동안 손을 놓으면 1분마다 묻기를 멈추고, 다시 쓰면 바로 한 번 읽는다 — 창만 열어 둔 화면이
 * 로그인을 늘려 "쓰지 않으면 1시간" 로그아웃이 오지 않던 것(SECURITY §4.2).
 * 시스템 관리자 계정에는 알림함이 없다(403) — 부르는 쪽이 종 모양을 그리지 않아 묻지도 않는다.
 */
export function useUnreadCountQuery(enabled = true) {
  const idle = useUserIdle();
  const query = useQuery({
    queryKey: notificationKeys.unread,
    queryFn: notificationApi.unreadCount,
    enabled,
    staleTime: 15_000,
    refetchInterval: idle ? false : UNREAD_REFRESH_MS,
    refetchOnWindowFocus: true,
    retry: false,
  });
  const { refetch } = query;
  const wasIdle = useRef(idle);
  useEffect(() => {
    // 손을 놓았다가 돌아오면 바로 한 번 — 종이 여럿이어도 같은 요청 하나로 합친다(cancelRefetch: false)
    if (wasIdle.current && !idle && enabled) void refetch({ cancelRefetch: false });
    wasIdle.current = idle;
  }, [idle, enabled, refetch]);
  return query;
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
