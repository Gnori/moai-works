package com.moai.works.api.account;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * 요청과 떼어 뒤에서 하는 일 — 비밀번호 재설정 요청의 계정 찾기 · 코드 만들기 · 메일 보내기.
 *
 * <p>재설정 요청은 계정이 있든 없든 같은 응답을 <b>같은 때에</b> 돌려줘야 한다(계정이 있는지 드러내지 않는다). 그래서 응답은 속도 제한만 보고
 * 바로 나가고, 계정을 찾고 메일을 보내는 일은 여기서 한다(메일 서버가 느려도 응답 시간이 갈리지 않는다).
 * 일꾼은 하나(기다리는 일이 200개를 넘으면 둘), 그래도 넘치면 버리고 경고 로그만 남긴다(속도 제한을 넘는 몰림이라 응답은 그대로).
 */
@Component
public class BackgroundTasks implements DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(BackgroundTasks.class);

  private final ThreadPoolExecutor executor;

  public BackgroundTasks() {
    AtomicInteger count = new AtomicInteger();
    this.executor = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(200), r -> {
      Thread t = new Thread(r, "account-background-" + count.incrementAndGet());
      t.setDaemon(true);
      return t;
    }, (r, e) -> log.warn("뒤에서 할 일이 너무 많아 하나를 버렸습니다(비밀번호 재설정 메일)"));
  }

  /** 뒤에서 한다. 실패는 로그만 남긴다(부른 쪽 응답은 이미 나갔다) */
  public void run(String what, Runnable task) {
    executor.execute(() -> {
      try {
        task.run();
      } catch (RuntimeException e) {
        log.warn("뒤에서 한 일이 실패했습니다({}): {}", what, e.getClass().getSimpleName());
      }
    });
  }

  @Override
  public void destroy() throws InterruptedException {
    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS);
  }
}
