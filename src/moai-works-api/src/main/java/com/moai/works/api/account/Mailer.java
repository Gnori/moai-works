package com.moai.works.api.account;

import java.time.Duration;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.moai.works.api.config.MoaiWorksProperties;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * 메일 보내기 — {@code moai-works.mail.mode}.
 *
 * <ul>
 *   <li>{@code log}(기본) — 보내지 않고 로그에 남긴다. SMTP 가 정해지기 전(WBS D-06) · 로컬 개발용</li>
 *   <li>{@code smtp} — Spring 의 {@link JavaMailSenderImpl} 로 보낸다. 서버 · 계정은 {@code moai-works.mail.smtp.*}(env 로만).
 *       평문 SMTP 는 없다(STARTTLS 필수 또는 SSL, 서버 인증서 확인). 아직 어디서도 켜지 않았다</li>
 * </ul>
 *
 * <p>{@code spring.mail.*} 을 쓰지 않고 여기서 직접 만든다 — 자동 설정이 켜지면 메일 건강 검사가 api 건강 상태에 붙어,
 * 메일 서버가 안 되면 api 가 죽은 것처럼 보인다. 모르는 모드 · smtp 인데 서버가 없으면 기동하지 않는다.
 */
@Component
public class Mailer {

  private static final Logger log = LoggerFactory.getLogger(Mailer.class);
  /** 메일 서버가 느려도 보내기가 이보다 오래 붙잡지 않는다(알림 메일은 보낼 통의 줄을 잠근 채 보낸다) */
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final MoaiWorksProperties.Mail mail;
  /** smtp 모드에서만 있다 */
  private final JavaMailSenderImpl smtp;

  public Mailer(MoaiWorksProperties properties) {
    this.mail = properties.mail();
    if (mail.smtpMode()) {
      this.smtp = smtpSender(mail.smtp());
    } else if ("log".equals(mail.mode())) {
      this.smtp = null;
    } else {
      throw new IllegalStateException("지원하지 않는 메일 모드: " + mail.mode() + " (log · smtp)");
    }
  }

  /**
   * 계정 메일(이메일 인증 코드 · 초대). 로그 모드에서는 로컬 개발자가 코드 · 링크를 읽을 수 있게 본문까지 남긴다 — 운영은 smtp 모드다.
   */
  public void send(String to, String subject, String body) {
    if (smtp == null) {
      log.info("[메일 · 로그 모드] 받는 사람={} 제목={}\n{}", to, subject, body);
      return;
    }
    deliver(to, subject, body, null);
  }

  /**
   * 알림 메일 (FR-NOTI-006). 로그 모드에서도 받는 사람 주소와 본문은 남기지 않는다 — 가린 받는 사람 · 종류 · 제목 한 줄뿐이다.
   *
   * @param mailId 보낼 통(notification_mail)의 id. Message-ID 를 이 값으로 정해, 다시 보내더라도 받는 쪽이 같은 메일로 본다
   * @throws org.springframework.mail.MailException 보내지 못했으면 — 부른 쪽이 다시 보낼 때를 정한다
   */
  public void sendNotification(String to, String kind, String subject, String body, UUID mailId) {
    if (smtp == null) {
      log.info("[메일 · 로그 모드] 알림 메일 받는 사람={} 종류={} 제목={}", mask(to), kind, oneLine(subject));
      return;
    }
    deliver(to, subject, body, "<" + mailId + ".notification@" + domainOf(mail.from()) + ">");
  }

  private void deliver(String to, String subject, String body, String messageId) {
    MimeMessage message = smtp.createMimeMessage();
    try {
      MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
      helper.setFrom(mail.from());
      helper.setTo(to);
      helper.setSubject(oneLine(subject));
      helper.setText(body, false);
      if (messageId != null) {
        // JavaMailSenderImpl 은 미리 정한 Message-ID 를 지켜 준다
        message.setHeader("Message-ID", messageId);
      }
    } catch (MessagingException e) {
      throw new MailPreparationException("메일을 만들지 못했습니다", e);
    }
    smtp.send(message);
  }

  /** 받는 사람을 가린다 — {@code alice@example.com} → {@code a***@example.com} */
  public static String mask(String email) {
    if (email == null) {
      return "?";
    }
    int at = email.indexOf('@');
    return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
  }

  /** 제목은 한 줄 — 일감 제목에 든 줄바꿈 · 제어 문자로 머리글이나 로그 줄을 꾸미지 못하게 */
  static String oneLine(String value) {
    return value == null ? "" : value.replaceAll("\\p{Cntrl}+", " ").strip();
  }

  private static String domainOf(String address) {
    int at = address == null ? -1 : address.lastIndexOf('@');
    String domain = at < 0 ? "" : address.substring(at + 1).replaceAll("[^A-Za-z0-9.-]", "");
    return domain.isEmpty() ? "moai-works.local" : domain;
  }

  static JavaMailSenderImpl smtpSender(MoaiWorksProperties.Smtp s) {
    if (s == null || s.host() == null || s.host().isBlank()) {
      throw new IllegalStateException("메일 smtp 모드인데 MOAI_WORKS_SMTP_HOST 가 비어 있습니다.");
    }
    String tls = s.tls() == null ? "starttls" : s.tls().strip().toLowerCase(Locale.ROOT);
    if (!"starttls".equals(tls) && !"ssl".equals(tls)) {
      throw new IllegalStateException("MOAI_WORKS_SMTP_TLS 는 starttls 또는 ssl 이어야 합니다(평문 SMTP 는 쓰지 않는다): " + tls);
    }
    String protocol = "ssl".equals(tls) ? "smtps" : "smtp";
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setProtocol(protocol);
    sender.setHost(s.host().strip());
    sender.setPort(s.port());
    sender.setDefaultEncoding("UTF-8");
    boolean auth = s.username() != null && !s.username().isBlank();
    if (auth) {
      sender.setUsername(s.username().strip());
      sender.setPassword(s.password());
    }
    Properties p = sender.getJavaMailProperties();
    String prefix = "mail." + protocol + ".";
    p.setProperty(prefix + "auth", String.valueOf(auth));
    if ("starttls".equals(tls)) {
      p.setProperty(prefix + "starttls.enable", "true");
      p.setProperty(prefix + "starttls.required", "true");
    }
    p.setProperty(prefix + "ssl.checkserveridentity", "true");
    String millis = String.valueOf(TIMEOUT.toMillis());
    p.setProperty(prefix + "connectiontimeout", millis);
    p.setProperty(prefix + "timeout", millis);
    p.setProperty(prefix + "writetimeout", millis);
    return sender;
  }
}
