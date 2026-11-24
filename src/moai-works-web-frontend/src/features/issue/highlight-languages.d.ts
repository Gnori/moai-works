/** highlight.js 의 언어 하나(`highlight.js/lib/languages/xxx`) — 패키지가 이 경로의 타입을 싣지 않는다 */
declare module 'highlight.js/lib/languages/*' {
  import type { LanguageFn } from 'lowlight';

  const language: LanguageFn;
  export default language;
}
