import 'pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css';
// 언어 리소스를 먼저 올린다 — 첫 화면부터 저장된 언어로 그린다
import '@/shared/i18n/i18n';

import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import { App } from '@/app/App';
import { listenForStaleChunks } from '@/shared/lib/chunk-reload';

// 새 판이 나와 옛 조각 파일이 없어지면 한 번 새로 고친다
listenForStaleChunks();

const root = document.getElementById('root');
if (!root) {
  throw new Error('#root 가 없습니다');
}
createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
