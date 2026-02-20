package greensnaback0229.pr_review_server.auth;

import greensnaback0229.pr_review_server.auth.dto.ApiKeySaveRequest;
import greensnaback0229.pr_review_server.auth.dto.ApiKeyStatusResponse;
import greensnaback0229.pr_review_server.auth.exception.ApiKeyValidationException;
import greensnaback0229.pr_review_server.auth.exception.InvalidApiKeyException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings/api-key")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @GetMapping
    public ResponseEntity<ApiKeyStatusResponse> getApiKeyStatus(
            @AuthenticationPrincipal CustomOAuth2User principal) {
        ApiKeyStatusResponse status = apiKeyService.getApiKeyStatus(principal.getUser().getId());
        return ResponseEntity.ok(status);
    }

    @PutMapping
    public ResponseEntity<String> saveApiKey(
            @AuthenticationPrincipal CustomOAuth2User principal,
            @RequestBody ApiKeySaveRequest request) {
        try {
            apiKeyService.saveApiKey(principal.getUser().getId(), request.getApiKey());
            return ResponseEntity.ok("API Key가 저장되었습니다.");
        } catch (InvalidApiKeyException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ApiKeyValidationException e) {
            return ResponseEntity.status(502).body(e.getMessage());
        }
    }

    @DeleteMapping
    public ResponseEntity<String> deleteApiKey(
            @AuthenticationPrincipal CustomOAuth2User principal) {
        apiKeyService.deleteApiKey(principal.getUser().getId());
        return ResponseEntity.ok("API Key가 삭제되었습니다.");
    }
}
