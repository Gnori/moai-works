# F10-04 — 승인 처리

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: high (Wave 3)
- **상위**: [F10 결재 마스터](./20260603-f10-approval-master.md) · **의존**: [F0-07 RBAC](./20260603-f0-07-rbac.md)·[F1-06 조직조회](./20260603-f1-06-org-query-view.md)·[F3 알림](./20260603-f3-notification-master.md)·[F0-08 감사](./20260603-f0-08-audit-log.md)

## 1. 요구사항 분석
### 1.1 문제 정의
상신된 문서는 결재선을 따라 **순차/병렬 처리**되어야 한다. 각 결재자는 **승인·반려·회수·후결·전결**을
수행하고, 진행상태가 추적되며, 다음 결재자에게 라우팅·알림되어야 한다. 반려 시 기안자에게 즉시 통지한다.
### 1.2 목표
- 결재 액션(승인/반려/회수/후결/전결) 처리 + 결재 의견 기록
- 단계별 진행상태 추적(대기/진행/완료/반려/회수) · 다음 결재자 라우팅
- 처리 시 알림(F3) · 상위자/조직 조회(F1-06) · 모든 행위 감사(F0-08) · 테넌트 격리(F0-01)

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 승인 처리(다음 단계 라우팅·완료 판정) | Must |
| F-002 | 반려 처리(기안자 알림·문서 반려상태) | Must |
| F-003 | 회수(기안자, 미결재 단계 한정) | Must |
| F-004 | 후결(부재자 사후 결재·대기단계 통과) | Should |
| F-005 | 전결(위임권자가 상위 단계 일괄 종결) | Should |
| F-006 | 결재 의견 기록 + 진행상태 추적 | Must |

## 3. 기능 설계
### 3.1 데이터 모델
- `approval_step(id, tenant_id, doc_id, seq, approver_id, type=approval/agreement, status=pending/active/done/rejected, acted_at, opinion)`
- `approval_action(id, tenant_id, doc_id, step_id, actor_id, action=approve/reject/withdraw/post/prerogative, opinion, created_at)`
- 모든 엔티티 `tenant_id` 포함 · 인덱스 `(tenant_id, doc_id, seq)`
### 3.2 처리 흐름
```
액션 수신 → @RequiresPermission("approval:approve") + 본인 active step 검증(격리)
  approve  → step.done → 다음 seq active(또는 마지막이면 문서 완료) → 다음 결재자 알림(F3)
  reject   → step.rejected → 문서 반려 → 기안자 알림(F3)
  withdraw → 기안자·미결재 한정 → 문서 회수 → 결재자 알림
  post     → 부재 통과 step 사후 done 처리
  prerogative → 위임권자가 상위 step 일괄 done + 문서 완료
모든 액션 → approval_action 적재 + 감사로그(F0-08)
```
### 3.3 API (`@RequiresPermission`)
- `POST /approvals/{docId}/approve` `{opinion}` — `approval:approve`
- `POST /approvals/{docId}/reject` `{opinion}` — `approval:approve`
- `POST /approvals/{docId}/withdraw` — `approval:submit`(기안자)
- `POST /approvals/{docId}/post` `{stepId, opinion}` — `approval:approve`
- `POST /approvals/{docId}/prerogative` `{opinion}` — `approval:prerogative`
- `GET  /approvals/{docId}/progress` — 단계별 상태/의견 조회

## 4. 개발 체크리스트
- [ ] F-001 승인/라우팅 · [ ] F-002 반려/기안자알림 · [ ] F-003 회수 · [ ] F-004 후결 · [ ] F-005 전결 · [ ] F-006 의견/진행추적
- [ ] 알림(F3) 연동 · [ ] 감사(F0-08) 적재 · [ ] 격리/권한 검증
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 승인 → 다음 단계 라우팅 | active 전이·알림 |
| T-2 | 마지막 단계 승인 | 문서 완료 |
| T-3 | 반려 | 문서 반려·기안자 알림 |
| T-4 | 회수(미결재) | 회수 성공 |
| T-5 | 회수(이미 결재됨) | 409 차단 |
| T-6 | 후결/전결 | 단계 일괄 종결 |
| T-7 | 타 결재자/타 테넌트 액션 | 403/격리 차단 |

## 7. 최종 검토
- [ ] 5종 액션·진행추적·라우팅·알림·감사·격리 그린
- [ ] 결재 마스터(F10) 상태기계와 정합
