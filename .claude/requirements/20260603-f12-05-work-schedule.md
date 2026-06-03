# F12-05 — 근무 일정 / 근무제

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: high (Wave 2)
- **상위**: [F12 인사·근태 마스터](./20260603-f12-hr-attendance-master.md) · **의존**: [F0-01 격리](./20260603-f0-01-tenant-isolation.md)·[F0-07 RBAC](./20260603-f0-07-rbac.md)·[F0-08 감사](./20260603-f0-08-audit-log.md)·[F1-06 조직조회](./20260603-f1-06-org-query-view.md)·[F3 알림](./20260603-f3-notification-master.md)

## 1. 요구사항 분석
### 1.1 문제 정의
근태 계산은 **"이 사람이 오늘 언제 일해야 하나"** 라는 기준 일정이 있어야 가능하다. 본 spec 은
근무제(표준·유연·교대·재택) 정의와 개인/부서 **근무 캘린더**·공휴일을 제공하여 근태 산정의
**입력값**을 만든다. 실제 출퇴근 기록·정산은 F12-06(근태 기록)이 담당한다.
### 1.2 목표
- **근무제 정의** — 표준 / 유연(시차출퇴근) / 교대(시프트) / 재택
- **배정** — 사용자·부서 단위로 근무제 할당(F1-06 조직 기준)
- **근무 캘린더** — 일자별 기대 근무(출근/퇴근/휴게·시프트·휴무) 산출 + **공휴일**
- 근태 계산용 **기준일정 조회 API** 제공 · 격리

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 근무제 정의(표준/유연/교대/재택) CRUD | Must |
| F-002 | 근무제 배정(사용자·부서, 기간) | Must |
| F-003 | 시프트 패턴(교대 로테이션) 정의·배정 | Should |
| F-004 | 공휴일 캘린더(법정·회사 지정) | Must |
| F-005 | 근무 캘린더 산출(기대일정 조회 API) | Must |
| F-006 | 일정 변경 알림(F3) | Should |

## 2.2 기능 상세
#### F-001 근무제
- `type`: STANDARD(고정 시각) / FLEX(코어타임 + 출퇴근 범위) / SHIFT(시프트 참조) / REMOTE(재택, 위치 무관)
- 공통 속성: 일일 소정근로(분), 휴게(분), 주 근무일, 코어타임(FLEX), 출근범위(FLEX)
#### F-002 배정
- 배정 대상 = 사용자 또는 부서(부서 배정은 F1-06 소속자에 상속) · 유효기간(from/to)
- 우선순위: **사용자 배정 > 부서 배정 > 테넌트 기본** (재정의)
#### F-003 시프트
- `shift(name, start, end, break)` · `shift_pattern`(요일/주기별 시프트 시퀀스) → 사용자에 로테이션 배정
#### F-004 공휴일
- 법정·회사 지정 휴일 등록(연 단위) · 근무 캘린더 산출 시 휴무 처리
#### F-005 근무 캘린더
- 입력(사용자, 날짜범위) → 일자별 `{expected_in, expected_out, break, is_holiday, is_workday, shift?}` 산출
- 근무제·시프트·공휴일·요일 결합 평가 → **F12-06 근태 계산 입력**

## 3. 기능 설계
### 3.1 기술 스택
Spring Boot · PostgreSQL+RLS · Flyway · JUnit5 · `@RequiresPermission`(F0-07)
### 3.2 데이터 모델 (전 엔티티 `tenant_id`, RLS 격리)
- `work_policy(id, tenant_id, name, type, daily_minutes, break_minutes, work_days, core_start, core_end, flex_in_from, flex_in_to)`
- `work_policy_assignment(id, tenant_id, policy_id, target_type=user/dept, target_id, valid_from, valid_to)`
- `shift(id, tenant_id, name, start_time, end_time, break_minutes)` · `shift_pattern(id, tenant_id, name, sequence_json)` · `shift_assignment(id, tenant_id, user_id, pattern_id, valid_from, valid_to)`
- `holiday(id, tenant_id, date, name, is_statutory)`
- 인덱스: `(tenant_id, target_type, target_id, valid_from)` · `(tenant_id, date)`
### 3.3 흐름 / API (`@RequiresPermission` 기본 deny)
```
GET  /api/hr/work-policies                  schedule:read   목록
POST /api/hr/work-policies                  schedule:manage 근무제 생성
PUT  /api/hr/work-policies/{id}             schedule:manage 수정
POST /api/hr/work-policies/{id}/assignments schedule:manage 배정(user/dept) → F3 알림
POST /api/hr/shifts·/shift-patterns         schedule:manage 시프트·패턴 정의
POST /api/hr/shift-assignments              schedule:manage 시프트 로테이션 배정
GET/POST /api/hr/holidays                    schedule:read/manage 공휴일
GET  /api/hr/calendar?userId&from&to        schedule:read   근무 캘린더(기대일정) 산출
```
- 캘린더 산출: 배정 우선순위 평가 → 근무제/시프트 적용 → 공휴일·요일 휴무 반영
- 모든 조회는 F0-01 RLS 위, 교차 테넌트 차단 · 변경은 F0-08 감사 대상

## 4. 개발 체크리스트
- [ ] F-001 근무제 CRUD · [ ] F-002 배정/우선순위 · [ ] F-003 시프트/패턴 · [ ] F-004 공휴일 · [ ] F-005 캘린더 산출 API · [ ] F-006 알림(F3)
- [ ] RLS 격리 · 감사로그(F0-08) · 권한 강제(F0-07)
- 진행률 0% (1단계)

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 근무제 CRUD(4종) | 정상 |
| T-2 | 배정 우선순위(사용자>부서>기본) | 정확 재정의 |
| T-3 | 시프트 로테이션 캘린더 산출 | 시퀀스 정확 |
| T-4 | 공휴일/주말 → 휴무 | is_workday=false |
| T-5 | FLEX 코어타임/출근범위 | 범위 반영 |
| T-6 | 캘린더 = F12-06 근태 입력 | 기대일정 일치 |
| T-7 | 권한 없음 / 401 / 타 테넌트 | 403 / 401 / 격리 |

## 7. 최종 검토
- [ ] 근무제·배정·시프트·공휴일·캘린더 산출 그린 (T-1~T-6)
- [ ] F12-06 근태 계산 입력으로 캘린더 제공 검증
- [ ] 권한(F0-07)·격리(F0-01)·감사(F0-08)·알림(F3) 결합 그린
