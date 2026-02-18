package greensnaback0229.pr_review_server.review.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "review_history", indexes = {
    @Index(name = "idx_rh_user_created", columnList = "user_id, created_at"),
    @Index(name = "idx_rh_user_repo", columnList = "user_id, repository_id"),
    @Index(name = "idx_rh_repo_pr", columnList = "repository_id, pr_number")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "repository_id", nullable = false)
    private Long repositoryId;

    @Column(name = "pr_number", nullable = false)
    private Integer prNumber;

    @Column(name = "pr_title", length = 500)
    private String prTitle;

    @Column(name = "feature_name")
    private String featureName;

    @Column(name = "general_review", columnDefinition = "TEXT")
    private String generalReview;

    @Column(name = "inline_comments", columnDefinition = "JSON")
    private String inlineComments;

    @Column(name = "memory_suggestion", columnDefinition = "JSON")
    private String memorySuggestion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReviewStatus status;

    @Column(name = "inline_comment_count", nullable = false)
    private Integer inlineCommentCount;

    @Column(name = "review_duration_ms")
    private Long reviewDurationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (inlineCommentCount == null) {
            inlineCommentCount = 0;
        }
    }
}
