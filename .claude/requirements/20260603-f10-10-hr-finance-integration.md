# F10-10 — HR/회계 연동 (결재 결과 → 후속 처리)

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: high (Wave 3)
- **상위**: [F10 결재 마스터](./20260603-f10-approval-master.md)
- **의존**: [F0-07 RBAC](./20260603-f0-07-rbac.md)·[F0-08 감사](./20260603-f0-08-audit-log.md)·[F1-06 조직 조회](./20260603-f1-06-org-query-view.md)·[F3 알림](./20260603-f3-notification-master.md)

## 1. 요구사항 분석
### 1.1 문제 정의
결재가 **승인 완료**되어도 후속 업무가 자동 반영되지 않으면 휴가 차감·지출 처리를
사람이 이중 입력해야 한다. 승인 결과를 **양식 종류별 후속 처리기**로 라우팅하여
**F12 인사근태(휴가 차감)·회계(지출결의)·업무 반영**과 **외부 ERP/회계(F15)** 로 연동한다.
### 1.2 목표
- 승인 완료 이벤트 → **양식 타입별 핸들러** 디스패치(휴가/지출/일반)
- 내부 연동: F12 휴가 차감 · 회계 지출결의 생성 · 업무(F11) 반영
- **외부 연동 훅**(F15 ERP/회계) — 비동기 큐·재시도·실패 알림
- 멱등·격리·감사 · 처리 실패 시 결재 상태 롤백 없이 **보정 큐**로 격리

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 승인완료 이벤트 → 양식타입별 핸들러 디스패치 | Must |
| F-002 | F12 휴가 차감 연동(휴가 양식) | Must |
| F-003 | 회계 지출결의 생성(지출 양식) | Must |
| F-004 | 외부 ERP/회계(F15) 연동 훅(비동기·재시도) | Should |
| F-005 | 멱등 처리 + 실패 보정 큐 + 알림(F3) | Must |
| F-006 | 연동 이력 조회/재처리(관리자) | Should |

## 3. 기능 설계
### 3.1 데이터 모델
- `approval_integration(id, tenant_id, approval_id, form_type, handler, status=pending/done/failed, idempotency_key, result_ref, retry_count, created_at)`
- `integration_outbox(id, tenant_id, target=f12/finance/f11/erp, payload, status, retry_count, next_retry_at)`
- 모든 엔티티 `tenant_id` 포함(F0-01), 인덱스 `(tenant_id, approval_id)`·`(tenant_id, status)`
### 3.2 흐름
```
F10 결재 승인 완료(이벤트)
  → IntegrationDispatcher: form_type 매핑 → 핸들러 선택
     - 휴가  → LeaveDeductHandler  → F12 휴가 차감
     - 지출  → ExpenseHandler      → 회계 지출결의 생성
     - 일반  → TaskReflectHandler  → F11 업무 반영
  → 외부 훅 대상이면 integration_outbox 적재 → 비동기 워커가 F15 호출(재시도)
  → 성공: status=done / 실패: failed + 보정 큐 + F3 알림(담당·상위자 F1-06)
  (멱등: idempotency_key = approval_id+handler)
```
### 3.3 API
- `GET /api/v1/approvals/{id}/integrations` — 연동 이력 조회 (`@RequiresPermission("approval:read")`)
- `POST /api/v1/integrations/{id}/retry` — 실패 건 재처리 (`@RequiresPermission("approval:admin")`)
- `GET /api/v1/integrations?status=failed` — 실패 목록(관리자)
- 모든 호출 테넌트 격리(F0-01) · 권한 변경/재처리 감사(F0-08)

## 4. 개발 체크리스트
- [ ] F-001 디스패처/핸들러 레지스트리
- [ ] F-002 휴가 차감(F12) · [ ] F-003 지출결의 생성 · [ ] F-004 외부 훅(outbox·재시도)
- [ ] F-005 멱등·보정 큐·알림(F3) · [ ] F-006 이력 조회/재처리 API
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 휴가 승인 → F12 차감 | 정확 반영 |
| T-2 | 지출 승인 → 지출결의 생성 | 정확 생성 |
| T-3 | 동일 이벤트 중복 수신 | 멱등(1회 처리) |
| T-4 | 외부 ERP 호출 실패 | 재시도→보정 큐+알림 |
| T-5 | 재처리 API(관리자) | 성공 반영 |
| T-6 | 권한 없는 재처리 호출 | 403 |
| T-7 | 타 테넌트 연동 이력 접근 | 격리 차단 |

## 7. 최종 검토
- [ ] 디스패치·휴가/지출/업무 반영·외부 훅·멱등·보정 그린(T-1~T-5)
- [ ] 권한(F0-07)·격리(F0-01)·감사(F0-08)·알림(F3) 결합 검증
