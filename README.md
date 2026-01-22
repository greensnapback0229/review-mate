# PR Review Server

AI 기반 자동 코드 리뷰 시스템으로, GitHub PR에 대해 기능별 맞춤 리뷰를 제공합니다.

## 주요 기능

### 1. Feature-Based Review

- PR에서 변경된 파일을 분석하여 영향받는 기능을 자동으로 식별
- 각 기능별로 독립적인 리뷰 수행
- Feature Registry를 통해 기능별 관련 파일 및 핵심 파일 관리

### 2. Feature Memory

- 각 기능에 대한 과거 리뷰 결과를 학습하여 저장
- MySQL 데이터베이스에 Repository별로 Feature Memory 저장
- 이전 리뷰에서 학습한 내용을 활용하여 더 정확한 리뷰 제공

### 3. GitHub App 통합

- GitHub Webhook을 통해 PR 이벤트 자동 감지
- GitHub App 인증으로 안전한 API 접근
- PR에 자동으로 리뷰 코멘트 작성

## 시스템 구조

```
PR 생성/업데이트
    ↓
GitHub Webhook
    ↓
WebhookController
    ↓
PrReviewService
    ↓
┌─────────────────────────────────┐
│ 1. Feature Registry 초기화        │
│    (PR 브랜치에서 읽기)             │
└─────────────────────────────────┘
    ↓
┌─────────────────────────────────┐
│ 2. PR 파싱                       │
│    - 변경된 파일 분석               │
│    - 영향받는 기능 식별              │
└─────────────────────────────────┘
    ↓
┌─────────────────────────────────┐
│ 3. 기능별 리뷰 수행                  │
│    - FeatureResolver             │
│    - CodeCollector               │
│    - LlmClient (Claude)          │
└─────────────────────────────────┘
    ↓
┌─────────────────────────────────┐
│ 4. Feature Memory 업데이트        │
│    - MySQL에 저장                 │
└─────────────────────────────────┘
    ↓
GitHub PR 코멘트 작성
```

## 핵심 컴포넌트

### Feature Registry

PR의 `.github/pr-review/feature-registry.yml` 파일에서 기능 정의를 읽어옵니다.

```yaml
features:
  PAYMENT:
    description: "결제 및 금액 처리"
    paths:
      - "src/main/java/com/app/payment/"
    coreFiles:
      - "PaymentService.java"
```

### Feature Memory

각 기능에 대한 학습 내용을 저장합니다.

```json
{
  "feature": "PAYMENT",
  "summary": "결제 기능은 트랜잭션 무결성이 중요",
  "keyPoints": [
    "동시성 처리 주의",
    "에러 로깅 필수"
  ],
  "relatedFiles": [
    "PaymentService.java"
  ],
  "updatedAt": "2026-01-22T10:00:00"
}
```

### Review Flow

1. **Feature Registry 초기화**: PR 브랜치에서 `feature-registry.yml` 읽기
2. **PR 파싱**: 제목, 본문, 변경된 파일 분석
3. **기능 식별**: 변경된 파일과 매칭되는 기능 찾기
4. **코드 수집**:
    - 변경된 파일의 diff
    - 핵심 파일의 전체 코드
5. **LLM 리뷰**: Claude API로 코드 리뷰 요청
6. **Memory 업데이트**: 리뷰 결과를 Feature Memory에 저장
7. **결과 병합**: 여러 기능의 리뷰를 하나로 병합
8. **GitHub 코멘트**: PR에 리뷰 결과 작성

## 설치 및 실행

### 필수 요구사항

- Docker & Docker Compose
- GitHub App 생성 (Private Key 필요)
- Anthropic API Key (Claude)

### 환경 변수 설정

`.env` 파일 생성:

```env
# MySQL
MYSQL_HOST=mysql
MYSQL_PORT=3306
MYSQL_DATABASE=pr_review
MYSQL_USERNAME=root
MYSQL_PASSWORD=your_password

# GitHub App
GITHUB_APP_ID=your_app_id
GITHUB_APP_INSTALLATION_ID=your_installation_id
GITHUB_APP_PRIVATE_KEY_PATH=secrets/private-key.pem
GITHUB_WEBHOOK_SECRET=your_webhook_secret

# Anthropic
ANTHROPIC_API_KEY=your_api_key

# Server
PORT=8080
DOCKER_IMAGE=smdmim/pr-review:latest
```

### GitHub App 설정

1. GitHub에서 새 App 생성
2. Permissions 설정:
    - Repository permissions:
        - Contents: Read
        - Pull requests: Read & Write
    - Subscribe to events:
        - Pull request
3. Private Key 생성 및 다운로드
4. `secrets/` 폴더에 Private Key 저장

### Docker Compose 실행

```bash
# MySQL 및 애플리케이션 시작
docker-compose up -d

# 로그 확인
docker-compose logs -f pr-review-server
```

### 개발 환경 배포

```bash
# 테스트 + 빌드 + Docker 이미지 생성 + 푸시
./deploy-dev.sh
```

## Feature Registry 작성 방법

PR을 리뷰하려는 저장소에 `.github/pr-review/feature-registry.yml` 파일을 생성합니다.

```yaml
features:
  AUTH:
    description: "인증 및 권한 관리"
    paths:
      - "src/main/java/com/app/auth/"
      - "src/main/java/com/app/security/"
    coreFiles:
      - "AuthService.java"
      - "SecurityConfig.java"

  PAYMENT:
    description: "결제 처리"
    paths:
      - "src/main/java/com/app/payment/"
    coreFiles:
      - "PaymentService.java"
```

**필드 설명:**

- `description`: 기능에 대한 간단한 설명
- `paths`: 이 기능과 관련된 디렉토리 경로 목록
- `coreFiles`: 리뷰 시 참고할 핵심 파일 목록

## 데이터베이스 스키마

### Repository 테이블

```sql
CREATE TABLE repository
(
    repository_id BIGINT PRIMARY KEY,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Feature Memory 테이블

```sql
CREATE TABLE feature_memory
(
    feature_memory_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    repository_id          BIGINT NOT NULL,
    feature_name           VARCHAR(255) NOT NULL,
    feature_memory_content JSON NOT NULL,
    created_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (repository_id) REFERENCES repository (repository_id),
    UNIQUE KEY uk_repo_feature (repository_id, feature_name)
);
```

## API 엔드포인트

### Webhook

```
POST /api/webhook/github
```

GitHub Webhook 이벤트를 수신합니다.

### Health Check

```
GET /actuator/health
```

서버 상태를 확인합니다.

## 기술 스택

- **Backend**: Spring Boot 3.4.1
- **Database**: MySQL 8.0
- **ORM**: Spring Data JPA
- **LLM**: Claude Sonnet 4 (Anthropic API)
- **GitHub Integration**: GitHub App, GitHub API Java Library
- **Container**: Docker, Docker Compose

## 개발 가이드

### 프로젝트 구조

```
src/main/java/greensnaback0229/pr_review_server/
├── aggregator/          # 리뷰 집계
├── collector/           # 코드 수집
├── config/              # 설정
├── feature/             # Feature Registry & Memory
├── github/              # GitHub App 인증
├── llm/                 # LLM 클라이언트
├── parser/              # PR 파싱
├── prompt/              # 프롬프트 빌더
└── webhook/             # Webhook 처리
```

### 테스트 실행

```bash
./gradlew test
```

### 빌드

```bash
./gradlew build
```

## 트러블슈팅

### GitHub "Bad credentials" 에러

- GitHub App 토큰이 1시간마다 만료됩니다
- FeatureRegistryLoader가 매번 새로운 토큰을 생성하도록 구현되어 있습니다

### MySQL 연결 실패

- `docker-compose logs mysql`로 MySQL 로그 확인
- `MYSQL_HOST`, `MYSQL_PORT` 환경 변수 확인
- DatabaseConnectionLogger가 연결 정보를 로그에 출력합니다

### Feature Memory가 저장되지 않음

- LLM 응답에 `memorySuggestion`이 포함되어 있는지 확인
- `LlmClient` 로그에서 JSON 파싱 확인

## 라이선스

MIT License

## 개발자

greensnaback0229
