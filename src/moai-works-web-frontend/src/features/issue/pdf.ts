/**
 * PDF 열기(pdf.js) — 첨부 보기의 PDF 화면(PdfView)만 쓰고, 그 화면과 함께 따로 받는다(lazy).
 *
 * 화면 보안 정책(CSP `script-src 'self'` · `connect-src 'self'`) 안에서 돈다:
 *   - worker 는 같은 출처의 빌드 파일(Vite `?url`) — blob: worker 를 만들지 않는다(worker-src 는 script-src 를 따른다)
 *   - `useWasm: false` — WebAssembly 를 쓰지 않아 `'wasm-unsafe-eval'` 이 필요 없다. JPEG 2000 · JBIG2 그림은 pdf.js 의 JS 판(…_nowasm_fallback.js)으로 푼다.
 *     pdf.js 6 에는 eval 쓰는 길(옛 `isEvalSupported`)이 없다 — PostScript 함수도 JS 로 푼다
 *   - 한중일 글꼴의 CMap · 표준 글꼴 · JS 판 디코더는 vite.config.ts 의 pdfjsAssets 가 같은 출처 `assets/pdfjs-<판>/` 에 둔다
 *   - 스크립트(PDF 안의 JavaScript) · XFA 양식은 켜지 않는다(기본값 그대로 꺼짐)
 */
import { getDocument, GlobalWorkerOptions, version, type PDFDocumentLoadingTask } from 'pdfjs-dist';
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';

GlobalWorkerOptions.workerSrc = workerUrl;

const assets = `${import.meta.env.BASE_URL}assets/pdfjs-${version}/`;

export function openPdf(data: Uint8Array): PDFDocumentLoadingTask {
  return getDocument({
    data,
    useWasm: false,
    wasmUrl: `${assets}wasm/`,
    cMapUrl: `${assets}cmaps/`,
    cMapPacked: true,
    standardFontDataUrl: `${assets}standard_fonts/`,
    enableXfa: false,
  });
}
