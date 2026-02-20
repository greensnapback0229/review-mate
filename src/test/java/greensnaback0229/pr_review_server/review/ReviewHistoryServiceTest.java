package greensnaback0229.pr_review_server.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import greensnaback0229.pr_review_server.aggregator.dto.AggregatedReview;
import greensnaback0229.pr_review_server.llm.dto.InlineComment;
import greensnaback0229.pr_review_server.llm.dto.MemorySuggestion;
import greensnaback0229.pr_review_server.review.dto.PrReviewDetailResponse;
import greensnaback0229.pr_review_server.review.dto.RepositoryStatsResponse;
import greensnaback0229.pr_review_server.review.dto.ReviewSummaryDto;
import greensnaback0229.pr_review_server.review.entity.ReviewHistory;
import greensnaback0229.pr_review_server.review.entity.ReviewStatus;
import greensnaback0229.pr_review_server.review.repository.ReviewHistoryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewHistoryService 테스트")
class ReviewHistoryServiceTest {

    @Mock
    private ReviewHistoryJpaRepository reviewHistoryJpaRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ReviewHistoryService reviewHistoryService;

    private AggregatedReview sampleReview;

    @BeforeEach
    void setUp() {
        sampleReview = AggregatedReview.builder()
                .feature("AUTH")
                .review("## AUTH 기능 리뷰\n인증 로직이 잘 구현되었습니다.")
                .inlineComments(List.of(
                        InlineComment.builder()
                                .path("src/main/java/AuthService.java")
                                .line(45)
                                .body("[Major] 비밀번호 해싱이 누락되었습니다.")
                                .build()
                ))
                .reviewedAt(LocalDateTime.now())
                .updatedMemory(null)
                .build();
    }

    @Test
    @DisplayName("saveReviewHistory_정상저장_COMPLETED")
    void saveReviewHistory_정상저장_COMPLETED() {
        // given
        when(reviewHistoryJpaRepository.save(any(ReviewHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        reviewHistoryService.saveReviewHistory(
                1L, 12345L, 42, "feat: Add auth", "AUTH",
                sampleReview, 25000L, ReviewStatus.COMPLETED);

        // then
        ArgumentCaptor<ReviewHistory> captor = ArgumentCaptor.forClass(ReviewHistory.class);
        verify(reviewHistoryJpaRepository).save(captor.capture());

        ReviewHistory saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getRepositoryId()).isEqualTo(12345L);
        assertThat(saved.getPrNumber()).isEqualTo(42);
        assertThat(saved.getPrTitle()).isEqualTo("feat: Add auth");
        assertThat(saved.getFeatureName()).isEqualTo("AUTH");
        assertThat(saved.getStatus()).isEqualTo(ReviewStatus.COMPLETED);
        assertThat(saved.getInlineCommentCount()).isEqualTo(1);
        assertThat(saved.getReviewDurationMs()).isEqualTo(25000L);
        assertThat(saved.getGeneralReview()).contains("AUTH 기능 리뷰");
    }

    @Test
    @DisplayName("saveReviewHistory_실패저장_FAILED_aggregatedReview_null")
    void saveReviewHistory_실패저장_FAILED() {
        // given
        when(reviewHistoryJpaRepository.save(any(ReviewHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        reviewHistoryService.saveReviewHistory(
                1L, 12345L, 42, "feat: Add auth", "AUTH",
                null, 0L, ReviewStatus.FAILED);

        // then
        ArgumentCaptor<ReviewHistory> captor = ArgumentCaptor.forClass(ReviewHistory.class);
        verify(reviewHistoryJpaRepository).save(captor.capture());

        ReviewHistory saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ReviewStatus.FAILED);
        assertThat(saved.getGeneralReview()).isNull();
        assertThat(saved.getInlineComments()).isNull();
        assertThat(saved.getInlineCommentCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("saveReviewHistory_inlineComments_JSON직렬화")
    void saveReviewHistory_inlineComments_JSON직렬화() {
        // given
        when(reviewHistoryJpaRepository.save(any(ReviewHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        reviewHistoryService.saveReviewHistory(
                1L, 12345L, 42, "feat: Add auth", "AUTH",
                sampleReview, 25000L, ReviewStatus.COMPLETED);

        // then
        ArgumentCaptor<ReviewHistory> captor = ArgumentCaptor.forClass(ReviewHistory.class);
        verify(reviewHistoryJpaRepository).save(captor.capture());

        String json = captor.getValue().getInlineComments();
        assertThat(json).isNotNull();
        assertThat(json).contains("AuthService.java");
        assertThat(json).contains("비밀번호 해싱");
    }

    @Test
    @DisplayName("getReviewHistory_페이징_정상조회")
    void getReviewHistory_페이징_정상조회() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        ReviewHistory history = ReviewHistory.builder()
                .id(1L).userId(1L).repositoryId(12345L).prNumber(42)
                .prTitle("feat: Add auth").featureName("AUTH")
                .status(ReviewStatus.COMPLETED).inlineCommentCount(3)
                .reviewDurationMs(25000L).createdAt(LocalDateTime.now())
                .build();

        when(reviewHistoryJpaRepository.findByUserIdOrderByCreatedAtDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(history), pageable, 1));

        // when
        Page<ReviewSummaryDto> result = reviewHistoryService.getReviewHistory(1L, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getReviewId()).isEqualTo(1L);
        assertThat(result.getContent().get(0).getFeatureName()).isEqualTo("AUTH");
        assertThat(result.getContent().get(0).getInlineCommentCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("getReviewHistory_사용자격리_다른userId데이터미포함")
    void getReviewHistory_사용자격리() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        when(reviewHistoryJpaRepository.findByUserIdOrderByCreatedAtDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        // when
        Page<ReviewSummaryDto> result = reviewHistoryService.getReviewHistory(1L, pageable);

        // then
        assertThat(result.getContent()).isEmpty();
        verify(reviewHistoryJpaRepository).findByUserIdOrderByCreatedAtDesc(1L, pageable);
    }

    @Test
    @DisplayName("getReviewsByRepository_정상조회")
    void getReviewsByRepository_정상조회() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        ReviewHistory history = ReviewHistory.builder()
                .id(1L).userId(1L).repositoryId(12345L).prNumber(42)
                .prTitle("feat: Add auth").featureName("AUTH")
                .status(ReviewStatus.COMPLETED).inlineCommentCount(2)
                .createdAt(LocalDateTime.now())
                .build();

        when(reviewHistoryJpaRepository.findByUserIdAndRepositoryIdOrderByCreatedAtDesc(1L, 12345L, pageable))
                .thenReturn(new PageImpl<>(List.of(history), pageable, 1));

        // when
        Page<ReviewSummaryDto> result = reviewHistoryService.getReviewsByRepository(1L, 12345L, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getRepositoryId()).isEqualTo(12345L);
    }

    @Test
    @DisplayName("getReviewsByPr_상세조회_JSON역직렬화")
    void getReviewsByPr_상세조회_JSON역직렬화() {
        // given
        String inlineJson = "[{\"path\":\"AuthService.java\",\"line\":45,\"body\":\"Fix this\"}]";
        ReviewHistory history = ReviewHistory.builder()
                .id(1L).userId(1L).repositoryId(12345L).prNumber(42)
                .prTitle("feat: Add auth").featureName("AUTH")
                .generalReview("Good review").inlineComments(inlineJson)
                .status(ReviewStatus.COMPLETED).inlineCommentCount(1)
                .reviewDurationMs(25000L).createdAt(LocalDateTime.now())
                .build();

        when(reviewHistoryJpaRepository.findByUserIdAndRepositoryIdAndPrNumberOrderByCreatedAtDesc(1L, 12345L, 42))
                .thenReturn(List.of(history));

        // when
        PrReviewDetailResponse result = reviewHistoryService.getReviewsByPr(1L, 12345L, 42);

        // then
        assertThat(result.getTotalReviewCount()).isEqualTo(1);
        assertThat(result.getReviews().get(0).getInlineComments()).hasSize(1);
        assertThat(result.getReviews().get(0).getInlineComments().get(0).getPath()).isEqualTo("AuthService.java");
    }

    @Test
    @DisplayName("getReviewsByPr_JSON역직렬화실패_빈리스트반환")
    void getReviewsByPr_JSON역직렬화실패_빈리스트반환() {
        // given
        ReviewHistory history = ReviewHistory.builder()
                .id(1L).userId(1L).repositoryId(12345L).prNumber(42)
                .prTitle("feat").featureName("AUTH")
                .generalReview("review").inlineComments("invalid json{{{")
                .status(ReviewStatus.COMPLETED).inlineCommentCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        when(reviewHistoryJpaRepository.findByUserIdAndRepositoryIdAndPrNumberOrderByCreatedAtDesc(1L, 12345L, 42))
                .thenReturn(List.of(history));

        // when
        PrReviewDetailResponse result = reviewHistoryService.getReviewsByPr(1L, 12345L, 42);

        // then
        assertThat(result.getReviews().get(0).getInlineComments()).isEmpty();
    }

    @Test
    @DisplayName("getRepositoryStats_통계계산_정상")
    void getRepositoryStats_통계계산_정상() {
        // given
        Long userId = 1L;
        Long repoId = 12345L;

        when(reviewHistoryJpaRepository.countByUserIdAndRepositoryId(userId, repoId)).thenReturn(10L);
        when(reviewHistoryJpaRepository.countByUserIdAndRepositoryIdAndStatus(userId, repoId, ReviewStatus.COMPLETED)).thenReturn(8L);
        when(reviewHistoryJpaRepository.countByUserIdAndRepositoryIdAndStatus(userId, repoId, ReviewStatus.FAILED)).thenReturn(2L);
        when(reviewHistoryJpaRepository.avgInlineCommentCountByUserIdAndRepositoryId(userId, repoId)).thenReturn(3.5);
        when(reviewHistoryJpaRepository.avgReviewDurationMsByUserIdAndRepositoryId(userId, repoId)).thenReturn(28000L);
        when(reviewHistoryJpaRepository.countByUserIdAndRepositoryIdAndCreatedAtAfter(eq(userId), eq(repoId), any(LocalDateTime.class)))
                .thenReturn(5L).thenReturn(8L);

        ReviewHistory h1 = ReviewHistory.builder().featureName("AUTH").build();
        ReviewHistory h2 = ReviewHistory.builder().featureName("AUTH").build();
        ReviewHistory h3 = ReviewHistory.builder().featureName("PAYMENT").build();
        when(reviewHistoryJpaRepository.findByUserIdAndRepositoryId(userId, repoId))
                .thenReturn(List.of(h1, h2, h3));

        // when
        RepositoryStatsResponse stats = reviewHistoryService.getRepositoryStats(userId, repoId);

        // then
        assertThat(stats.getTotalReviews()).isEqualTo(10);
        assertThat(stats.getCompletedReviews()).isEqualTo(8);
        assertThat(stats.getFailedReviews()).isEqualTo(2);
        assertThat(stats.getAverageInlineComments()).isEqualTo(3.5);
        assertThat(stats.getAverageReviewDurationMs()).isEqualTo(28000L);
        assertThat(stats.getReviewsByFeature()).containsEntry("AUTH", 2L);
        assertThat(stats.getReviewsByFeature()).containsEntry("PAYMENT", 1L);
    }

    @Test
    @DisplayName("getRepositoryStats_리뷰없음_모든값0")
    void getRepositoryStats_리뷰없음_모든값0() {
        // given
        Long userId = 1L;
        Long repoId = 99999L;

        when(reviewHistoryJpaRepository.countByUserIdAndRepositoryId(userId, repoId)).thenReturn(0L);
        when(reviewHistoryJpaRepository.countByUserIdAndRepositoryIdAndStatus(userId, repoId, ReviewStatus.COMPLETED)).thenReturn(0L);
        when(reviewHistoryJpaRepository.countByUserIdAndRepositoryIdAndStatus(userId, repoId, ReviewStatus.FAILED)).thenReturn(0L);
        when(reviewHistoryJpaRepository.avgInlineCommentCountByUserIdAndRepositoryId(userId, repoId)).thenReturn(0.0);
        when(reviewHistoryJpaRepository.avgReviewDurationMsByUserIdAndRepositoryId(userId, repoId)).thenReturn(0L);
        when(reviewHistoryJpaRepository.countByUserIdAndRepositoryIdAndCreatedAtAfter(eq(userId), eq(repoId), any(LocalDateTime.class)))
                .thenReturn(0L);
        when(reviewHistoryJpaRepository.findByUserIdAndRepositoryId(userId, repoId))
                .thenReturn(List.of());

        // when
        RepositoryStatsResponse stats = reviewHistoryService.getRepositoryStats(userId, repoId);

        // then
        assertThat(stats.getTotalReviews()).isEqualTo(0);
        assertThat(stats.getCompletedReviews()).isEqualTo(0);
        assertThat(stats.getAverageInlineComments()).isEqualTo(0.0);
        assertThat(stats.getReviewsByFeature()).isEmpty();
    }
}
