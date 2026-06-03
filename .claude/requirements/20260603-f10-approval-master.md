# F10 — 전자결재 (양식+결재선 엔진) 마스터

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 — 요구사항 분석 (L1 도메인 마스터) · **규모**: complex(마스터)
- **우선순위**: high (Wave 2) · **연관**: 의존 [F0](./20260603-f0-saas-foundation-master.md)·[F1 조직](./20260603-f1-organization-master.md) · 연계 [F3 알림](./20260603-f3-notification-master.md)·F8 문서작성·[F4 첨부](./20260603-f4-filedrive-master.md)·F12 휴가·F15 외부

## 1. 요구사항 분석
### 1.1 문제 정의
기업의 모든 승인요청(일반 기안서·휴가신청·지출결의·구매요청·출장신청·품의서·인사발령)이
**양식(form)만 다르고 동일한 결재선(workflow) 엔진**을 탄다. 새 결재유형 추가 = **양식 정의 1건**.
양식·결재선을 분리한 범용 엔진으로 결재유형이 늘어도 코드 변경 없이 확장.
### 1.2 목표
- **양식 엔진**: 필드 정의(JSON 스키마)·레이아웃·유형별 양식 등록 → 코드 없이 신규 결재유형
- **결재선 엔진**: 직렬·병렬·**합의**·**전결**·**대결**·**위임** 결재 경로
- **상신→처리→완료** 라이프사이클: 기안·승인·반려·회수·후결 · 문서함·원본보존·PDF
- 모바일 결재 · F3 알림 · F12 휴가잔여 연동 · HR/회계 자동반영
### 1.3 제약
- F0-01 격리: 모든 엔티티 `tenant_id`, 교차 테넌트 결재문서 접근 차단
- F0-07 RBAC: `@RequiresPermission("approval:draft|approve|admin")`, 양식관리 `form:manage`
- 결재선 = F1 조직/담당/상위자([F1-06](./20260603-f1-06-org-query-view.md)) 기반 동적 결정
- F0-08 감사: 상신·승인·반려·회수·위임 전 이력 감사로그

## 2. 기능 정의 — L3 분해
| L3 ID | spec | 규모 | 내용 |
|-------|------|------|------|
| **F10-01** | 양식 엔진(form-engine) | complex | 필드 스키마(JSON)·레이아웃·유형 등록·버전·코드없는 신규유형 |
| **F10-02** | 결재선 엔진(approval-line) | complex | 직렬·병렬·합의·전결·대결·위임 경로 정의·F1-06 동적 결정 |
| **F10-03** | 상신·기안(draft-submit) | standard | 기안 작성·임시저장·결재선 적용·상신 |
| **F10-04** | 승인 처리(approval-processing) | complex | 승인·반려·회수·후결·진행상태·전결 단축 |
| **F10-05** | 위임·대결(delegation) | standard | 부재 위임·기간 위임·대결 처리·위임이력 |
| **F10-06** | 문서함·원본보존(document-box) | standard | 기안/수신/완료함·원본 불변보존·PDF 출력 |
| **F10-07** | 유형별 양식(form-types) | standard | 휴가·지출·구매·출장·품의·인사발령 양식·F12 휴가잔여 연동 |
| **F10-08** | 모바일 결재(mobile-approval) | standard | 모바일 결재함·간편 승인/반려·푸시 |
| **F10-09** | 알림 연계(notifications) | simple | 상신·승인·반려·회수·완료 → F3 발송 |
| **F10-10** | HR·회계 반영(hr-finance-integration) | standard | 완료 결재 HR(F12)·회계 자동반영·외부연동 F15 |

## 3. 기능 설계 (토대)
### 3.1 데이터 모델
- `form_templates(id, tenant_id, type, version, schema_json, layout_json, status, ...)` — 양식 정의
- `approval_documents(id, tenant_id, form_template_id, drafter_id, title, fields_json, status, current_step, ...)` — 결재문서
- `approval_lines(id, tenant_id, document_id, step_no, approver_id, type[serial/parallel/agree/prearranged], state[wait/approve/reject/skip], acted_at, ...)` — 결재선 단계
- `delegations(id, tenant_id, from_user_id, to_user_id, period, scope, ...)` — 위임/대결
- `approval_documents.fields_json` = 양식 스키마 기반 동적 필드 · 완료문서 PDF·원본 불변
### 3.2 통합점
- 결재선 approver = [F1-06](./20260603-f1-06-org-query-view.md) 조직/담당/상위자 동적 조회
- 알림 = [F3](./20260603-f3-notification-master.md) · 첨부 = [F4](./20260603-f4-filedrive-master.md) · 기안 초안 작성 = F8
- F10-07 휴가양식 → F12 잔여 차감 · F10-10 완료문서 → F12 HR·회계·외부(F15) 반영
- 감사 = F0-08 전 결재 액션 기록

## 4. 개발 진행 (L3)
- [ ] F10-01 양식엔진 → [ ] F10-02 결재선엔진 → [ ] F10-03 상신 → [ ] F10-04 승인처리 → [ ] F10-05 위임/대결 → [ ] F10-06 문서함 → [ ] F10-07 유형별양식 → [ ] F10-08 모바일 → [ ] F10-09 알림 → [ ] F10-10 HR/회계반영

## 9. 완료 기준 (마스터)
- [ ] 양식 정의 1건으로 신규 결재유형 추가(코드 변경 없음)
- [ ] 직렬·병렬·합의·전결·대결·위임 결재선 동작
- [ ] 상신·승인·반려·회수·후결 라이프사이클 · 문서함·PDF·원본보존
- [ ] F1-06 결재선·F3 알림·F4 첨부·F12 휴가잔여·F15 외부 연계
- [ ] 격리(tenant_id)·RBAC(approval:*)·감사(F0-08) 적용
