# F5-09 — AI 할일 추출

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: medium (Wave 3)
- **상위**: [F5 마스터](./20260603-f5-messenger-master.md) · **의존**: [F5-02 실시간](./20260603-f5-02-realtime-messaging.md)·[F11 업무](./20260603-f11-task-master.md)·[F13 AI Agent](./20260603-f13-ai-agent-master.md)·[F0-07 RBAC](./20260603-f0-07-rbac.md)

## 1. 요구사항 분석
### 1.1 문제 정의
채널 대화에는 "내일까지 ○○ 정리해줘" 같은 **할 일이 흩어져** 사라진다.
대화에서 **할 일·담당자·기한**을 자동 추출하고, 확인 후 **F11 업무**로 등록한다.
회의가 필요한 맥락이면 **일정 후보**도 제안한다.
### 1.2 목표
- 메시지/구간 → 할 일 후보(제목·담당자·기한) 추출 · **사용자 검토 후 확정**
- 확정 시 F11 업무 생성([F11-05](./20260603-f11-task-master.md) 연계)
- 회의 필요 감지 시 일정 후보 제안(생성은 향후) · 격리

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 메시지/선택 구간에서 할 일 추출(F13 호출) | Must |
| F-002 | 담당자(멘션/문맥)·기한(상대/절대 날짜) 파싱 | Must |
| F-003 | 추출 후보 카드 표시 → 검토/수정 | Must |
| F-004 | 확정 → F11 업무 생성(F11-05) | Must |
| F-005 | 회의 필요 감지 → 일정 후보 제안 | Should |
| F-006 | F13 미가용 시 placeholder/fallback | Must |

## 3. 기능 설계
### 3.1 데이터 모델
`task_extractions(id, tenant_id, channel_id, source_msg_ids[], status=draft/confirmed/dismissed, requested_by, created_at)` ·
`task_extraction_items(id, extraction_id, title, assignee_user_id, due_at, confidence, linked_task_id)` ·
모든 엔티티 `tenant_id`([F0-01](./20260603-f0-01-tenant-isolation.md) RLS)
### 3.2 처리 흐름
1. 사용자가 메시지/구간 선택 → "할 일 추출" → [F13](./20260603-f13-ai-agent-master.md) Agent 호출(대화 컨텍스트 전달)
2. 결과 → `task_extraction_items` 저장(status=draft) · **확정 전 자동 등록 금지**
3. 검토 카드에서 수정/확정 → [F11-05](./20260603-f11-task-master.md) 업무 생성 · `linked_task_id` 기록
4. 회의 필요 신호 → 일정 후보(제목·참석자·후보 시간) 제안 카드(생성 연계는 향후)
### 3.3 AI 연계(미완 대비)
- **F13 미배포 시**: 추출 버튼은 노출하되 호출 시 placeholder("AI 추출 준비 중") 반환 · 규칙 기반 fallback(멘션=담당자, "내일/N일" 정규식=기한)으로 최소 후보 생성
- F13 완성 후 정식 Agent 추출로 전환 · 담당자/권한은 F0-07 검증

## 4. 개발 체크리스트
- [ ] F-001 추출 호출 · [ ] F-002 담당자/기한 파싱 · [ ] F-003 검토 카드 · [ ] F-004 F11 연계 · [ ] F-005 일정 후보 · [ ] F-006 fallback
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 할 일 추출(제목/담당자/기한) | 후보 정확 |
| T-2 | 상대 기한("내일") 파싱 | 절대 날짜 변환 |
| T-3 | 확정 → F11 업무 생성 | linked_task_id 연결 |
| T-4 | F13 미가용 fallback | placeholder/규칙 후보 |
| T-5 | 권한 없는 추출/확정 | 차단(F0-07) |
| T-6 | 타 테넌트 대화 접근 | 차단(격리) |

## 7. 최종 검토
- [ ] 추출·파싱·검토·F11연계·fallback·격리 그린
