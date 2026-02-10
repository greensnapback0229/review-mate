# Review Aggregator - SPEC (MVP, 구현 완료)

## 개요
Feature별 LLM 리뷰 결과를 집계하고, Feature Memory를 업데이트하는 컴포넌트.

## 상태: 구현 완료

## 관련 파일
- `ReviewAggregator.java` - 리뷰 집계 + 메모리 업데이트
- `aggregator/dto/AggregatedReview.java` - 집계 결과 DTO

## 범위 정의

### In-Scope
- Feature별 리뷰 결과 집계 (AggregatedReview)
- 여러 Feature 리뷰 병합 (mergeReviews)
- LLM memorySuggestion → Feature Memory 업데이트

### Out-of-Scope
- 리뷰 품질 점수 산정
- 리뷰 중복 제거
- 리뷰 결과 DB 저장 (F8에서 구현)

## 의존성
- **의존**: `FeatureMemoryRepository` → 메모리 저장
- **피의존**: `PrReviewService` → 리뷰 집계 요청
- **피의존**: `WebhookController` → 리뷰 병합 (mergeReviews)

## AggregatedReview 구조
```java
@Getter @Builder
public class AggregatedReview {
    private String feature;                    // Feature 이름
    private String review;                     // 전체 리뷰 텍스트
    private List<InlineComment> inlineComments;// 인라인 코멘트
    private FeatureMemory updatedMemory;       // 업데이트된 메모리
    private LocalDateTime reviewedAt;          // 리뷰 시각
}
```

## 주요 메서드

### aggregate(repositoryId, feature, reviewResponse)
단일 Feature 리뷰 결과 집계:
```
1. ReviewResponse에서 generalReview, inlineComments 추출
2. memorySuggestion이 있으면 → updateMemoryFromSuggestion() 호출
3. AggregatedReview 빌드 반환
```

### mergeReviews(reviews)
여러 Feature의 리뷰를 하나의 텍스트로 병합:
```
# 전체 리뷰 결과

## PAYMENT 기능
{review 내용}
---

## AUTH 기능
{review 내용}
---
```
- 1개 Feature만 있으면 바로 반환
- 빈 리스트면 "리뷰 결과가 없습니다." 반환

### updateMemoryFromSuggestion(repositoryId, feature, suggestion) [private]
LLM의 memorySuggestion을 Feature Memory에 반영:
```
1. 기존 메모리 조회 (FeatureMemoryRepository)
2. 기존 있음:
   - summary: 기존 + " | " + 새 summary (append)
   - keyPoints: 기존 + 새 keyPoints (리스트 합산)
   - relatedFiles: 기존 + 새 files (중복 제거)
3. 기존 없음:
   - LLM 제안 그대로 새 메모리 생성
4. FeatureMemoryRepository.save() 호출
```

## 크기 및 제한

| 항목 | 현재 값 | 권장 상한 | 비고 |
|------|---------|-----------|------|
| Feature별 리뷰 텍스트 | 제한 없음 (LLM 응답 전체) | maxTokens에 의존 | 4000 토큰 ≈ 한국어 약 2000자 |
| Feature별 inline comments | 제한 없음 | **10개/Feature** | F6 config에서 설정 가능 예정 |
| 병합 리뷰 전체 크기 | Feature 수 × 리뷰 크기 | **GitHub comment 65535자** | GitHub API body 길이 제한 |
| memorySuggestion summary | 제한 없음 (`" \| "` 연결) | **500자** | Feature Memory 크기 제한과 연동 |
| memorySuggestion keyPoints | 누적 (리스트 합산) | **10개** | Feature Memory 크기 제한과 연동 |

## 에러 처리 정책

| 상황 | 동작 | 영향 |
|------|------|------|
| ReviewResponse null | 빈 AggregatedReview 반환 | 해당 Feature 리뷰 없음 |
| memorySuggestion null | 메모리 업데이트 스킵 | Feature Memory 미갱신 |
| DB 저장 실패 (메모리 업데이트) | 에러 로그 + 리뷰는 반환 | 메모리만 누락 |
| mergeReviews에 빈 리스트 | "리뷰 결과가 없습니다." 반환 | 빈 리뷰 텍스트 |
| inlineComments null | 빈 리스트로 처리 | 정상 동작 |

## 테스트 현황
- `ReviewAggregatorTest.java` - 집계 + 인라인 코멘트 테스트

## 알려진 제한
- summary 병합이 단순 문자열 연결 (`" | "`) → 무한히 길어짐
- keyPoints가 누적만 되고 정리/요약 로직 없음
- memorySuggestion이 null이면 메모리 업데이트 스킵 (LLM이 안 줄 수 있음)
- 병합 시 LLM이 기존 메모리를 요약/정리하는 방식이 더 효과적일 수 있음
