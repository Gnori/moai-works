# moai-works-api

MOAI Works **백엔드** — Spring Boot REST API.

> 🌱 골격만 구성됨. 아직 빌드 파일(`build.gradle`/`pom.xml`)·소스 없음.

## 구조 (예정)

```
moai-works-api/
├─ src/
│  ├─ main/java/com/moai/works/   # 도메인 모듈 (user/auth/board/calendar/approval/file)
│  ├─ main/resources/             # application.yml, 정적 리소스
│  └─ test/java/                  # 테스트
├─ build.gradle  (또는 pom.xml)   # ← 초기화 시 생성
└─ README.md
```

## 초기화 방법 (착수 시)

[Spring Initializr](https://start.spring.io) 또는 CLI 로 생성:
- Dependencies(제안): Spring Web, Spring Data JPA, Spring Security, Validation, PostgreSQL Driver, Lombok
- Group: `com.moai` / Artifact: `works`

생성 후 본 디렉터리에 풀어 넣고 `./gradlew bootRun` (또는 `./mvnw spring-boot:run`).
