# F7: Multi-LLM Support - SPEC

## 개요
Claude 외에 GPT, Gemini 등 다른 LLM Provider를 지원하여
Repository별 또는 전역 설정으로 LLM을 선택할 수 있게 한다.

## 현재 상태
- `LlmClient.java`가 Claude API에 직접 의존 (Anthropic SDK 하드코딩)
- 모델: `Model.CLAUDE_SONNET_4_20250514` 하드코딩

## 범위

### In-Scope
- LLM Provider 추상화 (Strategy 패턴)
- Claude Provider (기존 로직 이전)
- OpenAI GPT Provider 추가
- config.yml에서 LLM 선택 가능
- Provider별 응답 파싱 통일

### Out-of-Scope
- 로컬 LLM (Ollama 등)
- LLM 응답 캐싱
- 비용 최적화 라우팅

## 설계

### 인터페이스

```java
public interface LlmProvider {
    /**
     * 1차 리뷰 수행
     * @param systemPrompt 시스템 프롬프트 (역할, 응답 형식 정의)
     * @param userMessage 사용자 프롬프트 (코드, Feature 정보)
     * @return Provider 무관한 표준 리뷰 응답
     */
    LlmReviewResult review(String systemPrompt, String userMessage);

    /**
     * 2차 리뷰 수행 (추가 컨텍스트 제공)
     * @param systemPrompt 시스템 프롬프트
     * @param conversationHistory Provider 무관한 대화 이력
     * @param additionalContext 추가 파일 내용
     * @return 표준 리뷰 응답
     */
    LlmReviewResult continueReview(String systemPrompt,
                                    List<ConversationTurn> conversationHistory,
                                    String additionalContext);

    /** Provider 이름 (로깅/설정용) */
    String getProviderName();

    /** 지원하는 최대 context window (토큰 수) */
    int getMaxContextTokens();

    /** 지원하는 최대 응답 토큰 수 */
    int getMaxResponseTokens();
}
```

#### Provider 무관 타입 정의

```java
/** Provider 응답을 표준화한 DTO */
public record LlmReviewResult(
    boolean needMoreContext,
    String generalReview,           // nullable
    List<InlineComment> inlineComments,  // empty list if none
    MemorySuggestion memorySuggestion,   // nullable
    List<String> requestedFiles,    // empty list if none
    String reason                   // nullable, needMoreContext=true일 때
) {}

/** Provider 무관 대화 이력 */
public record ConversationTurn(
    Role role,      // USER, ASSISTANT
    String content
) {
    public enum Role { USER, ASSISTANT }
}
```

**기존 ReviewResponse와의 관계:**
- `ReviewResponse` → `LlmReviewResult`로 대체
- 각 Provider 구현체 내부에서 SDK 응답을 `LlmReviewResult`로 변환
- `InlineComment`, `MemorySuggestion`은 그대로 재사용 (LLM 무관 DTO)

### Provider별 변환 책임

| Provider | SDK 응답 타입 | → 변환 대상 |
|----------|---------------|-------------|
| Claude | `com.anthropic.models.Message` | `LlmReviewResult` |
| OpenAI | `ChatCompletion` | `LlmReviewResult` |
| Gemini (향후) | `GenerateContentResponse` | `LlmReviewResult` |

각 Provider는 자체 SDK의 응답을 JSON 파싱하여 `LlmReviewResult`로 변환하는 책임을 가진다.
공통 JSON 파싱 유틸리티(`ReviewResponseParser`)를 제공하되, Provider별 특이사항은 각 구현체에서 처리.

### Provider 구현
- `ClaudeLlmProvider` - 기존 LlmClient 로직 이전
- `OpenAiLlmProvider` - GPT-4o 지원
- `LlmProviderFactory` - config 기반 Provider 선택

### config.yml 연동
```yaml
review:
  llm:
    provider: "claude"          # claude | openai
    model: "claude-sonnet-4"    # 모델 지정
```

## 수정 대상 파일
- **신규**: `LlmProvider.java` - 인터페이스
- **신규**: `ClaudeLlmProvider.java` - Claude 구현
- **신규**: `OpenAiLlmProvider.java` - OpenAI 구현
- **신규**: `LlmProviderFactory.java` - Provider 팩토리
- **수정**: `LlmClient.java` - Provider 위임으로 리팩토링
- **수정**: `build.gradle` - OpenAI SDK 의존성 추가

## 의존성
- **F6: review-customization** 필요 (config.yml 로딩 시스템)

## 완료 조건
- [ ] LlmProvider 인터페이스 정의
- [ ] Claude Provider 구현 (기존 동작 유지)
- [ ] OpenAI Provider 구현
- [ ] config.yml 기반 Provider 선택
- [ ] 단위 테스트 5개 이상
