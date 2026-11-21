package com.moai.works.api.platform;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moai.works.api.audit.AuditService;
import com.moai.works.api.common.Actor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 플랫폼 설정 읽기 · 바꾸기. 가입 정책은 키 6개를 한 묶음({@link SignupPolicy})으로 다룬다. */
@Service
public class PlatformSettingService {

  static final String ENABLED = "signup.enabled";
  static final String DOMAINS = "signup.allowed_email_domains";
  static final String VERIFY = "signup.require_email_verification";
  static final String APPROVAL = "signup.require_admin_approval";
  static final String RATE = "signup.rate_limit_per_ip_hour";
  static final String TEAM_GRANT = "workspace.team_workspace_grant_default";

  private final Repository settings;
  private final JsonMapper json;
  private final AuditService audit;
  private final Clock clock;

  public PlatformSettingService(Repository settings, JsonMapper json, AuditService audit, Clock clock) {
    this.settings = settings;
    this.json = json;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public SignupPolicy signupPolicy() {
    Map<String, JsonNode> v = new LinkedHashMap<>();
    settings.findAllById(List.of(ENABLED, DOMAINS, VERIFY, APPROVAL, RATE, TEAM_GRANT))
        .forEach(s -> v.put(s.getKey(), json.readTree(s.getValue())));
    return new SignupPolicy(
        bool(v, ENABLED, true),
        v.containsKey(DOMAINS) ? json.convertValue(v.get(DOMAINS), json.getTypeFactory()
            .constructCollectionType(List.class, String.class)) : List.of(),
        bool(v, VERIFY, true),
        bool(v, APPROVAL, false),
        v.containsKey(RATE) ? v.get(RATE).asInt() : 10,
        v.containsKey(TEAM_GRANT) ? v.get(TEAM_GRANT).asInt() : 2);
  }

  @Transactional
  public SignupPolicy updateSignupPolicy(SignupPolicy next, Actor actor) {
    SignupPolicy before = signupPolicy();
    List<String> domains = next.allowedEmailDomains().stream().map(String::toLowerCase).distinct().sorted().toList();
    Instant now = Instant.now(clock);
    put(ENABLED, next.enabled(), actor.id(), now);
    put(DOMAINS, domains, actor.id(), now);
    put(VERIFY, next.requireEmailVerification(), actor.id(), now);
    put(APPROVAL, next.requireAdminApproval(), actor.id(), now);
    put(RATE, next.rateLimitPerIpHour(), actor.id(), now);
    put(TEAM_GRANT, next.teamWorkspaceGrantDefault(), actor.id(), now);
    SignupPolicy after = signupPolicy();
    audit.record(actor, null, "platform.signup_policy.update", "platform_setting", "signup",
        Map.of("before", before, "after", after));
    return after;
  }

  private void put(String key, Object value, UUID by, Instant now) {
    String text = json.writeValueAsString(value);
    settings.findById(key).ifPresentOrElse(s -> s.change(text, by, now),
        () -> settings.save(new PlatformSetting(key, text, by, now)));
  }

  private static boolean bool(Map<String, JsonNode> v, String key, boolean fallback) {
    return v.containsKey(key) ? v.get(key).asBoolean() : fallback;
  }

  public interface Repository extends JpaRepository<PlatformSetting, String> {
  }
}
