package com.moai.works.api.account;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.moai.works.api.common.ApiException;
import com.moai.works.api.keycloak.KeycloakException;

/**
 * Keycloak 의 비밀번호 정책 오류 → 400 {@code PASSWORD_POLICY}. 가입 · 비밀번호 변경 · 재설정이 같이 쓴다.
 *
 * <p>비밀번호 검사는 Keycloak 렐름 정책이 한다(지금 {@code length(8)}). 응답에는 까닭 {@code reason}(아래 값)과, 정책에 수가 있으면
 * {@code value}(예: 최소 길이 8)를 싣는다 — 화면은 이 둘로 그 언어의 문장을 만든다. 비밀번호는 오류 · 로그 어디에도 넣지 않는다.
 */
final class PasswordPolicyError {

  /** Keycloak 오류 키(invalidPassword…Message) → 까닭 · 한국어 문장({0} 은 값) */
  private enum Reason {
    MIN_LENGTH("MinLength", "비밀번호는 {0}자 이상이어야 합니다."),
    MAX_LENGTH("MaxLength", "비밀번호는 {0}자를 넘을 수 없습니다."),
    DIGITS("MinDigits", "비밀번호에 숫자가 {0}개 이상 있어야 합니다."),
    LOWER_CASE("MinLowerCaseChars", "비밀번호에 영문 소문자가 {0}개 이상 있어야 합니다."),
    UPPER_CASE("MinUpperCaseChars", "비밀번호에 영문 대문자가 {0}개 이상 있어야 합니다."),
    SPECIAL_CHARS("MinSpecialChars", "비밀번호에 특수 문자가 {0}개 이상 있어야 합니다."),
    NOT_USERNAME("NotUsername", "비밀번호는 아이디와 같을 수 없습니다."),
    NOT_CONTAINS_USERNAME("NotContainsUsername", "비밀번호에 아이디를 넣을 수 없습니다."),
    NOT_EMAIL("NotEmail", "비밀번호는 이메일과 같을 수 없습니다."),
    PATTERN("RegexPattern", "비밀번호가 정해진 형식에 맞지 않습니다."),
    HISTORY("History", "최근에 쓴 비밀번호는 다시 쓸 수 없습니다."),
    BLACKLISTED("Blacklisted", "너무 흔한 비밀번호입니다. 다른 비밀번호를 쓰십시오."),
    OTHER(null, "비밀번호가 비밀번호 정책에 맞지 않습니다.");

    final String key;
    final String text;

    Reason(String key, String text) {
      this.key = key;
      this.text = text;
    }
  }

  /** 정책의 수(최소 길이 8 등) — Keycloak 오류 설명 · params 의 첫 수. 네 자리까지만 */
  private static final Pattern NUMBER = Pattern.compile("(?<!\\d)(\\d{1,4})(?!\\d)");

  private PasswordPolicyError() {
  }

  /** Keycloak 오류가 비밀번호 정책 위반이면 그 400, 아니면 empty */
  static Optional<ApiException> from(KeycloakException e) {
    String body = e.body();
    if (e.status() != 400 || !body.toLowerCase(Locale.ROOT).contains("password")) {
      return Optional.empty();
    }
    Reason reason = Reason.OTHER;
    for (Reason r : Reason.values()) {
      if (r.key != null && body.contains("invalidPassword" + r.key + "Message")) {
        reason = r;
        break;
      }
    }
    String value = null;
    if (reason.text.contains("{0}")) {
      Matcher m = NUMBER.matcher(body);
      value = m.find() ? m.group(1) : null;
      if (value == null) {
        reason = Reason.OTHER;
      }
    }
    String detail = value == null ? reason.text : reason.text.replace("{0}", value);
    ApiException error = ApiException.badRequest("PASSWORD_POLICY", detail).with("reason", reason.name());
    if (value != null) {
      error.with("value", Integer.parseInt(value));
    }
    return Optional.of(error);
  }
}
