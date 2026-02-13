package greensnaback0229.pr_review_server.installation;

import greensnaback0229.pr_review_server.installation.dto.RepositoryDto;
import greensnaback0229.pr_review_server.installation.dto.RepositoryListResponse;
import greensnaback0229.pr_review_server.installation.dto.ToggleRequest;
import greensnaback0229.pr_review_server.tenant.TenantContext;
import greensnaback0229.pr_review_server.tenant.entity.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/repositories")
@RequiredArgsConstructor
public class RepositoryController {

    private final RepositoryService repositoryService;

    @GetMapping
    public ResponseEntity<RepositoryListResponse> getRepositories() {
        Long userId = TenantContext.getCurrentUserIdOrThrow();
        List<UserRepository> repositories = repositoryService.findActiveRepositories(userId);

        RepositoryListResponse response = RepositoryListResponse.builder()
                .repositories(repositories.stream()
                        .map(this::toDto)
                        .toList())
                .total(repositories.size())
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RepositoryDto> getRepository(@PathVariable Long id) {
        Long userId = TenantContext.getCurrentUserIdOrThrow();
        UserRepository repo = repositoryService.findById(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        return ResponseEntity.ok(toDto(repo));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> toggleRepository(@PathVariable Long id, @RequestBody ToggleRequest request) {
        Long userId = TenantContext.getCurrentUserIdOrThrow();
        repositoryService.toggleActive(id, userId, request.getIsActive());

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRepository(@PathVariable Long id) {
        Long userId = TenantContext.getCurrentUserIdOrThrow();
        repositoryService.deactivate(id, userId);

        return ResponseEntity.noContent().build();
    }

    private RepositoryDto toDto(UserRepository repo) {
        return RepositoryDto.builder()
                .id(repo.getId())
                .repositoryId(repo.getRepositoryId())
                .fullName(repo.getRepoFullName())
                .installationId(repo.getInstallationId())
                .isActive(repo.getIsActive())
                .createdAt(repo.getCreatedAt())
                .build();
    }
}
