package greensnaback0229.pr_review_server.review.dto;

import greensnaback0229.pr_review_server.llm.dto.InlineComment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewDetailDto {
    private Long reviewId;
    private String featureName;
    private String generalReview;
    private List<InlineComment> inlineComments;
    private String memorySuggestion;
    private String status;
    private Long reviewDurationMs;
    private LocalDateTime createdAt;
}
