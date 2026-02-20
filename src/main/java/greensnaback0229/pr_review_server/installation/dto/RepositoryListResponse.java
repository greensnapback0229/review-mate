package greensnaback0229.pr_review_server.installation.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryListResponse {

    private List<RepositoryDto> repositories;
    private int total;
}
