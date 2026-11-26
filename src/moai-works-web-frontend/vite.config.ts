import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { Agent } from 'node:https';
import { fileURLToPath, URL } from 'node:url';

import react from '@vitejs/plugin-react';
import type { Plugin } from 'vite';
import { defineConfig } from 'vitest/config';

// 개발: vite(https 11003) → /api 는 web-backend(BFF, https 11002) 로. 쿠키(세션 · XSRF)는 localhost 에 붙으므로 그대로 동작한다.
// 빌드: web-backend 의 static 폴더로 바로 넣는다 → web-backend 가 정적 파일 + /api 를 함께 서비스.

// 모든 서비스는 HTTPS 로 연다. 로컬 인증서는 infra/tls/make-certs.sh 가 만든다(git 제외).
const certs = fileURLToPath(new URL('../../infra/tls/certs/', import.meta.url));
const tls = existsSync(`${certs}localhost.pem`)
  ? {
      cert: readFileSync(`${certs}localhost.pem`),
      key: readFileSync(`${certs}localhost-key.pem`),
      // web-backend 의 인증서를 발급한 로컬 인증 기관 — 넘겨줄 때 인증서를 확인한다(확인을 끄지 않는다)
      ca: readFileSync(`${certs}ca.pem`),
    }
  : undefined;

/**
 * pdf.js 가 PDF 를 그리는 중에 받는 파일 — 한중일 글꼴의 CMap · 표준 글꼴 · WebAssembly 대신 쓰는 JS 판 디코더(JPEG 2000 · JBIG2).
 * 바깥 CDN 이 아니라 같은 출처에서 받게(CSP connect-src · script-src 'self') 빌드의 `assets/pdfjs-<판>/` 에 넣고, 개발 서버도 같은 주소로 준다.
 * 판이 주소에 들어 있어 web-backend 의 /assets 1년 캐시를 그대로 쓴다(src/features/issue/pdf.ts 가 이 주소를 쓴다).
 */
function pdfjsAssets(): Plugin {
  const root = fileURLToPath(new URL('./node_modules/pdfjs-dist/', import.meta.url));
  const { version } = JSON.parse(readFileSync(`${root}package.json`, 'utf8')) as { version: string };
  const base = `assets/pdfjs-${version}/`;
  const files = () => [
    ...readdirSync(`${root}cmaps`).map((name) => `cmaps/${name}`),
    ...readdirSync(`${root}standard_fonts`).map((name) => `standard_fonts/${name}`),
    'wasm/openjpeg_nowasm_fallback.js',
    'wasm/jbig2_nowasm_fallback.js',
  ];
  return {
    name: 'pdfjs-assets',
    configureServer(server) {
      const known = new Set(files());
      server.middlewares.use((req, res, next) => {
        const path = decodeURIComponent((req.url ?? '').split('?')[0] ?? '');
        const file = path.startsWith(`/${base}`) ? path.slice(base.length + 1) : '';
        if (!known.has(file)) {
          next();
          return;
        }
        res.setHeader('Content-Type', file.endsWith('.js') ? 'text/javascript' : 'application/octet-stream');
        res.end(readFileSync(`${root}${file}`));
      });
    },
    generateBundle() {
      for (const file of files()) {
        this.emitFile({ type: 'asset', fileName: `${base}${file}`, source: readFileSync(`${root}${file}`) });
      }
    },
  };
}

export default defineConfig(({ command }) => {
  if (command === 'serve' && !tls && !process.env.VITEST) {
    throw new Error('로컬 TLS 인증서가 없습니다 — 저장소 맨 위에서 infra/tls/make-certs.sh 를 먼저 돌리십시오.');
  }
  return {
  plugins: [react(), pdfjsAssets()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 11003,
    https: tls && { cert: tls.cert, key: tls.key },
    proxy: {
      '/api': {
        target: 'https://localhost:11002',
        changeOrigin: false,
        secure: true,
        agent: tls && new Agent({ ca: tls.ca }),
      },
      // MCP 연결 주소(/mcp/issue · /mcp/project, 첨부 티켓 /mcp/files) — 화면이 보여 주는 연결 안내가 개발 서버에서도 그대로 통하게 web-backend 로 넘긴다
      '/mcp': {
        target: 'https://localhost:11002',
        changeOrigin: false,
        secure: true,
        agent: tls && new Agent({ ca: tls.ca }),
      },
    },
  },
  build: {
    outDir: '../moai-works-web-backend/src/main/resources/static',
    emptyOutDir: true,
    sourcemap: false,
    // 화면은 경로 단위로 나뉜다(src/app/routes.tsx 의 page). 라이브러리는 바뀌는 주기가 달라서 따로 묶는다 —
    // 화면 코드만 고친 배포에서는 브라우저가 라이브러리 조각을 다시 받지 않는다.
    rolldownOptions: {
      output: {
        codeSplitting: {
          groups: [
            { name: 'react', test: /node_modules[\\/](react|react-dom|react-router|scheduler)[\\/]/, priority: 30 },
          ],
        },
      },
    },
    // 가장 큰 조각(mui)이 이 값을 넘으면 경고한다
    chunkSizeWarningLimit: 600,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test-setup.ts'],
  },
  };
});
