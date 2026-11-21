package com.moai.works.api.common;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.moai.works.api.keycloak.KeycloakException;

/** 오류를 RFC 9457 problem+json 으로 바꾼다. 모든 응답에 {@code code} 속성이 있다. */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> api(ApiException e) {
    return problem(e.status(), e.code(), e.getMessage(), e.properties().isEmpty() ? null : e.properties());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> invalid(MethodArgumentNotValidException e) {
    List<Map<String, String>> fields = e.getBindingResult().getFieldErrors().stream()
        .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
        .toList();
    return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "입력값을 확인하십시오.", Map.of("errors", fields));
  }

  @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
  ResponseEntity<ProblemDetail> denied(RuntimeException e) {
    return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다.", null);
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ResponseEntity<ProblemDetail> conflict(ObjectOptimisticLockingFailureException e) {
    return problem(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "다른 요청이 먼저 바꿨습니다. 다시 시도하십시오.", null);
  }

  @ExceptionHandler(KeycloakException.class)
  ResponseEntity<ProblemDetail> keycloak(KeycloakException e) {
    log.error("Keycloak 오류: {}", e.getMessage());
    return problem(HttpStatus.BAD_GATEWAY, "IDENTITY_PROVIDER_ERROR",
        "계정 서버(Keycloak) 처리에 실패했습니다. 잠시 뒤 다시 시도하십시오.", null);
  }

  /** 올린 파일이 서버가 받는 크기(spring.servlet.multipart)를 넘었다 — 첨부 크기 상한(moai-works.storage.max-file-mb)과 같은 코드로 답한다 */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ProblemDetail> tooLarge(MaxUploadSizeExceededException e) {
    return problem(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "파일이 너무 큽니다.", null);
  }

  /** 파일 올리기 요청이 깨졌거나(끊긴 전송 · 잘못된 multipart) 파일 칸이 없다 */
  @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class})
  ResponseEntity<ProblemDetail> brokenUpload(Exception e) {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "올린 파일을 읽을 수 없습니다. 다시 올려 주십시오.", null);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ProblemDetail> illegalArgument(IllegalArgumentException e) {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage(), null);
  }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<ProblemDetail> illegalState(IllegalStateException e) {
    return problem(HttpStatus.CONFLICT, "INVALID_STATE", e.getMessage(), null);
  }

  private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String message,
      Map<String, Object> extra) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(status, message);
    body.setTitle(status.getReasonPhrase());
    body.setProperty("code", code);
    if (extra != null) {
      extra.forEach(body::setProperty);
    }
    return ResponseEntity.status(status).body(body);
  }
}
