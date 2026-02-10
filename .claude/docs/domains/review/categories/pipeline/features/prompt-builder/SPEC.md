# Prompt Builder - SPEC (MVP, 구현 완료)

## 개요
Feature 정보, Feature Memory, 변경 코드를 조합하여 LLM에 전송할 프롬프트를 생성하는 컴포넌트.

## 상태: 구현 완료

## 관련 파일
- `PromptBuilder.java` - 프롬프트 생성 로직

## 범위 정의

### In-Scope
- 시스템 프롬프트 생성 (LLM 역할/형식 정의)
- Feature 정보 + Memory + Code 기반 사용자 프롬프트 생성
- 핵심 파일에 라인 번호 추가
- 2차 리뷰용 추가 프롬프트 생성

### Out-of-Scope
- 프롬프트 A/B 테스팅
- 다국어 프롬프트
- 프롬프트 크기 자동 조절 (F1에서 구현 예정)

## 의존성
- **의존**: `FeatureRegistry` → Feature 정보
- **의존**: `FeatureMemory` → 과거 학습 내용
- **의존**: `CodeCollector` → 변경/핵심 파일 내용
- **피의존**: `PrReviewService` → 프롬프트 생성 요청

## 주요 메서드

### buildSystemPrompt()
LLM의 역할과 응답 형식을 정의하는 시스템 프롬프트 반환.

**현재 내용:**
- 역할: "전문 코드 리뷰어"
- 리뷰 원칙: 구체적 피드백, 긍정 언급, 우선순위(Critical/Major/Minor), 라인 번호 명시
- 응답 형식 2가지:
  - `needMoreContext: true` → 추가 파일 요청 (requestedFiles, reason)
  - `needMoreContext: false` → 최종 리뷰 (generalReview, inlineComments, memorySuggestion)
- inlineComments 작성 규칙 (path, line, body)
- memorySuggestion 가이드 (summary, keyPoints, relatedFiles)

### buildInitialPrompt(resolvedFeature, changedFiles, coreFilesContent)
1차 리뷰 요청용 사용자 프롬프트 생성.

**프롬프트 구성:**
```
# 기능 정보
- 기능: {name}
- 설명: {description}

# 기능 메모리 (과거 지식)    ← memory가 있을 때만
- 요약: {summary}
- 핵심 포인트:
  * {keyPoint1}
  * {keyPoint2}

# 변경된 파일
## {filePath}
```diff
{diff 내용}
```

# 핵심 파일 (전체 코드 - 라인 번호 포함)    ← coreFiles가 있을 때만
## {filePath}
```java
   1: {line1}
   2: {line2}
```

위 코드를 리뷰해주세요.
**중요:** 특정 코드 라인에 대한 지적사항이 있다면 반드시 파일 경로와 라인 번호를 명시해주세요.
```

### addLineNumbers(content)
- 코드에 `%4d: ` 형식으로 라인 번호 추가
- 핵심 파일(coreFiles)에만 적용

### buildFollowUpPrompt(requestedFilesContent)
2차 리뷰용 추가 파일 제공 프롬프트 (현재 F2 미구현으로 미사용).

**구성:**
```
# 요청하신 추가 파일
## {filePath}
```java
{파일 내용}
```

이제 최종 리뷰를 진행해주세요.
```

## 토큰 한계 전략

### 현재 제한
- Anthropic API `maxTokens` 응답: 4000 토큰
- 입력 context window: Claude Sonnet 4 기준 200K 토큰
- 프롬프트 크기 제한 없음 (현재 미관리)

### 프롬프트 크기 추정

| 섹션 | 예상 토큰 | 비율 |
|------|-----------|------|
| System Prompt | ~500 | 고정 |
| Feature 정보 | ~100 | 고정 |
| Feature Memory | ~200 | 가변 (누적 증가) |
| 변경 파일 (diff) | ~500-10000 | 가변 (PR 크기) |
| 핵심 파일 (core files) | ~1000-20000 | 가변 (파일 수/크기) |
| **합계** | **~2300-30800** | - |

### Context Window 초과 시 전략 (미구현, F1에서 구현 예정)

1. **대용량 PR 감지**: 총 diff 라인 > 500줄 또는 파일 수 > 15개
2. **단계적 축소**:
   - 1단계: Core files 중 변경되지 않은 파일 제외
   - 2단계: diff에서 context 라인 축소 (±3줄 → ±1줄)
   - 3단계: 파일별 diff를 요약 형태로 변환 ("N줄 추가, M줄 삭제")
3. **Feature Memory 크기 제한**: summary 최대 500자, keyPoints 최대 10개
4. **하드 리밋**: 전체 프롬프트 100K 토큰 초과 시 에러 반환 (리뷰 불가 알림)

## 에러 처리 정책

| 상황 | 동작 | 영향 |
|------|------|------|
| Feature 정보 null | 기본 "UNKNOWN" Feature로 프롬프트 생성 | 범용 리뷰 수행 |
| Feature Memory null | 메모리 섹션 생략 | 메모리 없이 리뷰 |
| 변경 파일 목록 비어있음 | "변경된 파일이 없습니다" 포함 | LLM에 diff 없이 전달 |
| Core file 내용 null | 해당 파일 섹션 생략 | 핵심 파일 컨텍스트 누락 |
| 프롬프트 크기 초과 (100K+ 토큰) | 현재 미처리 (F1에서 구현 예정) | API 에러 가능 |

## 테스트 현황
- **없음** (향후 F4에서 추가 예정)

## 알려진 제한
- Feature의 `reviewFocus` 필드가 프롬프트에 미포함
- PR 제목/본문 컨텍스트가 프롬프트에 미포함
- 시스템 프롬프트가 일반적 ("전문 코드 리뷰어" 수준)
- 변경 파일에 diff만 제공 (변경 전후 맥락 부족)
- → F1: review-quality에서 대폭 개선 예정
