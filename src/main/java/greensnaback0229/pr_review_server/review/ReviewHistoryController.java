package greensnaback0229.pr_review_server.review;

import greensnaback0229.pr_review_server.review.dto.PrReviewDetailResponse;
import greensnaback0229.pr_review_server.review.dto.RepositoryStatsResponse;
import greensnaback0229.pr_review_server.review.dto.ReviewSummaryDto;
import greensnaback0229.pr_review_server.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewHistoryController {

    private final ReviewHistoryService reviewHistoryService;

    @GetMapping
    public ResponseEntity<Page<ReviewSummaryDto>> getReviews(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long userId = TenantContext.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.ok(Page.empty());
        }
        return ResponseEntity.ok(reviewHistoryService.getReviewHistory(userId, pageable));
    }

    @GetMapping("/{repositoryId}")
    public ResponseEntity<Page<ReviewSummaryDto>> getReviewsByRepository(
            @PathVariable Long repositoryId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long userId = TenantContext.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.ok(Page.empty());
        }
        return ResponseEntity.ok(reviewHistoryService.getReviewsByRepository(userId, repositoryId, pageable));
    }

    @GetMapping("/{repositoryId}/pr/{prNumber}")
    public ResponseEntity<PrReviewDetailResponse> getReviewsByPr(
            @PathVariable Long repositoryId,
            @PathVariable int prNumber) {
        Long userId = TenantContext.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.ok(PrReviewDetailResponse.builder()
                    .repositoryId(repositoryId).prNumber(prNumber)
                    .reviews(java.util.List.of()).totalReviewCount(0)
                    .build());
        }
        return ResponseEntity.ok(reviewHistoryService.getReviewsByPr(userId, repositoryId, prNumber));
    }

    @GetMapping("/{repositoryId}/stats")
    public ResponseEntity<RepositoryStatsResponse> getRepositoryStats(
            @PathVariable Long repositoryId) {
        Long userId = TenantContext.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.ok(RepositoryStatsResponse.builder()
                    .repositoryId(repositoryId).build());
        }
        return ResponseEntity.ok(reviewHistoryService.getRepositoryStats(userId, repositoryId));
    }
}
