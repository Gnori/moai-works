package com.moai.works.api.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.moai.works.api.config.MoaiWorksProperties;

/** 메일 모드 · smtp 설정 · 받는 사람 가리기 — 스프링 없이 */
class MailerTest {

  private static MoaiWorksProperties props(String mode, MoaiWorksProperties.Smtp smtp) {
    return new MoaiWorksProperties(null, null, null, new MoaiWorksProperties.Mail(mode, "no-reply@moai-works.local", smtp, "-"),
        null, null);
  }

  @Test
  void 받는_사람은_첫_글자와_도메인만_남긴다() {
    assertThat(Mailer.mask("alice@example.com")).isEqualTo("a***@example.com");
    assertThat(Mailer.mask("@example.com")).isEqualTo("***");
    assertThat(Mailer.mask("nobody")).isEqualTo("***");
    assertThat(Mailer.mask(null)).isEqualTo("?");
  }

  @Test
  void 제목은_한_줄이다() {
    assertThat(Mailer.oneLine("가\r\nBcc: x@y\t나")).isEqualTo("가 Bcc: x@y 나");
  }

  @Test
  void 모르는_모드와_서버_없는_smtp_와_평문_smtp_는_기동하지_않는다() {
    assertThatThrownBy(() -> new Mailer(props("console", null))).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new Mailer(props("smtp", new MoaiWorksProperties.Smtp("", 587, "", "", "starttls"))))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("MOAI_WORKS_SMTP_HOST");
    assertThatThrownBy(() -> new Mailer(props("smtp", new MoaiWorksProperties.Smtp("smtp.example.com", 25, "", "", "none"))))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("평문");
    new Mailer(props("log", null));   // 로그 모드는 smtp 값이 없어도 뜬다
  }

  @Test
  void smtp_는_STARTTLS_를_꼭_쓰고_서버_인증서를_확인한다() {
    JavaMailSenderImpl starttls = Mailer.smtpSender(new MoaiWorksProperties.Smtp("smtp.example.com", 587, "bot", "pw", "starttls"));
    assertThat(starttls.getProtocol()).isEqualTo("smtp");
    assertThat(starttls.getHost()).isEqualTo("smtp.example.com");
    assertThat(starttls.getJavaMailProperties()).containsEntry("mail.smtp.auth", "true")
        .containsEntry("mail.smtp.starttls.enable", "true").containsEntry("mail.smtp.starttls.required", "true")
        .containsEntry("mail.smtp.ssl.checkserveridentity", "true").containsKey("mail.smtp.timeout");

    JavaMailSenderImpl ssl = Mailer.smtpSender(new MoaiWorksProperties.Smtp("smtp.example.com", 465, "", "", "ssl"));
    assertThat(ssl.getProtocol()).isEqualTo("smtps");
    assertThat(ssl.getUsername()).isNull();
    assertThat(ssl.getJavaMailProperties()).containsEntry("mail.smtps.auth", "false")
        .containsEntry("mail.smtps.ssl.checkserveridentity", "true");
  }
}
