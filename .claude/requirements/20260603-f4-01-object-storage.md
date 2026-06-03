# F4-01 — 오브젝트 스토리지 토대 (MinIO)

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: high (Wave 2)
- **상위**: [F4 마스터](./20260603-f4-filedrive-master.md) · **의존**: [F0-01](./20260603-f0-01-tenant-isolation.md)·[F0-09 quota](./20260603-f0-09-subscription-quota.md) · 인프라 **MOAI-MinIO**

## 1. 요구사항 분석
### 1.1 문제 정의
파일의 **물리 저장/조회** 토대. MinIO에 업로드·다운로드하고, 테넌트/공간으로 격리하며 quota를
적용한다. (F2 첨부·F8 문서가 재사용)
### 1.2 목표
- 업로드(멀티파트·**presigned**)·다운로드(presigned) · SHA-256 dedup
- 버킷=테넌트, prefix=공간/폴더 **네임스페이싱** · quota(F0-09) 차감
- MIME/매직바이트 검증 · 메타데이터

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 업로드(presigned/멀티파트) + 메타 | Must |
| F-002 | 다운로드(presigned·권한) | Must |
| F-003 | dedup(해시)·MIME 검증 | Should |
| F-004 | quota 차감·격리 네임스페이싱 | Must |

## 3. 기능 설계
### 3.1 설계
`files(id, tenant_id, storage_key, mime, size, hash, ...)` · MinIO 버킷/prefix · presigned URL(TTL) · quota(F0-09) 연동
### 3.2 인프라
MOAI-MinIO 재사용(버킷 분리) · 검증·바이러스 스캔(후속)

## 4. 개발 체크리스트
- [ ] F-001 업로드 · [ ] F-002 다운로드 · [ ] F-003 dedup/검증 · [ ] F-004 quota/격리
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 업/다운 | presigned 동작 |
| T-2 | 중복 업로드 | dedup |
| T-3 | quota 초과 | 차단(F0-09) |
| T-4 | 격리 | 버킷/prefix 분리 |

## 7. 최종 검토
- [ ] 업/다운·dedup·검증·quota·격리 그린 (F2/F8 재사용 가능)
