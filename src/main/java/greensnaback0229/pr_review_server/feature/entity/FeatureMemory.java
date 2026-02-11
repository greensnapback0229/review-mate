package greensnaback0229.pr_review_server.feature.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "feature_memory")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feature_memory_id")
    private Long featureMemoryId;

    @Column(name = "repository_id", nullable = false)
    private Long repositoryId;

    @Setter
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "feature_name", nullable = false, length = 100)
    private String featureName;

    @Column(name = "feature_memory", nullable = false, columnDefinition = "JSON")
    private String featureMemoryContent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", insertable = false, updatable = false)
    private Repository repository;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
