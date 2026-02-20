package greensnaback0229.pr_review_server.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrReviewDetailResponse {
    private Long repositoryId;
    private int prNumber;
    private String prTitle;
    private List<ReviewDetailDto> reviews;
    private int totalReviewCount;
}
