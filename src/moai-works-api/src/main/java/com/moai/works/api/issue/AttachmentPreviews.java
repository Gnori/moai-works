package com.moai.works.api.issue;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileCacheImageInputStream;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.moai.works.api.common.ApiException;
import com.moai.works.api.storage.ObjectStore;

/**
 * 첨부 이미지의 작은 미리보기 그림 (moai-works 일감 #19) — 첨부 목록이 원본 대신 받는다.
 *
 * <ul>
 *   <li>처음 받을 때 만든다(올리기는 그대로 빠르다). 원본 옆 {@code <원본 객체 이름>.preview} 에 PNG 로 두고 다음부터는 그것을 준다.
 *       예전에 올린 그림도 처음 볼 때 생긴다. 지운 첨부 정리(원본 이름이 접두사)가 함께 지운다</li>
 *   <li>서버가 첫 바이트로 알아낸 png · jpeg · gif 만(gif 는 첫 장). 그 형식의 리더만 쓴다 — 이름 · 받은 형식은 믿지 않는다.
 *       WebP 는 JDK(ImageIO)에 디코더가 없어(새 라이브러리 없이) 미리보기를 만들지 않고 화면이 원본을 쓴다</li>
 *   <li>압축 폭탄 막기: 먼저 크기만 읽고 {@link #MAX_PIXELS} 를 넘으면 풀지 않는다. 풀 때는 건너 읽기(subsampling)로 목표의 두 배쯤만
 *       메모리에 올리고, 메타데이터는 읽지 않는다(JPEG 의 방향 표시만 본다). 원본은 메모리가 아니라 임시 파일 캐시로 읽는다.
 *       동시에 {@link #PARALLEL} 개까지만 푼다</li>
 *   <li>긴 변 {@link #SIZE}px PNG(투명 · 글자 그대로, 원본의 메타데이터는 따라가지 않는다). 더 작은 그림은 키우지 않는다</li>
 *   <li>깨진 그림 · 너무 큰 그림 · 풀 수 없는 그림은 빈 표시 객체(0바이트)를 남겨 다음부터 다시 풀지 않는다 — 받기는 404.
 *       저장소에서 원본을 읽다 난 실패는 표시를 남기지 않는다(다음에 다시)</li>
 * </ul>
 */
@Component
public class AttachmentPreviews {

  private static final Logger log = LoggerFactory.getLogger(AttachmentPreviews.class);
  /** 미리보기의 긴 변(px) */
  public static final int SIZE = 320;
  /** 이보다 픽셀이 많은 그림은 풀지 않는다(압축 폭탄 — 약 40메가픽셀) */
  static final long MAX_PIXELS = 40_000_000L;
  /** 미리보기 객체 = 원본 객체 이름 + 이 꼬리 */
  public static final String SUFFIX = ".preview";
  public static final String CONTENT_TYPE = "image/png";
  /** 미리보기를 만드는 형식(서버가 첫 바이트로 알아낸 것) */
  static final Set<String> TYPES = Set.of("image/png", "image/jpeg", "image/gif");
  /** 동시에 그림을 푸는 수 */
  static final int PARALLEL = 2;
  private static final long WAIT_SECONDS = 20;
  /** 저장해 둔 미리보기를 읽을 때의 끝(넉넉히) */
  private static final long MAX_PREVIEW_BYTES = 8L * 1024 * 1024;
  private static final byte[] NONE = new byte[0];

  private final ObjectStore store;
  private final Semaphore permits = new Semaphore(PARALLEL);

  public AttachmentPreviews(ObjectStore store) {
    this.store = store;
  }

  /** 미리보기를 만들 수 있는 형식인가 — 목록의 {@code preview} */
  public static boolean supports(IssueAttachment a) {
    return a.isImage() && TYPES.contains(a.getContentType());
  }

  public static String key(IssueAttachment a) {
    return a.getStorageKey() + SUFFIX;
  }

  /**
   * 미리보기 PNG — 두었으면 그것을, 없으면 원본에서 만들어 둔다. 만들 수 없는 그림이면 empty.
   * 원본이 저장소에 없으면 404 {@code ATTACHMENT_CONTENT_MISSING}, 푸는 자리가 모자라면 503 {@code PREVIEW_BUSY}.
   */
  public Optional<byte[]> get(IssueAttachment a) {
    if (!supports(a)) {
      return Optional.empty();
    }
    Optional<byte[]> stored = stored(a);
    if (stored.isPresent()) {
      return usable(stored.get());
    }
    acquire();
    try {
      // 기다리는 사이 다른 요청이 만들었을 수 있다
      stored = stored(a);
      if (stored.isPresent()) {
        return usable(stored.get());
      }
      byte[] made = make(a);
      store.put(ObjectStore.Zone.ATTACHMENT, key(a), made, made.length == 0 ? "application/octet-stream" : CONTENT_TYPE,
          Map.of("attachment", a.getId().toString(), "preview", made.length == 0 ? "none" : SIZE + "px"));
      return usable(made);
    } finally {
      permits.release();
    }
  }

  private Optional<byte[]> stored(IssueAttachment a) {
    return store.read(ObjectStore.Zone.ATTACHMENT, key(a), MAX_PREVIEW_BYTES);
  }

  private static Optional<byte[]> usable(byte[] bytes) {
    return bytes.length == 0 ? Optional.empty() : Optional.of(bytes);
  }

  private void acquire() {
    try {
      if (permits.tryAcquire(WAIT_SECONDS, TimeUnit.SECONDS)) {
        return;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PREVIEW_BUSY", "미리보기를 만드는 중입니다. 잠시 뒤 다시 시도하십시오.");
  }

  /** 원본을 읽어 만든다 — 만들 수 없는 그림이면 빈 배열(표시). 저장소에서 읽다 난 실패는 그대로 던진다 */
  private byte[] make(IssueAttachment a) {
    InputStream raw = store.open(ObjectStore.Zone.ATTACHMENT, a.getStorageKey())
        .orElseThrow(() -> ApiException.notFound("ATTACHMENT_CONTENT_MISSING", "첨부 내용을 찾을 수 없습니다."));
    SourceStream source = new SourceStream(raw);
    try (source) {
      byte[] png = render(source, a.getContentType());
      if (png == null) {
        log.info("미리보기를 만들지 않음 — 첨부 {}: 너무 크거나 풀 수 없는 그림", a.getId());
        return NONE;
      }
      return png;
    } catch (IOException | RuntimeException e) {
      if (source.failed) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_READ_FAILED", "첨부 내용을 읽지 못했습니다. 잠시 뒤 다시 시도하십시오.");
      }
      log.info("미리보기를 만들지 않음 — 첨부 {}: 깨진 그림({})", a.getId(), e.toString());
      return NONE;
    }
  }

  /**
   * 그림을 풀어 긴 변 {@link #SIZE}px PNG 로 — 그 형식의 리더가 없거나 {@link #MAX_PIXELS} 를 넘으면 null.
   * 깨진 그림은 예외(IIOException 등).
   */
  static byte[] render(InputStream in, String contentType) throws IOException {
    Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType(contentType);
    if (!TYPES.contains(contentType) || !readers.hasNext()) {
      return null;
    }
    ImageReader reader = readers.next();
    try (ImageInputStream iis = new FileCacheImageInputStream(in, null)) {
      // JPEG 는 앞부분의 EXIF 방향 표시만 직접 읽고 되돌린다. 리더는 앞으로만 읽고 메타데이터는 읽지 않는다
      int orientation = "image/jpeg".equals(contentType) ? jpegOrientation(iis) : 1;
      reader.setInput(iis, true, true);
      int w = reader.getWidth(0);
      int h = reader.getHeight(0);
      if (w <= 0 || h <= 0 || (long) w * h > MAX_PIXELS) {
        return null;
      }
      ImageReadParam param = reader.getDefaultReadParam();
      int step = Math.max(1, Math.max(w, h) / (SIZE * 2));
      if (step > 1) {
        param.setSourceSubsampling(step, step, 0, 0);
      }
      BufferedImage image = reader.read(0, param);
      if (image == null) {
        throw new IIOException("그림이 비었습니다");
      }
      return png(shrink(image, orientation));
    } finally {
      reader.dispose();
    }
  }

  /** 긴 변을 {@link #SIZE} 로 줄이고(키우지 않는다) 방향 표시대로 돌린다. 반씩 여러 번 줄여 글자가 뭉개지지 않게 한다 */
  static BufferedImage shrink(BufferedImage source, int orientation) {
    int type = source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    int w = source.getWidth();
    int h = source.getHeight();
    double factor = Math.min(1.0, (double) SIZE / Math.max(w, h));
    int tw = Math.max(1, (int) Math.round(w * factor));
    int th = Math.max(1, (int) Math.round(h * factor));
    BufferedImage current = source;
    while (current.getWidth() / 2 >= tw && current.getHeight() / 2 >= th) {
      current = draw(current, current.getWidth() / 2, current.getHeight() / 2, type, 1);
    }
    return draw(current, tw, th, type, orientation);
  }

  /** src 를 tw × th 로 줄여 방향 표시대로 놓는다 — 90° · 270° 로 돌리는 표시면 결과는 th × tw */
  private static BufferedImage draw(BufferedImage src, int tw, int th, int type, int orientation) {
    boolean swap = orientation >= 5 && orientation <= 8;
    BufferedImage out = new BufferedImage(swap ? th : tw, swap ? tw : th, type);
    Graphics2D g = out.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.transform(orient(orientation, tw, th));
      g.drawImage(src, 0, 0, tw, th, null);
    } finally {
      g.dispose();
    }
    return out;
  }

  /**
   * EXIF 방향(1 ~ 8)대로 w × h 그림을 놓는 변환. 1 그대로 · 2 좌우 뒤집기 · 3 180° · 4 위아래 뒤집기 · 5 좌우 뒤집고 270° ·
   * 6 90° · 7 좌우 뒤집고 90° · 8 270°(시계 방향).
   */
  static AffineTransform orient(int orientation, int w, int h) {
    return switch (orientation) {
      case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
      case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
      case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
      case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
      case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
      case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
      case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);
      default -> new AffineTransform();
    };
  }

  private static byte[] png(BufferedImage image) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
    if (!ImageIO.write(image, "png", out)) {
      throw new IIOException("PNG 로 쓸 수 없습니다");
    }
    return out.toByteArray();
  }

  /** 이만큼 앞에서만 JPEG 의 방향 표시를 찾는다 */
  private static final long ORIENTATION_SCAN_BYTES = 256 * 1024;

  /**
   * JPEG 의 EXIF 방향 표시(0x0112) — 첫 그림 자료(SOS) 앞의 APP1 조각에서 찾는다. 없거나 읽을 수 없으면 1.
   * 읽은 뒤 스트림을 처음 자리로 되돌린다(캐시 파일이 있어 되돌릴 수 있다).
   */
  static int jpegOrientation(ImageInputStream iis) throws IOException {
    iis.mark();
    try {
      if (iis.read() != 0xFF || iis.read() != 0xD8) {
        return 1;
      }
      while (iis.getStreamPosition() < ORIENTATION_SCAN_BYTES) {
        if (iis.read() != 0xFF) {
          return 1;
        }
        int marker = iis.read();
        while (marker == 0xFF) {
          marker = iis.read();
        }
        if (marker < 0 || marker == 0xDA || marker == 0xD9) {
          return 1;
        }
        int length = iis.readUnsignedShort();
        if (length < 2) {
          return 1;
        }
        if (marker == 0xE1) {
          byte[] data = new byte[length - 2];
          iis.readFully(data);
          int o = exifOrientation(data);
          if (o > 0) {
            return o;
          }
        } else if (iis.skipBytes(length - 2) < length - 2) {
          return 1;
        }
      }
      return 1;
    } catch (java.io.EOFException e) {
      return 1;
    } finally {
      iis.reset();
    }
  }

  /** APP1 조각("Exif\0\0" + TIFF)에서 IFD0 의 방향 값 — 없으면 0 */
  static int exifOrientation(byte[] data) {
    if (data.length < 14 || data[0] != 'E' || data[1] != 'x' || data[2] != 'i' || data[3] != 'f' || data[4] != 0 || data[5] != 0) {
      return 0;
    }
    ByteBuffer tiff = ByteBuffer.wrap(data, 6, data.length - 6).slice();
    if (tiff.get(0) == 'I' && tiff.get(1) == 'I') {
      tiff.order(ByteOrder.LITTLE_ENDIAN);
    } else if (tiff.get(0) == 'M' && tiff.get(1) == 'M') {
      tiff.order(ByteOrder.BIG_ENDIAN);
    } else {
      return 0;
    }
    long ifd = Integer.toUnsignedLong(tiff.getInt(4));
    if (ifd < 8 || ifd + 2 > tiff.limit()) {
      return 0;
    }
    int count = Short.toUnsignedInt(tiff.getShort((int) ifd));
    for (int i = 0; i < count; i++) {
      int at = (int) ifd + 2 + i * 12;
      if (at + 12 > tiff.limit()) {
        return 0;
      }
      if (Short.toUnsignedInt(tiff.getShort(at)) == 0x0112 && Short.toUnsignedInt(tiff.getShort(at + 2)) == 3) {
        int value = Short.toUnsignedInt(tiff.getShort(at + 8));
        return value >= 1 && value <= 8 ? value : 0;
      }
    }
    return 0;
  }

  /** 저장소에서 읽다 난 실패인지 가려 둔다 — 그때는 깨진 그림 표시를 남기지 않는다 */
  private static final class SourceStream extends FilterInputStream {
    private boolean failed;

    SourceStream(InputStream in) {
      super(in);
    }

    @Override
    public int read() throws IOException {
      try {
        return super.read();
      } catch (IOException | RuntimeException e) {
        failed = true;
        throw e;
      }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      try {
        return super.read(b, off, len);
      } catch (IOException | RuntimeException e) {
        failed = true;
        throw e;
      }
    }

    /** 다 읽었으면 닫다가 난 실패는 상관없다 — 미리보기를 버리지 않는다 */
    @Override
    public void close() {
      try {
        super.close();
      } catch (IOException | RuntimeException e) {
        // 무시
      }
    }
  }
}
