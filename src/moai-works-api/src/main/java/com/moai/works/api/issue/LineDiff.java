package com.moai.works.api.issue;

import java.util.ArrayList;
import java.util.List;

/**
 * 두 글의 줄 단위 차이 — 리비전 비교에 쓴다(FR-ISS-015 — moai-wiki 의 LineDiff 와 같다).
 *
 * <p>앞뒤의 같은 줄을 먼저 걷어 내고, 남은 가운데만 가장 긴 공통 줄(LCS)로 맞춘다. 사람이 고친 판끼리는 대개 가운데가 작다.
 * 가운데가 너무 크면(줄 수의 곱이 {@link #MAX_CELLS} 를 넘으면) 줄을 맞추지 않고 "옛 줄을 모두 지우고 새 줄을 모두 넣음"으로
 * 돌려준다 — 틀린 결과는 아니고 덜 촘촘할 뿐이다. 그래야 30,000자 설명 두 판의 비교가 늘 빨리 끝난다.
 */
public final class LineDiff {

  /** 가운데를 줄 단위로 맞출 때 쓰는 표의 최대 칸 수 (약 16MB) */
  static final long MAX_CELLS = 4_000_000L;

  /** 같은 줄 · 넣은 줄 · 지운 줄 */
  public enum Op {
    SAME,
    ADD,
    DEL
  }

  /**
   * @param oldLine 옛 글에서의 줄 번호(1부터). 넣은 줄이면 null
   * @param newLine 새 글에서의 줄 번호(1부터). 지운 줄이면 null
   */
  public record Line(Op op, String text, Integer oldLine, Integer newLine) {
  }

  /**
   * @param lines  옛 글 → 새 글로 가는 줄들 (화면에 그대로 차례로 그린다)
   * @param coarse 가운데가 너무 커서 줄을 맞추지 못했다 — 통째로 지우고 넣은 것으로 나온다
   */
  public record Result(List<Line> lines, int added, int removed, boolean coarse) {
  }

  private LineDiff() {
  }

  public static Result diff(String oldText, String newText) {
    String[] a = split(oldText);
    String[] b = split(newText);
    int prefix = 0;
    while (prefix < a.length && prefix < b.length && a[prefix].equals(b[prefix])) {
      prefix++;
    }
    int suffix = 0;
    while (suffix < a.length - prefix && suffix < b.length - prefix
        && a[a.length - 1 - suffix].equals(b[b.length - 1 - suffix])) {
      suffix++;
    }
    List<Line> out = new ArrayList<>(Math.max(a.length, b.length) + 16);
    for (int i = 0; i < prefix; i++) {
      out.add(new Line(Op.SAME, a[i], i + 1, i + 1));
    }

    int n = a.length - prefix - suffix;
    int m = b.length - prefix - suffix;
    boolean coarse = (long) n * m > MAX_CELLS;
    if (coarse) {
      for (int i = 0; i < n; i++) {
        out.add(new Line(Op.DEL, a[prefix + i], prefix + i + 1, null));
      }
      for (int j = 0; j < m; j++) {
        out.add(new Line(Op.ADD, b[prefix + j], null, prefix + j + 1));
      }
    } else {
      middle(a, b, prefix, n, m, out);
    }

    for (int k = 0; k < suffix; k++) {
      int i = a.length - suffix + k;
      int j = b.length - suffix + k;
      out.add(new Line(Op.SAME, a[i], i + 1, j + 1));
    }
    int added = 0;
    int removed = 0;
    for (Line line : out) {
      if (line.op() == Op.ADD) {
        added++;
      } else if (line.op() == Op.DEL) {
        removed++;
      }
    }
    return new Result(out, added, removed, coarse);
  }

  /** 가운데 n × m 줄을 가장 긴 공통 줄로 맞춘다. 지운 줄을 넣은 줄보다 먼저 보인다. */
  private static void middle(String[] a, String[] b, int offset, int n, int m, List<Line> out) {
    // lcs[i][j] = a 의 i번째부터, b 의 j번째부터 끝까지의 가장 긴 공통 줄 수
    int[][] lcs = new int[n + 1][m + 1];
    for (int i = n - 1; i >= 0; i--) {
      for (int j = m - 1; j >= 0; j--) {
        lcs[i][j] = a[offset + i].equals(b[offset + j]) ? lcs[i + 1][j + 1] + 1
            : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
      }
    }
    int i = 0;
    int j = 0;
    while (i < n && j < m) {
      if (a[offset + i].equals(b[offset + j])) {
        out.add(new Line(Op.SAME, a[offset + i], offset + i + 1, offset + j + 1));
        i++;
        j++;
      } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
        out.add(new Line(Op.DEL, a[offset + i], offset + i + 1, null));
        i++;
      } else {
        out.add(new Line(Op.ADD, b[offset + j], null, offset + j + 1));
        j++;
      }
    }
    for (; i < n; i++) {
      out.add(new Line(Op.DEL, a[offset + i], offset + i + 1, null));
    }
    for (; j < m; j++) {
      out.add(new Line(Op.ADD, b[offset + j], null, offset + j + 1));
    }
  }

  private static String[] split(String text) {
    if (text == null || text.isEmpty()) {
      return new String[0];
    }
    return text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
  }
}
