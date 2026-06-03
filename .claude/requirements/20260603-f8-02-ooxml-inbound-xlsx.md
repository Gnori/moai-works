# F8-02 — OOXML 인바운드 변환 (xlsx → Univer)

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: complex · **레벨**: L3 · **우선순위**: **high (최대 난점·PoC 선행)**
- **상위**: [F8 마스터](./20260603-f8-ai-document-master.md) · **의존**: [F8-01 문서모델](./20260603-f8-01-document-model.md)·[F4-05 파싱(POI)](./20260603-f4-05-document-parsing.md)

## 1. 요구사항 분석
### 1.1 문제 정의
기존 `.xlsx`를 Univer OSS 편집기로 **고충실로 열려면**, OOXML → **Univer 스냅샷(JSON)** 변환기를
직접 만들어야 한다(Univer OSS 는 import 미지원=Pro). **이게 F8 의 최대 난점이라 PoC 선행.**
### 1.2 목표
- `original.xlsx` → **Apache POI XSSF** 파싱 → Univer Sheet 스냅샷(셀·서식·수식·병합 등)
- **앵커 매핑**: Univer 요소 ↔ OOXML 파트/노드 키 보존(F8-03 패치용)
- POI 미모델 요소(차트·피벗 등)는 **Shadow Package(F8-03)에 보존** + 스냅샷에 placeholder

### 1.3 제약
- POI XSSF · Univer 스냅샷 스키마(버전) · 대용량 비동기 · 충실도 한계 명시

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | XSSF 파싱 → Univer 스냅샷(셀·서식·수식) | Must |
| F-002 | 병합·시트·기본 스타일 매핑 | Must |
| F-003 | **앵커 키** 내장(셀↔XML 위치) | Must |
| F-004 | 미지원 요소 placeholder + Shadow 보존 연계 | Must |
| F-005 | 비동기·실패 graceful | Should |

## 3. 기능 설계
### 3.1 흐름
```
original.xlsx → POI XSSF → 매핑(셀값·서식·수식·병합) → Univer 스냅샷 JSON(+앵커)
미모델 파트 → Shadow Package(F8-03) 보존 표시
```
### 3.2 설계
- 매핑 테이블(XSSF→Univer) · 앵커=sheet/row/col · 충실도 테스트(F8-11) 필수

## 4. 개발 체크리스트
- [ ] F-001 파싱→스냅샷 · [ ] F-002 병합/스타일 · [ ] F-003 앵커 · [ ] F-004 placeholder/Shadow · [ ] F-005 비동기
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 일반 xlsx | 셀·서식·수식 재현 |
| T-2 | 병합/스타일 | 유지 |
| T-3 | 차트 포함 | placeholder + Shadow 보존 |
| T-4 | 앵커 | F8-03 패치 가능 |
| T-5 | 손상 파일 | graceful |

## 7. 최종 검토
- [ ] xlsx→Univer 스냅샷·앵커·미지원 보존 · **fidelity 테스트(F8-11) 통과** (PoC 검증)
