/**
 * 마지막 입력 뒤 글(getMarkdown → 원문 보존)을 내놓기까지 기다리는 시간 — 글이 클수록 길게.
 * 3만 자 문서를 한 번 적는 데 100ms 쯤 들어서, 큰 글은 쓰다 잠깐 멈출 때마다 적지 않게 더 기다린다.
 * 저장 · 원문 전환 · 칸을 떠날 때의 flush 는 기다리지 않는다. moai-wiki 1232b13 과 같은 값이다.
 *
 * @param size 문서 크기(ProseMirror doc.content.size — 글자 수에 가깝다)
 */
export function syncDelay(size: number): number {
  if (size < 8_000) {
    return 120;
  }
  if (size < 20_000) {
    return 300;
  }
  return 600;
}
