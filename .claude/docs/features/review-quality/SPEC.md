# F1: Review Quality - SPEC

## 개요
LLM에게 전달하는 프롬프트를 개선하고, 응답 파싱을 강화하여
리뷰 품질을 높인다. 더 구체적이고 실행 가능한 리뷰를 생성하는 것이 목표.

## 현재 문제점

### 프롬프트 관련
1. **시스템 프롬프트가 너무 일반적**: "전문 코드 리뷰어" 수준의 지시만 있음
2. **리뷰 Focus 미활용**: Feature Registry의 `reviewFocus` 필드가 프롬프트에 미포함
3. **diff 컨텍스트 부족**: 변경된 코드만 전달, 변경 전후 맥락이 부족
4. **응답 토큰 제한**: 4000 토큰으로 복잡한 PR에서 리뷰가 잘릴 수 있음

### 응답 파싱 관련
1. **JSON 파싱 실패 시 fallback이 약함**: 전체 텍스트를 generalReview로 사용
2. **inlineComment의 line 번호 정확도**: LLM이 잘못된 라인 번호를 반환할 수 있음
3. **memorySuggestion 누락 가능**: LLM이 memory 제안을 안 할 수 있음

## 범위

### In-Scope
- 시스템 프롬프트 구조화 및 개선
- reviewFocus를 프롬프트에 포함
- diff 컨텍스트 개선 (변경된 라인 전후 코드 포함)
- 응답 토큰 한도 조정 (4000 → 8000)
- JSON 응답 파싱 강화 (부분 파싱, 복구 로직)
- inlineComment 라인 번호 검증 로직 추가

### Out-of-Scope
- 다국어 프롬프트
- 모델 변경 (Sonnet 4 유지)
- 프롬프트 A/B 테스팅 시스템

## 상세 개선 항목

### 1. 시스템 프롬프트 개선
```
현재:
- "당신은 전문 코드 리뷰어입니다" (너무 일반적)

개선:
- 역할 구체화 (Senior Software Engineer, 특정 도메인 전문)
- 리뷰 스타일 가이드 (구체적 예시 포함)
- 출력 품질 기준 명시 (Critical/Major/Minor 분류 기준)
- 긍정 피드백 비율 가이드
- inlineComment 작성 가이드 강화 (라인 번호 정확도)
```

### 2. 사용자 프롬프트 개선 (PromptBuilder)
- Feature의 `reviewFocus` 포함
- PR 제목/본문 컨텍스트 추가
- diff에 변경 전후 코드 3줄 컨텍스트 포함
- 핵심 파일 제공 시 "이 파일이 왜 중요한지" 설명 추가

### 3. 응답 파싱 강화 (LlmClient)
- JSON 추출 시 `\`\`\`json` 외에 순수 JSON도 탐지
- 부분적 JSON 복구 (닫는 괄호 누락 등)
- inlineComment.line이 diff 범위 밖이면 경고 + 스킵
- memorySuggestion 기본값 생성 (LLM이 누락한 경우)

### 4. 토큰 한도 조정

#### 응답 토큰
- `maxTokens`: 4000 → 8000 (응답 최대 길이)
- 대용량 PR (inline comments 10개+)에서 응답이 잘리는 문제 해결

#### 입력 프롬프트 크기 관리
- 전체 프롬프트 토큰 수 추정 로직 추가 (문자 수 기반 근사: 1 토큰 ≈ 3.5 한국어 글자)
- 대용량 PR 감지 기준: diff 500줄+ 또는 15파일+
- 단계적 축소 전략:
  1. Core files 중 변경되지 않은 파일 제외
  2. diff context 라인 축소 (±3줄 → ±1줄)
  3. 파일별 diff 요약 변환
- Feature Memory 크기 제한: summary 500자, keyPoints 10개
- 하드 리밋: 100K 토큰 초과 시 리뷰 불가 알림

## 수정 대상 파일
- **수정**: `PromptBuilder.java` - 시스템/사용자 프롬프트 전면 개선
- **수정**: `LlmClient.java` - maxTokens 조정, 응답 파싱 강화
- **수정**: `PrReviewService.java` - PR 제목/본문을 프롬프트에 전달

## 테스트 케이스
1. 개선된 프롬프트에 reviewFocus 포함 여부
2. diff 컨텍스트에 전후 3줄 포함 여부
3. JSON 파싱 - 정상 JSON
4. JSON 파싱 - `\`\`\`json` 없이 순수 JSON
5. JSON 파싱 - 깨진 JSON → graceful fallback
6. inlineComment 라인 번호가 diff 범위 밖일 때 스킵
7. memorySuggestion 누락 시 기본값 생성

## 완료 조건
- [ ] 시스템 프롬프트 구조화 완료
- [ ] reviewFocus가 프롬프트에 포함
- [ ] 응답 토큰 8000으로 조정
- [ ] JSON 파싱 강화 (3가지 fallback)
- [ ] inlineComment 라인 검증 로직 추가
- [ ] 단위 테스트 6개 이상 통과
