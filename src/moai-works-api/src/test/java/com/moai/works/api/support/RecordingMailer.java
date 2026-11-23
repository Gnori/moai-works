package com.moai.works.api.support;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.mail.MailSendException;

import com.moai.works.api.account.Mailer;
import com.moai.works.api.config.MoaiWorksProperties;

/**
 * 시험용 메일 — 실제 {@link Mailer}(로그 모드)를 그대로 부르고, 보낸 알림 메일을 받아 둔다.
 * 시험이 정한 주소는 정한 횟수만큼 메일 서버가 받지 않은 것처럼 실패시키고(다시 보내기 시험), 느리게 할 수 있다(여럿이 함께 보내기 시험).
 * 계정 메일(인증 코드 · 초대)은 그대로 부모에게 넘긴다.
 */
public class RecordingMailer extends Mailer {

  /** 보낸 알림 메일 한 통 */
  public record Sent(String to, String kind, String subject, String body, UUID mailId) {
  }

  private final List<Sent> sent = new CopyOnWriteArrayList<>();
  private final Map<String, AtomicInteger> failures = new ConcurrentHashMap<>();
  private final Map<String, Duration> delays = new ConcurrentHashMap<>();

  public RecordingMailer(MoaiWorksProperties properties) {
    super(properties);
  }

  @Override
  public void sendNotification(String to, String kind, String subject, String body, UUID mailId) {
    AtomicInteger left = failures.get(to);
    if (left != null && left.getAndDecrement() > 0) {
      // 진짜 메일 서버의 거절처럼 주소가 든 글 — 보내는 쪽이 까닭을 적을 때 주소를 가리는지 본다
      throw new MailSendException("시험: 메일 서버가 받지 않음 <" + to + ">");
    }
    Duration delay = delays.get(to);
    if (delay != null) {
      try {
        Thread.sleep(delay.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    super.sendNotification(to, kind, subject, body, mailId);
    sent.add(new Sent(to, kind, subject, body, mailId));
  }

  /** 그 주소로 보낸 알림 메일 (보낸 순서) */
  public List<Sent> sentTo(String to) {
    return sent.stream().filter(s -> s.to().equals(to)).toList();
  }

  /** 그 주소로 보내기를 다음 times 번 실패시킨다 */
  public void failNext(String to, int times) {
    failures.put(to, new AtomicInteger(times));
  }

  /** 그 주소로 보내기를 늦춘다 */
  public void slow(String to, Duration delay) {
    delays.put(to, delay);
  }
}
