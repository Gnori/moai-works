import '@testing-library/jest-dom/vitest';
import { vi } from 'vitest';

// 쓰면서 바로 그려지는 편집기(Milkdown)는 jsdom 에서 돌지 않는다 — 같은 손잡이를 흉내 내는 시험용 편집기로 바꾼다(editor/__mocks__/RichEditor.tsx)
vi.mock('@/features/issue/editor/RichEditor');
