# F12-02 — 근태 기록 (출퇴근 · 근무시간)

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: high (Wave 3)
- **상위**: [F12 인사·근태 마스터](./20260603-f12-hr-attendance-master.md)
- **의존**: [F0-01 테넌트 격리](./20260603-f0-01-tenant-isolation.md) · [F0-07 RBAC](./20260603-f0-07-rbac.md) · [F0-08 감사](./20260603-f0-08-audit-log.md) · [F1-06 조직 조회](./20260603-f1-06-org-query-view.md) · [F3 알림](./20260603-f3-notification-master.md)

## 1. 요구사항 분석
### 1.1 문제 정의
직원의 **출근·퇴근 시각**을 기록하고 일별 **근무시간**을 산출해야 한다.
지각·조퇴·결근을 자동 판정하고, 누락·오류는 **보정 신청(승인)** 으로 정정한다.
출처(웹/모바일/단말)를 구분해 기록 신뢰성을 확보한다.
### 1.2 목표
- 출근/퇴근 **체크인·아웃** · 출처 기록(web/mobile/device) · 격리(tenant_id)
- 일별 근무시간 계산 · **지각/조퇴/결근** 자동 판정(근무일정 기준)
- 기록 **보정 신청 → 상위자 승인**(F1-06) · 승인 후 재계산
- 보정 승인/반려 시 신청자 **알림**(F3) · 변경은 감사(F0-08)

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 출근/퇴근 체크인·아웃(출처 기록) | Must |
| F-002 | 일별 근태 집계(근무시간·상태 판정) | Must |
| F-003 | 지각/조퇴/결근 자동 판정(일정 기준) | Must |
| F-004 | 기록 보정 신청 → 상위자 승인/반려 | Must |
| F-005 | 일별/기간 근태 조회(본인·관리자) | Must |
| F-006 | 보정 승인 결과 알림(F3) | Should |

## 3. 기능 설계
### 3.1 데이터 모델
- `attendance_records(id, tenant_id, user_id, work_date, check_in_at, check_out_at, source=web/mobile/device, status=normal/late/early_leave/absent, work_minutes, created_at)`
- `attendance_corrections(id, tenant_id, record_id, requester_id, approver_id, field=check_in/check_out, old_value, new_value, reason, status=pending/approved/rejected, decided_at)`
- 인덱스: `(tenant_id, user_id, work_date)` · 일별 유일 `(tenant_id, user_id, work_date)`
- 근무일정(시업/종업·휴게)은 F12 마스터 정책 참조

### 3.2 흐름 / API
```
체크인 → attendance_records upsert(work_date=오늘, source) → 체크아웃 시 work_minutes·status 재계산
보정: 신청(POST) → 상위자(F1-06) 승인 → record 갱신 + 재계산 → 신청자 알림(F3) → 감사(F0-08)
```
- `POST /api/attendance/check-in` · `POST /api/attendance/check-out` — `@RequiresPermission("attendance:write")`
- `GET /api/attendance/daily?from&to&userId` — 본인 또는 `attendance:read`(관리자)
- `POST /api/attendance/corrections` — `attendance:write`(신청)
- `POST /api/attendance/corrections/{id}/approve|reject` — `attendance:approve`(상위자)
- 모든 조회/쓰기 tenant_id 격리 · 교차 테넌트 차단

## 4. 개발 체크리스트
- [ ] F-001 체크인·아웃(출처) · [ ] F-002 일별 집계 · [ ] F-003 상태 판정
- [ ] F-004 보정 승인 흐름 · [ ] F-005 조회 · [ ] F-006 알림(F3)
- [ ] @RequiresPermission(attendance:*) · 보정 변경 감사(F0-08)
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 체크인/아웃 · 근무시간 계산 | 정확 |
| T-2 | 지각/조퇴/결근 판정 | 일정 기준 정확 |
| T-3 | 출처(web/mobile/device) 기록 | 보존 |
| T-4 | 보정 신청 → 상위자 승인 → 재계산 | 정정 반영 |
| T-5 | 보정 결과 알림(F3) | 발송 |
| T-6 | 권한 없는 보정 승인 | 403 |
| T-7 | 타 테넌트 기록 접근 | 격리 차단 |

## 7. 최종 검토
- [ ] 체크인·아웃·집계·판정·보정·조회 그린(T-1~T-4)
- [ ] 권한(F0-07)·알림(F3)·감사(F0-08)·격리(F0-01) 결합 검증(T-5~T-7)
