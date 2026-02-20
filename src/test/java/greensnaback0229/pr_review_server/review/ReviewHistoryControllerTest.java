package greensnaback0229.pr_review_server.review;

import greensnaback0229.pr_review_server.auth.CustomOAuth2User;
import greensnaback0229.pr_review_server.auth.entity.User;
import greensnaback0229.pr_review_server.review.dto.PrReviewDetailResponse;
import greensnaback0229.pr_review_server.review.dto.RepositoryStatsResponse;
import greensnaback0229.pr_review_server.review.dto.ReviewDetailDto;
import greensnaback0229.pr_review_server.review.dto.ReviewSummaryDto;
import org.junit.jupiter.api.DisplayName;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("ReviewHistoryController 테스트")
class ReviewHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewHistoryService reviewHistoryService;

    private SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor mockOAuth2User() {
        User user = User.builder()
                .id(1L)
                .githubId(12345L)
                .githubLogin("testuser")
                .githubToken("encrypted-token")
                .role("USER")
                .build();
        CustomOAuth2User customUser = new CustomOAuth2User(user, Map.of(
                "id", 12345,
                "login", "testuser"
        ));
        return oauth2Login().oauth2User(customUser);
    }

    @Test
    @DisplayName("GET /api/reviews 인증 → 200, 페이징 응답")
    void getReviews_인증_페이징응답() throws Exception {
        ReviewSummaryDto dto = ReviewSummaryDto.builder()
                .reviewId(1L).repositoryId(12345L).prNumber(42)
                .prTitle("feat: Add auth").featureName("AUTH")
                .status("COMPLETED").inlineCommentCount(3)
                .reviewDurationMs(25000L).createdAt(LocalDateTime.now())
                .build();

        when(reviewHistoryService.getReviewHistory(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/reviews").with(mockOAuth2User()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reviewId").value(1))
                .andExpect(jsonPath("$.content[0].featureName").value("AUTH"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/reviews 미인증 → 302 redirect")
    void getReviews_미인증_리다이렉트() throws Exception {
        mockMvc.perform(get("/api/reviews"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("GET /api/reviews/{repoId} 인증 → 200, 필터링된 결과")
    void getReviewsByRepository_인증_필터링() throws Exception {
        ReviewSummaryDto dto = ReviewSummaryDto.builder()
                .reviewId(2L).repositoryId(12345L).prNumber(43)
                .prTitle("fix: Bug fix").featureName("PAYMENT")
                .status("COMPLETED").inlineCommentCount(1)
                .createdAt(LocalDateTime.now())
                .build();

        when(reviewHistoryService.getReviewsByRepository(eq(1L), eq(12345L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/reviews/12345").with(mockOAuth2User()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].repositoryId").value(12345));
    }

    @Test
    @DisplayName("GET /api/reviews/{repoId}/pr/{prNumber} 인증 → 200, 상세 응답")
    void getReviewsByPr_인증_상세응답() throws Exception {
        ReviewDetailDto detail = ReviewDetailDto.builder()
                .reviewId(1L).featureName("AUTH")
                .generalReview("Good review")
                .inlineComments(Collections.emptyList())
                .status("COMPLETED").createdAt(LocalDateTime.now())
                .build();

        PrReviewDetailResponse response = PrReviewDetailResponse.builder()
                .repositoryId(12345L).prNumber(42).prTitle("feat: Add auth")
                .reviews(List.of(detail)).totalReviewCount(1)
                .build();

        when(reviewHistoryService.getReviewsByPr(1L, 12345L, 42)).thenReturn(response);

        mockMvc.perform(get("/api/reviews/12345/pr/42").with(mockOAuth2User()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prNumber").value(42))
                .andExpect(jsonPath("$.totalReviewCount").value(1));
    }

    @Test
    @DisplayName("GET /api/reviews/{repoId}/stats 인증 → 200, 통계 응답")
    void getRepositoryStats_인증_통계응답() throws Exception {
        RepositoryStatsResponse stats = RepositoryStatsResponse.builder()
                .repositoryId(12345L).totalReviews(10).completedReviews(8)
                .failedReviews(2).averageInlineComments(3.5)
                .averageReviewDurationMs(28000L)
                .reviewsByFeature(Map.of("AUTH", 5L, "PAYMENT", 5L))
                .last7DaysReviews(3).last30DaysReviews(10)
                .build();

        when(reviewHistoryService.getRepositoryStats(1L, 12345L)).thenReturn(stats);

        mockMvc.perform(get("/api/reviews/12345/stats").with(mockOAuth2User()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReviews").value(10))
                .andExpect(jsonPath("$.completedReviews").value(8))
                .andExpect(jsonPath("$.reviewsByFeature.AUTH").value(5));
    }

    @Test
    @DisplayName("GET /api/reviews/{repoId}/stats 미인증 → 302 redirect")
    void getRepositoryStats_미인증_리다이렉트() throws Exception {
        mockMvc.perform(get("/api/reviews/12345/stats"))
                .andExpect(status().is3xxRedirection());
    }
}
