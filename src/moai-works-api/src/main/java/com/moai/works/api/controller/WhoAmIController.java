package com.moai.works.api.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.security.KeycloakPrincipal;
import com.moai.works.api.security.access.AuthenticatedApi;

/**
 * 토큰 주인 확인 — 토큰 내용 그대로. 사람 · 서비스 계정 모두(연결 확인용).
 * 사람 계정의 프로필은 {@code /v1/me}.
 */
@RestController
@AuthenticatedApi
public class WhoAmIController {

  @GetMapping(EndPointPaths.BASE_PATH + "/me")
  public KeycloakPrincipal whoAmI(@AuthenticationPrincipal KeycloakPrincipal me) {
    return me;
  }
}
