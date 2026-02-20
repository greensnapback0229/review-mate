package greensnaback0229.pr_review_server.usage;

import greensnaback0229.pr_review_server.tenant.TenantContext;
import greensnaback0229.pr_review_server.usage.dto.MonthlyUsage;
import greensnaback0229.pr_review_server.usage.dto.UsageHistoryResponse;
import greensnaback0229.pr_review_server.usage.dto.UsageSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 사용량 조회 API
 * TenantContext 기반으로 현재 사용자의 사용량만 반환
 */
@RestController
@RequestMapping("/api/usage")
@RequiredArgsConstructor
public class UsageController {

    private final UsageService usageService;

    /**
     * 현재 월 사용량 조회
     * GET /api/usage
     */
    @GetMapping
    public ResponseEntity<UsageSummary> getCurrentUsage() {
        Long userId = TenantContext.getCurrentUserIdOrThrow();
        UsageSummary summary = usageService.getCurrentMonthUsage(userId);
        return ResponseEntity.ok(summary);
    }

    /**
     * 월별 사용량 이력 조회
     * GET /api/usage/history?months=6
     */
    @GetMapping("/history")
    public ResponseEntity<UsageHistoryResponse> getUsageHistory(
            @RequestParam(defaultValue = "6") int months) {

        Long userId = TenantContext.getCurrentUserIdOrThrow();
        List<MonthlyUsage> history = usageService.getUsageHistory(userId, months);

        UsageHistoryResponse response = UsageHistoryResponse.builder()
                .userId(userId)
                .months(history)
                .build();

        return ResponseEntity.ok(response);
    }
}
