package com.moai.works.api.account;

import java.util.EnumSet;
import java.util.Set;

/** 계정 상태 (design/03 §7.2). ACTIVE 가 아니면 Keycloak 사용자도 enabled=false 다. */
public enum AccountStatus {
  PENDING_VERIFICATION,
  PENDING_APPROVAL,
  ACTIVE,
  REJECTED,
  SUSPENDED,
  DELETED;

  /** 허용된 다음 상태 */
  public boolean canMoveTo(AccountStatus next) {
    return next(this).contains(next);
  }

  private static Set<AccountStatus> next(AccountStatus from) {
    return switch (from) {
      case PENDING_VERIFICATION -> EnumSet.of(PENDING_APPROVAL, ACTIVE, DELETED);
      case PENDING_APPROVAL -> EnumSet.of(ACTIVE, REJECTED, DELETED);
      case ACTIVE -> EnumSet.of(SUSPENDED, DELETED);
      case SUSPENDED -> EnumSet.of(ACTIVE, DELETED);
      case REJECTED -> EnumSet.of(DELETED);
      case DELETED -> EnumSet.noneOf(AccountStatus.class);
    };
  }
}
