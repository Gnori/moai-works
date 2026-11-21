package com.moai.works.api.support;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.moai.works.api.storage.ObjectStore;

/** 테스트용 객체 저장소 — 메모리에 둔다. 첨부 기능의 테스트가 쓴다. */
public class MemoryObjectStore implements ObjectStore {

  private record Stored(byte[] bytes, String contentType) {
  }

  private final Map<String, Stored> objects = new ConcurrentHashMap<>();

  private static String id(Zone zone, String key) {
    return zone + ":" + key;
  }

  @Override
  public String bucket(Zone zone) {
    return "test-attachments";
  }

  @Override
  public void put(Zone zone, String key, Path file, String contentType, Map<String, String> metadata) {
    try {
      objects.put(id(zone, key), new Stored(Files.readAllBytes(file), contentType));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** 글을 바로 넣는다 (테스트 준비용) */
  public void put(Zone zone, String key, String text) {
    objects.put(id(zone, key), new Stored(text.getBytes(StandardCharsets.UTF_8), "application/json"));
  }

  @Override
  public Optional<Stat> stat(Zone zone, String key) {
    return Optional.ofNullable(objects.get(id(zone, key))).map(s -> new Stat(s.bytes().length, s.contentType()));
  }

  @Override
  public Optional<InputStream> open(Zone zone, String key) {
    return Optional.ofNullable(objects.get(id(zone, key))).map(s -> new ByteArrayInputStream(s.bytes()));
  }

  @Override
  public Optional<byte[]> read(Zone zone, String key, long maxBytes) {
    return Optional.ofNullable(objects.get(id(zone, key))).map(Stored::bytes);
  }

  @Override
  public int deletePrefix(Zone zone, String prefix) {
    List<String> keys = objects.keySet().stream().filter(k -> k.startsWith(id(zone, prefix))).toList();
    keys.forEach(objects::remove);
    return keys.size();
  }

  /** 그 접두사 아래에 남은 객체의 키 */
  public List<String> keys(Zone zone, String prefix) {
    return objects.keySet().stream().filter(k -> k.startsWith(id(zone, prefix)))
        .map(k -> k.substring(zone.name().length() + 1)).sorted().toList();
  }
}
