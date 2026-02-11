package greensnaback0229.pr_review_server.auth.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("User 엔티티 테스트")
class UserTest {

    @Test
    @DisplayName("User_빌더로생성")
    void User_빌더로생성() {
        // given & when
        User user = User.builder()
                .githubId(12345L)
                .githubLogin("testuser")
                .email("test@example.com")
                .name("Test User")
                .avatarUrl("https://avatars.githubusercontent.com/u/12345")
                .githubToken("encrypted-token")
                .role("USER")
                .build();

        // then
        assertThat(user.getGithubId()).isEqualTo(12345L);
        assertThat(user.getGithubLogin()).isEqualTo("testuser");
        assertThat(user.getEmail()).isEqualTo("test@example.com");
        assertThat(user.getName()).isEqualTo("Test User");
        assertThat(user.getAvatarUrl()).isEqualTo("https://avatars.githubusercontent.com/u/12345");
        assertThat(user.getGithubToken()).isEqualTo("encrypted-token");
        assertThat(user.getRole()).isEqualTo("USER");
        assertThat(user.getAnthropicApiKey()).isNull();
    }

    @Test
    @DisplayName("User_setter로필드수정")
    void User_setter로필드수정() {
        // given
        User user = User.builder()
                .githubId(12345L)
                .githubLogin("testuser")
                .githubToken("old-token")
                .role("USER")
                .build();

        // when
        user.setEmail("new@example.com");
        user.setAvatarUrl("https://new-avatar.com");
        user.setGithubToken("new-token");
        user.setAnthropicApiKey("encrypted-api-key");

        // then
        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getAvatarUrl()).isEqualTo("https://new-avatar.com");
        assertThat(user.getGithubToken()).isEqualTo("new-token");
        assertThat(user.getAnthropicApiKey()).isEqualTo("encrypted-api-key");
    }
}
