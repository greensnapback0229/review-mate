package greensnaback0229.pr_review_server.installation.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryDto {

    private Long id;
    private Long repositoryId;
    private String fullName;
    private Long installationId;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
