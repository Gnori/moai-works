# F12-08 — 외부 인사시스템 연동

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: medium (Wave 3)
- **상위**: [F12 인사·근태 마스터](./20260603-f12-hr-attendance-master.md)
- **의존**: [F15 외부연계](./20260603-f15-external-integration-master.md)·[F0-07 RBAC](./20260603-f0-07-rbac.md)·[F0-01 테넌트 격리](./20260603-f0-01-tenant-isolation.md)·[F1-06 조직 조회](./20260603-f1-06-org-query-view.md)·[F0-08 감사](./20260603-f0-08-audit-log.md)·[F3 알림](./20260603-f3-notification-master.md)

## 1. 요구사항 분석
### 1.1 문제 정의
사내 인사·근태·급여 데이터는 외부 HR/급여 시스템(외주 급여대행·ERP)과 **이중 입력**되어 불일치가 발생한다.
F15 연계 기반 위에 **커넥터 + 동기화 훅**을 두어 인사·근태·급여 데이터를 자동 교환한다.
### 1.2 목표
- 외부 시스템별 **커넥터**(REST/파일/배치) + **필드 매핑**(외부↔내부 코드)
- **동기화 방향 정책**(단방향 inbound/outbound · 양방향) · 도메인별(인사/근태/급여) 설정
- **변경 훅**(F12 인사/근태 변경 시 outbound 트리거) · 스케줄 동기화 · 충돌/오류 처리
- 격리(테넌트별 커넥터·자격증명) · 감사(F0-08) · 실패 알림(F3)

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 외부 시스템 커넥터 등록(유형·엔드포인트·자격증명) | Must |
| F-002 | 필드 매핑·코드 변환(외부↔내부, F1-06 조직코드) | Must |
| F-003 | 동기화 방향 정책(inbound/outbound/bidirectional × 도메인) | Must |
| F-004 | inbound 동기화(인사·근태·급여 입력 수신·반영) | Must |
| F-005 | outbound 훅(F12 변경 시 외부 전송) + 스케줄 배치 | Must |
| F-006 | 충돌·오류 처리(재시도·DLQ·실패 알림 F3) | Should |

## 3. 기능 설계
### 3.1 기술 스택
Spring Boot · Spring Scheduler/배치 · F15 커넥터 SDK · PostgreSQL+RLS · 기존 Redis(큐/락)·MinIO(파일 교환) · JUnit5
### 3.2 데이터 모델
- `hr_connector(id, tenant_id, name, type=rest/file/batch, endpoint, credential_ref, enabled)`
- `hr_sync_policy(id, tenant_id, connector_id, domain=hr/attendance/payroll, direction=in/out/both, schedule_cron)`
- `hr_field_mapping(id, tenant_id, connector_id, domain, external_field, internal_field, code_map)`
- `hr_sync_log(id, tenant_id, connector_id, domain, direction, status, processed, failed, started_at, error)`
- 모든 테이블 `tenant_id` + RLS · 자격증명은 `credential_ref`(F15 비밀저장소 참조, 평문 미저장)
### 3.3 흐름
```
[inbound] 외부→커넥터 수신/배치 → 필드매핑·코드변환(F1-06) → F12 인사/근태/급여 반영 → 감사(F0-08)
[outbound] F12 변경 이벤트 → 훅 → 정책(out/both) 확인 → 변환 → 외부 전송(즉시/스케줄)
          실패 → 재시도(지수백오프) → DLQ → 관리자 알림(F3)
```
### 3.4 API (모두 `@RequiresPermission`, 기본 deny)
| 메서드 | 경로 | 권한 | 설명 |
|--------|------|------|------|
| POST | `/api/hr/connectors` | `hr:integration:manage` | 커넥터 등록 |
| GET | `/api/hr/connectors` | `hr:integration:read` | 커넥터 목록 |
| PUT | `/api/hr/connectors/{id}/policy` | `hr:integration:manage` | 동기화 방향/스케줄 설정 |
| PUT | `/api/hr/connectors/{id}/mappings` | `hr:integration:manage` | 필드 매핑 설정 |
| POST | `/api/hr/connectors/{id}/sync` | `hr:integration:manage` | 수동 동기화 실행 |
| GET | `/api/hr/connectors/{id}/logs` | `hr:integration:read` | 동기화 로그 조회 |
- 인증 없음 401 · 권한 없음 403 · 검증 실패 400 · 미존재 404 · 중복 등록 409 · 외부 호출은 F15 게이트웨이 경유

## 4. 개발 체크리스트
### 4.1 환경 설정
- [ ] F15 커넥터 SDK 연동 · 비밀저장소 참조 · 스케줄러/큐
### 4.2 기능 개발
- [ ] F-001 커넥터 등록 · [ ] F-002 필드매핑/코드변환 · [ ] F-003 방향 정책
- [ ] F-004 inbound 반영 · [ ] F-005 outbound 훅+배치 · [ ] F-006 오류처리/알림
### 4.3 코드 품질
- [ ] 테넌트 격리·자격증명 비노출 · 멱등 처리 · 부분 실패 격리
### 4.4 진행률
0% (1단계)

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 커넥터 등록/정책 설정 | 정상 |
| T-2 | inbound 동기화(인사·근태·급여) | F12 반영 정확 |
| T-3 | outbound 훅(인사 변경) | 정책 out/both만 전송 |
| T-4 | 단방향 정책 위반 전송 시도 | 차단 |
| T-5 | 필드매핑·조직코드(F1-06) 변환 | 정확 |
| T-6 | 외부 호출 실패 | 재시도→DLQ→알림(F3) |
| T-7 | 권한 없음 / 인증 없음 | 403 / 401 |
| T-8 | 타 테넌트 커넥터·자격증명 접근 | 격리(F0-01) 차단 |

## 7. 최종 검토
- [ ] 커넥터·매핑·방향정책·inbound/outbound 동작 (T-1~T-5 그린)
- [ ] 오류처리·재시도·DLQ·실패 알림(F3) (T-6)
- [ ] 권한(F0-07)·격리(F0-01)·자격증명 비노출 검증 (T-7·T-8)
- [ ] 동기화 변경 감사(F0-08) 기록 + 보안 리뷰 완료
