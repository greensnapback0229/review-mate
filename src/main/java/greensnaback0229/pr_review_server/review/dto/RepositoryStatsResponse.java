package greensnaback0229.pr_review_server.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryStatsResponse {
    private Long repositoryId;
    private long totalReviews;
    private long completedReviews;
    private long failedReviews;
    private double averageInlineComments;
    private long averageReviewDurationMs;
    private Map<String, Long> reviewsByFeature;
    private long last7DaysReviews;
    private long last30DaysReviews;
}
