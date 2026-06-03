# moai-works-web

MOAI Works **프런트엔드** — React 19 + TypeScript + Vite.

> 🌱 골격만 구성됨. 아직 `package.json`·소스 없음.

## 구조 (예정)

```
moai-works-web/
├─ src/            # 컴포넌트·페이지·훅·API 클라이언트
├─ public/         # 정적 자산
├─ package.json    # ← 초기화 시 생성
├─ vite.config.ts
└─ README.md
```

## 초기화 방법 (착수 시)

```bash
# 본 디렉터리에서
npm create vite@latest . -- --template react-ts
npm install
npm run dev
```

UI 라이브러리(MUI 등)·라우터·상태관리·React Query 는 착수 시 결정.
