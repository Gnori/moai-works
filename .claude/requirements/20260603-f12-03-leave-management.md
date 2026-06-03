# F12-03 — 연차 · 휴가 관리

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: high (Wave 4)
- **상위**: [F12 인사·근태 마스터](./20260603-f12-hr-attendance-master.md)
- **의존**: [F0-07 RBAC](./20260603-f0-07-rbac.md)·[F1-06 조직조회](./20260603-f1-06-org-query-view.md)·[F3 알림](./20260603-f3-notification-master.md)·[F0-08 감사](./20260603-f0-08-audit-log.md)
- **연계**: [F10-07 휴가신청 결재](./20260603-f10-07-form-types.md)(이 잔여를 차감)

## 1. 요구사항 분석
### 1.1 문제 정의
직원별 **연차 발생(근속 기반)·잔여·사용·이월**을 정확히 산정하고, 연차 외 휴가 종류
(반차·병가·경조 등)를 함께 관리해야 한다. F10-07 휴가신청 결재가 **승인 시 이 잔여를 차감**
하므로, 잔여 계산은 단일 권위 소스여야 한다. 테넌트별 발생 정책(입사일/회계연도 기준)이 다르다.
### 1.2 목표
- **휴가 종류** 정의(연차·반차·병가·경조·기타, 유급/무급·차감단위)
- **연차 발생**(근속 1년 미만 월 1일, 1년 이상 15일+가산, 정책 기준일 선택)
- 직원별 **잔여 원장**(발생·사용·이월·소멸) · **잔여 조회/차감 API**(F10-07 연계)
- F0-01 격리 · F0-07 권한 · F3 알림(소멸 예정·발생) · F0-08 감사

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 휴가 종류 정의(유급/무급·차감단위·연차차감여부) | Must |
| F-002 | 연차 발생 정책(입사일/회계연도 기준·근속 가산) | Must |
| F-003 | 연차 발생 배치(월/연 단위 자동 적립) | Must |
| F-004 | 잔여 원장(발생·사용·이월·소멸 이력) | Must |
| F-005 | 잔여 조회·차감/복원 API(F10-07 연계) | Must |
| F-006 | 이월/소멸 처리(연말 이월 한도·소멸) | Should |
| F-007 | 소멸 예정·발생 알림(F3) | Should |

## 3. 기능 설계
### 3.1 기술 스택
Spring Boot · Spring Scheduler(발생/소멸 배치) · PostgreSQL+RLS · Flyway · JUnit5

### 3.2 데이터 모델
- `leave_type(id, tenant_id, name, paid, deduct_unit=day/half, counts_annual, active)` — 테넌트별 시드
- `leave_policy(id, tenant_id, base=hire_date/fiscal, fiscal_start, carryover_limit, max_grant)`
- `leave_balance(id, tenant_id, user_id, leave_type_id, year, granted, used, carried, expired, remaining)` — 잔여 원장(권위 소스)
- `leave_ledger(id, tenant_id, balance_id, txn=grant/use/restore/carryover/expire, amount, ref_id, created_at)` — 변동 이력
- 인덱스: `(tenant_id, user_id, leave_type_id, year)` UNIQUE · 모든 행 RLS(F0-01)

### 3.3 흐름 / API (`@RequiresPermission`)
```
발생 배치 → leave_policy 기준 근속 계산(F1-06 입사일) → leave_balance.granted 적립 + ledger(grant)
F10-07 승인 → POST /leave/balance/deduct → remaining 검증 → used+ / ledger(use) → 부족 시 409
F10-07 반려/취소 → POST /leave/balance/restore → ledger(restore)
연말 배치 → carryover(한도 내) + expire(잔여 소멸) → F3 알림
```
- `GET /api/v1/leave/types` · `POST /api/v1/leave/types` `leave:manage`
- `GET /api/v1/leave/balance?userId=` `leave:read`(본인/상위자 F1-06)
- `POST /api/v1/leave/balance/deduct|restore` `leave:deduct`(F10-07 내부 호출)
- `POST /api/v1/leave/grant/run` · `/leave/carryover/run` `leave:manage`(수동 트리거)
- 차감/복원·정책변경 → F0-08 감사 기록

## 4. 개발 체크리스트
- [ ] F-001 휴가 종류 · [ ] F-002 발생 정책 · [ ] F-003 발생 배치 · [ ] F-004 잔여 원장
- [ ] F-005 조회·차감/복원 API · [ ] F-006 이월/소멸 · [ ] F-007 알림(F3)
- [ ] RLS 격리 · @RequiresPermission · 감사(F0-08)
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 근속 1년 미만/이상 발생 산정 | 정책대로 정확 |
| T-2 | 차감(잔여 충분) | used+·remaining 감소 |
| T-3 | 차감(잔여 부족) | 409 차단 |
| T-4 | 반려 후 복원 | remaining 원복·ledger 기록 |
| T-5 | 연말 이월(한도)·소멸 | 한도 내 이월·초과 소멸 |
| T-6 | 조회 권한(타인) | 본인/상위자만 허용·그 외 403 |
| T-7 | 타 테넌트 잔여 접근 | 격리 차단 |
| T-8 | 소멸 예정 알림 | F3 발송 |

## 7. 최종 검토
- [ ] 발생·잔여·차감/복원·이월/소멸 그린 (T-1·T-2·T-3·T-4·T-5)
- [ ] F10-07 차감 연계 동작 · 권한·격리·감사·알림 검증
