# F10-02 — 결재선 설계

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: high (Wave 2)
- **상위**: [F10 결재 마스터](./20260603-f10-approval-master.md)
- **의존**: [F0-01 테넌트 격리](./20260603-f0-01-tenant-isolation.md)·[F0-07 RBAC](./20260603-f0-07-rbac.md)·[F0-08 감사](./20260603-f0-08-audit-log.md)·[F1-06 조직 조회](./20260603-f1-06-org-query-view.md)·[F3 알림](./20260603-f3-notification-master.md)

## 1. 요구사항 분석
### 1.1 문제 정의
결재 문서는 **누구에게 어떤 순서로** 흘러야 하는지 정의가 필요하다. 조직 결재는
직렬뿐 아니라 **병렬·합의·전결·대결·위임** 등 다양한 형태를 가지며, 금액·부서에 따라
**조건부로 결재선이 달라진다**. 본 spec 은 결재선 구성·조건 분기·템플릿·단계 모델을 정의한다.
### 1.2 목표
- 결재 단계(Step) 모델 + **6종 결재 유형**(직렬/병렬/합의/전결/대결/위임)
- **조건부 결재선**(금액·부서 기반, F1-06 상위자 체인 활용) 자동 구성
- **결재선 템플릿**(재사용·기안 시 적용) · 격리 · 권한(F0-07) · 감사(F0-08)

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 결재 단계(Step) 모델 + 6종 유형(직렬/병렬/합의/전결/대결/위임) | Must |
| F-002 | 결재선 동적 생성(F1-06 상위자 체인 N단계) | Must |
| F-003 | 조건부 결재선(금액·부서 룰 → 단계 분기) | Must |
| F-004 | 결재선 템플릿 CRUD + 기안 시 적용 | Should |
| F-005 | 위임/대결 처리(위임자→수임자, 부재 대체) | Should |
| F-006 | 결재선 검증(빈 단계·자기결재·순환 차단) | Must |

## 3. 기능 설계
### 3.1 결재 유형 정의
- **직렬(SERIAL)**: 단계 순차 진행, 한 명 승인 후 다음
- **병렬(PARALLEL)**: 동일 단계 다수 결재자 **동시** 진행
- **합의(AGREEMENT)**: 병렬이되 **전원 동의** 필요(1명 반려 시 반려)
- **전결(DELEGATED_FINAL)**: 특정 단계에서 **상위 단계 생략하고 종결**(위임 권한)
- **대결(ACTING)**: 결재자 부재 시 **대리자**가 본인 명의로 결재(사후 표시)
- **위임(DELEGATION)**: 사전 설정으로 수임자가 결재권 위임받아 처리

### 3.2 데이터 모델
- `approval_line(id, tenant_id, document_id, status)` — 문서별 확정 결재선
- `approval_step(id, tenant_id, line_id, seq, type, mode=serial/parallel/agreement, status)` — 단계
- `approval_approver(id, tenant_id, step_id, user_id, role, delegate_user_id, status, acted_at)` — 결재자(role=approver/agreement/acting/final)
- `approval_line_template(id, tenant_id, name, dept_scope, definition_json)` — 템플릿
- `approval_condition_rule(id, tenant_id, template_id, field=amount/dept, operator, value, action_json)` — 조건 룰
- 인덱스: `(tenant_id, document_id)` · `(tenant_id, step_id, user_id)`

### 3.3 흐름 / API
```
기안 → 템플릿 선택 or 자동
  → 조건 룰 평가(금액/부서) → 적용 액션(단계 추가/스킵/결재자 변경)
  → F1-06 상위자 체인 조회(N단계) → 결재자 채움
  → 검증(빈 단계·자기결재·순환) → approval_line 확정
  → 위임 설정 있으면 수임자로 치환 · F3 알림
```
- `POST /api/approval/lines` — 결재선 생성(템플릿·조건 적용) · `@RequiresPermission("approval:submit")`
- `GET /api/approval/lines/{documentId}` — 결재선 조회 · `approval:read`
- `POST /api/approval/lines/preview` — 조건 평가 결과 미리보기
- `CRUD /api/approval/line-templates` — 템플릿 관리 · `approval:manage`
- 모든 조회/생성 F0-01 격리 · 변경 F0-08 감사

### 3.4 조건부 결재선 평가
- 룰: `field(amount/dept) operator(>=, <, ==, in) value → action`
- action 예: 금액 ≥ 1000만 → **임원 단계 추가**, 부서=재무 → **재무팀장 합의 단계 삽입**
- 상위자 체인(F1-06): `org-query`로 기안자 기준 N단계 상위자 자동 채움(공석 시 다음 상위)
- 평가 순서: 템플릿 기본 단계 → 조건 룰 순차 적용 → 최종 단계 배열

## 4. 개발 체크리스트
- [ ] F-001 Step 모델/6종 유형 · [ ] F-002 상위자 체인 생성(F1-06)
- [ ] F-003 조건 룰 평가(금액·부서) · [ ] F-004 템플릿 CRUD/적용
- [ ] F-005 위임/대결 처리 · [ ] F-006 결재선 검증
- [ ] 격리(F0-01)·권한(F0-07)·감사(F0-08)·알림(F3) 결합
- 진행률 0% (1단계)

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 직렬/병렬/합의 단계 구성 | 유형별 정확 |
| T-2 | 상위자 체인 N단계 자동 채움(F1-06) | 정확·공석 대체 |
| T-3 | 조건 룰(금액 ≥ 임계) 단계 추가 | 임원 단계 삽입 |
| T-4 | 조건 룰(부서) 합의 단계 삽입 | 정확 |
| T-5 | 전결/대결/위임 결재자 치환 | 명의·표시 정확 |
| T-6 | 템플릿 적용 후 결재선 생성 | 일치 |
| T-7 | 자기결재·빈 단계·순환 | 검증 차단 |
| T-8 | 권한 없는 템플릿 변경 | 403(F0-07) |
| T-9 | 타 테넌트 결재선 접근 | 격리 차단(F0-01) |

## 7. 최종 검토
- [ ] 6종 유형·상위자 체인·조건 룰·템플릿·검증 그린(T-1~T-7)
- [ ] 격리·권한·감사·알림 결합 검증(T-8·T-9)
- [ ] F10-03(결재 진행/처리)에 결재선 모델 제공(재사용 토대)
