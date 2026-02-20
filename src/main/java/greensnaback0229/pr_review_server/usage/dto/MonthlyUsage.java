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
public class MonthlyUsage {

    private String month;
    private int reviewCount;
    private BigDecimal totalCost;
}
