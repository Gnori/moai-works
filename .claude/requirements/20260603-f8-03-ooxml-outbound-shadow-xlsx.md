# F8-03 — OOXML 아웃바운드 + Shadow Package (Univer → xlsx)

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: complex · **레벨**: L3 · **우선순위**: high (Wave 5)
- **상위**: [F8 마스터](./20260603-f8-ai-document-master.md) · **의존**: [F8-02 인바운드](./20260603-f8-02-ooxml-inbound-xlsx.md)·[F8-01 문서모델](./20260603-f8-01-document-model.md)

## 1. 요구사항 분석
### 1.1 문제 정의
편집 결과(change-set/스냅샷)를 **고충실 `.xlsx`로 내보낸다**. 핵심은 **Shadow Package** —
원본 OOXML 파트를 보존하고 **바뀐 파트만 패치**해 POI 미모델 요소(차트·피벗 등) 손실을 막는다.
### 1.2 목표
- change-set → POI XSSF 변형(셀·서식·수식) + **Shadow Package 패치**(원본 파트 보존, 변경분만)
- 앵커(F8-02)로 변경 위치 식별 · OPC 무결성(rels·ContentTypes·sharedStrings·styles) 유지
- exported.xlsx 생성·검증

### 1.3 제약
- OPC 패키지 안전 패치(손상 방지) · fidelity 테스트(F8-11) 필수

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | change-set → POI 변형(셀/서식/수식) | Must |
| F-002 | **Shadow Package**: 원본 보존 + 변경 파트 패치 | Must |
| F-003 | 앵커 기반 변경 식별 | Must |
| F-004 | OPC 무결성 검증(손상 방지) | Must |
| F-005 | exported 저장(F8-01/F4-04 버전) | Must |

## 3. 기능 설계
### 3.1 흐름
```
change-set + 앵커 → 변경 셀/파트 식별 → shadow-package(원본 복제)에 해당 파트만 패치(POI)
 → OPC 무결성 검증 → exported-vN.xlsx → 저장(F8-01/버전 F4-04)
```
### 3.2 설계
POI 미모델 파트(차트·피벗·도형)는 **원본 그대로 보존** · sharedStrings/styles 동기화 주의

## 4. 개발 체크리스트
- [ ] F-001 변형 · [ ] F-002 Shadow 패치 · [ ] F-003 앵커식별 · [ ] F-004 무결성 · [ ] F-005 저장
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 셀 편집 내보내기 | 변경 반영 |
| T-2 | **차트 포함 라운드트립** | 차트 보존(손실 0) |
| T-3 | OPC 무결성 | 손상 없음 |
| T-4 | 열기→편집→내보내기 diff | F8-11 통과 |

## 7. 최종 검토
- [ ] change-set→xlsx·**Shadow 보존(손실0)**·OPC 무결성·fidelity 그린
