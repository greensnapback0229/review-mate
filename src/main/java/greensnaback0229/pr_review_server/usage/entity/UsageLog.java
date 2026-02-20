package greensnaback0229.pr_review_server.usage.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "usage_log", indexes = {
    @Index(name = "idx_user_month", columnList = "user_id, created_at"),
    @Index(name = "idx_repo", columnList = "repository_id"),
    @Index(name = "idx_review_type", columnList = "review_type")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "repository_id", nullable = false)
    private Long repositoryId;

    @Column(name = "pr_number", nullable = false)
    private Integer prNumber;

    @Column(name = "feature_name")
    private String featureName;

    @Column(name = "input_tokens", nullable = false)
    private Integer inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private Integer outputTokens;

    @Column(name = "estimated_cost", nullable = false, precision = 10, scale = 6)
    private BigDecimal estimatedCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_type", nullable = false, length = 20)
    private ReviewType reviewType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (inputTokens == null) {
            inputTokens = 0;
        }
        if (outputTokens == null) {
            outputTokens = 0;
        }
        if (estimatedCost == null) {
            estimatedCost = BigDecimal.ZERO;
        }
    }
}