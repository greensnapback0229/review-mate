package greensnaback0229.pr_review_server.review;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import greensnaback0229.pr_review_server.aggregator.dto.AggregatedReview;
import greensnaback0229.pr_review_server.llm.dto.InlineComment;
import greensnaback0229.pr_review_server.review.dto.*;
import greensnaback0229.pr_review_server.review.entity.ReviewHistory;
import greensnaback0229.pr_review_server.review.entity.ReviewStatus;
import greensnaback0229.pr_review_server.review.repository.ReviewHistoryJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewHistoryService {

    private final ReviewHistoryJpaRepository reviewHistoryJpaRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveReviewHistory(Long userId, Long repositoryId, int prNumber, String prTitle,
                                  String featureName, AggregatedReview aggregatedReview,
                                  long durationMs, ReviewStatus status) {
        ReviewHistory.ReviewHistoryBuilder builder = ReviewHistory.builder()
                .userId(userId)
                .repositoryId(repositoryId)
                .prNumber(prNumber)
                .prTitle(prTitle)
                .featureName(featureName)
                .status(status)
                .reviewDurationMs(durationMs)
                .createdAt(LocalDateTime.now());

        if (aggregatedReview != null) {
            builder.generalReview(aggregatedReview.getReview());
            builder.inlineCommentCount(
                    aggregatedReview.getInlineComments() != null
                            ? aggregatedReview.getInlineComments().size() : 0);
            builder.inlineComments(serializeJson(aggregatedReview.getInlineComments()));
            builder.memorySuggestion(serializeJson(aggregatedReview.getUpdatedMemory()));
        } else {
            builder.inlineCommentCount(0);
        }

        reviewHistoryJpaRepository.save(builder.build());
    }

    public Page<ReviewSummaryDto> getReviewHistory(Long userId, Pageable pageable) {
        return reviewHistoryJpaRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toSummaryDto);
    }

    public Page<ReviewSummaryDto> getReviewsByRepository(Long userId, Long repositoryId, Pageable pageable) {
        return reviewHistoryJpaRepository.findByUserIdAndRepositoryIdOrderByCreatedAtDesc(userId, repositoryId, pageable)
                .map(this::toSummaryDto);
    }

    public PrReviewDetailResponse getReviewsByPr(Long userId, Long repositoryId, int prNumber) {
        List<ReviewHistory> histories = reviewHistoryJpaRepository
                .findByUserIdAndRepositoryIdAndPrNumberOrderByCreatedAtDesc(userId, repositoryId, prNumber);

        String prTitle = histories.isEmpty() ? null : histories.get(0).getPrTitle();

        List<ReviewDetailDto> details = histories.stream()
                .map(this::toDetailDto)
                .collect(Collectors.toList());

        return PrReviewDetailResponse.builder()
                .repositoryId(repositoryId)
                .prNumber(prNumber)
                .prTitle(prTitle)
                .reviews(details)
                .totalReviewCount(details.size())
                .build();
    }

    public RepositoryStatsResponse getRepositoryStats(Long userId, Long repositoryId) {
        long total = reviewHistoryJpaRepository.countByUserIdAndRepositoryId(userId, repositoryId);
        long completed = reviewHistoryJpaRepository.countByUserIdAndRepositoryIdAndStatus(userId, repositoryId, ReviewStatus.COMPLETED);
        long failed = reviewHistoryJpaRepository.countByUserIdAndRepositoryIdAndStatus(userId, repositoryId, ReviewStatus.FAILED);
        double avgComments = reviewHistoryJpaRepository.avgInlineCommentCountByUserIdAndRepositoryId(userId, repositoryId);
        long avgDuration = reviewHistoryJpaRepository.avgReviewDurationMsByUserIdAndRepositoryId(userId, repositoryId);

        LocalDateTime now = LocalDateTime.now();
        long last7Days = reviewHistoryJpaRepository.countByUserIdAndRepositoryIdAndCreatedAtAfter(userId, repositoryId, now.minusDays(7));
        long last30Days = reviewHistoryJpaRepository.countByUserIdAndRepositoryIdAndCreatedAtAfter(userId, repositoryId, now.minusDays(30));

        List<ReviewHistory> allReviews = reviewHistoryJpaRepository.findByUserIdAndRepositoryId(userId, repositoryId);
        Map<String, Long> reviewsByFeature = allReviews.stream()
                .filter(r -> r.getFeatureName() != null)
                .collect(Collectors.groupingBy(ReviewHistory::getFeatureName, Collectors.counting()));

        return RepositoryStatsResponse.builder()
                .repositoryId(repositoryId)
                .totalReviews(total)
                .completedReviews(completed)
                .failedReviews(failed)
                .averageInlineComments(avgComments)
                .averageReviewDurationMs(avgDuration)
                .reviewsByFeature(reviewsByFeature)
                .last7DaysReviews(last7Days)
                .last30DaysReviews(last30Days)
                .build();
    }

    private ReviewSummaryDto toSummaryDto(ReviewHistory history) {
        return ReviewSummaryDto.builder()
                .reviewId(history.getId())
                .repositoryId(history.getRepositoryId())
                .prNumber(history.getPrNumber())
                .prTitle(history.getPrTitle())
                .featureName(history.getFeatureName())
                .status(history.getStatus().name())
                .inlineCommentCount(history.getInlineCommentCount())
                .reviewDurationMs(history.getReviewDurationMs())
                .createdAt(history.getCreatedAt())
                .build();
    }

    private ReviewDetailDto toDetailDto(ReviewHistory history) {
        List<InlineComment> comments = deserializeInlineComments(history.getInlineComments());

        return ReviewDetailDto.builder()
                .reviewId(history.getId())
                .featureName(history.getFeatureName())
                .generalReview(history.getGeneralReview())
                .inlineComments(comments)
                .memorySuggestion(history.getMemorySuggestion())
                .status(history.getStatus().name())
                .reviewDurationMs(history.getReviewDurationMs())
                .createdAt(history.getCreatedAt())
                .build();
    }

    private String serializeJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize to JSON: {}", e.getMessage());
            return null;
        }
    }

    private List<InlineComment> deserializeInlineComments(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize inline comments: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
