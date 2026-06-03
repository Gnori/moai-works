# F12-01 — 인사정보 (인사카드 · 발령/직급 이력 · 개인정보 보호)

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: high (Wave 4)
- **상위**: [F12 인사/근태 마스터](./20260603-f12-hr-attendance-master.md)
- **의존**: [F0-01 테넌트 격리](./20260603-f0-01-tenant-isolation.md)·[F0-07 RBAC](./20260603-f0-07-rbac.md)·[F0-08 감사로그](./20260603-f0-08-audit-log.md)·[F1-04 발령/인사이동](./20260603-f1-04-personnel-events.md)·[F1-06 조직조회](./20260603-f1-06-org-query-view.md)·[F3 알림](./20260603-f3-notification-master.md)

## 1. 요구사항 분석
### 1.1 문제 정의
사원의 **인사정보·연락처·계약**을 한 곳에 모은 **인사카드**가 필요하다. 발령/직급 변동은
F1-04와 연계해 **이력**으로 누적되고, 주민번호·계좌 등 **민감정보**는 접근통제·마스킹으로 보호해야 한다.
### 1.2 목표
- **인사카드** CRUD (기본/연락처/계약/학력·경력) · F1 조직(부서·직급·담당) 연동
- **발령/직급 이력** 타임라인(F1-04 발령 이벤트 누적)
- **민감정보 보호** — 권한별 마스킹·복호화·열람 감사(F0-08)
- 인사 권한(`hr:*`) 기반 접근 · 본인 열람 범위 분리 · 테넌트 격리

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 인사카드 CRUD(기본·연락처·계약·학력/경력) | Must |
| F-002 | 발령/직급 이력 타임라인(F1-04 연계) | Must |
| F-003 | 민감정보 마스킹·복호화(권한별) | Must |
| F-004 | 민감정보 열람 감사로그(F0-08) | Must |
| F-005 | 본인 인사카드 조회(self) + 항목 정정요청 | Should |
| F-006 | 인사정보 변경 알림(F3, 본인/담당 인사) | Should |

## 3. 기능 설계
### 3.1 데이터 모델
- `personnel_record(id, tenant_id, user_id, emp_no, join_date, employment_type, status, base_dept_id, position_id, manager_user_id)` — F1 조직 참조
- `personnel_contact(record_id, tenant_id, phone, email, address, emergency_contact)`
- `personnel_contract(record_id, tenant_id, contract_type, start_date, end_date, salary_band)`
- `personnel_sensitive(record_id, tenant_id, resident_no_enc, bank_account_enc, ...)` — **암호화 저장**(AES, KMS 키)
- `personnel_assignment_history(id, tenant_id, user_id, type=appoint/promote/transfer, from_*, to_*, effective_date, source_ref→F1-04)`
- 모든 테이블 `tenant_id` + RLS(F0-01) · 인덱스 `(tenant_id, user_id)`

### 3.2 권한 / 흐름
- 권한 키(F0-07): `hr:record:read/write` · `hr:sensitive:read`(민감정보 복호화) · `hr:history:read` · `self`(본인 한정)
- **마스킹 기본**: `hr:sensitive:read` 없으면 주민번호/계좌 마스킹(`900101-*******`) 반환
- **복호화 흐름**: `hr:sensitive:read` 보유 + 사유 입력 → 복호화 → **F0-08 감사**(누가/언제/무엇/사유)
- **발령 이력**: F1-04 발령 확정 이벤트 구독 → `assignment_history` append + 인사카드 `base_dept/position` 갱신
- 변경/정정요청 시 **F3 알림**(본인·담당 인사) · 모든 쓰기는 감사 대상

### 3.3 API (요약, 모두 `@RequiresPermission`)
- `GET /hr/records/{userId}` (`hr:record:read` 또는 self) · `PUT /hr/records/{userId}` (`hr:record:write`)
- `GET /hr/records/{userId}/sensitive` (`hr:sensitive:read`, 사유 필수) → 복호화 + 감사
- `GET /hr/records/{userId}/history` (`hr:history:read`)
- `POST /hr/records/{userId}/correction` (self) — 정정요청

## 4. 개발 체크리스트
- [ ] F-001 인사카드 CRUD · [ ] F-002 발령/직급 이력(F1-04 구독)
- [ ] F-003 마스킹·복호화 · [ ] F-004 열람 감사(F0-08)
- [ ] F-005 본인조회/정정요청 · [ ] F-006 변경 알림(F3)
- [ ] 민감정보 암호화 저장 + RLS 격리 검증
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 인사카드 CRUD | 정확 |
| T-2 | `hr:sensitive:read` 없이 민감정보 조회 | 마스킹 반환 |
| T-3 | 권한 보유 복호화 + 사유 | 평문 반환 + F0-08 감사 기록 |
| T-4 | F1-04 발령 확정 | 이력 append + 인사카드 갱신 |
| T-5 | 본인(self) 조회/정정요청 | 본인 범위만 허용 |
| T-6 | 권한 없는 타인 인사카드 접근 | **403** |
| T-7 | 타 테넌트 인사카드 접근 | 격리 차단(F0-01) |

## 7. 최종 검토
- [ ] 인사카드·발령이력·마스킹/복호화·감사 그린 (T-1·T-3·T-4)
- [ ] 민감정보 암호화 저장 + 권한별 마스킹 동작 (T-2)
- [ ] self/권한 분리 + 테넌트 격리 검증 (T-5·T-6·T-7)
- [ ] 보안 리뷰(개인정보 접근통제) 완료
