import { useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';

import { authApi } from '@/shared/api/endpoints';
import { isApiError } from '@/shared/api/http';
import type { Me } from '@/shared/api/types';
import { storageKeys, writeSetting } from '@/shared/lib/storage';

type SessionStatus = 'loading' | 'authenticated' | 'unauthenticated';

interface SessionValue {
  status: SessionStatus;
  /** 로그인한 사람 (authenticated 일 때만) */
  me: Me | null;
  signIn: (username: string, password: string) => Promise<Me>;
  signOut: () => Promise<void>;
  /** 내 정보 다시 읽기 (워크스페이스를 만들거나 가입한 뒤) */
  refresh: () => Promise<void>;
  /** 플랫폼 역할 (system-admin …) — 화면 표시용. 판정은 api 가 한다 */
  hasPlatformRole: (role: string) => boolean;
  /**
   * 사람이 직접 로그아웃했나(이 탭이든 다른 탭이든). 그렇다면 로그인 화면에 지금 주소(returnTo)를 달지 않는다 —
   * 같은 컴퓨터에서 다음 사람이 앞 사람이 보던 화면으로 가지 않게. 세션 만료(401)일 때만 돌아올 주소를 단다.
   */
  signedOutByUser: boolean;
}

export const SESSION_KEY = ['session'] as const;
const CHANNEL = 'moai-works-session';
const SessionContext = createContext<SessionValue | null>(null);

/** 다른 탭에 보내는 신호 — 사건 종류와 시각뿐(개인 정보 · 토큰 없음) */
interface SessionSignal {
  type: 'signed-in' | 'signed-out';
  at: number;
}

function isSignal(value: unknown): value is SessionSignal {
  return (
    typeof value === 'object' &&
    value !== null &&
    ((value as SessionSignal).type === 'signed-in' || (value as SessionSignal).type === 'signed-out')
  );
}

/** 이 사람의 캐시를 지운다 — 세션 쿼리는 지우지 않고 값만 바꾼다(지우면 공급자가 보고 있는 쿼리가 사라진다). */
export function resetUserData(queryClient: QueryClient, me: Me | null) {
  queryClient.setQueryData(SESSION_KEY, me);
  queryClient.removeQueries({ predicate: (q) => q.queryKey[0] !== SESSION_KEY[0] });
}

/**
 * 로그인 상태. 서버(BFF)가 정본이다 — 처음 열 때 /api/auth/session 을 묻고, 401 이면 로그인 안 됨.
 *
 * 보안 규칙
 * - 브라우저에는 토큰이 없다(세션 쿠키 HttpOnly). 내 정보도 메모리(쿼리 캐시)에만 두고 저장소에 쓰지 않는다
 * - 로그인 · 로그아웃은 다른 탭에도 알린다(BroadcastChannel, 없으면 storage 이벤트) — 한 탭에서 로그아웃하면 모두 로그아웃
 * - 다른 사람으로 로그인하면 이전 사람의 캐시를 모두 지운다
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const channel = useRef<BroadcastChannel | null>(null);
  const [signedOutByUser, setSignedOutByUser] = useState(false);

  const query = useQuery({
    queryKey: SESSION_KEY,
    queryFn: async () => {
      try {
        return await authApi.session();
      } catch (e) {
        if (isApiError(e) && (e.status === 401 || e.status === 403)) {
          return null;
        }
        throw e;
      }
    },
    staleTime: 60_000,
    retry: false,
  });

  const announce = useCallback((type: SessionSignal['type']) => {
    const signal: SessionSignal = { type, at: Date.now() };
    if (channel.current) {
      channel.current.postMessage(signal);
    } else {
      writeSetting(storageKeys.sessionSignal, JSON.stringify(signal));
    }
  }, []);

  useEffect(() => {
    const onSignal = (signal: unknown) => {
      if (!isSignal(signal)) {
        return;
      }
      if (signal.type === 'signed-out') {
        const wasSignedIn = !!queryClient.getQueryData(SESSION_KEY);
        setSignedOutByUser(true);
        resetUserData(queryClient, null);
        if (wasSignedIn) {
          toast.info(t('states.sessionEnded'));
        }
      } else {
        setSignedOutByUser(false);
        queryClient.removeQueries({ predicate: (q) => q.queryKey[0] !== SESSION_KEY[0] });
        void queryClient.invalidateQueries({ queryKey: SESSION_KEY });
      }
    };
    if (typeof BroadcastChannel !== 'undefined') {
      const bc = new BroadcastChannel(CHANNEL);
      bc.onmessage = (event: MessageEvent) => onSignal(event.data);
      channel.current = bc;
      return () => {
        bc.close();
        channel.current = null;
      };
    }
    const onStorage = (event: StorageEvent) => {
      if (event.key === storageKeys.sessionSignal && event.newValue) {
        try {
          onSignal(JSON.parse(event.newValue));
        } catch {
          // 알 수 없는 값은 무시
        }
      }
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, [queryClient, t]);

  const signIn = useCallback(
    async (username: string, password: string) => {
      const me = await authApi.signIn(username, password);
      setSignedOutByUser(false);
      resetUserData(queryClient, me);
      announce('signed-in');
      return me;
    },
    [queryClient, announce],
  );

  const signOut = useCallback(async () => {
    try {
      await authApi.signOut();
    } finally {
      setSignedOutByUser(true);
      resetUserData(queryClient, null);
      announce('signed-out');
    }
  }, [queryClient, announce]);

  const refresh = useCallback(async () => {
    await queryClient.invalidateQueries({ queryKey: SESSION_KEY });
  }, [queryClient]);

  const value = useMemo<SessionValue>(() => {
    const me = query.data ?? null;
    const status: SessionStatus = query.isPending ? 'loading' : me ? 'authenticated' : 'unauthenticated';
    return {
      status,
      me,
      signIn,
      signOut,
      refresh,
      hasPlatformRole: (role) => me?.platformRoles.includes(role) ?? false,
      signedOutByUser,
    };
  }, [query.data, query.isPending, signIn, signOut, refresh, signedOutByUser]);

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionValue {
  const value = useContext(SessionContext);
  if (!value) {
    throw new Error('useSession 은 SessionProvider 안에서만 쓸 수 있습니다');
  }
  return value;
}

/** 로그인한 사람 — RequireAuth 안쪽 화면에서만 쓴다 */
export function useMe(): Me {
  const { me } = useSession();
  if (!me) {
    throw new Error('로그인 정보가 없습니다 — RequireAuth 안에서 쓰십시오');
  }
  return me;
}
