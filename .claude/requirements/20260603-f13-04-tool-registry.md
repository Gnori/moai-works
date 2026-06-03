# F13-04 — Tool 레지스트리 / 플러그인 (LangChain Tool 바인딩)

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: complex · **레벨**: L3 · **우선순위**: critical (Wave 4)
- **상위**: [F13 마스터](./20260603-f13-ai-agent-master.md) · **의존**: [F13-01](./20260603-f13-01-ai-service-foundation.md)·[F13-06 컨텍스트](./20260603-f13-06-context-propagation.md) · **참고: echomind** `agent_tools.create_recall_tools` + SNS action tools

## 1. 요구사항 분석
### 1.1 문제 정의
Agent 가 그룹웨어 기능(지식검색·문서생성·일정·결재·메일…)을 수행하려면, 각 기능을
**LangChain Tool 로 감싸 Agent 에 바인딩**해야 한다. 도구를 **레지스트리로 등록**해 도메인
Agent 별로 도구셋을 구성한다.
### 1.2 목표
- **Tool 레지스트리**: `name → LangChain BaseTool` 매핑 + 도메인별 도구셋
- 각 Tool = 코어 API/F7 RAG/F8 문서 호출(컨텍스트 가드 F13-06)
- Agent 바인딩(echomind `agent_info.tools.extend()` 패턴) · 신규 도구 1줄 등록

### 1.3 제약
- 모든 Tool 진입부 **컨텍스트/권한 가드**(F13-06) · echomind SNS action tools 구조 재사용

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | Tool 레지스트리(name→Tool, 도메인셋) | Must |
| F-002 | LangChain BaseTool 래퍼(가드 포함) | Must |
| F-003 | Agent 바인딩(도메인별 도구셋) | Must |
| F-004 | 핵심 도구셋(지식검색·문서생성·일정·결재·메일·업무) | Must |
| F-005 | 도구 호출 audit·미터링(F13-09) | Should |

## 2.2 상세
- **F-002**: echomind SNS action tools 처럼 `_arun` 에서 컨텍스트 가드 → 코어 API on-behalf 호출 → 결과/오류 정직 반환.
- **F-004 도구 예**: `knowledge_search`(F7) · `create_document`(F8) · `schedule_meeting` · `submit_approval`(F10) · `send_mail_draft`(F6) · `create_task`(F11) · `org_lookup`(F1).
- **F-003**: 도메인 Agent(F13-05) 마다 필요한 도구셋만 바인딩.

## 3. 기능 설계
### 3.1 구조
```
tools/
├─ registry.py     # name→Tool, 도메인셋
├─ base.py         # BaseTool 래퍼(컨텍스트 가드)
├─ knowledge.py    # knowledge_search (F7)
├─ document.py     # create_document (F8)
├─ calendar.py · approval.py · mail.py · task.py · org.py
```
### 3.2 패턴 (echomind 재사용)
`create_<domain>_tools()` → 도메인 Agent 에 `agent_info.tools.extend(...)`

## 4. 개발 체크리스트
- [ ] F-001 레지스트리 · [ ] F-002 BaseTool 래퍼/가드 · [ ] F-003 Agent 바인딩 · [ ] F-004 핵심 도구셋 · [ ] F-005 audit/미터
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 도구 등록/바인딩 | Agent tool-call 가능 |
| T-2 | 컨텍스트 없는 도구 호출 | 거부(F13-06) |
| T-3 | 권한 없는 도구 | 차단 |
| T-4 | 도구→코어 API | on-behalf·이중검증 |
| T-5 | audit 기록 | 남음 |

## 7. 최종 검토
- [ ] 레지스트리·LangChain Tool·가드·바인딩·핵심도구셋 그린 (echomind 패턴)
