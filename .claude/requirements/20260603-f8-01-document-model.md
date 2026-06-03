# F8-01 — 문서 모델 · 저장 (Snapshot / Change-Set)

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: high (Wave 5)
- **상위**: [F8 문서작성 마스터](./20260603-f8-ai-document-master.md) · **의존**: [F4-01 스토리지](./20260603-f4-01-object-storage.md)·[F4-04 버전](./20260603-f4-04-versioning.md)·[F0-01](./20260603-f0-01-tenant-isolation.md)

## 1. 요구사항 분석
### 1.1 문제 정의
Univer OSS 편집기의 문서 상태를 **JSON Snapshot + Change-Set**으로 저장·자동저장·버전관리하는
토대. (F8 스택: Univer OSS + POI + Shadow Package)
### 1.2 목표
- Univer **스냅샷(JSON)** + **변경셋(change-set)** 저장 · **자동저장**(주기/디바운스)
- 버전(F4-04)·원본 파일(F4) 연결 · 문서 메타·권한(F4-03)
- 오브젝트 스토리지(F4-01): original·snapshot·change-set·shadow·exported 구분 보관

### 1.3 제약
- 격리(F0-01) · 동시편집(F8-10) 충돌은 별도 · 스냅샷 스키마 버전 관리

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 스냅샷/변경셋 저장 | Must |
| F-002 | 자동저장(주기/디바운스) | Must |
| F-003 | 버전 연결(F4-04)·원본 보관 | Must |
| F-004 | 문서 메타·권한(F4-03) | Must |

## 3. 기능 설계
### 3.1 데이터/스토리지
`documents(id, tenant_id, file_id, current_version, snapshot_key, ...)` · 스토리지(F4-01): `original.*`·`snapshot-vN.json`·`change-set-vN.json`·`shadow-package.zip`·`exported-vN.*`
### 3.2 흐름
편집 → 자동저장(스냅샷/변경셋) → 버전 증가(F4-04) → 내보내기 시 F8-03/04/05

## 4. 개발 체크리스트
- [ ] F-001 저장 · [ ] F-002 자동저장 · [ ] F-003 버전/원본 · [ ] F-004 메타/권한
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 스냅샷/변경셋 저장 | 복원 가능 |
| T-2 | 자동저장 | 주기 반영 |
| T-3 | 버전/원본 | 연결·보존 |
| T-4 | 격리/권한 | 차단 |

## 7. 최종 검토
- [ ] 스냅샷/변경셋·자동저장·버전·격리 그린 (F8 스택 토대)
