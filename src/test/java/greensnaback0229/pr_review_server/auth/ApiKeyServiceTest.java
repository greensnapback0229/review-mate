package greensnaback0229.pr_review_server.auth;

import greensnaback0229.pr_review_server.auth.dto.ApiKeyStatusResponse;
import greensnaback0229.pr_review_server.auth.entity.User;
import greensnaback0229.pr_review_server.auth.exception.InvalidApiKeyException;
import greensnaback0229.pr_review_server.auth.repository.UserJpaRepository;
import greensnaback0229.pr_review_server.feature.entity.Repository;
import greensnaback0229.pr_review_server.feature.repository.RepositoryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyService 테스트")
class ApiKeyServiceTest {

    @Mock
    private UserJpaRepository userJpaRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private RepositoryJpaRepository repositoryJpaRepository;

    @InjectMocks
    private ApiKeyService apiKeyService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .githubId(12345L)
                .githubLogin("testuser")
                .githubToken("encrypted-github-token")
                .role("USER")
                .build();
    }

    @Test
    @DisplayName("saveApiKey_유효한키_암호화저장")
    void saveApiKey_유효한키_암호화저장() {
        // given
        String rawApiKey = "sk-ant-api03-valid-key-12345";
        when(userJpaRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(encryptionService.encrypt(rawApiKey)).thenReturn("encrypted-api-key");

        // when
        apiKeyService.saveApiKey(1L, rawApiKey);

        // then
        assertThat(testUser.getAnthropicApiKey()).isEqualTo("encrypted-api-key");
        verify(userJpaRepository).save(testUser);
    }

    @Test
    @DisplayName("saveApiKey_유효하지않은키_예외발생")
    void saveApiKey_유효하지않은키_예외발생() {
        // given
        String invalidApiKey = "invalid-key";

        // when & then
        assertThatThrownBy(() -> apiKeyService.saveApiKey(1L, invalidApiKey))
                .isInstanceOf(InvalidApiKeyException.class);
    }

    @Test
    @DisplayName("saveApiKey_너무짧은키_예외발생")
    void saveApiKey_너무짧은키_예외발생() {
        // given
        String shortApiKey = "sk-ant-short";

        // when & then
        assertThatThrownBy(() -> apiKeyService.saveApiKey(1L, shortApiKey))
                .isInstanceOf(InvalidApiKeyException.class);
    }

    @Test
    @DisplayName("getDecryptedApiKey_키존재_복호화반환")
    void getDecryptedApiKey_키존재_복호화반환() {
        // given
        testUser.setAnthropicApiKey("encrypted-api-key");
        when(userJpaRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(encryptionService.decrypt("encrypted-api-key")).thenReturn("sk-ant-api03-decrypted-key");

        // when
        String result = apiKeyService.getDecryptedApiKey(1L);

        // then
        assertThat(result).isEqualTo("sk-ant-api03-decrypted-key");
    }

    @Test
    @DisplayName("getDecryptedApiKey_키미설정_null반환")
    void getDecryptedApiKey_키미설정_null반환() {
        // given
        testUser.setAnthropicApiKey(null);
        when(userJpaRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // when
        String result = apiKeyService.getDecryptedApiKey(1L);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("deleteApiKey_키삭제_null설정")
    void deleteApiKey_키삭제_null설정() {
        // given
        testUser.setAnthropicApiKey("encrypted-api-key");
        when(userJpaRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // when
        apiKeyService.deleteApiKey(1L);

        // then
        assertThat(testUser.getAnthropicApiKey()).isNull();
        verify(userJpaRepository).save(testUser);
    }

    @Test
    @DisplayName("getApiKeyStatus_마스킹반환")
    void getApiKeyStatus_마스킹반환() {
        // given
        testUser.setAnthropicApiKey("encrypted-api-key");
        when(userJpaRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(encryptionService.decrypt("encrypted-api-key"))
                .thenReturn("sk-ant-api03-abcdefghijklmnop-a3f2");

        // when
        ApiKeyStatusResponse status = apiKeyService.getApiKeyStatus(1L);

        // then
        assertThat(status.isHasApiKey()).isTrue();
        assertThat(status.getMaskedKey()).startsWith("sk-ant-");
        assertThat(status.getMaskedKey()).endsWith("a3f2");
        assertThat(status.getMaskedKey()).contains("****");
    }

    @Test
    @DisplayName("resolveApiKeyByRepositoryId_정상_복호화키반환")
    void resolveApiKeyByRepositoryId_정상_복호화키반환() {
        // given
        Long repositoryId = 123L;
        Repository repo = Repository.builder()
                .repositoryId(repositoryId)
                .userId(1L)
                .build();
        testUser.setAnthropicApiKey("encrypted-api-key");

        when(repositoryJpaRepository.findById(repositoryId)).thenReturn(Optional.of(repo));
        when(userJpaRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(encryptionService.decrypt("encrypted-api-key")).thenReturn("sk-ant-api03-decrypted-key");

        // when
        String result = apiKeyService.resolveApiKeyByRepositoryId(repositoryId);

        // then
        assertThat(result).isEqualTo("sk-ant-api03-decrypted-key");
    }

    @Test
    @DisplayName("resolveApiKeyByRepositoryId_저장소없음_null반환")
    void resolveApiKeyByRepositoryId_저장소없음_null반환() {
        // given
        Long repositoryId = 999L;
        when(repositoryJpaRepository.findById(repositoryId)).thenReturn(Optional.empty());

        // when
        String result = apiKeyService.resolveApiKeyByRepositoryId(repositoryId);

        // then
        assertThat(result).isNull();
        verify(userJpaRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("resolveApiKeyByRepositoryId_userId없음_null반환")
    void resolveApiKeyByRepositoryId_userId없음_null반환() {
        // given
        Long repositoryId = 123L;
        Repository repo = Repository.builder()
                .repositoryId(repositoryId)
                .userId(null)
                .build();

        when(repositoryJpaRepository.findById(repositoryId)).thenReturn(Optional.of(repo));

        // when
        String result = apiKeyService.resolveApiKeyByRepositoryId(repositoryId);

        // then
        assertThat(result).isNull();
        verify(userJpaRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("resolveApiKeyByRepositoryId_API키미설정_null반환")
    void resolveApiKeyByRepositoryId_API키미설정_null반환() {
        // given
        Long repositoryId = 123L;
        Repository repo = Repository.builder()
                .repositoryId(repositoryId)
                .userId(1L)
                .build();
        testUser.setAnthropicApiKey(null);

        when(repositoryJpaRepository.findById(repositoryId)).thenReturn(Optional.of(repo));
        when(userJpaRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // when
        String result = apiKeyService.resolveApiKeyByRepositoryId(repositoryId);

        // then
        assertThat(result).isNull();
    }
}
