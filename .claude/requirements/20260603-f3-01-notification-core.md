# F3-01 — 알림 코어 (이벤트 모델 · 저장 · 읽음)

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: high (Wave 1)
- **상위**: [F3 알림 마스터](./20260603-f3-notification-master.md) · **의존**: [F0-01](./20260603-f0-01-tenant-isolation.md)·[F0-06](./20260603-f0-06-users-memberships.md)

## 1. 요구사항 분석
### 1.1 문제 정의
알림의 **저장·조회·읽음 처리** 토대. 전 모듈이 알림을 쌓고, 사용자는 목록·안읽음 카운트를
보고 읽음 처리한다.
### 1.2 목표
- 알림 생성(type·title·body·link·meta) · 사용자별 목록(페이지)
- **읽음/안읽음** 토글 · 안읽음 카운트 · 전체 읽음 · 격리

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 알림 생성·저장 | Must |
| F-002 | 목록 조회(페이지·필터) | Must |
| F-003 | 읽음/전체읽음·안읽음 카운트 | Must |
| F-004 | 보존/정리(오래된 알림) | Should |

## 3. 기능 설계
### 3.1 데이터 모델
`notifications(id, tenant_id, recipient_id, type, title, body, link, is_read, read_at, created_at, meta)` + 인덱스 `(tenant_id, recipient_id, is_read, created_at)`
### 3.2 API
`GET /notifications?unread` · `PATCH /notifications/{id}/read` · `POST /notifications/read-all` · `GET /notifications/unread-count`

## 4. 개발 체크리스트
- [ ] F-001 생성 · [ ] F-002 목록 · [ ] F-003 읽음/카운트 · [ ] F-004 보존
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 알림 생성/목록 | 정확 |
| T-2 | 읽음/카운트 | 갱신 |
| T-3 | 전체 읽음 | 일괄 |
| T-4 | 격리 | 본인/테넌트만 |

## 7. 최종 검토
- [ ] 생성·목록·읽음·카운트·격리 그린
