# F12-06 — 근태 규정 엔진 (정책 · 근무시간 · 연장/야간/휴일)

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: high (Wave 3)
- **상위**: [F12 인사·근태 마스터](./20260603-f12-hr-attendance-master.md)
- **의존**: [F0-01 테넌트 격리](./20260603-f0-01-tenant-isolation.md) · [F0-07 RBAC](./20260603-f0-07-rbac.md) · [F0-08 감사](./20260603-f0-08-audit-log.md) · [F12-02 근태기록](./20260603-f12-02-attendance-records.md) · [F12-05 근무제](./20260603-f12-05-work-schedule.md) · [F1-06 조직 조회](./20260603-f1-06-org-query-view.md) · [F3 알림](./20260603-f3-notification-master.md)

## 1. 요구사항 분석
### 1.1 문제 정의
근태 판정과 근무시간 산출이 **사업장·근무제마다 다른 규칙**(소정근로·휴게·연장/야간/휴일 가산)을
따른다. 규칙을 코드에 박지 않고 **정책(룰셋)으로 정의**하고, 출퇴근 기록(F12-02)에 적용해
**자동 계산·판정**하는 엔진이 필요하다. 근무제(F12-05)별 시업/종업·교대 패턴을 반영한다.
### 1.2 목표
- **근태 정책 정의**(소정근로시간·휴게·지각/조퇴/결근 임계·라운딩) · 격리(tenant_id)
- **근무시간 계산**: 실근로·휴게 차감 · **연장/야간/휴일** 구간 분해·가산율 산정
- 근무제(F12-05) **시업/종업·교대표** 반영한 기준시각 결정
- 정책 적용 결과 → `attendance_results` 산출(판정 + 시간 분해) · 변경은 감사(F0-08)

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 근태 정책(룰셋) 정의·버전 관리 | Must |
| F-002 | 근무시간 계산(실근로·휴게 차감) | Must |
| F-003 | 연장/야간/휴일 구간 분해·가산율 | Must |
| F-004 | 지각/조퇴/결근 판정(임계·라운딩) | Must |
| F-005 | 근무제(F12-05) 기준시각·교대 반영 | Must |
| F-006 | 정책 일괄/단건 재계산·결과 산출 | Should |

## 3. 기능 설계
### 3.1 데이터 모델
- `attendance_policies(id, tenant_id, name, is_active, version, std_work_minutes, break_minutes, late_grace_min, rounding_unit, effective_from)`
- `attendance_policy_rules(id, policy_id, rule_type=overtime/night/holiday/late/early/absent, condition, multiplier, action)`
- `attendance_results(id, tenant_id, record_id, work_date, regular_min, overtime_min, night_min, holiday_min, judged=normal/late/early/absent, policy_version)`
- 인덱스: `(tenant_id, record_id)` 유일 · `(tenant_id, policy_id, version)` · 모든 테이블 `tenant_id` + RLS(F0-01)
- 야간 = 22:00~06:00 구간 · 휴일 = 근무캘린더(F12-05) 기준 · 가산율은 룰 `multiplier`로 정책화

### 3.2 흐름 / API
```
근태기록(F12-02 check_in/out) + 근무제(F12-05 시업/종업·휴일) + 활성 정책(version)
  → 엔진: 휴게 차감 → 실근로 → 연장/야간/휴일 구간 분해 → 가산 → 판정(지각/조퇴/결근)
  → attendance_results upsert(policy_version) → 변경 감사(F0-08)
보정 승인·정책 변경 시 해당 기록 재계산(F-006)
```
- `GET /api/attendance/policies` · `POST /api/attendance/policies` · `PUT /api/attendance/policies/{id}` — `@RequiresPermission("attendance:manage")`
- `POST /api/attendance/policies/{id}/activate` — `attendance:manage`(버전 활성)
- `POST /api/attendance/results/recalculate?from&to&userId` — `attendance:manage`(일괄 재계산)
- `GET /api/attendance/results?from&to&userId` — 본인 또는 `attendance:read`(관리자)
- 모든 조회/쓰기 tenant_id 격리 · 교차 테넌트 차단 · 정책은 활성 버전 1개

## 4. 개발 체크리스트
- [ ] F-001 정책·룰셋 정의/버전 · [ ] F-002 근무시간 계산 · [ ] F-003 연장/야간/휴일 분해
- [ ] F-004 지각/조퇴/결근 판정 · [ ] F-005 근무제(F12-05) 반영 · [ ] F-006 재계산/결과 산출
- [ ] @RequiresPermission(attendance:*) · 정책 변경·결과 산출 감사(F0-08)
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 정책 정의·활성 버전 전환 | 단일 활성·버전 보존 |
| T-2 | 근무시간 계산(휴게 차감) | 실근로 정확 |
| T-3 | 연장/야간/휴일 구간 분해·가산 | 룰 multiplier 정확 |
| T-4 | 지각/조퇴/결근 판정(임계·라운딩) | 정책 기준 정확 |
| T-5 | 근무제(F12-05) 교대·휴일 반영 | 기준시각 정확 |
| T-6 | 정책 변경 후 재계산 | 결과·policy_version 갱신 |
| T-7 | 권한 없는 정책 변경 | 403 |
| T-8 | 타 테넌트 정책/결과 접근 | 격리 차단 |

## 7. 최종 검토
- [ ] 정책 정의·근무시간 계산·연장/야간/휴일 분해·판정 그린(T-1~T-5)
- [ ] 근무제(F12-05) 반영·재계산(T-6) · 권한(F0-07)·감사(F0-08)·격리(F0-01) 결합 검증(T-7·T-8)
