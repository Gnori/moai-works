package com.moai.works.api.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.moai.works.api.config.MoaiWorksProperties;

/**
 * 메일 보내기. 지금은 {@code moai-works.mail.mode=log} 만 있다 — SMTP 가 없는 로컬 개발용으로 본문을 로그에 남긴다.
 * SMTP 를 붙일 때 {@code smtp} 모드를 더한다(design/03 §17 미결정: 메일 서버).
 */
@Component
public class Mailer {

  private static final Logger log = LoggerFactory.getLogger(Mailer.class);

  private final MoaiWorksProperties.Mail mail;

  public Mailer(MoaiWorksProperties properties) {
    this.mail = properties.mail();
  }

  public void send(String to, String subject, String body) {
    if (!"log".equals(mail.mode())) {
      throw new IllegalStateException("지원하지 않는 메일 모드: " + mail.mode());
    }
    log.info("[메일 · 로그 모드] 받는 사람={} 제목={}\n{}", to, subject, body);
  }
}
