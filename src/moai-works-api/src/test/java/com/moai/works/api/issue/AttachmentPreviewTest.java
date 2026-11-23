package com.moai.works.api.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.CRC32;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.moai.works.api.storage.ObjectStore;
import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.MemoryObjectStore;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * 첨부의 작은 미리보기 그림 (일감 #19) — 원본과 같은 판정 · 머리글, 긴 변 320px PNG, 원본 옆에 한 번만 만들어 둠,
 * png · jpeg(방향 표시) · gif(첫 장), 파일 · WebP · 깨진 그림 · 너무 큰 그림은 미리보기 없음(다시 풀지 않음).
 */
@ApiTest
class AttachmentPreviewTest {

  private static final String P = "thumbs";
  private static final String BASE = "/workspaces/{ws}/issues/{n}/attachments";

  @Autowired
  TestApi api;
  @Autowired
  MemoryObjectStore store;
  @Autowired
  JdbcTemplate jdbc;

  private record Space(String ws, TestUser owner, TestUser member, int issue) {
  }

  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "미리보기 시험");
    TestUser member = api.user("member");
    api.join(ws, owner, member, "member");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "그림", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    int n = TestApi.read(api.post(owner, "/workspaces/{ws}/projects/{p}/issues", Map.of("type", "feature", "title", "그림"),
        ws, P).andExpect(status().isCreated()), "$.number");
    return new Space(ws, owner, member, n);
  }

  private String upload(Space s, String name, byte[] content) throws Exception {
    return TestApi.read(api.upload(s.owner(), BASE, name, "application/octet-stream", content, Map.of(), s.ws(), s.issue())
        .andExpect(status().isCreated()), "$.id");
  }

  private String key(String id) {
    return jdbc.queryForObject("SELECT storage_key FROM issue_attachment WHERE id = ?::uuid", String.class, id);
  }

  private BufferedImage previewOf(Space s, TestUser as, String id) throws Exception {
    byte[] body = api.get(as, "/workspaces/{ws}/issues/{n}/attachments/{id}/preview", s.ws(), s.issue(), id).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsByteArray();
    return ImageIO.read(new ByteArrayInputStream(body));
  }

  // ── 시험용 그림 ────────────────────────────────────────────────────

  private static BufferedImage picture(int w, int h, Color color) {
    BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setColor(color);
    g.fillRect(0, 0, w, h);
    g.setColor(Color.WHITE);
    g.drawString("moai-works", 10, h / 2);
    g.dispose();
    return image;
  }

  private static byte[] encode(BufferedImage image, String format) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    assertThat(ImageIO.write(image, format, out)).isTrue();
    return out.toByteArray();
  }

  /** JFIF 조각 뒤에 EXIF 방향 표시(APP1)를 끼운 JPEG */
  private static byte[] jpegWithOrientation(BufferedImage image, int orientation) throws IOException {
    byte[] jpeg = encode(image, "jpg");
    assertThat(jpeg[2] & 0xFF).isEqualTo(0xFF);
    assertThat(jpeg[3] & 0xFF).isEqualTo(0xE0);
    int app0End = 4 + (((jpeg[4] & 0xFF) << 8) | (jpeg[5] & 0xFF));
    ByteBuffer tiff = ByteBuffer.allocate(26);
    tiff.put(new byte[] {'M', 'M', 0, 42}).putInt(8).putShort((short) 1)
        .putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0)
        .putInt(0);
    byte[] payload = new byte[6 + tiff.capacity()];
    System.arraycopy("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), 0, payload, 0, 6);
    System.arraycopy(tiff.array(), 0, payload, 6, tiff.capacity());
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(jpeg, 0, app0End);
    out.write(0xFF);
    out.write(0xE1);
    out.write((payload.length + 2) >> 8);
    out.write((payload.length + 2) & 0xFF);
    out.write(payload);
    out.write(jpeg, app0End, jpeg.length - app0End);
    return out.toByteArray();
  }

  /** 두 장짜리 GIF — 첫 장 빨강, 둘째 장 파랑 */
  private static byte[] twoFrameGif(int w, int h) throws IOException {
    ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
      writer.setOutput(ios);
      writer.prepareWriteSequence(null);
      for (Color c : new Color[] {Color.RED, Color.BLUE}) {
        BufferedImage frame = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_INDEXED);
        Graphics2D g = frame.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, w, h);
        g.dispose();
        writer.writeToSequence(new IIOImage(frame, null, null), null);
      }
      writer.endWriteSequence();
    } finally {
      writer.dispose();
    }
    return out.toByteArray();
  }

  /** IHDR 만 바르고(가로 · 세로를 크게 적은) 그림 자료는 없는 PNG — 풀면 폭탄일 그림의 머리 */
  private static byte[] pngHeaderClaiming(int w, int h) {
    ByteBuffer ihdr = ByteBuffer.allocate(17);
    ihdr.put(new byte[] {'I', 'H', 'D', 'R'}).putInt(w).putInt(h).put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0)
        .put((byte) 0);
    CRC32 crc = new CRC32();
    crc.update(ihdr.array());
    ByteBuffer png = ByteBuffer.allocate(8 + 4 + 17 + 4 + 12);
    png.put(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}).putInt(13).put(ihdr.array()).putInt((int) crc.getValue());
    CRC32 end = new CRC32();
    end.update(new byte[] {'I', 'E', 'N', 'D'});
    png.putInt(0).put(new byte[] {'I', 'E', 'N', 'D'}).putInt((int) end.getValue());
    return png.array();
  }

  // ── 시험 ──────────────────────────────────────────────────────────

  @Test
  void 미리보기는_원본과_같은_판정과_머리글로_긴_변_320_PNG_를_한_번만_만들어_둔다() throws Exception {
    Space s = space();
    String id = upload(s, "화면.png", encode(picture(1000, 500, Color.DARK_GRAY), "png"));
    api.get(s.member(), BASE, s.ws(), s.issue()).andExpect(jsonPath("$[0].preview").value(true));

    api.get(s.member(), "/workspaces/{ws}/issues/{n}/attachments/{id}/preview", s.ws(), s.issue(), id).andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "image/png"))
        .andExpect(header().string("Content-Disposition", Matchers.startsWith("inline")))
        .andExpect(header().string("Content-Disposition", Matchers.containsString("preview.png")))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
        .andExpect(header().string("Cache-Control", Matchers.startsWith("private")));
    BufferedImage preview = previewOf(s, s.member(), id);
    assertThat(preview.getWidth()).isEqualTo(320);
    assertThat(preview.getHeight()).isEqualTo(160);

    // 원본 옆(같은 연/월 폴더, 이름 + .preview)에 두고, 두 번째부터는 원본을 다시 읽지 않는다
    String original = key(id);
    assertThat(store.keys(ObjectStore.Zone.ATTACHMENT, original)).containsExactly(original, original + ".preview");
    assertThat(store.opened(ObjectStore.Zone.ATTACHMENT, original)).isEqualTo(1);

    // 독자가 아니면 404 — 바깥 사람 · 지정 공개 프로젝트의 독자 아닌 멤버
    TestUser outsider = api.user("outsider");
    api.get(outsider, "/workspaces/{ws}/issues/{n}/attachments/{id}/preview", s.ws(), s.issue(), id).andExpect(status().isNotFound());
    api.post(s.owner(), "/workspaces/{ws}/projects", Map.of("key", "closed", "name", "지정", "visibility", "RESTRICTED"), s.ws())
        .andExpect(status().isCreated());
    int hidden = TestApi.read(api.post(s.owner(), "/workspaces/{ws}/projects/{p}/issues", Map.of("type", "bug", "title", "비공개"),
        s.ws(), "closed").andExpect(status().isCreated()), "$.number");
    String secret = TestApi.read(api.upload(s.owner(), BASE, "secret.png", "image/png", encode(picture(50, 50, Color.RED), "png"),
        Map.of(), s.ws(), hidden).andExpect(status().isCreated()), "$.id");
    api.get(s.member(), "/workspaces/{ws}/issues/{n}/attachments/{id}/preview", s.ws(), hidden, secret).andExpect(status().isNotFound());
    api.get(s.owner(), "/workspaces/{ws}/issues/{n}/attachments/{id}/preview", s.ws(), hidden, secret).andExpect(status().isOk());
    // 작은 그림은 키우지 않는다
    BufferedImage small = ImageIO.read(new ByteArrayInputStream(api.get(s.owner(), "/workspaces/{ws}/issues/{n}/attachments/{id}/preview", s.ws(),
        hidden, secret).andReturn().getResponse().getContentAsByteArray()));
    assertThat(small.getWidth()).isEqualTo(50);

    // 지운 첨부는 미리보기도 없다
    api.delete(s.owner(), "/workspaces/{ws}/issues/{n}/attachments/{id}", s.ws(), s.issue(), id).andExpect(status().isNoContent());
    api.get(s.member(), "/workspaces/{ws}/issues/{n}/attachments/{id}/preview", s.ws(), s.issue(), id).andExpect(status().isNotFound());
  }

  @Test
  void JPEG_는_방향_표시대로_돌리고_GIF_는_첫_장으로_만든다() throws Exception {
    Space s = space();
    // 가로 400 × 세로 200 으로 찍혔고 "90° 돌려 보라"는 표시(6) — 미리보기는 세로로 선다
    String jpeg = upload(s, "phone.jpg", jpegWithOrientation(picture(400, 200, Color.GREEN), 6));
    BufferedImage turned = previewOf(s, s.member(), jpeg);
    assertThat(turned.getWidth()).isEqualTo(160);
    assertThat(turned.getHeight()).isEqualTo(320);
    String plain = upload(s, "plain.jpg", encode(picture(640, 480, Color.GREEN), "jpg"));
    BufferedImage straight = previewOf(s, s.member(), plain);
    assertThat(straight.getWidth()).isEqualTo(320);
    assertThat(straight.getHeight()).isEqualTo(240);

    String gif = upload(s, "anim.gif", twoFrameGif(640, 480));
    BufferedImage first = previewOf(s, s.member(), gif);
    assertThat(first.getWidth()).isEqualTo(320);
    assertThat(first.getHeight()).isEqualTo(240);
    Color center = new Color(first.getRGB(160, 120), true);
    assertThat(center.getRed()).isGreaterThan(200);
    assertThat(center.getBlue()).isLessThan(50);
  }

  @Test
  void 파일_WebP_깨진_그림_너무_큰_그림은_미리보기가_없고_다시_풀지_않는다() throws Exception {
    Space s = space();
    String text = upload(s, "memo.txt", "글".getBytes(StandardCharsets.UTF_8));
    byte[] webpHead = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};
    String webp = upload(s, "pic.webp", webpHead);
    String broken = upload(s, "broken.png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D,
        'I', 'H', 'D', 'R', 1, 2, 3});
    // 1만 × 1만 = 1억 픽셀이라고 적은 머리 — 크기만 읽고 풀지 않는다
    String bomb = upload(s, "bomb.png", pngHeaderClaiming(10_000, 10_000));

    api.get(s.member(), BASE, s.ws(), s.issue())
        .andExpect(jsonPath("$[0].preview").value(false))
        .andExpect(jsonPath("$[1].image").value(true))
        .andExpect(jsonPath("$[1].contentType").value("image/webp"))
        .andExpect(jsonPath("$[1].preview").value(false))
        .andExpect(jsonPath("$[2].preview").value(true))
        .andExpect(jsonPath("$[3].preview").value(true));

    for (String id : new String[] {text, webp}) {
      api.get(s.member(), "/workspaces/{ws}/issues/{n}/attachments/{id}/preview", s.ws(), s.issue(), id).andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("ATTACHMENT_PREVIEW_NONE"));
      assertThat(store.keys(ObjectStore.Zone.ATTACHMENT, key(id))).hasSize(1);
    }
    for (String id : new String[] {broken, bomb}) {
      api.get(s.member(), "/workspaces/{ws}/issues/{n}/attachments/{id}/preview", s.ws(), s.issue(), id).andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("ATTACHMENT_PREVIEW_NONE"));
      // 빈 표시를 남겨 다음에는 원본을 다시 읽지 않는다
      assertThat(store.bytes(ObjectStore.Zone.ATTACHMENT, key(id) + ".preview")).isEmpty();
      api.get(s.member(), "/workspaces/{ws}/issues/{n}/attachments/{id}/preview", s.ws(), s.issue(), id).andExpect(status().isNotFound());
      assertThat(store.opened(ObjectStore.Zone.ATTACHMENT, key(id))).isEqualTo(1);
    }
    // 원본 받기는 그대로다(WebP 는 화면이 원본을 쓴다)
    api.get(s.member(), "/workspaces/{ws}/issues/{n}/attachments/{id}/content", s.ws(), s.issue(), webp).andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "image/webp"));
  }

  @Test
  void 그림_풀기는_픽셀_끝과_JPEG_방향_표시를_지킨다() throws Exception {
    assertThat(AttachmentPreviews.render(new ByteArrayInputStream(pngHeaderClaiming(8000, 5001)), "image/png")).isNull();
    assertThat(AttachmentPreviews.render(new ByteArrayInputStream(encode(picture(10, 10, Color.RED), "png")), "image/webp"))
        .isNull();
    for (int o = 1; o <= 8; o++) {
      byte[] png = AttachmentPreviews.render(new ByteArrayInputStream(jpegWithOrientation(picture(400, 100, Color.BLUE), o)),
          "image/jpeg");
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
      assertThat(image.getWidth()).as("방향 %d", o).isEqualTo(o >= 5 ? 80 : 320);
      assertThat(image.getHeight()).as("방향 %d", o).isEqualTo(o >= 5 ? 320 : 80);
    }
    assertThat(AttachmentController.previewName("화면.jpg")).isEqualTo("화면-preview.png");
    assertThat(AttachmentController.previewName(".hidden")).isEqualTo(".hidden-preview.png");
  }
}
