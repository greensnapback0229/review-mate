package greensnaback0229.pr_review_server.comment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "review_context", uniqueConstraints = {
    @UniqueConstraint(name = "idx_repo_pr_feature", columnNames = {"repository_id", "pr_number", "feature_name"})
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewContext {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repository_id", nullable = false)
    private Long repositoryId;

    @Setter
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "pr_number", nullable = false)
    private Integer prNumber;

    @Column(name = "feature_name", nullable = false, length = 255)
    private String featureName;

    @Column(name = "head_sha", nullable = false, length = 40)
    private String headSha;

    @Column(name = "file_contexts", nullable = false, columnDefinition = "JSON")
    private String fileContexts;

    @Column(name = "general_review", columnDefinition = "TEXT")
    private String generalReview;

    @Column(name = "inline_comments", columnDefinition = "JSON")
    private String inlineComments;

    @Column(name = "bot_comment_ids", nullable = false, columnDefinition = "JSON")
    private String botCommentIds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (botCommentIds == null) {
            botCommentIds = "[]";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
