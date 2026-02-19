package greensnaback0229.pr_review_server.web;

import greensnaback0229.pr_review_server.auth.ApiKeyService;
import greensnaback0229.pr_review_server.auth.CustomOAuth2User;
import greensnaback0229.pr_review_server.auth.dto.ApiKeyStatusResponse;
import greensnaback0229.pr_review_server.auth.entity.User;
import greensnaback0229.pr_review_server.review.ReviewHistoryService;
import greensnaback0229.pr_review_server.review.dto.RepositoryStatsResponse;
import greensnaback0229.pr_review_server.review.dto.ReviewSummaryDto;
import greensnaback0229.pr_review_server.tenant.UserRepositoryService;
import greensnaback0229.pr_review_server.tenant.entity.UserRepository;
import greensnaback0229.pr_review_server.usage.UsageService;
import greensnaback0229.pr_review_server.usage.dto.UsageSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
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

    @MockBean
    private ReviewHistoryService reviewHistoryService;

    @MockBean
    private UsageService usageService;

    @MockBean
    private UserRepositoryService userRepositoryService;

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

    private UserRepository createTestUserRepository(Long repoId, String fullName) {
        return UserRepository.builder()
                .id(1L)
                .userId(1L)
                .repositoryId(repoId)
                .repoFullName(fullName)
                .installationId(999L)
                .isActive(true)
                .build();
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
        @DisplayName("인증된 사용자 → 대시보드 정상 반환 + Model에 repositories/recentReviews/usage 포함")
        void 인증된사용자_대시보드_정상반환() throws Exception {
            UserRepository repo = createTestUserRepository(100L, "testuser/my-repo");
            ReviewSummaryDto review = ReviewSummaryDto.builder()
                    .reviewId(1L).repositoryId(100L).prNumber(1)
                    .prTitle("feat: test").featureName("AUTH")
                    .status("COMPLETED").createdAt(LocalDateTime.now())
                    .build();
            UsageSummary usage = UsageSummary.builder()
                    .userId(1L).currentMonth("2026-02").reviewCount(5)
                    .totalInputTokens(10000).totalOutputTokens(3000)
                    .estimatedCost(new BigDecimal("0.075"))
                    .build();

            when(userRepositoryService.findActiveRepositoriesByUserId(1L))
                    .thenReturn(List.of(repo));
            when(reviewHistoryService.getReviewHistory(eq(1L), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(review), PageRequest.of(0, 10), 1));
            when(usageService.getCurrentMonthUsage(1L)).thenReturn(usage);

            mockMvc.perform(get("/dashboard").with(mockOAuth2User()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("dashboard"))
                    .andExpect(model().attributeExists("user"))
                    .andExpect(model().attributeExists("repositories"))
                    .andExpect(model().attributeExists("recentReviews"))
                    .andExpect(model().attributeExists("usage"));
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
    @DisplayName("GET /repositories/{repositoryId}")
    class RepositoryDetailPage {

        @Test
        @DisplayName("인증된 사용자 + 본인 Repository → 상세 페이지 정상 반환")
        void 인증된사용자_레포상세_정상반환() throws Exception {
            UserRepository repo = createTestUserRepository(100L, "testuser/my-repo");
            ReviewSummaryDto review = ReviewSummaryDto.builder()
                    .reviewId(1L).repositoryId(100L).prNumber(1)
                    .prTitle("feat: test").featureName("AUTH")
                    .status("COMPLETED").createdAt(LocalDateTime.now())
                    .build();
            RepositoryStatsResponse stats = RepositoryStatsResponse.builder()
                    .repositoryId(100L).totalReviews(10).completedReviews(8)
                    .failedReviews(2).averageInlineComments(3.5)
                    .reviewsByFeature(Map.of("AUTH", 5L))
                    .build();

            when(userRepositoryService.findActiveRepositoriesByUserId(1L))
                    .thenReturn(List.of(repo));
            when(reviewHistoryService.getReviewsByRepository(eq(1L), eq(100L), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(review), PageRequest.of(0, 20), 1));
            when(reviewHistoryService.getRepositoryStats(1L, 100L)).thenReturn(stats);

            mockMvc.perform(get("/repositories/100").with(mockOAuth2User()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("repositories/detail"))
                    .andExpect(model().attributeExists("repository"))
                    .andExpect(model().attributeExists("reviews"))
                    .andExpect(model().attributeExists("stats"));
        }

        @Test
        @DisplayName("타인의 Repository → 404")
        void 타인의레포_404() throws Exception {
            when(userRepositoryService.findActiveRepositoriesByUserId(1L))
                    .thenReturn(List.of()); // 해당 user에 연결된 repo 없음

            mockMvc.perform(get("/repositories/999").with(mockOAuth2User()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("미인증 사용자 → /login redirect")
        void 미인증사용자_로그인리다이렉트() throws Exception {
            mockMvc.perform(get("/repositories/100"))
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