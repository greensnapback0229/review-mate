package greensnaback0229.pr_review_server.web;

import greensnaback0229.pr_review_server.auth.ApiKeyService;
import greensnaback0229.pr_review_server.auth.CustomOAuth2User;
import greensnaback0229.pr_review_server.auth.dto.ApiKeyStatusResponse;
import greensnaback0229.pr_review_server.auth.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("WebPageController 테스트")
class WebPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApiKeyService apiKeyService;

    private User createTestUser() {
        return User.builder()
                .id(1L)
                .githubId(12345L)
                .githubLogin("testuser")
                .name("Test User")
                .email("test@example.com")
                .avatarUrl("https://avatars.githubusercontent.com/u/12345")
                .githubToken("encrypted-token")
                .role("USER")
                .build();
    }

    private SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor mockOAuth2User() {
        User user = createTestUser();
        CustomOAuth2User customUser = new CustomOAuth2User(user, Map.of(
                "id", 12345,
                "login", "testuser"
        ));
        return oauth2Login().oauth2User(customUser);
    }

    @Nested
    @DisplayName("GET / (루트)")
    class RootPage {

        @Test
        @DisplayName("인증된 사용자 → /dashboard redirect")
        void 인증된사용자_대시보드리다이렉트() throws Exception {
            mockMvc.perform(get("/").with(mockOAuth2User()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/dashboard"));
        }

        @Test
        @DisplayName("미인증 사용자 → /login redirect")
        void 미인증사용자_로그인리다이렉트() throws Exception {
            mockMvc.perform(get("/"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
        }
    }

    @Nested
    @DisplayName("GET /login")
    class LoginPage {

        @Test
        @DisplayName("로그인 페이지 정상 반환")
        void 로그인페이지_정상반환() throws Exception {
            mockMvc.perform(get("/login"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("login"));
        }
    }

    @Nested
    @DisplayName("GET /dashboard")
    class DashboardPage {

        @Test
        @DisplayName("인증된 사용자 → 대시보드 정상 반환")
        void 인증된사용자_대시보드_정상반환() throws Exception {
            mockMvc.perform(get("/dashboard").with(mockOAuth2User()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("dashboard"))
                    .andExpect(model().attributeExists("user"));
        }

        @Test
        @DisplayName("미인증 사용자 → /login redirect")
        void 미인증사용자_로그인리다이렉트() throws Exception {
            mockMvc.perform(get("/dashboard"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
        }
    }

    @Nested
    @DisplayName("GET /profile")
    class ProfilePage {

        @Test
        @DisplayName("인증된 사용자 → 프로필 정상 반환 + Model에 user, apiKeyStatus 포함")
        void 인증된사용자_프로필_정상반환() throws Exception {
            ApiKeyStatusResponse apiKeyStatus = ApiKeyStatusResponse.builder()
                    .hasApiKey(true)
                    .maskedKey("sk-ant-****a3f2")
                    .build();
            when(apiKeyService.getApiKeyStatus(1L)).thenReturn(apiKeyStatus);

            mockMvc.perform(get("/profile").with(mockOAuth2User()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("profile"))
                    .andExpect(model().attributeExists("user"))
                    .andExpect(model().attributeExists("apiKeyStatus"));
        }

        @Test
        @DisplayName("미인증 사용자 → /login redirect")
        void 미인증사용자_로그인리다이렉트() throws Exception {
            mockMvc.perform(get("/profile"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
        }
    }

    @Nested
    @DisplayName("GET /settings/api-key")
    class ApiKeySettingsPage {

        @Test
        @DisplayName("인증된 사용자 → API Key 설정 페이지 정상 반환")
        void 인증된사용자_apiKey설정_정상반환() throws Exception {
            ApiKeyStatusResponse apiKeyStatus = ApiKeyStatusResponse.builder()
                    .hasApiKey(false)
                    .build();
            when(apiKeyService.getApiKeyStatus(1L)).thenReturn(apiKeyStatus);

            mockMvc.perform(get("/settings/api-key").with(mockOAuth2User()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("settings/api-key"))
                    .andExpect(model().attributeExists("apiKeyStatus"));
        }

        @Test
        @DisplayName("미인증 사용자 → /login redirect")
        void 미인증사용자_로그인리다이렉트() throws Exception {
            mockMvc.perform(get("/settings/api-key"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
        }
    }
}
