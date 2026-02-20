package greensnaback0229.pr_review_server.web;

import greensnaback0229.pr_review_server.auth.CustomOAuth2User;
import greensnaback0229.pr_review_server.auth.entity.User;
import greensnaback0229.pr_review_server.tenant.UserRepositoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("RepositorySettingsController 테스트")
class RepositorySettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepositoryService userRepositoryService;

    private SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor mockOAuth2User() {
        User user = User.builder()
                .id(1L).githubId(12345L).githubLogin("testuser")
                .name("Test User").email("test@example.com")
                .avatarUrl("https://avatars.githubusercontent.com/u/12345")
                .githubToken("encrypted-token").role("USER").build();
        return oauth2Login().oauth2User(new CustomOAuth2User(user, Map.of("id", 12345, "login", "testuser")));
    }

    @Nested
    @DisplayName("PUT /api/settings/repositories/{repositoryId}/active")
    class ToggleActive {

        @Test
        @DisplayName("활성화 요청 → 200 + active=true 반환")
        void 활성화요청_성공() throws Exception {
            when(userRepositoryService.toggleActive(1L, 100L, true)).thenReturn(true);

            mockMvc.perform(put("/api/settings/repositories/100/active")
                            .with(mockOAuth2User())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\": true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(true))
                    .andExpect(jsonPath("$.repositoryId").value(100));
        }

        @Test
        @DisplayName("비활성화 요청 → 200 + active=false 반환")
        void 비활성화요청_성공() throws Exception {
            when(userRepositoryService.toggleActive(1L, 100L, false)).thenReturn(false);

            mockMvc.perform(put("/api/settings/repositories/100/active")
                            .with(mockOAuth2User())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\": false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false));
        }

        @Test
        @DisplayName("존재하지 않는 저장소 → 404")
        void 존재하지않는저장소_404() throws Exception {
            when(userRepositoryService.toggleActive(1L, 999L, true))
                    .thenThrow(new IllegalArgumentException("Repository not found"));

            mockMvc.perform(put("/api/settings/repositories/999/active")
                            .with(mockOAuth2User())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\": true}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("미인증 사용자 → 302 redirect")
        void 미인증사용자_리다이렉트() throws Exception {
            mockMvc.perform(put("/api/settings/repositories/100/active")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\": true}"))
                    .andExpect(status().is3xxRedirection());
        }
    }
}
