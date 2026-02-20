package greensnaback0229.pr_review_server.usage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageHistoryResponse {

    private Long userId;
    private List<MonthlyUsage> months;
}
