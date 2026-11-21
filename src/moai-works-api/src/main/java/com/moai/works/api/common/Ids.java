package com.moai.works.api.common;

import java.security.SecureRandom;
import java.util.UUID;

/** 새 id 만들기. */
public final class Ids {

  private static final SecureRandom RANDOM = new SecureRandom();

  private Ids() {
  }

  /**
   * UUID 버전 7 — 앞 48비트가 만든 시각(밀리초)이라 만든 순서대로 정렬된다(RFC 9562).
   * 행이 많이 쌓이는 표(위키 · 페이지 · 리비전)는 이 id 를 쓴다. 무작위 UUID 보다 인덱스가 덜 흩어진다.
   */
  public static UUID next() {
    long millis = System.currentTimeMillis();
    byte[] random = new byte[10];
    RANDOM.nextBytes(random);
    long most = (millis << 16) | 0x7000L | (((random[0] & 0x0FL) << 8) | (random[1] & 0xFFL));
    long least = 0x8000000000000000L | ((random[2] & 0x3FL) << 56);
    for (int i = 3; i < 10; i++) {
      least |= (random[i] & 0xFFL) << (8 * (9 - i));
    }
    return new UUID(most, least);
  }
}
