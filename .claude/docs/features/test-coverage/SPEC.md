# F4: Test Coverage - SPEC

## 개요
핵심 컴포넌트의 테스트 커버리지를 확대하여 리팩토링과 기능 확장의 안전망을 구축한다.

## 현재 테스트 상태

| 파일 | 테스트 존재 | 커버리지 |
|------|------------|----------|
| FeatureRegistry | **있음** | 기본 매칭 |
| FeatureRegistryLoader | **있음** | 로딩 테스트 |
| FeatureResolver | **있음** | 기본 해석 |
| PrParser | **있음** | 파싱 테스트 |
| CodeCollector | **있음** | 수집 테스트 |
| ReviewAggregator | **있음** | 집계 테스트 |
| **PromptBuilder** | **없음** | - |
| **LlmClient** | **없음** | - |
| **GitHubReviewClient** | **없음** | - |
| **WebhookController** | **없음** | - |
| **PrReviewService** | **없음** | - |

## 범위

### In-Scope
- PromptBuilder 단위 테스트
- LlmClient 단위 테스트 (응답 파싱 중심, API 호출은 mock)
- GitHubReviewClient 단위 테스트 (parsePatch 로직 중심)
- WebhookController 통합 테스트 (MockMvc)
- PrReviewService 통합 테스트 (전체 플로우 mock 기반)

### Out-of-Scope
- 실제 GitHub API / Claude API 연동 E2E 테스트
- 성능/부하 테스트
- UI 테스트

## 테스트 계획

### 1. PromptBuilder 테스트
- 시스템 프롬프트에 필수 섹션 포함 여부
- Feature 정보가 프롬프트에 올바르게 포함되는지
- Feature Memory가 있을 때 / 없을 때
- 라인 번호 추가 로직
- 핵심 파일 포함 여부

### 2. LlmClient 테스트 (Mock 기반)
- JSON 정상 파싱 (generalReview + inlineComments + memorySuggestion)
- needMoreContext=true 응답 파싱
- JSON 블록 없는 응답 → fallback
- 깨진 JSON → fallback
- inlineComment에 필수 필드 누락 시

### 3. GitHubReviewClient 테스트
- `parsePatch()` 단위 테스트:
  - 단일 hunk diff → 올바른 position 매핑
  - 다중 hunk diff → 올바른 position 매핑
  - 추가/삭제/컨텍스트 라인 혼합
  - 빈 patch → 빈 매핑
- Review 생성 로직 (GitHub API mock)

### 4. WebhookController 통합 테스트 (MockMvc)
- 정상 PR 이벤트 → 200 OK
- 중복 delivery ID → "Duplicate" 응답
- 무시되는 action (closed, labeled) → "Ignored" 응답
- Draft PR → 처리 안 함
- 잘못된 payload → 에러 응답

### 5. PrReviewService 통합 테스트
- 전체 리뷰 플로우 (mock 기반): Registry → Parser → Collector → LLM → Aggregator
- Feature가 없는 파일만 변경됐을 때
- LLM 호출 실패 시 → graceful 에러 처리
- 여러 Feature가 감지됐을 때

## 수정/추가 대상 파일
- **신규**: `PromptBuilderTest.java`
- **신규**: `LlmClientTest.java`
- **신규**: `GitHubReviewClientTest.java`
- **신규**: `WebhookControllerTest.java`
- **신규**: `PrReviewServiceTest.java`

## 완료 조건
- [ ] 5개 신규 테스트 파일 생성
- [ ] 테스트 20개 이상 추가
- [ ] 전체 테스트 통과 (`./gradlew test`)
- [ ] parsePatch() 에지 케이스 커버
- [ ] LlmClient 응답 파싱 에지 케이스 커버
