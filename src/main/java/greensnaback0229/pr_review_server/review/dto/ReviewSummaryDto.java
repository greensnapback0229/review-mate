package greensnaback0229.pr_review_server.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewSummaryDto {
    private Long reviewId;
    private Long repositoryId;
    private int prNumber;
    private String prTitle;
    private String featureName;
    private String status;
    private int inlineCommentCount;
    private Long reviewDurationMs;
    private LocalDateTime createdAt;
}
