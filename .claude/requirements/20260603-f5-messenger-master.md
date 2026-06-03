# F5 — AI 메신저 마스터

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 — 요구사항 분석 (L1 도메인 마스터) · **규모**: complex(마스터)
- **우선순위**: high (Wave 2) · **연관**: 토대 [F0-01 격리](./20260603-f0-01-tenant-isolation.md)·[F0-07 RBAC](./20260603-f0-07-rbac.md)·[F0-08 감사](./20260603-f0-08-audit-log.md)·[F1-06 조직조회](./20260603-f1-06-org-query-view.md) · 실시간 [F3-02 실시간전달](./20260603-f3-02-realtime-delivery.md) · 파일 [F4](./20260603-f4-filedrive-master.md) · 할일 [F11](./20260603-f11-task-master.md) · AI [F13 Agent](./20260603-f13-ai-agent-master.md)·[F7 지식](./20260603-f7-knowledge-master.md)·[F8 문서](./20260603-f8-ai-document-master.md) · 인프라 **MOAI-DB/Redis/MinIO/Qdrant**

## 1. 요구사항 분석
### 1.1 문제 정의
조직 구성원이 **채널·DM 기반으로 실시간 협업**하는 사내 메신저. 메시지가 단순 채팅을 넘어
**할일·문서·지식·회의로 자동 전환**되는 AI 협업 허브. 흩어진 대화 맥락을 요약·추출하여
업무 손실을 줄이고, 채널 내에서 **Agent를 직접 호출**해 작업을 지시.
### 1.2 목표
- 채널(1:1·그룹·부서·프로젝트·공지) · **실시간 송수신(F3-02 WS+Redis 재사용)** · 답글/멘션/이모지 · 읽음확인
- **파일공유(F4)** · 문서 공동검토 · 메시지 검색 · 메신저 알림(F3)
- **AI**: 대화요약·안읽은요약·중요메시지추출 · 할일/담당자/기한 자동추출(→F11) · 회의일정후보 · 답변초안 · 대화기반 문서초안(F8) · 채널지식 자동축적(F7) · **채널 내 Agent 호출(F13)**
### 1.3 제약
- 모든 엔티티 `tenant_id`(F0-01 격리) · 채널 멤버십 + F0-07 RBAC 권한 · 메시지 발신/멤버변경 F0-08 감사
- 실시간 채널은 **F3-02 재사용**(자체 WS 구축 금지) · 파일 첨부 **F4 재사용**
- **AI 기능은 F13/F7/F8 미완 시 placeholder/fallback** — 기능 토글 OFF 또는 룰베이스 대체, 완성 후 연계 활성화

## 2. 기능 정의 — L3 분해
| L3 ID | spec | 규모 | 내용 |
|-------|------|------|------|
| **F5-01** | [채널 관리](./20260603-f5-01-channels.md) | complex | 1:1·그룹·부서·프로젝트·공지 채널·멤버십·아카이브 |
| **F5-02** | [실시간 메시징](./20260603-f5-02-realtime-messaging.md) | complex | 송수신·전송상태·타이핑·**F3-02 WS/Redis 재사용** |
| **F5-03** | [메시지 기능](./20260603-f5-03-message-features.md) | standard | 답글(스레드)·멘션·이모지 리액션·편집/삭제 |
| **F5-04** | [읽음 확인](./20260603-f5-04-read-receipts.md) | standard | 읽음 마커·안읽은 카운트·최근 본 위치 |
| **F5-05** | [파일 공유](./20260603-f5-05-file-sharing.md) | standard | 첨부·문서 공동검토·**F4 재사용** |
| **F5-06** | [메시지 검색](./20260603-f5-06-message-search.md) | standard | 키워드·필터·채널/사용자 스코프(Qdrant 시맨틱 옵션) |
| **F5-07** | [메신저 알림](./20260603-f5-07-messenger-notifications.md) | standard | 멘션·DM·키워드 알림·**F3 재사용** |
| **F5-08** | [AI 대화요약](./20260603-f5-08-ai-summary.md) | complex | 채널요약·안읽은요약·중요메시지추출 |
| **F5-09** | [AI 할일추출](./20260603-f5-09-ai-task-extraction.md) | complex | 할일·담당자·기한 추출 → **F11 등록** |
| **F5-10** | [AI 답변/회의](./20260603-f5-10-ai-reply-meeting.md) | complex | 답변초안·회의일정 후보 제안 |
| **F5-11** | [AI 문서/지식](./20260603-f5-11-ai-doc-knowledge.md) | complex | 대화기반 문서초안(F8)·채널지식 자동축적(F7) |
| **F5-12** | [채널 Agent 호출](./20260603-f5-12-channel-agent.md) | complex | 채널 내 `@agent` 멘션 → **F13 Agent 실행** |

## 3. 기능 설계 (토대)
### 3.1 데이터 모델
- `channels(id, tenant_id, type: dm/group/dept/project/announce, name, owner_id, archived, ...)`
- `channel_members(id, tenant_id, channel_id, user_id, role: owner/admin/member, last_read_at, muted, joined_at)`
- `messages(id, tenant_id, channel_id, sender_id, body, parent_id(스레드), edited_at, deleted_at, created_at)`
- `message_reactions(id, tenant_id, message_id, user_id, emoji)` · `message_mentions(message_id, mentioned_user_id)`
- `message_attachments(message_id, file_id→F4)` · `message_reads(channel_id, user_id, last_read_message_id)`
- AI: `channel_summaries`·`extracted_tasks(→F11)`·`channel_knowledge(→F7)` (캐시/링크 테이블)
### 3.2 통합점
- **실시간** = F3-02 WS 채널(`tenant:{id}:channel:{cid}`) + Redis pub/sub 재사용 · **알림** = F3 (멘션/DM/키워드 트리거)
- **파일** = F4(첨부·공동검토) · **할일추출** F5-09 → F11 Task 생성 · **문서초안** F5-11 → F8 · **지식축적** F5-11 → F7(Qdrant 임베딩)
- **Agent** F5-12 → F13(채널 컨텍스트 주입·실행·결과 메시지 회신)
### 3.3 AI fallback 패턴 (F13/F7/F8 미완 시)
- 요약/추출/초안: F13 미연동 시 **기능 비활성(토글 OFF) 또는 룰베이스 stub** 반환 · 지식축적: F7 미완 시 **로컬 메타 인덱싱만**, 문서초안: F8 미완 시 **plain text export** · 완성 후 연계 스위치 ON

## 4. 개발 진행 (L3)
- [ ] F5-01 채널 → [ ] F5-02 실시간(F3-02) → [ ] F5-03 답글/멘션/이모지 → [ ] F5-04 읽음 → [ ] F5-05 파일(F4) → [ ] F5-06 검색 → [ ] F5-07 알림(F3)
- [ ] F5-08 요약 → [ ] F5-09 할일추출(F11) → [ ] F5-10 답변/회의 → [ ] F5-11 문서/지식(F8/F7) → [ ] F5-12 Agent(F13) *(AI는 fallback 우선, 연계 후 활성화)*

## 9. 완료 기준 (마스터)
- [ ] 5종 채널·실시간 송수신(F3-02)·답글/멘션/이모지·읽음·파일공유(F4)·검색·알림(F3) 동작
- [ ] AI 요약·중요메시지·할일추출(→F11)·답변초안·회의후보·문서초안(F8)·지식축적(F7)·Agent 호출(F13) 동작 또는 fallback
- [ ] 전 엔티티 격리(F0-01)·채널 멤버십+RBAC(F0-07)·발신/멤버변경 감사(F0-08)
