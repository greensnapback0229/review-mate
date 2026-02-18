package greensnaback0229.pr_review_server.usage;

import greensnaback0229.pr_review_server.tenant.TenantContext;
import greensnaback0229.pr_review_server.usage.dto.MonthlyUsage;
import greensnaback0229.pr_review_server.usage.dto.UsageSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsageController 테스트")
class UsageControllerTest {

    @Mock
    private UsageService usageService;

    @InjectMocks
    private UsageController usageController;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentUserId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getCurrentUsage_정상_월간사용량반환")
    void getCurrentUsage_정상_월간사용량반환() {
        // given
        UsageSummary summary = UsageSummary.builder()
                .userId(1L)
                .currentMonth("2025-02")
                .reviewCount(18)
                .totalInputTokens(90000)
                .totalOutputTokens(36000)
                .estimatedCost(new BigDecimal("0.810000"))
                .build();

        when(usageService.getCurrentMonthUsage(1L)).thenReturn(summary);

        // when
        ResponseEntity<UsageSummary> response = usageController.getCurrentUsage();

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getReviewCount()).isEqualTo(18);
        assertThat(response.getBody().getTotalInputTokens()).isEqualTo(90000);
        assertThat(response.getBody().getEstimatedCost()).isEqualByComparingTo(new BigDecimal("0.810000"));

        verify(usageService).getCurrentMonthUsage(1L);
    }

    @Test
    @DisplayName("getCurrentUsage_데이터없음_0반환")
    void getCurrentUsage_데이터없음_0반환() {
        // given
        UsageSummary emptySummary = UsageSummary.builder()
                .userId(1L)
                .currentMonth("2025-02")
                .reviewCount(0)
                .totalInputTokens(0)
                .totalOutputTokens(0)
                .estimatedCost(BigDecimal.ZERO)
                .build();

        when(usageService.getCurrentMonthUsage(1L)).thenReturn(emptySummary);

        // when
        ResponseEntity<UsageSummary> response = usageController.getCurrentUsage();

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getReviewCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("getCurrentUsage_TenantContext미설정_예외발생")
    void getCurrentUsage_TenantContext미설정_예외발생() {
        // given
        TenantContext.clear();

        // when & then
        assertThatThrownBy(() -> usageController.getCurrentUsage())
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("getUsageHistory_정상_월별이력반환")
    void getUsageHistory_정상_월별이력반환() {
        // given
        List<MonthlyUsage> history = List.of(
                MonthlyUsage.builder().month("2025-02").reviewCount(18).totalCost(new BigDecimal("0.810000")).build(),
                MonthlyUsage.builder().month("2025-01").reviewCount(25).totalCost(new BigDecimal("1.125000")).build()
        );

        when(usageService.getUsageHistory(1L, 6)).thenReturn(history);

        // when
        ResponseEntity<?> response = usageController.getUsageHistory(6);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(usageService).getUsageHistory(1L, 6);
    }

    @Test
    @DisplayName("getUsageHistory_기본값_6개월")
    void getUsageHistory_기본값_6개월() {
        // given
        when(usageService.getUsageHistory(1L, 6)).thenReturn(List.of());

        // when
        ResponseEntity<?> response = usageController.getUsageHistory(6);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(usageService).getUsageHistory(1L, 6);
    }

    @Test
    @DisplayName("getUsageHistory_빈이력_빈배열반환")
    void getUsageHistory_빈이력_빈배열반환() {
        // given
        when(usageService.getUsageHistory(1L, 3)).thenReturn(List.of());

        // when
        ResponseEntity<?> response = usageController.getUsageHistory(3);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
