package greensnaback0229.pr_review_server.auth;

import greensnaback0229.pr_review_server.auth.dto.ApiKeyStatusResponse;
import greensnaback0229.pr_review_server.auth.entity.User;
import greensnaback0229.pr_review_server.auth.exception.InvalidApiKeyException;
import greensnaback0229.pr_review_server.auth.repository.UserJpaRepository;
import greensnaback0229.pr_review_server.feature.entity.Repository;
import greensnaback0229.pr_review_server.feature.repository.RepositoryJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final String API_KEY_PREFIX = "sk-ant-";
    private static final int API_KEY_MIN_LENGTH = 20;

    private final UserJpaRepository userJpaRepository;
    private final EncryptionService encryptionService;
    private final RepositoryJpaRepository repositoryJpaRepository;

    @Transactional
    public void saveApiKey(Long userId, String rawApiKey) {
        validateApiKeyFormat(rawApiKey);

        String encrypted = encryptionService.encrypt(rawApiKey);
        User user = userJpaRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setAnthropicApiKey(encrypted);
        userJpaRepository.save(user);

        log.info("API key saved for userId={}", userId);
    }

    public String getDecryptedApiKey(Long userId) {
        User user = userJpaRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (user.getAnthropicApiKey() == null) {
            return null;
        }
        return encryptionService.decrypt(user.getAnthropicApiKey());
    }

    @Transactional
    public void deleteApiKey(Long userId) {
        User user = userJpaRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setAnthropicApiKey(null);
        userJpaRepository.save(user);

        log.info("API key deleted for userId={}", userId);
    }

    public ApiKeyStatusResponse getApiKeyStatus(Long userId) {
        User user = userJpaRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (user.getAnthropicApiKey() == null) {
            return ApiKeyStatusResponse.builder()
                    .hasApiKey(false)
                    .build();
        }

        String decrypted = encryptionService.decrypt(user.getAnthropicApiKey());
        String masked = maskApiKey(decrypted);

        return ApiKeyStatusResponse.builder()
                .hasApiKey(true)
                .maskedKey(masked)
                .build();
    }

    private void validateApiKeyFormat(String apiKey) {
        if (apiKey == null || !apiKey.startsWith(API_KEY_PREFIX) || apiKey.length() < API_KEY_MIN_LENGTH) {
            throw new InvalidApiKeyException("유효하지 않은 API Key입니다. 'sk-ant-'로 시작하는 올바른 형식의 키를 입력해주세요.");
        }
    }

    private String maskApiKey(String apiKey) {
        if (apiKey.length() <= 11) {
            return apiKey.substring(0, 7) + "****";
        }
        String prefix = apiKey.substring(0, 7);
        String suffix = apiKey.substring(apiKey.length() - 4);
        return prefix + "****" + suffix;
    }

    /**
     * Repository ID로부터 소유자의 Anthropic API Key를 조회
     * @param repositoryId GitHub Repository ID
     * @return 복호화된 API Key, 없으면 null
     */
    public String resolveApiKeyByRepositoryId(Long repositoryId) {
        Repository repo = repositoryJpaRepository.findById(repositoryId).orElse(null);
        if (repo == null || repo.getUserId() == null) {
            return null;
        }
        return getDecryptedApiKey(repo.getUserId());
    }
}
