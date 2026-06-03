# F12-07 — 근태 집계 / 리포트

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: high (Wave 3)
- **상위**: [F12 인사/근태 마스터](./20260603-f12-hr-attendance-master.md)
- **의존**: [F0-07 RBAC](./20260603-f0-07-rbac.md)·[F1-06 조직 조회/뷰](./20260603-f1-06-org-query-view.md)·[F3 알림](./20260603-f3-notification-master.md)·[F0-08 감사](./20260603-f0-08-audit-log.md)

## 1. 요구사항 분석
### 1.1 문제 정의
근태 원천 데이터(출퇴근·연차·연장)는 분산되어 있어 **개인·부서·기간별 집계**와 통계가 어렵다.
관리자는 **연차 사용 현황·지각/연장 통계**를 한눈에 보고 급여/평가용으로 **내보내기**해야 한다.
### 1.2 목표
- 개인·부서·기간(일/주/월) **근태 집계**(근무·지각·결근·연장·연차)
- **연차 사용 현황**(부여/사용/잔여) · 지각/연장 **통계**
- 관리자 **대시보드** + **Excel 내보내기**(F8) · 조직범위(F1-06) 권한·격리

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 개인 기간별 근태 집계(근무/지각/결근/연장) | Must |
| F-002 | 부서별 집계(F1-06 조직범위) | Must |
| F-003 | 연차 사용 현황(부여/사용/잔여) | Must |
| F-004 | 지각/연장 통계(추이·순위) | Should |
| F-005 | 관리자 대시보드 조회 API | Must |
| F-006 | Excel 내보내기(F8) | Should |
| F-007 | 마감/확정 + 이상 알림(F3) | Could |

## 3. 기능 설계
### 3.1 기술 스택
Spring Boot · PostgreSQL(RLS, F0-01) · Flyway · Apache POI(F8 내보내기) · JUnit5

### 3.2 데이터 모델
- `attendance_summary(id, tenant_id, user_id, dept_id, period_type=day/week/month, period_start, work_minutes, late_count, absent_count, overtime_minutes, leave_days, status=draft/confirmed)`
- `leave_balance(id, tenant_id, user_id, year, granted_days, used_days, remaining_days)`
- 집계는 근태 원천(출퇴근/연차 신청)에서 **배치/온디맨드 재계산** → `attendance_summary` 적재
- 인덱스: `(tenant_id, dept_id, period_start)` · `(tenant_id, user_id, period_type)`

### 3.3 흐름 / API
```
원천(출퇴근·연차) → 집계 엔진(기간/대상) → attendance_summary
조회: 개인=본인, 부서=F1-06 조직범위 검사 → 격리(F0-01) + RBAC(F0-07)
```
- `GET /api/v1/attendance/summary?userId&period&from&to` — 개인 집계 *(self 또는 `attendance:read`)*
- `GET /api/v1/attendance/summary/dept?deptId&period&from&to` — 부서 집계 *(`attendance:read.dept`, F1-06 범위)*
- `GET /api/v1/attendance/leave-balance?userId&year` — 연차 현황
- `GET /api/v1/attendance/stats?deptId&metric=late|overtime&from&to` — 통계(추이·순위)
- `GET /api/v1/attendance/dashboard?deptId&from&to` — 관리자 대시보드 집합 *(`attendance:manage`)*
- `POST /api/v1/attendance/export` — Excel 내보내기(F8) *(`attendance:export`)*
- `POST /api/v1/attendance/confirm` — 기간 마감/확정 + 이상 시 알림(F3) *(`attendance:manage`)*
- 전 API `@RequiresPermission` + 조직범위(F1-06) 결합 · 확정/내보내기는 감사(F0-08)

## 4. 개발 체크리스트
- [ ] F-001 개인 집계 · [ ] F-002 부서 집계(F1-06) · [ ] F-003 연차 현황
- [ ] F-004 지각/연장 통계 · [ ] F-005 대시보드 · [ ] F-006 Excel(F8) · [ ] F-007 마감+알림(F3)
- [ ] RBAC/조직범위/격리 결합 · [ ] 확정·내보내기 감사(F0-08)
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 개인 기간 집계 | 근무/지각/연장 정확 |
| T-2 | 부서 집계(조직범위 내) | 정확·범위 내 사용자만 |
| T-3 | 조직범위 밖 부서 조회 | 403(F1-06) |
| T-4 | 연차 부여/사용/잔여 | 정합 |
| T-5 | 지각/연장 통계 | 추이·순위 정확 |
| T-6 | Excel 내보내기 | 파일 생성·감사 |
| T-7 | 권한 없는 호출 | 403(F0-07) |
| T-8 | 타 테넌트 데이터 | 차단(F0-01) |

## 7. 최종 검토
- [ ] 개인·부서·연차·통계·대시보드·내보내기 그린
- [ ] RBAC(F0-07)·조직범위(F1-06)·격리(F0-01)·감사(F0-08)·알림(F3) 결합 검증
