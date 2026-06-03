# F5-11 — AI 문서초안·지식축적

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: medium (Wave 3)
- **상위**: [F5 마스터](./20260603-f5-messenger-master.md) · **의존**: [F5-01 채널](./20260603-f5-01-channels.md)·[F5-02 실시간](./20260603-f5-02-realtime-messaging.md)·[F8 AI문서](./20260603-f8-ai-document-master.md)·[F7 지식](./20260603-f7-knowledge-master.md)·[F4 파일](./20260603-f4-filedrive-master.md)·[F0-07 RBAC](./20260603-f0-07-rbac.md)·[F0-01 격리](./20260603-f0-01-tenant-isolation.md)

## 1. 요구사항 분석
### 1.1 문제 정의
채널 대화에는 **결정·합의·노하우**가 흩어져 있으나 문서화되지 않아 휘발된다. 대화 흐름을
**문서 초안(F8)** 으로 전환하고, 가치 있는 대화를 **지식베이스(F7)** 에 자동 인덱싱해
검색·재사용 가능하게 한다. **출처(채널·메시지)** 를 보존해 신뢰성을 확보한다.
### 1.2 목표
- 선택/구간 메시지 → **문서 초안 생성**(F8 위임) · 초안에 출처 메시지 링크 보존
- 채널 대화 **지식 자동 축적**(F7 인덱싱) · 중복/노이즈 필터 · 출처 역추적
- F8/F7 **미완 시 placeholder/fallback**(초안=대화 요약 텍스트, 인덱싱=보류 큐 적재)
- AI 처리는 **비동기 잡** · 격리(F0-01) · 권한(F0-07)

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 메시지 선택/구간 → 문서 초안 생성 요청(F8 위임) | Must |
| F-002 | 초안에 출처(채널·메시지 ID·작성자) 보존·역링크 | Must |
| F-003 | 채널 지식 자동 축적 잡(F7 인덱싱·청크·임베딩) | Should |
| F-004 | 노이즈/중복 필터 · 인덱싱 대상 선별(휴리스틱+옵트인) | Should |
| F-005 | 지식 검색 결과의 출처 역추적(원본 메시지 점프) | Should |
| F-006 | F8/F7 미완 시 placeholder/fallback 경로 | Must |
| F-007 | 채널 단위 지식 축적 on/off · 옵트인(F0-07 manage) | Must |

## 3. 기능 설계
### 3.1 데이터 모델
- `msg_doc_draft(id, tenant_id, channel_id, requester_id, source_message_ids[], doc_id NULL, status=pending/ready/failed/placeholder, summary_text, created_at)` — `doc_id`는 F8 문서 참조(미완 시 NULL+summary_text)
- `channel_knowledge_index(id, tenant_id, channel_id, message_id, chunk_ref, kb_node_id NULL, status=queued/indexed/skipped, reason, indexed_at)` — `kb_node_id`는 F7 노드 참조
- `channel_knowledge_config(channel_id, tenant_id, enabled, filter_policy=jsonb)` — 전 엔티티 tenant_id, 채널 멤버십(F5-01)·RBAC(F0-07) 검사
### 3.2 흐름
- **초안**: 메시지 선택 → 잡 적재 → F8 가용 시 초안 생성·`doc_id` 채움 → 미완 시 대화 요약 텍스트만 저장(status=placeholder) → 알림(F3)
- **축적**: 신규 메시지 이벤트(F5-02) → config.enabled 채널만 필터 잡 → 휴리스틱 통과분 F7 인덱싱(청크·임베딩 Qdrant) → 미완 시 `channel_knowledge_index` queued 보류 → F7 완성 후 백필
- **역추적**: 검색 결과 노드 → `message_id` → 채널 메시지 점프(권한 재검사)
### 3.3 API (`/api/v1/messenger/channels/{cid}`)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/drafts` | 메시지 선택→문서 초안 생성 요청(F8 위임/fallback) |
| GET | `/drafts/{id}` | 초안 상태·출처·doc_id 조회 |
| PUT | `/knowledge/config` | 지식 축적 on/off·필터 정책(manage) |
| GET | `/knowledge` | 채널 지식 인덱스 목록·역추적 링크 |
### 3.4 AI 연계 (F8/F7 미완 대응)
- **F8 미완**: 초안=메시지 결합 요약 텍스트(LLM 단순 호출 or 원문 연결), `doc_id=NULL`·status=placeholder. F8 완성 후 재생성 API로 정식 문서 승격
- **F7 미완**: 인덱싱 잡은 `queued`로 적재만, 임베딩/검색 비활성. F7 완성 후 보류분 백필 배치
- 출처 메타(채널·메시지·작성자·시각)는 두 경로 모두에서 **항상 보존**

## 4. 개발 체크리스트
- [ ] F-001 초안 생성 · [ ] F-002 출처 보존 · [ ] F-003 축적 잡 · [ ] F-004 필터
- [ ] F-005 역추적 · [ ] F-006 placeholder/fallback · [ ] F-007 옵트인·권한
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 메시지 선택→초안(F8 가용) | doc_id 채움·출처 보존 |
| T-2 | F8 미완 fallback | placeholder+요약 텍스트 |
| T-3 | 축적 잡(F7 가용) | 인덱싱·검색 노출 |
| T-4 | F7 미완 | queued 보류·백필 대상 |
| T-5 | 노이즈/중복 필터 | skipped 처리 |
| T-6 | 검색 역추적 | 원본 메시지 점프·권한 재검사 |
| T-7 | config off 채널 | 미축적 |
| T-8 | 비멤버/격리 | 차단(403/404) |

## 7. 최종 검토
- [ ] 초안·출처보존·축적·필터·역추적·placeholder·옵트인·격리 그린
- [ ] F8/F7 완성 후 정식 문서 승격·보류 백필 연계 확인
