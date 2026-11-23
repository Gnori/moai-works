package com.moai.works.api.storage;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.moai.works.api.common.ApiException;
import com.moai.works.api.config.MoaiWorksProperties;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * S3 호환 저장소(MinIO). 클라이언트는 처음 쓸 때 만든다 — 열쇠가 없어도 api 는 뜨고, 첨부 파일을 쓸 때 알린다.
 * 주소는 경로 방식(http://host/버킷/키)으로 부른다. MinIO 가 그 방식이다.
 */
@Component
public class S3ObjectStore implements ObjectStore {

  private final MoaiWorksProperties.Storage settings;
  private volatile S3Client client;

  public S3ObjectStore(MoaiWorksProperties properties) {
    this.settings = properties.storage();
  }

  private S3Client s3() {
    S3Client local = client;
    if (local == null) {
      synchronized (this) {
        local = client;
        if (local == null) {
          if (settings == null || isBlank(settings.accessKey()) || isBlank(settings.secretKey())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_NOT_CONFIGURED",
                "객체 저장소의 열쇠가 없습니다. infra/minio/init-minio.sh 를 돌려 주십시오.");
          }
          local = S3Client.builder()
              .endpointOverride(URI.create(settings.endpoint()))
              .region(Region.US_EAST_1)
              .forcePathStyle(true)
              .credentialsProvider(StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(settings.accessKey(), settings.secretKey())))
              .httpClient(UrlConnectionHttpClient.create())
              .build();
          client = local;
        }
      }
    }
    return local;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  @Override
  public String bucket(Zone zone) {
    return settings.attachmentBucket();
  }

  @Override
  public void put(Zone zone, String key, Path file, String contentType, Map<String, String> metadata) {
    s3().putObject(b -> b.bucket(bucket(zone)).key(key).contentType(contentType).metadata(metadata),
        RequestBody.fromFile(file));
  }

  @Override
  public void put(Zone zone, String key, byte[] content, String contentType, Map<String, String> metadata) {
    s3().putObject(b -> b.bucket(bucket(zone)).key(key).contentType(contentType).metadata(metadata),
        RequestBody.fromBytes(content));
  }

  @Override
  public Optional<Stat> stat(Zone zone, String key) {
    try {
      var head = s3().headObject(b -> b.bucket(bucket(zone)).key(key));
      return Optional.of(new Stat(head.contentLength(), head.contentType()));
    } catch (NoSuchKeyException e) {
      return Optional.empty();
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        return Optional.empty();
      }
      throw e;
    }
  }

  @Override
  public Optional<InputStream> open(Zone zone, String key) {
    try {
      return Optional.of(s3().getObject(b -> b.bucket(bucket(zone)).key(key)));
    } catch (NoSuchKeyException e) {
      return Optional.empty();
    }
  }

  @Override
  public Optional<byte[]> read(Zone zone, String key, long maxBytes) {
    Optional<Stat> stat = stat(zone, key);
    if (stat.isEmpty()) {
      return Optional.empty();
    }
    if (stat.get().size() > maxBytes) {
      throw ApiException.badRequest("OBJECT_TOO_LARGE", "객체가 너무 큽니다: " + key);
    }
    return Optional.of(s3().getObjectAsBytes(b -> b.bucket(bucket(zone)).key(key)).asByteArray());
  }

  @Override
  public int deletePrefix(Zone zone, String prefix) {
    if (prefix == null || prefix.length() < 40) {
      // 워크스페이스 id 와 자료 id 가 든 접두사만 지운다 — 짧은 접두사로 넓게 지우는 사고를 막는다
      throw new IllegalArgumentException("지울 접두사가 너무 짧습니다: " + prefix);
    }
    String bucket = bucket(zone);
    int deleted = 0;
    List<ObjectIdentifier> batch = new ArrayList<>();
    for (var page : s3().listObjectVersionsPaginator(b -> b.bucket(bucket).prefix(prefix))) {
      page.versions().forEach(v -> batch.add(ObjectIdentifier.builder().key(v.key()).versionId(v.versionId()).build()));
      page.deleteMarkers().forEach(m -> batch.add(ObjectIdentifier.builder().key(m.key()).versionId(m.versionId()).build()));
      while (batch.size() >= 500) {
        deleted += flush(bucket, batch.subList(0, 500));
      }
    }
    if (!batch.isEmpty()) {
      deleted += flush(bucket, batch);
    }
    return deleted;
  }

  private int flush(String bucket, List<ObjectIdentifier> ids) {
    List<ObjectIdentifier> copy = List.copyOf(ids);
    s3().deleteObjects(b -> b.bucket(bucket).delete(Delete.builder().objects(copy).quiet(true).build()));
    ids.clear();
    return copy.size();
  }
}
