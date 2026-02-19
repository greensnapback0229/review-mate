package greensnaback0229.pr_review_server.web;

import greensnaback0229.pr_review_server.auth.CustomOAuth2User;
import greensnaback0229.pr_review_server.tenant.UserRepositoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 저장소 설정 REST API
 * CSRF 제외 경로: /api/settings/**
 */
@Slf4j
@RestController
@RequestMapping("/api/settings/repositories")
@RequiredArgsConstructor
public class RepositorySettingsController {

    private final UserRepositoryService userRepositoryService;

    /**
     * 저장소 활성/비활성 토글
     * PUT /api/settings/repositories/{repositoryId}/active
     * Body: {"active": true/false}
     */
    @PutMapping("/{repositoryId}/active")
    public ResponseEntity<Map<String, Object>> toggleActive(
            @AuthenticationPrincipal CustomOAuth2User principal,
            @PathVariable Long repositoryId,
            @RequestBody Map<String, Boolean> body) {

        Long userId = principal.getUser().getId();
        boolean active = Boolean.TRUE.equals(body.get("active"));

        try {
            boolean result = userRepositoryService.toggleActive(userId, repositoryId, active);
            log.info("Repository {} active={} for userId={}", repositoryId, result, userId);
            return ResponseEntity.ok(Map.of(
                    "repositoryId", repositoryId,
                    "active", result,
                    "message", active ? "저장소가 활성화되었습니다." : "저장소가 비활성화되었습니다."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
