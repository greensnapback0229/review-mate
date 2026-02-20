package greensnaback0229.pr_review_server.usage;

import greensnaback0229.pr_review_server.usage.dto.MonthlyUsage;
import greensnaback0229.pr_review_server.usage.dto.UsageSummary;
import greensnaback0229.pr_review_server.usage.entity.ReviewType;
import greensnaback0229.pr_review_server.usage.entity.UsageLog;
import greensnaback0229.pr_review_server.usage.repository.UsageLogJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 사용량 추적 서비스
 * LLM API 사용량 기록, 월간 집계, 비용 추정을 담당
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageService {

    private static final BigDecimal INPUT_PRICE_PER_MILLION = new BigDecimal("3.0");
    private static final BigDecimal OUTPUT_PRICE_PER_MILLION = new BigDecimal("15.0");
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final UsageLogJpaRepository usageLogJpaRepository;

    /**
     * 사용량 기록. 실패 시 예외를 전파하지 않음 (리뷰 우선).
     */
    @Transactional
    public void recordUsage(Long userId, Long repositoryId, Integer prNumber,
                            String featureName, int inputTokens, int outputTokens,
                            ReviewType reviewType) {
        try {
            BigDecimal cost = calculateCost(inputTokens, outputTokens);

            UsageLog usageLog = UsageLog.builder()
                    .userId(userId)
                    .repositoryId(repositoryId)
                    .prNumber(prNumber)
                    .featureName(featureName)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .estimatedCost(cost)
                    .reviewType(reviewType)
                    .build();

            usageLogJpaRepository.save(usageLog);

            log.info("Usage recorded: userId={}, repo={}, pr={}, type={}, tokens=({}/{}), cost=${}",
                    userId, repositoryId, prNumber, reviewType, inputTokens, outputTokens, cost);

        } catch (Exception e) {
            log.warn("Failed to record usage: {}", e.getMessage());
        }
    }

    /**
     * 현재 월 사용량 집계
     */
    public UsageSummary getCurrentMonthUsage(Long userId) {
        LocalDateTime monthStart = LocalDateTime.now()
                .withDayOfMonth(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        List<UsageLog> logs = usageLogJpaRepository.findByUserIdAndCreatedAtAfter(userId, monthStart);

        int reviewCount = logs.size();
        int totalInputTokens = logs.stream().mapToInt(UsageLog::getInputTokens).sum();
        int totalOutputTokens = logs.stream().mapToInt(UsageLog::getOutputTokens).sum();
        BigDecimal totalCost = logs.stream()
                .map(UsageLog::getEstimatedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return UsageSummary.builder()
                .userId(userId)
                .currentMonth(monthStart.format(MONTH_FORMATTER))
                .reviewCount(reviewCount)
                .totalInputTokens(totalInputTokens)
                .totalOutputTokens(totalOutputTokens)
                .estimatedCost(totalCost)
                .build();
    }

    /**
     * 최근 N개월 사용량 이력 (월별 그룹화, 역순 정렬)
     */
    public List<MonthlyUsage> getUsageHistory(Long userId, int months) {
        LocalDateTime startDate = LocalDateTime.now().minusMonths(months);
        List<UsageLog> logs = usageLogJpaRepository.findByUserIdAndCreatedAtAfter(userId, startDate);

        Map<String, List<UsageLog>> byMonth = logs.stream()
                .collect(Collectors.groupingBy(usageLog ->
                        usageLog.getCreatedAt().format(MONTH_FORMATTER)
                ));

        return byMonth.entrySet().stream()
                .map(entry -> {
                    List<UsageLog> monthLogs = entry.getValue();
                    return MonthlyUsage.builder()
                            .month(entry.getKey())
                            .reviewCount(monthLogs.size())
                            .totalCost(monthLogs.stream()
                                    .map(UsageLog::getEstimatedCost)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add))
                            .build();
                })
                .sorted(Comparator.comparing(MonthlyUsage::getMonth).reversed())
                .toList();
    }

    /**
     * Claude Sonnet 4 기준 비용 추정
     * Input: $3/1M tokens, Output: $15/1M tokens
     */
    public BigDecimal calculateCost(int inputTokens, int outputTokens) {
        BigDecimal inputCost = BigDecimal.valueOf(inputTokens)
                .multiply(INPUT_PRICE_PER_MILLION)
                .divide(ONE_MILLION, 6, RoundingMode.HALF_UP);

        BigDecimal outputCost = BigDecimal.valueOf(outputTokens)
                .multiply(OUTPUT_PRICE_PER_MILLION)
                .divide(ONE_MILLION, 6, RoundingMode.HALF_UP);

        return inputCost.add(outputCost);
    }
}
