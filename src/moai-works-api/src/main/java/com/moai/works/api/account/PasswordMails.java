package com.moai.works.api.account;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.moai.works.api.config.MoaiWorksProperties;

/**
 * 비밀번호 메일 — 재설정 코드와 "비밀번호가 바뀌었습니다" 알림. 계정 메일이라 {@link Mailer#send}(가입 인증 코드와 같은 길)로 보낸다.
 * 알림 메일 보낼 통(notification_mail)은 워크스페이스 알림을 보낼 때 다시 판정하는 장치라 쓰지 않는다 — 이 메일은 계정 주인에게 바로 간다.
 *
 * <p>보내지 못해도 비밀번호 일은 되돌리지 않는다(경고 로그만 — 주소는 가린다). 본문에 비밀번호는 넣지 않는다.
 * 로그 모드(로컬)에서는 본문이 로그에 남는다 — 로컬 개발자가 재설정 코드를 읽는 길이다. 운영은 smtp 모드다.
 */
@Component
class PasswordMails {

  private static final Logger log = LoggerFactory.getLogger(PasswordMails.class);
  private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));

  /** 어떻게 바뀌었나 */
  enum How {
    /** 로그인한 사람이 개인 설정에서 */
    CHANGE,
    /** 메일 코드로 다시 정함 */
    RESET
  }

  private final Mailer mailer;
  private final String webBaseUrl;

  PasswordMails(Mailer mailer, MoaiWorksProperties properties) {
    this.mailer = mailer;
    String base = properties.web() == null ? null : properties.web().baseUrl();
    base = base == null || base.isBlank() ? "https://localhost:11002" : base.strip();
    this.webBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
  }

  void sendResetCode(String email, String code) {
    deliver(email, "[moai-works] 비밀번호 재설정 코드",
        "비밀번호 재설정 코드: " + code + "\n"
            + "10분 안에 비밀번호 재설정 화면에 입력하십시오.\n"
            + "요청하지 않았다면 이 메일을 무시하십시오 — 코드를 넣기 전에는 비밀번호가 바뀌지 않습니다.");
  }

  void sendChanged(UserProfile user, How how, String ip, Instant at, boolean otherSessionsEnded) {
    String way = how == How.RESET ? "메일로 받은 코드로 비밀번호를 다시 정했습니다." : "로그인한 화면(개인 설정)에서 비밀번호를 바꿨습니다.";
    String sessions = otherSessionsEnded ? "\n다른 기기 · 브라우저의 로그인은 모두 끝냈습니다." : "";
    deliver(user.getEmail(), "[moai-works] 비밀번호가 바뀌었습니다",
        user.getDisplayName() + " 님, moai-works 계정(" + user.getUsername() + ")의 비밀번호가 바뀌었습니다.\n\n"
            + "때: " + WHEN.format(at) + " (한국 시간)\n"
            + "방법: " + way + "\n"
            + "요청한 곳(IP): " + (ip == null ? "알 수 없음" : ip)
            + sessions + "\n\n"
            + "직접 바꾸지 않았다면 바로 비밀번호를 다시 정하고(" + webBaseUrl + "/password-reset) 관리자에게 알리십시오.");
  }

  private void deliver(String to, String subject, String body) {
    try {
      mailer.send(to, subject, body);
    } catch (RuntimeException e) {
      log.warn("비밀번호 메일을 보내지 못했습니다 — 받는 사람={} 까닭={}", Mailer.mask(to), e.getClass().getSimpleName());
    }
  }
}
