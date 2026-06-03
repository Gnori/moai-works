# F8 — AI 문서작성 마스터 (문서 편집 · 생성 · OOXML)

## 메타데이터

- **작성일**: 2026-06-03
- **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 — 요구사항 분석 (L1 도메인 마스터) · **문서 편집 스택 결정 기록**
- **우선순위**: high (Wave 5 — F13 Agent · F7 지식 이후 본격화)
- **규모**: complex (마스터 — 상세는 하위 L3 spec)
- **레벨**: L1 도메인 마스터 → 하위 L3 = 실제 `/dev req` 구현 단위
- **연관**: [요구사항 로드맵](../../docs/requirements-roadmap.md) · [아키텍처 §6](../../docs/architecture.md) · F4(파일드라이브·파싱) · F7(지식 기반 작성) · F10(전자결재 초안) · F13(문서작성 Agent)

---

## 1. 요구사항 분석

### 1.1 문제 정의
사용자가 **AI로 문서를 작성**(보고서·제안서·기획서·회의록·결재초안)하고, **기존 MS
Office 파일(.docx/.xlsx/.pptx)을 고충실로 열어 편집**하며, **Office·PDF로 내보내기**할 수
있어야 한다. 둘 다(고충실 로드 + AI 생성/웹 편집/내보내기) 충족이 요건.

### 1.2 목표
- 기존 OOXML **고충실 열기/편집/라운드트립**
- **AI 생성**(템플릿·지식 기반) + 웹 협업 편집 + Office/PDF 내보내기
- **라이선스 비용 최소화** (OSS 우선)

### 1.3 제약 / 사전 조사 결론 (2026-06-03)
- OnlyOffice Community = **AGPL v3** + **ONLYOFFICE 로고 제거 불가**(화이트라벨은 상용) → 자체
  브랜드 SaaS 부적합 가능 (상용 라이선스 비용 발생)
- **Univer OOXML import/export = Pro(상용)**. 무료 Apache 코어는 **에디터 엔진만**.
  docx 미성숙·pptx 미지원.
- 백엔드 Java → **Apache POI / docx4j** 로 OOXML 파싱·생성 **무료** 가능.

→ **결론: 라이선스 비용을 자체 엔지니어링으로 치환** (아래 §3.1 스택 결정).

---

## 2. 기능 정의 — 에픽 → L3 spec

| L3 ID | spec | 규모 | 내용 |
|-------|------|------|------|
| **F8-01** | 문서 모델·저장(Snapshot/Change-Set) | standard | Univer 스냅샷 JSON · 변경셋 · 자동저장 · 버전 |
| **F8-02** | **OOXML 인바운드 변환 (xlsx)** | complex | original.xlsx → POI XSSF → Univer 스냅샷 (앵커 포함) |
| **F8-03** | **OOXML 아웃바운드 + Shadow Package (xlsx)** | complex | change-set → POI + 원본 패키지 패치 → exported.xlsx |
| **F8-04** | OOXML 변환 (docx) | complex | XWPF/**docx4j** ↔ Univer Doc (F8-02/03 패턴 확장) |
| **F8-05** | OOXML 변환 (pptx) | complex | XSLF + **Raw XML Patch** ↔ Univer Slide (난이도 최상) |
| **F8-06** | PDF 내보내기 | standard | Office→PDF 변환 |
| **F8-07** | 회사 템플릿 엔진 | standard | 양식 등록·변수 바인딩·문서 종류별 템플릿 |
| **F8-08** | AI 생성(문서작성 Agent 연계) | complex | F13 Agent → 콘텐츠 생성 → 에디터 삽입/문서화 (보고서·회의록·결재초안) |
| **F8-09** | 지식 기반 작성 | standard | F7 RAG·기존 문서 참고 작성 · 출처 |
| **F8-10** | 협업 편집·검토 | standard | 공동편집·코멘트(F5 메신저 "문서 공동 검토" 연계) |
| **F8-11** | **Fidelity 테스트 하니스** | standard | 실 기업 파일 코퍼스 열기→편집→내보내기→diff 회귀 |

---

## 3. 기능 설계

### 3.1 ✅ 문서 편집 스택 결정 (2026-06-03)

**채택: Univer OSS(에디터) + Apache POI/docx4j(서버 변환) + Shadow Package 라운드트립.**
상용(OnlyOffice/Univer Pro) 대신 **OSS + 자체 변환**으로 라이선스 비용 0, 충실도는 Shadow
Package로 확보.

```
[브라우저]  React + Univer OSS 웹 편집기
   │  자동저장: JSON Snapshot / Change-Set
   ▼
[Spring Boot Document API]
   ├─ 문서 메타데이터 · 사용자 권한
   ├─ 자동저장 · 버전 관리
   ├─ 원본 파일 보관
   └─ OOXML 변환 요청 (인바운드/아웃바운드)
   ▼
[OOXML Conversion Worker]
   ├─ XLSX : Apache POI XSSF
   ├─ DOCX : Apache POI XWPF 또는 docx4j
   ├─ PPTX : Apache POI XSLF + Raw XML Patch
   └─ 공통 : OPCPackage + Shadow Package
   ▼
[Object Storage]
   ├─ original.xlsx          (원본 보존)
   ├─ snapshot-v15.json      (Univer 스냅샷)
   ├─ change-set-v16.json    (변경셋)
   ├─ shadow-package.zip     (원본 파트 보존 패키지)
   └─ exported-v16.xlsx      (내보내기 결과)
```

**Shadow Package 원리**: POI가 모델링 못 하는 OOXML 파트(차트·피벗·도형·매크로 등)를 **원본
패키지 그대로 보존**하고, **변경된 파트만 패치** → 라운드트립 충실도 유지.

**양방향 명시**:
- **인바운드(로드)**: `original → POI 파싱 → Univer 스냅샷(JSON) + 앵커`. ⚠️ Univer OSS 는
  OOXML import 가 없으므로 **이 변환기를 직접 구현**(가장 큰 작업).
- **아웃바운드(저장/내보내기)**: `change-set → POI 변형 + Shadow 패치 → exported`.

### 3.2 적용 순서 (충실도·난이도 기준)
1. **XLSX** 먼저 — POI XSSF 강함 + Univer Sheets 가 OSS 중 가장 성숙
2. **DOCX** — **docx4j 권장**(복합 문서 OOXML 모델이 XWPF 보다 우수)
3. **PPTX** — 최후. XSLF 약함 → Raw XML Patch 의존 큼 (고충실 *편집*은 기대치 조정)

### 3.3 ⚠️ 리스크 & 대응 (반드시)
| 리스크 | 대응 |
|--------|------|
| 인바운드 변환기(OOXML→Univer)가 핵심 난점 | F8-02 를 PoC 로 선행 검증 |
| 편집 충실도 천장 = **Univer OSS 렌더 능력**(보존≠편집) | 미지원 요소는 "보존+읽기전용" 처리, 범위 명시 |
| Shadow 패칭 시 OPC(rels·ContentTypes·sharedStrings·styles) 손상 | 앵커 기반 안전 패치 + 무결성 검증 |
| change-set→OOXML 앵커 매핑 | 스냅샷에 OOXML 파트/노드 매핑 키 내장 |
| PPTX 고충실 편집 | 범위 축소(보기·생성 위주) 또는 별도 검토 |
| OOXML 엣지·Univer 스키마 변동 (유지보수) | **Fidelity 테스트 하니스(F8-11)** 로 회귀 차단 |

### 3.4 라이선스 메모 (조사 결과)
- 본 스택은 **Univer OSS(Apache-2.0) + POI/docx4j(Apache-2.0)** → **라이선스 비용 0**.
- 만약 pptx 고충실 *편집*까지 필수가 되면 **OnlyOffice 상용**(AGPL/로고 회피)이 자체 변환
  유지보수보다 저렴할 수 있음 → 그 시점 재평가.
- (참고) OnlyOffice Community 무료는 가능하나 **ONLYOFFICE 로고 유지 의무** + AGPL → 자체
  브랜드 SaaS 에 제약.

---

## 4. 개발 진행 (L3)
- [ ] F8-01 문서 모델·저장 → [ ] **F8-02/03 xlsx 변환(PoC 우선)** → [ ] F8-11 fidelity 하니스
- [ ] F8-04 docx → [ ] F8-05 pptx → [ ] F8-06 PDF
- [ ] F8-07 템플릿 · F8-08 AI 생성 · F8-09 지식기반 · F8-10 협업

---

## 9. 완료 기준 (마스터)
- [ ] xlsx/docx **열기→편집→내보내기 라운드트립**이 fidelity 테스트 코퍼스에서 손상 0
- [ ] AI 생성(템플릿·지식 기반) 문서 작성 + Office/PDF 내보내기
- [ ] pptx 는 합의된 범위(편집 or 보기/생성) 충족
- [ ] 라이선스 비용 0 유지 (또는 상용 전환 결정이 명시적으로 기록)
