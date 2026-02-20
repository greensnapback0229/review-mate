package greensnaback0229.pr_review_server.installation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pending_installations", indexes = {
    @Index(name = "idx_pending_github_id", columnList = "github_id"),
    @Index(name = "idx_pending_installation_id", columnList = "installation_id")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingInstallation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "github_id", nullable = false)
    private Long githubId;

    @Column(name = "installation_id", nullable = false)
    private Long installationId;

    @Column(name = "repositories", nullable = false, columnDefinition = "JSON")
    private String repositories;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
