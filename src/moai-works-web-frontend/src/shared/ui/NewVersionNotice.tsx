import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';

import { APP_BUILD, VERSION_CHECK_MS, fetchServerBuild, isNewBuild } from '@/shared/lib/app-version';
import { useUserIdle } from '@/shared/lib/use-user-idle';

/** 알림의 id — 몇 번을 알아채도 하나만 떠 있다 */
const TOAST_ID = 'new-version';

/**
 * "새 버전이 있습니다 — 새로고침" — 새 화면이 올라왔는데 이 창이 옛 화면이면 화면 위에 남는 알림을 띄운다(닫기 전까지).
 * 판은 VERSION_CHECK_MS 마다 · 창으로 돌아올 때 묻는다. 사람이 손을 놓은 동안은 묻지 않고, 돌아오면 바로 한 번 묻는다.
 * 한 번 알렸으면(닫았어도) 더 묻지 않는다 — 새로고침하면 새 판으로 처음부터.
 * @param build 이 화면의 판(시험용) — 없으면 묻지 않는다(개발 서버)
 */
export function NewVersionNotice({ build = APP_BUILD }: { build?: string }) {
  const { t } = useTranslation('common');
  const idle = useUserIdle();
  const noticed = useRef(false);

  useEffect(() => {
    if (!build || idle || noticed.current) {
      return;
    }
    let alive = true;
    const check = async () => {
      if (noticed.current) {
        return;
      }
      const server = await fetchServerBuild();
      if (!alive || noticed.current || !isNewBuild(server, build)) {
        return;
      }
      noticed.current = true;
      toast.info(t('newVersion.title'), {
        id: TOAST_ID,
        description: t('newVersion.body'),
        duration: Infinity,
        action: { label: t('newVersion.reload'), onClick: () => window.location.reload() },
      });
    };
    const onVisible = () => {
      if (document.visibilityState === 'visible') {
        void check();
      }
    };
    // 손을 놓았다가 돌아온 때(이 효과가 다시 돈다)도 바로 한 번
    void check();
    const timer = window.setInterval(() => void check(), VERSION_CHECK_MS);
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      alive = false;
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [build, idle, t]);

  return null;
}
