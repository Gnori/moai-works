import { useSyncExternalStore } from 'react';

/**
 * 사람이 화면에서 손을 놓았는지 — 저절로 서버에 묻는 요청(알림 수 등)을 멈추는 데 쓴다.
 *
 * 로그인은 "쓰지 않으면 1시간"에 끝난다(Keycloak 렐름, SECURITY §4.2). 화면이 저절로 묻는 요청도 접근 토큰을 갱신하므로,
 * 창만 열어 두면 로그인이 영영 "쓰는 중"으로 잡힌다. 그래서 사람이 IDLE_MS 동안 누르기 · 키 · 굴리기 · 터치 · 마우스 움직임 ·
 * 창으로 돌아오기가 없으면 손을 놓은 것으로 보고, 다시 쓰면 바로 돌아온다(moai-wiki 와 같은 2분).
 */
export const IDLE_MS = 120_000;
/** 손을 놓았는지 이만큼마다 본다 — 멈춤은 최대 이만큼 늦을 수 있다 */
export const IDLE_CHECK_MS = 10_000;

const EVENTS = ['pointerdown', 'pointermove', 'keydown', 'wheel', 'touchstart', 'focus'] as const;

let lastActivity = Date.now();
let idle = false;
let timer: number | undefined;
const listeners = new Set<() => void>();

const emit = () => listeners.forEach((listener) => listener());

function markActive() {
  lastActivity = Date.now();
  if (idle) {
    idle = false;
    emit();
  }
}

function check() {
  if (!idle && Date.now() - lastActivity >= IDLE_MS) {
    idle = true;
    emit();
  }
}

function start() {
  // 처음 붙을 때는 막 화면을 연 것이라 쓰는 중으로 본다
  lastActivity = Date.now();
  idle = false;
  EVENTS.forEach((type) => window.addEventListener(type, markActive, { capture: true, passive: true }));
  timer = window.setInterval(check, IDLE_CHECK_MS);
}

function stop() {
  EVENTS.forEach((type) => window.removeEventListener(type, markActive, { capture: true }));
  window.clearInterval(timer);
  timer = undefined;
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  if (listeners.size === 1) start();
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0) stop();
  };
}

/** 사람이 IDLE_MS 동안 화면을 건드리지 않았으면 true. 다시 건드리면 바로 false */
export function useUserIdle(): boolean {
  return useSyncExternalStore(subscribe, () => idle, () => false);
}
