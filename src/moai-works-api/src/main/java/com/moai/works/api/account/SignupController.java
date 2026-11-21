package com.moai.works.api.account;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.platform.PlatformSettingService;
import com.moai.works.api.platform.SignupPolicy;
import com.moai.works.api.security.access.PublicApi;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 회원가입 — 공개 경로(속도 제한). web-backend 가 브라우저 요청을 그대로 넘긴다. */
@RestController
@RequestMapping(EndPointPaths.V1 + "/signup")
@PublicApi
public class SignupController {

  private final SignupService signup;
  private final PlatformSettingService settings;

  public SignupController(SignupService signup, PlatformSettingService settings) {
    this.signup = signup;
    this.settings = settings;
  }

  public record SignupRequest(@NotBlank @Email @Size(max = 320) String email,
      @NotBlank @Size(min = 8, max = 128) String password,
      @NotBlank @Size(max = 100) String displayName,
      @AssertTrue(message = "약관에 동의해야 합니다") boolean agreeTerms) {
  }

  public record VerifyRequest(@NotBlank @Email String email, @NotBlank @Pattern(regexp = "^\\d{6}$") String code) {
  }

  public record ResendRequest(@NotBlank @Email String email) {
  }

  /** 가입 화면이 보여 줄 정책 (도메인 제한 · 인증 · 승인 여부) */
  public record PublicPolicy(boolean enabled, List<String> allowedEmailDomains, boolean requireEmailVerification,
      boolean requireAdminApproval) {
  }

  @GetMapping("/policy")
  public PublicPolicy policy() {
    SignupPolicy p = settings.signupPolicy();
    return new PublicPolicy(p.enabled(), p.allowedEmailDomains(), p.requireEmailVerification(), p.requireAdminApproval());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SignupService.SignupResult signup(@Valid @RequestBody SignupRequest r, HttpServletRequest http) {
    return signup.signup(r.email(), r.password(), r.displayName().trim(), ip(http));
  }

  public record VerifyResult(AccountStatus status) {
  }

  @PostMapping("/verify")
  public VerifyResult verify(@Valid @RequestBody VerifyRequest r, HttpServletRequest http) {
    return new VerifyResult(signup.verify(r.email(), r.code(), ip(http)));
  }

  @PostMapping("/resend")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void resend(@Valid @RequestBody ResendRequest r, HttpServletRequest http) {
    signup.resend(r.email(), ip(http));
  }

  private static String ip(HttpServletRequest http) {
    String forwarded = http.getHeader("X-Forwarded-For");
    return forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : http.getRemoteAddr();
  }
}
