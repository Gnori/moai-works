package com.moai.works.api.issue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.moai.works.api.account.UserProfile;
import com.moai.works.api.account.UserProfileRepository;
import com.moai.works.api.audit.AuditService;
import com.moai.works.api.common.Actor;
import com.moai.works.api.common.ApiException;
import com.moai.works.api.common.Ids;
import com.moai.works.api.config.MoaiWorksProperties;
import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.storage.ObjectStore;
import com.moai.works.api.workspace.Permission;

/**
 * 일감 첨부파일 — 올리기 · 목록 · 받기 · 지우기 (2026-11-22 사용자: "첨부파일도 넣을 수 있어야 하고 본문에도 이미지를 중간중간"
 * · "파일 저장소 구조 잘 설계 … 너무 한곳에 다 모아 두면 문제").
 *
 * <ul>
 *   <li>파일은 객체 저장소에만: {@code ws/<워크스페이스>/p/<프로젝트>/<연>/<월>/<첨부 id>} — 원래 이름은 DB 에만</li>
 *   <li>받으면서 임시 파일에 쓰고 SHA-256 · 크기를 센다(메모리에 올리지 않는다). 파일 하나 상한 · 워크스페이스 총 용량을 본다</li>
 *   <li>이미지(png · jpeg · gif · webp)는 내용(첫 바이트)으로 알아내고 본문에 그릴 수 있다. 그 밖은 늘 내려받기로만 준다</li>
 *   <li>본문 · 댓글에 넣는 표시: 이미지 {@code ![이름](attachment:<id>)}, 파일 {@code [이름](attachment:<id>)}</li>
 *   <li>이력(판은 아님) · 감사에 남는다. 지우기는 올린 사람 또는 project.manage — 지움 표시만</li>
 * </ul>
 */
@Service
public class AttachmentService {

  private final IssueRepositories.Issues issues;
  private final IssueRepositories.Attachments attachments;
  private final IssueService issueService;
  private final ObjectStore store;
  private final AuditService audit;
  private final UserProfileRepository users;
  private final MoaiWorksProperties.Storage storage;
  private final TransactionTemplate tx;
  private final Clock clock;

  public AttachmentService(IssueRepositories.Issues issues, IssueRepositories.Attachments attachments,
      IssueService issueService, ObjectStore store, AuditService audit, UserProfileRepository users,
      MoaiWorksProperties properties, PlatformTransactionManager txManager, Clock clock) {
    this.issues = issues;
    this.attachments = attachments;
    this.issueService = issueService;
    this.store = store;
    this.audit = audit;
    this.users = users;
    this.storage = properties.storage();
    this.tx = new TransactionTemplate(txManager);
    this.clock = clock;
  }

  /**
   * 첨부 하나.
   *
   * @param markdown 본문 · 댓글에 넣을 표시(이미지는 {@code ![…](attachment:id)})
   */
  public record AttachmentView(UUID id, String fileName, String contentType, long size, boolean image, String sha256,
      IssueViews.Person uploadedBy, String via, Instant createdAt, String markdown) {
  }

  /** 받기 — 메타데이터와 내용(부르는 쪽이 닫는다) */
  public record Content(IssueAttachment meta, InputStream stream) {
  }

  // ── 목록 ──────────────────────────────────────────────────────────

  public List<AttachmentView> list(ProjectContext ctx, int number) {
    Issue issue = requireIssue(ctx, number);
    List<IssueAttachment> rows = attachments.findLive(issue.getId());
    Map<UUID, UserProfile> people = users.findAllById(rows.stream().map(IssueAttachment::getUploadedBy).toList())
        .stream().collect(Collectors.toMap(UserProfile::getId, u -> u));
    return rows.stream().map(a -> view(a, people.get(a.getUploadedBy()))).toList();
  }

  // ── 올리기 ────────────────────────────────────────────────────────

  public AttachmentView upload(ProjectContext ctx, int number, String originalName, String declaredType, InputStream in,
      Actor actor, IssueJournal.Via via) throws IOException {
    requireOpen(ctx);
    Issue issue = requireIssue(ctx, number);
    String name = cleanName(originalName);
    UUID id = Ids.next();
    Path temp = Files.createTempFile("moai-works-attachment-", ".bin");
    try {
      long max = storage.maxFileBytes();
      long size;
      String sha256;
      byte[] head = new byte[16];
      int headLength;
      MessageDigest digest = sha256();
      try (InputStream source = in; OutputStream out = new DigestOutputStream(Files.newOutputStream(temp), digest)) {
        headLength = source.readNBytes(head, 0, head.length);
        out.write(head, 0, headLength);
        size = headLength + copyLimited(source, out, max - headLength);
      }
      if (size == 0) {
        throw ApiException.badRequest("EMPTY_FILE", "빈 파일은 올릴 수 없습니다.");
      }
      sha256 = HexFormat.of().formatHex(digest.digest());
      long limit = workspaceLimitBytes();
      if (limit > 0 && attachments.usedBytes(ctx.workspaceId()) + size > limit) {
        throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "WORKSPACE_STORAGE_FULL",
            "이 워크스페이스의 첨부 용량(" + storage.maxWorkspaceGb() + "GB)을 넘습니다.");
      }
      String imageType = imageType(head, headLength);
      String contentType = imageType != null ? imageType : cleanType(declaredType);
      ZonedDateTime now = Instant.now(clock).atZone(ZoneOffset.UTC);
      String key = "ws/" + ctx.workspaceId() + "/p/" + ctx.projectId() + "/" + now.getYear() + "/"
          + String.format("%02d", now.getMonthValue()) + "/" + id;
      store.put(ObjectStore.Zone.ATTACHMENT, key, temp, contentType, Map.of("sha256", sha256, "attachment", id.toString()));
      IssueAttachment saved = tx.execute(s -> {
        IssueAttachment a = attachments.saveAndFlush(new IssueAttachment(id, ctx.workspaceId(), ctx.projectId(),
            issue.getId(), name, contentType, size, sha256, imageType != null, key, actor.id(), via, Instant.now(clock)));
        issueService.journalAttachment(issue.getId(), actor, via, null, journalValue(a));
        Map<String, Object> details = new LinkedHashMap<>(journalValue(a));
        details.put("project", ctx.project().getKey());
        details.put("number", number);
        details.put("via", via.name());
        audit.record(actor, ctx.workspaceId(), "issue.attachment.add", "issue", issue.getId().toString(), details);
        return a;
      });
      return view(saved, users.findById(actor.id()).orElse(null));
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  // ── 받기 ──────────────────────────────────────────────────────────

  public Content open(ProjectContext ctx, int number, UUID attachmentId) {
    IssueAttachment a = requireAttachment(ctx, number, attachmentId);
    InputStream stream = store.open(ObjectStore.Zone.ATTACHMENT, a.getStorageKey())
        .orElseThrow(() -> ApiException.notFound("ATTACHMENT_CONTENT_MISSING", "첨부 내용을 찾을 수 없습니다."));
    return new Content(a, stream);
  }

  // ── 지우기 ────────────────────────────────────────────────────────

  /** 지움 표시만 — 올린 사람, 또는 project.manage */
  public void delete(ProjectContext ctx, int number, UUID attachmentId, Actor actor, IssueJournal.Via via) {
    requireOpen(ctx);
    IssueAttachment a = requireAttachment(ctx, number, attachmentId);
    if (!a.getUploadedBy().equals(actor.id()) && !ctx.has(Permission.PROJECT_MANAGE)) {
      throw ApiException.forbidden("PERMISSION_REQUIRED", "남이 올린 첨부를 지우려면 권한 project.manage 이(가) 필요합니다.");
    }
    tx.executeWithoutResult(s -> {
      IssueAttachment locked = attachments.findById(a.getId()).orElseThrow();
      locked.markDeleted(actor.id(), Instant.now(clock));
      issueService.journalAttachment(locked.getIssueId(), actor, via, journalValue(locked), null);
      Map<String, Object> details = new LinkedHashMap<>(journalValue(locked));
      details.put("project", ctx.project().getKey());
      details.put("number", number);
      details.put("via", via.name());
      audit.record(actor, ctx.workspaceId(), "issue.attachment.remove", "issue", locked.getIssueId().toString(), details);
    });
  }

  // ── 도움 ──────────────────────────────────────────────────────────

  private Issue requireIssue(ProjectContext ctx, int number) {
    return issues.findLive(ctx.projectId(), number)
        .orElseThrow(() -> ApiException.notFound("ISSUE_NOT_FOUND", "일감을 찾을 수 없습니다: #" + number));
  }

  private IssueAttachment requireAttachment(ProjectContext ctx, int number, UUID attachmentId) {
    Issue issue = requireIssue(ctx, number);
    return attachments.findById(attachmentId)
        .filter(a -> a.getIssueId().equals(issue.getId()) && !a.isDeleted())
        .orElseThrow(() -> ApiException.notFound("ATTACHMENT_NOT_FOUND", "첨부를 찾을 수 없습니다."));
  }

  private static void requireOpen(ProjectContext ctx) {
    if (!ctx.project().isOpenToReaders()) {
      throw ApiException.conflict("PROJECT_NOT_WRITABLE", "보관한 프로젝트의 일감은 바꿀 수 없습니다. 먼저 복원하십시오.");
    }
  }

  private long workspaceLimitBytes() {
    return storage.maxWorkspaceGb() <= 0 ? 0 : storage.maxWorkspaceGb() * 1024L * 1024L * 1024L;
  }

  /** 상한까지만 옮긴다 — 넘으면 413 */
  private long copyLimited(InputStream in, OutputStream out, long remaining) throws IOException {
    byte[] buffer = new byte[64 * 1024];
    long total = 0;
    int n;
    while ((n = in.read(buffer)) != -1) {
      total += n;
      if (total > remaining) {
        throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
            "파일 하나는 " + storage.maxFileMb() + "MB 를 넘을 수 없습니다.");
      }
      out.write(buffer, 0, n);
    }
    return total;
  }

  /** 첫 바이트로 본문에 그릴 수 있는 이미지인지 알아낸다 — 이름 · 받은 형식은 믿지 않는다. SVG 는 이미지로 보지 않는다(스크립트) */
  static String imageType(byte[] h, int n) {
    if (n >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') {
      return "image/png";
    }
    if (n >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
      return "image/jpeg";
    }
    if (n >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8') {
      return "image/gif";
    }
    if (n >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W' && h[9] == 'E' && h[10] == 'B'
        && h[11] == 'P') {
      return "image/webp";
    }
    return null;
  }

  /** 파일 이름 — 경로 · 제어 문자를 빼고 255자까지. 비면 "file" */
  static String cleanName(String original) {
    String n = original == null ? "" : original;
    n = n.substring(Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\')) + 1);
    n = n.replaceAll("[\\p{Cntrl}]", "").strip();
    if (n.isEmpty() || n.equals(".") || n.equals("..")) {
      n = "file";
    }
    return n.length() > 255 ? n.substring(n.length() - 255) : n;
  }

  /** 받은 형식 — 모양이 맞지 않으면 application/octet-stream. 어차피 이미지가 아니면 내려받기로만 준다 */
  static String cleanType(String declared) {
    if (declared == null || !declared.matches("[a-zA-Z0-9.+-]{1,40}/[a-zA-Z0-9.+-]{1,50}")) {
      return "application/octet-stream";
    }
    return declared.toLowerCase();
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Map<String, Object> journalValue(IssueAttachment a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", a.getId().toString());
    m.put("name", a.getFileName());
    m.put("size", a.getSizeBytes());
    m.put("image", a.isImage());
    return m;
  }

  private static AttachmentView view(IssueAttachment a, UserProfile uploader) {
    String markdown = (a.isImage() ? "!" : "") + "[" + a.getFileName().replace("]", "\\]") + "](attachment:" + a.getId() + ")";
    return new AttachmentView(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(), a.isImage(), a.getSha256(),
        uploader == null ? new IssueViews.Person(a.getUploadedBy(), null, null)
            : new IssueViews.Person(uploader.getId(), uploader.getUsername(), uploader.getDisplayName()),
        a.getVia().name(), a.getCreatedAt(), markdown);
  }
}
