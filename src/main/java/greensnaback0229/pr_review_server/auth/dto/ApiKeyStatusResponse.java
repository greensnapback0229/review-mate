package greensnaback0229.pr_review_server.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKeyStatusResponse {

    private boolean hasApiKey;
    private String maskedKey;
}
