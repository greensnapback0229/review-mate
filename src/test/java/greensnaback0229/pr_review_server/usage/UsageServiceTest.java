package greensnaback0229.pr_review_server.usage;

import greensnaback0229.pr_review_server.usage.dto.MonthlyUsage;
import greensnaback0229.pr_review_server.usage.dto.UsageSummary;
import greensnaback0229.pr_review_server.usage.entity.ReviewType;
import greensnaback0229.pr_review_server.usage.entity.UsageLog;
import greensnaback0229.pr_review_server.usage.repository.UsageLogJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsageService 테스트")
class UsageServiceTest {

    @Mock
    private UsageLogJpaRepository usageLogJpaRepository;

    @InjectMocks
    private UsageService usageService;

    @Test
    @DisplayName("calculateCost_정확한비용계산")
    void calculateCost_정확한비용계산() {
        // Claude Sonnet 4: Input $3/1M, Output $15/1M
        // 5000 input tokens × $3/1M = $0.015000
        // 2000 output tokens × $15/1M = $0.030000
        // Total = $0.045000
        BigDecimal cost = usageService.calculateCost(5000, 2000);

        assertThat(cost).isEqualByComparingTo(new BigDecimal("0.045000"));
    }

    @Test
    @DisplayName("calculateCost_0토큰_0비용")
    void calculateCost_0토큰_0비용() {
        BigDecimal cost = usageService.calculateCost(0, 0);

        assertThat(cost).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("calculateCost_대량토큰_정확한계산")
    void calculateCost_대량토큰_정확한계산() {
        // 1,000,000 input × $3/1M = $3.000000
        // 500,000 output × $15/1M = $7.500000
        // Total = $10.500000
        BigDecimal cost = usageService.calculateCost(1_000_000, 500_000);

        assertThat(cost).isEqualByComparingTo(new BigDecimal("10.500000"));
    }

    @Test
    @DisplayName("recordUsage_정상기록_DB저장")
    void recordUsage_정상기록_DB저장() {
        // given
        when(usageLogJpaRepository.save(any(UsageLog.class))).thenAnswer(i -> i.getArgument(0));

        // when
        usageService.recordUsage(1L, 100L, 42, "auth-feature", 5000, 2000, ReviewType.PR_REVIEW);

        // then
        ArgumentCaptor<UsageLog> captor = ArgumentCaptor.forClass(UsageLog.class);
        verify(usageLogJpaRepository).save(captor.capture());

        UsageLog saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getRepositoryId()).isEqualTo(100L);
        assertThat(saved.getPrNumber()).isEqualTo(42);
        assertThat(saved.getFeatureName()).isEqualTo("auth-feature");
        assertThat(saved.getInputTokens()).isEqualTo(5000);
        assertThat(saved.getOutputTokens()).isEqualTo(2000);
        assertThat(saved.getReviewType()).isEqualTo(ReviewType.PR_REVIEW);
        assertThat(saved.getEstimatedCost()).isEqualByComparingTo(new BigDecimal("0.045000"));
    }

    @Test
    @DisplayName("recordUsage_DB오류_예외미전파_리뷰계속")
    void recordUsage_DB오류_예외미전파() {
        // given
        when(usageLogJpaRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        // when - 예외가 전파되지 않아야 함
        usageService.recordUsage(1L, 100L, 42, "feature", 5000, 2000, ReviewType.PR_REVIEW);

        // then - 예외 없이 정상 종료
        verify(usageLogJpaRepository).save(any());
    }

    @Test
    @DisplayName("recordUsage_COMMENT_REPLY타입_정상기록")
    void recordUsage_COMMENT_REPLY타입_정상기록() {
        // given
        when(usageLogJpaRepository.save(any(UsageLog.class))).thenAnswer(i -> i.getArgument(0));

        // when
        usageService.recordUsage(1L, 100L, 42, null, 3000, 1000, ReviewType.COMMENT_REPLY);

        // then
        ArgumentCaptor<UsageLog> captor = ArgumentCaptor.forClass(UsageLog.class);
        verify(usageLogJpaRepository).save(captor.capture());

        UsageLog saved = captor.getValue();
        assertThat(saved.getReviewType()).isEqualTo(ReviewType.COMMENT_REPLY);
        assertThat(saved.getFeatureName()).isNull();
    }

    @Test
    @DisplayName("getCurrentMonthUsage_월간집계_정확한합계")
    void getCurrentMonthUsage_월간집계_정확한합계() {
        // given
        LocalDateTime now = LocalDateTime.now();
        List<UsageLog> logs = List.of(
            UsageLog.builder()
                .userId(1L).repositoryId(100L).prNumber(1)
                .inputTokens(5000).outputTokens(2000)
                .estimatedCost(new BigDecimal("0.045000"))
                .reviewType(ReviewType.PR_REVIEW).createdAt(now)
                .build(),
            UsageLog.builder()
                .userId(1L).repositoryId(100L).prNumber(2)
                .inputTokens(3000).outputTokens(1000)
                .estimatedCost(new BigDecimal("0.024000"))
                .reviewType(ReviewType.COMMENT_REPLY).createdAt(now)
                .build()
        );

        when(usageLogJpaRepository.findByUserIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class)))
                .thenReturn(logs);

        // when
        UsageSummary summary = usageService.getCurrentMonthUsage(1L);

        // then
        assertThat(summary.getUserId()).isEqualTo(1L);
        assertThat(summary.getReviewCount()).isEqualTo(2);
        assertThat(summary.getTotalInputTokens()).isEqualTo(8000);
        assertThat(summary.getTotalOutputTokens()).isEqualTo(3000);
        assertThat(summary.getEstimatedCost()).isEqualByComparingTo(new BigDecimal("0.069000"));
    }

    @Test
    @DisplayName("getCurrentMonthUsage_데이터없음_0반환")
    void getCurrentMonthUsage_데이터없음_0반환() {
        // given
        when(usageLogJpaRepository.findByUserIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class)))
                .thenReturn(List.of());

        // when
        UsageSummary summary = usageService.getCurrentMonthUsage(1L);

        // then
        assertThat(summary.getReviewCount()).isEqualTo(0);
        assertThat(summary.getTotalInputTokens()).isEqualTo(0);
        assertThat(summary.getTotalOutputTokens()).isEqualTo(0);
        assertThat(summary.getEstimatedCost()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getUsageHistory_월별그룹화_역순정렬")
    void getUsageHistory_월별그룹화_역순정렬() {
        // given
        List<UsageLog> logs = List.of(
            UsageLog.builder()
                .userId(1L).repositoryId(100L).prNumber(1)
                .inputTokens(5000).outputTokens(2000)
                .estimatedCost(new BigDecimal("0.045000"))
                .reviewType(ReviewType.PR_REVIEW)
                .createdAt(LocalDateTime.of(2025, 2, 15, 10, 0))
                .build(),
            UsageLog.builder()
                .userId(1L).repositoryId(100L).prNumber(2)
                .inputTokens(3000).outputTokens(1000)
                .estimatedCost(new BigDecimal("0.024000"))
                .reviewType(ReviewType.PR_REVIEW)
                .createdAt(LocalDateTime.of(2025, 2, 20, 10, 0))
                .build(),
            UsageLog.builder()
                .userId(1L).repositoryId(100L).prNumber(3)
                .inputTokens(4000).outputTokens(1500)
                .estimatedCost(new BigDecimal("0.034500"))
                .reviewType(ReviewType.PR_REVIEW)
                .createdAt(LocalDateTime.of(2025, 1, 10, 10, 0))
                .build()
        );

        when(usageLogJpaRepository.findByUserIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class)))
                .thenReturn(logs);

        // when
        List<MonthlyUsage> history = usageService.getUsageHistory(1L, 6);

        // then
        assertThat(history).hasSize(2);
        // 역순: 2025-02 먼저
        assertThat(history.get(0).getMonth()).isEqualTo("2025-02");
        assertThat(history.get(0).getReviewCount()).isEqualTo(2);
        assertThat(history.get(0).getTotalCost()).isEqualByComparingTo(new BigDecimal("0.069000"));

        assertThat(history.get(1).getMonth()).isEqualTo("2025-01");
        assertThat(history.get(1).getReviewCount()).isEqualTo(1);
        assertThat(history.get(1).getTotalCost()).isEqualByComparingTo(new BigDecimal("0.034500"));
    }

    @Test
    @DisplayName("getUsageHistory_데이터없음_빈리스트")
    void getUsageHistory_데이터없음_빈리스트() {
        // given
        when(usageLogJpaRepository.findByUserIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class)))
                .thenReturn(List.of());

        // when
        List<MonthlyUsage> history = usageService.getUsageHistory(1L, 6);

        // then
        assertThat(history).isEmpty();
    }
}
