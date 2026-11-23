package com.moai.works.api.issue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.moai.works.api.common.ApiException;
import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.security.access.IssueApi;
import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.workspace.Permission;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 일감 첨부파일 REST — 올리기는 댓글을 달 수 있는 사람(issue.comment), 받기는 독자, 지우기는 올린 사람 또는 project.manage.
 * 일감의 정식 주소(워크스페이스 번호) 아래에 있고 판정은 그 일감의 지금 프로젝트로 한다({@link IssueApi}) — 독자가 아니면 404(첨부도 없는 것과 같다).
 */
@RestController
@RequestMapping(EndPointPaths.V1 + "/workspaces/{workspace}/issues/{number}/attachments")
public class AttachmentController {

  private final AttachmentService attachments;

  public AttachmentController(AttachmentService attachments) {
    this.attachments = attachments;
  }

  @GetMapping
  @IssueApi(Permission.ISSUE_READ)
  public List<AttachmentService.AttachmentView> list(ProjectContext ctx, @PathVariable int number) {
    return attachments.list(ctx, number);
  }

  /** multipart 의 file 하나. 응답의 markdown 을 본문 · 댓글에 넣으면 이미지가 그 자리에 그려진다 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @IssueApi(Permission.ISSUE_COMMENT)
  public AttachmentService.AttachmentView upload(ProjectContext ctx, CurrentUser user, @PathVariable int number,
      @RequestParam("file") MultipartFile file) throws IOException {
    if (file == null || file.isEmpty()) {
      throw ApiException.badRequest("EMPTY_FILE", "빈 파일은 올릴 수 없습니다.");
    }
    try (InputStream in = file.getInputStream()) {
      return attachments.upload(ctx, number, file.getOriginalFilename(), file.getContentType(), in, user.actor(),
          IssueJournal.Via.WEB);
    }
  }

  /**
   * 내용 — 이미지는 그 자리에서 보이고(inline), 그 밖은 늘 내려받기(attachment · octet-stream)로 준다.
   * 브라우저가 형식을 짐작하지 않게 nosniff, 내용이 스크립트로 돌지 않게 sandbox CSP.
   */
  @GetMapping("/{id}/content")
  @IssueApi(Permission.ISSUE_READ)
  public void content(ProjectContext ctx, @PathVariable int number, @PathVariable UUID id, HttpServletResponse response)
      throws IOException {
    AttachmentService.Content content = attachments.open(ctx, number, id);
    IssueAttachment a = content.meta();
    response.setContentType(a.isImage() ? a.getContentType() : "application/octet-stream");
    response.setContentLengthLong(a.getSizeBytes());
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
        (a.isImage() ? ContentDisposition.inline() : ContentDisposition.attachment())
            .filename(a.getFileName(), StandardCharsets.UTF_8).build().toString());
    safeHeaders(response, "\"" + a.getSha256() + "\"");
    try (InputStream in = content.stream()) {
      in.transferTo(response.getOutputStream());
    }
  }

  /**
   * 작은 미리보기 그림(긴 변 320px PNG) — 첨부 목록이 원본 대신 쓴다(일감 #19). 판정 · 머리글은 내용 받기와 같다:
   * 독자가 아니면 404, inline · nosniff · sandbox CSP. png · jpeg · gif 만 있고, 처음 받을 때 만든다. 없으면 404 ATTACHMENT_PREVIEW_NONE.
   */
  @GetMapping("/{id}/preview")
  @IssueApi(Permission.ISSUE_READ)
  public void preview(ProjectContext ctx, @PathVariable int number, @PathVariable UUID id, HttpServletResponse response)
      throws IOException {
    AttachmentService.Preview preview = attachments.preview(ctx, number, id);
    IssueAttachment a = preview.meta();
    response.setContentType(AttachmentPreviews.CONTENT_TYPE);
    response.setContentLength(preview.png().length);
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.inline().filename(previewName(a.getFileName()), StandardCharsets.UTF_8).build().toString());
    safeHeaders(response, "\"" + a.getSha256() + "-preview" + AttachmentPreviews.SIZE + "\"");
    response.getOutputStream().write(preview.png());
  }

  /** 받기 머리글 — 브라우저가 형식을 짐작하지 않게 nosniff, 내용이 스크립트로 돌지 않게 sandbox CSP. 내용 · 미리보기가 같이 쓴다 */
  private static void safeHeaders(HttpServletResponse response, String etag) {
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Content-Security-Policy", "default-src 'none'; sandbox");
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, max-age=86400");
    response.setHeader(HttpHeaders.ETAG, etag);
  }

  /** 화면.jpg → 화면-preview.png */
  static String previewName(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return (dot > 0 ? fileName.substring(0, dot) : fileName) + "-preview.png";
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @IssueApi(Permission.ISSUE_COMMENT)
  public void delete(ProjectContext ctx, CurrentUser user, @PathVariable int number, @PathVariable UUID id) {
    attachments.delete(ctx, number, id, user.actor(), IssueJournal.Via.WEB);
  }
}
