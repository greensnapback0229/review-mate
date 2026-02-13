package greensnaback0229.pr_review_server.auth;

import greensnaback0229.pr_review_server.auth.entity.User;
import greensnaback0229.pr_review_server.auth.repository.UserJpaRepository;
import greensnaback0229.pr_review_server.comment.repository.ReviewContextJpaRepository;
import greensnaback0229.pr_review_server.feature.repository.FeatureMemoryJpaRepository;
import greensnaback0229.pr_review_server.feature.repository.RepositoryJpaRepository;
import greensnaback0229.pr_review_server.installation.InstallationHandler;
import greensnaback0229.pr_review_server.installation.repository.PendingInstallationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomOAuth2UserService 테스트")
class CustomOAuth2UserServiceTest {

    @Mock
    private UserJpaRepository userJpaRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private RepositoryJpaRepository repositoryJpaRepository;

    @Mock
    private FeatureMemoryJpaRepository featureMemoryJpaRepository;

    @Mock
    private ReviewContextJpaRepository reviewContextJpaRepository;

    @Mock
    private PendingInstallationJpaRepository pendingInstallationJpaRepository;

    @Mock
    private InstallationHandler installationHandler;

    @InjectMocks
    private CustomOAuth2UserService customOAuth2UserService;

    private Map<String, Object> githubAttributes;

    @BeforeEach
    void setUp() {
        githubAttributes = new HashMap<>();
        githubAttributes.put("id", 12345);
        githubAttributes.put("login", "testuser");
        githubAttributes.put("email", "test@example.com");
        githubAttributes.put("name", "Test User");
        githubAttributes.put("avatar_url", "https://avatars.githubusercontent.com/u/12345");
    }

    @Test
    @DisplayName("loadUser_첫번째가입자_ADMIN역할부여")
    void loadUser_첫번째가입자_ADMIN역할부여() {
        // given
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted-token");
        when(userJpaRepository.count()).thenReturn(0L);
        when(userJpaRepository.findByGithubId(12345L)).thenReturn(Optional.empty());
        when(userJpaRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return User.builder()
                    .id(1L)
                    .githubId(user.getGithubId())
                    .githubLogin(user.getGithubLogin())
                    .email(user.getEmail())
                    .name(user.getName())
                    .avatarUrl(user.getAvatarUrl())
                    .githubToken(user.getGithubToken())
                    .role(user.getRole())
                    .build();
        });

        // when
        CustomOAuth2User result = customOAuth2UserService.processOAuth2User(
                githubAttributes, "github-access-token");

        // then
        assertThat(result.getUser().getRole()).isEqualTo("ADMIN");
        assertThat(result.getUser().getGithubLogin()).isEqualTo("testuser");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userJpaRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("loadUser_두번째가입자_USER역할부여")
    void loadUser_두번째가입자_USER역할부여() {
        // given
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted-token");
        when(userJpaRepository.count()).thenReturn(1L);
        when(userJpaRepository.findByGithubId(12345L)).thenReturn(Optional.empty());
        when(userJpaRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return User.builder()
                    .id(2L)
                    .githubId(user.getGithubId())
                    .githubLogin(user.getGithubLogin())
                    .email(user.getEmail())
                    .name(user.getName())
                    .avatarUrl(user.getAvatarUrl())
                    .githubToken(user.getGithubToken())
                    .role(user.getRole())
                    .build();
        });

        // when
        CustomOAuth2User result = customOAuth2UserService.processOAuth2User(
                githubAttributes, "github-access-token");

        // then
        assertThat(result.getUser().getRole()).isEqualTo("USER");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userJpaRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo("USER");
    }

    @Test
    @DisplayName("loadUser_기존사용자_토큰갱신")
    void loadUser_기존사용자_토큰갱신() {
        // given
        User existingUser = User.builder()
                .id(1L)
                .githubId(12345L)
                .githubLogin("testuser")
                .email("old@example.com")
                .avatarUrl("https://old-avatar.com")
                .githubToken("old-encrypted-token")
                .role("ADMIN")
                .build();

        when(encryptionService.encrypt(anyString())).thenReturn("new-encrypted-token");
        when(userJpaRepository.findByGithubId(12345L)).thenReturn(Optional.of(existingUser));
        when(userJpaRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        CustomOAuth2User result = customOAuth2UserService.processOAuth2User(
                githubAttributes, "new-github-token");

        // then
        assertThat(result.getUser().getEmail()).isEqualTo("test@example.com");
        assertThat(result.getUser().getAvatarUrl()).isEqualTo("https://avatars.githubusercontent.com/u/12345");
        assertThat(result.getUser().getGithubToken()).isEqualTo("new-encrypted-token");
        assertThat(result.getUser().getRole()).isEqualTo("ADMIN"); // 역할 유지
    }

    @Test
    @DisplayName("loadUser_첫번째가입자_익명데이터마이그레이션")
    void loadUser_첫번째가입자_익명데이터마이그레이션() {
        // given
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted-token");
        when(userJpaRepository.count()).thenReturn(0L);
        when(userJpaRepository.findByGithubId(12345L)).thenReturn(Optional.empty());
        when(userJpaRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return User.builder()
                    .id(1L)
                    .githubId(user.getGithubId())
                    .githubLogin(user.getGithubLogin())
                    .email(user.getEmail())
                    .name(user.getName())
                    .avatarUrl(user.getAvatarUrl())
                    .githubToken(user.getGithubToken())
                    .role(user.getRole())
                    .build();
        });

        // when
        customOAuth2UserService.processOAuth2User(githubAttributes, "github-access-token");

        // then - 마이그레이션 쿼리 실행 검증
        verify(repositoryJpaRepository).updateUserIdWhereNull(1L);
        verify(featureMemoryJpaRepository).updateUserIdWhereNull(1L);
        verify(reviewContextJpaRepository).updateUserIdWhereNull(1L);
    }
}
