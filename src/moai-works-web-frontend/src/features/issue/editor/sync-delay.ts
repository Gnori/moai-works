/**
 * 마지막 입력 뒤 글(Markdown 으로 적기 → 원문 보존)을 내놓기까지 기다리는 시간 — 글이 클수록 길게.
 * 처음에는 3만 자 문서를 한 번 적는 데 100ms 쯤 들어서 큰 글은 더 기다리게 했다. 일감 #16 뒤로는 바뀐 덩어리만 적고 나눠
 * 멈출 때의 일이 몇 ms 라 기다림의 몫이 작지만, 값은 moai-wiki 1232b13 과 같게 둔다(바꾸려면 위키와 함께).
 * 저장 · 원문 전환 · 칸을 떠날 때의 flush 는 기다리지 않는다.
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
