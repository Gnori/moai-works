# moai-works-web-frontend

moai-works 의 웹 화면(React). 빌드 결과는 `../moai-works-web-backend/src/main/resources/static/` 으로 들어가고, web-backend 가 정적 파일과 `/api` 를 함께 서비스한다. 화면의 권한 확인은 표시만 하고, 판정은 api 가 한다. 지금은 로그인 · 가입 · 워크스페이스 · 멤버 · 역할 · 초대 · 감사 · 시스템 관리 화면이 있고, 프로젝트 · 일감 화면은 계획이다.

| 항목 | 값 |
|---|---|
| 스택 | React 19.3 · React Router 8.5 · MUI 9.5 · TanStack Query 5 · i18next 27.2 · Vite 8 · TypeScript 6.0 · Vitest 5 |
| Node | 22 이상 |
| 포트 | 11003 (개발 서버, **HTTPS**. `/api` 는 web-backend https 11002 로. 로컬 인증서: 저장소 맨 위에서 `infra/tls/make-certs.sh`) |
| 비밀값 | 없음 |

## 빠른 실행

```bash
npm install
npm run dev        # https://localhost:11003
bin/startup.sh     # 같은 개발 서버를 백그라운드로 (끄기 bin/shutdown.sh, 출력은 logs/ 에 날짜별로)
npm run build      # 타입 검사 + web-backend static 으로 빌드
```

## 검사

```bash
npm run lint && npm run typecheck && npm test && npm run build    # npm test = Vitest 60건
```

화면을 바꿨으면 데스크톱과 휴대폰 너비(390px)에서 모두 확인한다.

자세한 문서: [docs/modules/03-web-frontend.md](../../docs/modules/03-web-frontend.md) · 화면 규칙: [WEB-DESIGN.md](../../WEB-DESIGN.md) · 화면 보안: [SECURITY.md](../../SECURITY.md) · 개발 규칙: [DEVELOPE.md](../../DEVELOPE.md)
