package greensnaback0229.pr_review_server.usage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageSummary {

    private Long userId;
    private String currentMonth;
    private int reviewCount;
    private int totalInputTokens;
    private int totalOutputTokens;
    private BigDecimal estimatedCost;
}
