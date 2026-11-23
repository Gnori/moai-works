package com.moai.works.api.account;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.security.ClientIp;
import com.moai.works.api.security.access.PublicApi;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 찾기 (FR-WSP-006) — 공개 경로(속도 제한). 가입과 같이 web-backend 가 토큰 없이 그대로 넘기고, CSRF 는 web-backend 가 본다.
 * 계정이 있는지 드러내지 않는다 — {@link PasswordResetService}.
 */
@RestController
@RequestMapping(EndPointPaths.V1 + "/password-reset")
@PublicApi
public class PasswordResetController {

  private final PasswordResetService resets;
  private final ClientIp clientIp;

  public PasswordResetController(PasswordResetService resets, ClientIp clientIp) {
    this.resets = resets;
    this.clientIp = clientIp;
  }

  public record ResetRequest(@NotBlank @Email @Size(max = 320) String email) {
  }

  public record ResetConfirm(@NotBlank @Email @Size(max = 320) String email,
      @NotBlank @Pattern(regexp = "^\\d{6}$") String code,
      @NotBlank @Size(min = 8, max = 128) String newPassword) {
  }

  /** 코드를 메일로 보내 달라 — 계정이 있든 없든 같은 202(본문 없음) */
  @PostMapping("/request")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void request(@Valid @RequestBody ResetRequest r, HttpServletRequest http) {
    resets.request(r.email(), clientIp.of(http));
  }

  /** 코드 + 새 비밀번호. 성공하면 그 계정의 로그인을 모두 끊는다 — 화면은 로그인으로 보낸다 */
  @PostMapping("/confirm")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void confirm(@Valid @RequestBody ResetConfirm r, HttpServletRequest http) {
    resets.confirm(r.email(), r.code(), r.newPassword(), clientIp.of(http));
  }
}
