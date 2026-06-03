# F12 — 인사·근태 마스터

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 — 요구사항 분석 (L1 도메인 마스터) · **규모**: complex(마스터)
- **우선순위**: high (Wave 2) · **연관**: 의존 [F0](./20260603-f0-saas-foundation-master.md)·[F1 조직](./20260603-f1-organization-master.md) · 연계 [F10 결재](./20260603-f10-approval-master.md)·[F3 알림](./20260603-f3-notification-master.md)·[F15 외부연동](./20260603-f15-external-integration-master.md)

## 1. 요구사항 분석
### 1.1 문제 정의
임직원의 **인사정보·근태기록·연차/휴가·근무제**를 관리하고 **근태규정 엔진**으로 출결을 판정,
**집계 리포트**를 산출하는 인사·근태 도메인. **휴가 신청은 F10 결재**로 처리하고,
승인 시 F12가 잔여 차감·근태 반영을 수행한다.
### 1.2 목표
- **인사카드·이력**(발령/직위 이력 = F1-04 연동) · **출퇴근 기록**(웹/모바일/단말)
- **연차·휴가 잔여** 관리(부여·차감·소멸·이월) · **근무제**(정상/유연/교대/재택)
- **근태규정 엔진**(지각/조퇴/결근/연장 판정) · **집계 리포트**(개인/부서/기간)
- **휴가 결재 연동**(F10) · **외부 급여·근태단말 연동**(F15)
### 1.3 제약
- F0-01 격리(모든 엔티티 `tenant_id`, 교차 테넌트 차단) · F0-07 RBAC(`hr:*`, `attendance:*`)
- F0-08 감사(인사정보 변경·잔여 조정) · 휴가 신청 흐름은 **F10 결재 단일화**(F12 중복 신청 UI 금지)

## 2. 기능 정의 — L3 분해
| L3 ID | spec | 규모 | 내용 |
|-------|------|------|------|
| **F12-01** | 인사정보 관리(personnel-records) | complex | 인사카드·발령/직위 이력(F1-04)·개인정보 보호 |
| **F12-02** | 근태기록(attendance-records) | standard | 출퇴근·외근/출장·정정·단말 입력 |
| **F12-03** | 연차·휴가 관리(leave-management) | complex | 휴가유형·부여/차감/이월/소멸·잔여 원장 |
| **F12-04** | 휴가 결재 연동(leave-approval-link) | standard | F10 결재 상신·승인 콜백 → 잔여 차감·근태 반영 |
| **F12-05** | 근무제(work-schedule) | standard | 정상/유연/교대/재택·근무캘린더·교대표 |
| **F12-06** | 근태규정 엔진(attendance-policy) | complex | 지각/조퇴/결근/연장 판정·규정 룰셋 |
| **F12-07** | 집계 리포트(attendance-report) | standard | 개인/부서/기간 집계·근태 마감·내보내기 |
| **F12-08** | 외부 연동(hr-external-integration) | standard | 급여·근태단말·외부 HR(F15) 동기화 |

## 3. 기능 설계 (토대)
### 3.1 데이터 모델
- `personnel_records(id, tenant_id, user_id, emp_no, hire_date, position, dept_id, status, ...)` · `personnel_history(record_id, change_type, before, after, effective_date)`
- `attendance_records(id, tenant_id, user_id, work_date, check_in, check_out, type, status, source)`
- `leave_balances(id, tenant_id, user_id, leave_type, granted, used, remaining, expire_at, year)` · `leave_transactions(balance_id, delta, reason, approval_id)`
- `work_schedules(id, tenant_id, name, mode: normal/flex/shift/remote, rules)` · `schedule_assignments(schedule_id, target)`
- `attendance_policies(id, tenant_id, rule_type, threshold, action)` · `attendance_results(record_id, judged: late/early/absent/overtime)`
- 모든 테이블 `tenant_id` + RLS(F0-01)
### 3.2 통합점
- **F1**: 조직·발령 이력 [F1-04], 담당/상위자 조회 [F1-06](./20260603-f1-06-org-query-view.md)(휴가 결재선·근태 승인자)
- **F10**: 휴가 신청 = 결재 문서로 상신, 승인 콜백 → F12-03 잔여 차감 + F12-02 근태 반영(F12-04)
- **F3**: 출결 이상·잔여 소멸 임박·근태 마감 알림
- **F15**: 외부 급여/근태단말 양방향 동기화(F12-08) · 감사 F0-08

## 4. 개발 진행 (L3)
- [ ] F12-01 인사정보 → [ ] F12-02 근태기록 → [ ] F12-03 연차·휴가 → [ ] F12-04 결재연동(F10) → [ ] F12-05 근무제 → [ ] F12-06 규정엔진 → [ ] F12-07 집계리포트 → [ ] F12-08 외부연동(F15)

## 9. 완료 기준 (마스터)
- [ ] 인사카드·이력(F1-04)·근태기록·연차/휴가 잔여 원장 동작
- [ ] 휴가 신청 F10 결재 단일화 · 승인 시 잔여 차감·근태 반영(F12-04)
- [ ] 근무제·근태규정 엔진 판정 · 집계 리포트·마감
- [ ] F1 조직·F3 알림·F15 외부 연동 · 격리(tenant_id/RLS)·RBAC·감사(F0-08)
