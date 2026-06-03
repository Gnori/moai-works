# F0-04 — Keycloak Identity Brokering (OIDC · SAML · OAuth2/소셜)

## 메타데이터
- **작성일**: 2026-06-03 · **개정**: 2026-06-03 (자체 OIDC → **Keycloak 브로커링**, SAML·OAuth2 포함)
- **작성자**: GnoRi · AI Assistant · **상태**: 1단계 · **규모**: standard · **레벨**: L3
- **우선순위**: P0 (Wave 0) · **상위**: [F0 마스터](./20260603-f0-saas-foundation-master.md)
- **의존**: [F0-03 Keycloak 통합](./20260603-f0-03-authentication.md) · [F0-06 사용자/멤버십](./20260603-f0-06-users-memberships.md)(매핑)

---

## 1. 요구사항 분석
### 1.1 문제 정의
기업 고객은 자사 IdP로 **SSO**(OIDC, **SAML 2.0**, **OAuth2/소셜**)를 요구한다. 이를 앱에서
직접 구현하지 않고 **Keycloak의 Identity Brokering** 으로 처리한다 — Keycloak이 외부 IdP와
연동하고, 앱은 F0-03 의 단일 OIDC 경로만 본다.

### 1.2 목표
- **테넌트(Organization)별 외부 IdP 등록·브로커링**: OIDC · **SAML 2.0** · **OAuth2/소셜**(Google/MS 등)
- 외부 로그인 → Keycloak 중개 → 앱은 동일 Keycloak 토큰 수신 (F0-03)
- 외부 사용자 **JIT 프로비저닝·매핑**(F0-06), claim/attribute 매핑

### 1.3 제약
- 외부 IdP 설정은 **Keycloak Admin(또는 IaC)** 에서 · 시크릿은 Keycloak 보관
- 테넌트 격리: Organization 범위 IdP · 앱 변경 최소(F0-03 경로 재사용)

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | OIDC IdP 브로커링(테넌트별) | Must |
| F-002 | **SAML 2.0 IdP 브로커링** | Must |
| F-003 | **OAuth2/소셜 IdP**(Google/Microsoft 등) | Should |
| F-004 | 외부 사용자 JIT 프로비저닝·attribute 매핑 | Must |
| F-005 | First-login flow·계정 연결/충돌 정책 | Should |

### 2.2 상세
- **F-001~003**: Keycloak Identity Provider 로 OIDC/SAML/소셜 등록(테넌트=Organization 범위).
  SAML 은 메타데이터(IdP) 임포트 + 서명/암호화 설정
- **F-004**: IdP claim/SAML attribute → Keycloak 사용자 → 앱 사용자(F0-06) 매핑(mapper)
- **F-005**: 동일 email 기존 계정 연결 정책(자동 링크/검증 후 링크/거부)

## 3. 기능 설계
### 3.1 스택
Keycloak Identity Brokering(OIDC/SAML/Social) · Realm/Org IaC · (앱은 F0-03 그대로)
### 3.2 흐름
```
사용자 → 앱 로그인 → Keycloak → (테넌트 IdP 선택) → 외부 IdP(OIDC/SAML/소셜) 인증
      → Keycloak 브로커가 사용자 매핑/프로비저닝 → Keycloak 토큰 발급 → 앱(F0-03)
```
### 3.3 설정/모델
- Keycloak: Organization 별 IdP(OIDC/SAML/Social) + Mapper. **Realm Import(IaC)** 로 관리
- 앱: 외부 IdP별 추가 코드 없음(F0-03 토큰 경로 공통). 매핑 결과만 F0-06 반영

## 4. 개발 체크리스트
- [ ] F-001 OIDC 브로커링 · [ ] F-002 **SAML** 브로커링 · [ ] F-003 소셜/OAuth2 · [ ] F-004 JIT/매핑 · [ ] F-005 연결정책
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 외부 OIDC SSO | Keycloak 중개 → 앱 토큰 |
| T-2 | **SAML SSO** | 메타데이터/서명 검증 → 로그인 |
| T-3 | 소셜(Google) | 로그인·프로비저닝 |
| T-4 | 테넌트별 IdP 라우팅 | 올바른 Organization IdP |
| T-5 | 동일 email 충돌 | 정책대로 처리 |

## 7. 최종 검토
- [ ] 테넌트별 OIDC/**SAML**/소셜 SSO 동작(Keycloak 브로커링) · 매핑/프로비저닝 · 격리 검증
- [ ] 앱 코드 변경 최소(F0-03 경로 재사용) 확인
