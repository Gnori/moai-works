import '@testing-library/jest-dom/vitest';
import { configure } from '@testing-library/react';
import { vi } from 'vitest';

// findBy · waitFor 가 기다리는 시간(기본 1초)을 3초로. 큰 MUI 화면(일감 수정 · 개인 설정)을 처음 그리고 *ByRole 로 찾는 데
// 혼자 돌면 0.4~0.7초지만, api 빌드나 다른 시험과 함께 돌면 1초를 넘겨 그때마다 다른 시험 하나가 실패했다(일감 #208 —
// 두 벌을 함께 돌려 4번 중 4번 재현). 기다림을 빠뜨린 단언이 아니라 느린 환경이 원인이라 기다리는 한도만 늘린다.
configure({ asyncUtilTimeout: 3000 });

// 쓰면서 바로 그려지는 편집기(Milkdown)는 jsdom 에서 돌지 않는다 — 같은 손잡이를 흉내 내는 시험용 편집기로 바꾼다(editor/__mocks__/RichEditor.tsx)
vi.mock('@/features/issue/editor/RichEditor');
