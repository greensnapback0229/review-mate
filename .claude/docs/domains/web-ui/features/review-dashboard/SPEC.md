# F8: Review Dashboard - SPEC

## 개요
리뷰 히스토리를 저장하고, REST API를 통해 조회할 수 있는 기능을 제공한다.
간단한 대시보드로 리뷰 현황을 파악할 수 있게 한다.

## 현재 상태
- 리뷰 결과가 GitHub에만 게시되고 서버에 저장되지 않음
- Feature Memory만 MySQL에 저장
- 리뷰 히스토리 조회 불가

## 범위

### In-Scope
- 리뷰 결과 DB 저장 (review_history 테이블)
- 리뷰 히스토리 조회 API
  - `GET /api/reviews` - 전체 조회 (페이징)
  - `GET /api/reviews/{repositoryId}` - Repository별 조회
  - `GET /api/reviews/{repositoryId}/pr/{prNumber}` - PR별 조회
- 리뷰 통계 API
  - `GET /api/stats/{repositoryId}` - 리뷰 수, 평균 코멘트 수 등

### Out-of-Scope
- 웹 프론트엔드 대시보드 UI
- 실시간 알림
- 리뷰 수정/삭제

## DB 스키마

```sql
CREATE TABLE review_history (
    review_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    repository_id     BIGINT NOT NULL,
    pr_number         INT NOT NULL,
    pr_title          VARCHAR(500),
    feature_name      VARCHAR(255),
    general_review    TEXT,
    inline_comments   JSON,
    memory_suggestion JSON,
    status            VARCHAR(20) NOT NULL,  -- COMPLETED, FAILED
    review_duration   BIGINT,                -- ms
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (repository_id) REFERENCES repository(repository_id),
    INDEX idx_repo_pr (repository_id, pr_number)
);
```

## REST API 상세

### 1. 전체 리뷰 조회

```
GET /api/reviews?page=0&size=20&sort=createdAt,desc
```

**응답 DTO: `PagedReviewResponse`**
```json
{
  "content": [
    {
      "reviewId": 1,
      "repositoryId": 12345,
      "repositoryName": "owner/repo",
      "prNumber": 42,
      "prTitle": "feat: Add user authentication",
      "featureName": "AUTH",
      "status": "COMPLETED",
      "inlineCommentCount": 5,
      "reviewDurationMs": 25000,
      "createdAt": "2025-01-15T10:30:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8
}
```

### 2. Repository별 리뷰 조회

```
GET /api/reviews/{repositoryId}?page=0&size=20
```

**응답**: `PagedReviewResponse`와 동일 구조 (해당 Repository 필터링)

### 3. PR별 리뷰 조회

```
GET /api/reviews/{repositoryId}/pr/{prNumber}
```

**응답 DTO: `PrReviewDetailResponse`**
```json
{
  "repositoryId": 12345,
  "prNumber": 42,
  "prTitle": "feat: Add user authentication",
  "reviews": [
    {
      "reviewId": 1,
      "featureName": "AUTH",
      "generalReview": "## AUTH 기능 리뷰\n인증 로직이 잘 구현...",
      "inlineComments": [
        {
          "path": "src/main/java/AuthService.java",
          "line": 45,
          "body": "[Major] 비밀번호 해싱이 누락되었습니다."
        }
      ],
      "memorySuggestion": {
        "summary": "JWT 기반 인증 구현",
        "keyPoints": ["BCrypt 해싱 필요"],
        "relatedFiles": ["AuthService.java"]
      },
      "status": "COMPLETED",
      "reviewDurationMs": 25000,
      "createdAt": "2025-01-15T10:30:00Z"
    }
  ],
  "totalReviewCount": 1
}
```

### 4. Repository 통계 조회

```
GET /api/stats/{repositoryId}
```

**응답 DTO: `RepositoryStatsResponse`**
```json
{
  "repositoryId": 12345,
  "repositoryName": "owner/repo",
  "totalReviews": 150,
  "completedReviews": 145,
  "failedReviews": 5,
  "averageInlineComments": 3.2,
  "averageReviewDurationMs": 28000,
  "reviewsByFeature": {
    "AUTH": 30,
    "PAYMENT": 45,
    "USER": 75
  },
  "recentActivity": {
    "last7Days": 12,
    "last30Days": 45
  },
  "periodStart": "2025-01-01T00:00:00Z",
  "periodEnd": "2025-01-15T23:59:59Z"
}
```

### 공통 에러 응답

```json
{
  "error": "NOT_FOUND",
  "message": "Repository not found: 99999",
  "timestamp": "2025-01-15T10:30:00Z",
  "path": "/api/reviews/99999"
}
```

| HTTP 상태 | 코드 | 설명 |
|-----------|------|------|
| `200 OK` | - | 정상 응답 |
| `400 Bad Request` | `INVALID_PARAMETER` | 잘못된 쿼리 파라미터 |
| `404 Not Found` | `NOT_FOUND` | Repository/PR 없음 |
| `500 Internal Server Error` | `INTERNAL_ERROR` | 서버 내부 오류 |

### Java DTO 클래스

```java
@Getter @Builder
public class ReviewSummaryDto {
    private Long reviewId;
    private Long repositoryId;
    private String repositoryName;
    private int prNumber;
    private String prTitle;
    private String featureName;
    private String status;
    private int inlineCommentCount;
    private Long reviewDurationMs;
    private LocalDateTime createdAt;
}

@Getter @Builder
public class PrReviewDetailResponse {
    private Long repositoryId;
    private int prNumber;
    private String prTitle;
    private List<ReviewDetailDto> reviews;
    private int totalReviewCount;
}

@Getter @Builder
public class ReviewDetailDto {
    private Long reviewId;
    private String featureName;
    private String generalReview;
    private List<InlineComment> inlineComments;
    private MemorySuggestion memorySuggestion;
    private String status;
    private Long reviewDurationMs;
    private LocalDateTime createdAt;
}

@Getter @Builder
public class RepositoryStatsResponse {
    private Long repositoryId;
    private String repositoryName;
    private int totalReviews;
    private int completedReviews;
    private int failedReviews;
    private double averageInlineComments;
    private long averageReviewDurationMs;
    private Map<String, Integer> reviewsByFeature;
    private Map<String, Integer> recentActivity;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
}
```

## 수정 대상 파일
- **신규**: `ReviewHistory.java` - JPA Entity
- **신규**: `ReviewHistoryRepository.java` - JPA Repository
- **신규**: `ReviewHistoryService.java` - 비즈니스 로직
- **신규**: `ReviewHistoryController.java` - REST API
- **수정**: `WebhookController.java` / `PrReviewService.java` - 리뷰 결과 저장

## 의존성
- **F5: async-processing** 권장 (비동기 결과 저장)

## 완료 조건
- [ ] review_history 테이블 생성
- [ ] 리뷰 결과 DB 저장
- [ ] 조회 API 3개 구현
- [ ] 통계 API 1개 구현
- [ ] 단위 테스트 5개 이상
