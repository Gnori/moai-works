package com.moai.works.api.storage;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * 객체 저장소 — 첨부 파일이 사는 곳.
 *
 * <ul>
 *   <li>키는 api 가 정한다. 한 번 쓴 키는 덮어쓰지 않는다</li>
 *   <li>객체를 지우는 것은 api 뿐이다</li>
 * </ul>
 *
 * <p>지금은 쓰는 곳이 없다. 첨부 파일(일감 · 위키 · 문서)을 만들 때 쓴다(moai-wiki 의 저장소 구성을 그대로 가져왔다).
 */
public interface ObjectStore {

  enum Zone {
    /** 첨부 파일 */
    ATTACHMENT
  }

  record Stat(long size, String contentType) {
  }

  /** 그 구역의 버킷 이름 */
  String bucket(Zone zone);

  /** 파일을 올린다. metadata 는 ASCII 만(해시 · id). */
  void put(Zone zone, String key, Path file, String contentType, Map<String, String> metadata);

  Optional<Stat> stat(Zone zone, String key);

  /** 내용을 연다. 부르는 쪽이 닫는다. 없으면 {@link Optional#empty()} */
  Optional<InputStream> open(Zone zone, String key);

  /** 작은 객체를 통째로 읽는다. 없으면 empty, maxBytes 를 넘으면 예외 */
  Optional<byte[]> read(Zone zone, String key, long maxBytes);

  /** 그 접두사 아래의 객체를 모두 지운다(버전 보관분까지). @return 지운 수 */
  int deletePrefix(Zone zone, String prefix);
}
