package com.moai.works.api.notification;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.security.access.UserApi;

/**
 * 내 알림함 — 로그인한 사람 자신의 알림만 다룬다(다른 사람의 알림은 어떤 값으로도 닿지 않는다).
 *
 * <p>워크스페이스의 일을 알리는 것이라 시스템 관리자 계정에게는 열지 않는다({@link UserApi} 기본) —
 * 그 계정은 워크스페이스의 멤버가 아니어서 받을 알림이 없다.
 */
@RestController
@RequestMapping(EndPointPaths.V1 + "/me/notifications")
@UserApi
public class NotificationController {

  private final NotificationService notifications;

  public NotificationController(NotificationService notifications) {
    this.notifications = notifications;
  }

  /** 알림함(새 것부터 30개씩). unread=true 면 읽지 않은 것만. before = 앞 쪽의 next */
  @GetMapping
  public NotificationViews.Inbox inbox(CurrentUser user, @RequestParam(defaultValue = "false") boolean unread,
      @RequestParam(required = false) String before, @RequestParam(defaultValue = "30") int limit) {
    return notifications.inbox(user.id(), unread, before, limit);
  }

  /** 읽지 않은 알림의 수 — 화면이 종 모양 옆의 숫자를 주기적으로 읽는다 */
  @GetMapping("/unread-count")
  public NotificationViews.UnreadCount unreadCount(CurrentUser user) {
    return new NotificationViews.UnreadCount(notifications.unreadCount(user.id()));
  }

  /** 알림 하나를 읽음으로 */
  @PostMapping("/{id}/read")
  public NotificationViews.UnreadCount read(CurrentUser user, @PathVariable UUID id) {
    notifications.read(user.id(), id);
    return new NotificationViews.UnreadCount(notifications.unreadCount(user.id()));
  }

  /** 알림 받기 설정 — 모든 종류와 알림함 · 메일 켬 · 끔 */
  @GetMapping("/preferences")
  public List<NotificationViews.Preference> preferences(CurrentUser user) {
    return notifications.preferences(user.id());
  }

  /**
   * @param enabled 알림함 (없으면 그대로)
   * @param email   메일 (없으면 그대로)
   */
  public record PreferenceRequest(Boolean enabled, Boolean email) {
  }

  /**
   * 종류 하나의 알림함 · 메일을 켜거나 끈다 — 꼭 받아야 하는 종류의 알림함은 끌 수 없다(400 NOTIFICATION_MANDATORY),
   * 둘 다 없으면 400 INVALID_PREFERENCE
   */
  @PutMapping("/preferences/{kind}")
  public List<NotificationViews.Preference> setPreference(CurrentUser user, @PathVariable String kind,
      @RequestBody PreferenceRequest r) {
    return notifications.setPreference(user.id(), kind, r.enabled(), r.email());
  }

  /** 모두 읽음으로 */
  @PostMapping("/read-all")
  public NotificationViews.ReadAll readAll(CurrentUser user) {
    return new NotificationViews.ReadAll(notifications.readAll(user.id()));
  }
}
