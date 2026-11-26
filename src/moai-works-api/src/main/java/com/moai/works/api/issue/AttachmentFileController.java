package com.moai.works.api.issue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.api.common.Actor;
import com.moai.works.api.security.BearerTokens;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 첨부 티켓으로 파일을 보내고 받는 곳 — 바깥 AI Agent 용({@code /mcp/files/**}, web-backend 의 /mcp 중계로 들어온다).
 *
 * <p>인증은 {@code Authorization: Bearer mwt_…}(일회용 티켓)뿐이다. MCP 키 · 로그인 세션은 쓰지 않는다. 판정은
 * {@link AttachmentTicketService#use} 한 곳 — 티켓을 원자적으로 쓰고, 키와 키 주인의 지금 권한을 다시 본다.
 * {@code /moai-works-api} 밖이라 접근 어노테이션 대상이 아니다(티켓이 그 자리).
 */
@RestController
public class AttachmentFileController {

  private final AttachmentTicketService tickets;
  private final AttachmentService attachments;

  public AttachmentFileController(AttachmentTicketService tickets, AttachmentService attachments) {
    this.tickets = tickets;
    this.attachments = attachments;
  }

  /** 올리기 — 본문이 파일 그대로. Content-Type 은 티켓에 정한 것이 우선이고 이미지는 내용으로 다시 판별한다 */
  @PutMapping("/mcp/files/upload")
  @ResponseStatus(HttpStatus.CREATED)
  public AttachmentService.AttachmentView upload(HttpServletRequest request) throws IOException {
    AttachmentTicketService.Use use = tickets.use(BearerTokens.read(request), AttachmentTicketService.Kind.UPLOAD);
    String type = use.contentType() != null ? use.contentType() : request.getContentType();
    try (InputStream in = request.getInputStream()) {
      return attachments.upload(use.ctx(), use.issueNumber(), use.fileName(), type, in, use.maxBytes(), use.agentSession(),
          Actor.user(use.principal().ownerUserId(), use.principal().ownerName(), null), IssueJournal.Via.MCP);
    }
  }

  /** 받기 — REST 의 받기와 같은 헤더(이미지가 아니면 늘 내려받기 · nosniff · sandbox) */
  @GetMapping("/mcp/files/download")
  public void download(HttpServletRequest request, HttpServletResponse response) throws IOException {
    AttachmentTicketService.Use use = tickets.use(BearerTokens.read(request), AttachmentTicketService.Kind.DOWNLOAD);
    AttachmentService.Content content = attachments.open(use.ctx(), use.issueNumber(), use.attachmentId());
    IssueAttachment a = content.meta();
    response.setContentType(a.isImage() ? a.getContentType() : "application/octet-stream");
    response.setContentLengthLong(a.getSizeBytes());
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.attachment().filename(a.getFileName(), StandardCharsets.UTF_8).build().toString());
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Content-Security-Policy", "default-src 'none'; sandbox");
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    try (InputStream in = content.stream()) {
      in.transferTo(response.getOutputStream());
    }
  }
}
