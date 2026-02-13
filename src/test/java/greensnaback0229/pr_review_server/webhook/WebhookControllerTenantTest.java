package greensnaback0229.pr_review_server.webhook;

import greensnaback0229.pr_review_server.aggregator.dto.AggregatedReview;
import greensnaback0229.pr_review_server.auth.ApiKeyService;
import greensnaback0229.pr_review_server.comment.CommentResponseService;
import greensnaback0229.pr_review_server.comment.ReviewContextService;
import greensnaback0229.pr_review_server.github.GitHubReviewClient;
import greensnaback0229.pr_review_server.tenant.TenantContext;
import greensnaback0229.pr_review_server.tenant.UserRepositoryService;
import greensnaback0229.pr_review_server.webhook.dto.WebhookPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookControllerTenantTest {

    @Mock private PrReviewService prReviewService;
    @Mock private GitHubReviewClient gitHubReviewClient;
    @Mock private ReviewContextService reviewContextService;
    @Mock private CommentResponseService commentResponseService;
    @Mock private ApiKeyService apiKeyService;
    @Mock private UserRepositoryService userRepositoryService;

    @InjectMocks
    private WebhookController webhookController;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private WebhookPayload createPrPayload(String action, Long repoId, String repoFullName, int prNumber) {
        return WebhookPayload.builder()
                .action(action)
                .repository(WebhookPayload.Repository.builder()
                        .id(repoId)
                        .fullName(repoFullName)
                        .build())
                .pullRequest(WebhookPayload.PullRequest.builder()
                        .number(prNumber)
                        .title("Test PR")
                        .body("Test body")
                        .base(WebhookPayload.Branch.builder()
                                .ref("main")
                                .build())
                        .head(WebhookPayload.Branch.builder()
                                .ref("feature/test")
                                .sha("abc123")
                                .build())
                        .build())
                .build();
    }

    @Test
    @DisplayName("PR 이벤트 - 다중 사용자에 대해 각각 리뷰 실행")
    void handlePrEvent_multipleUsers_reviewsForEach() throws IOException {
        // given
        WebhookPayload payload = createPrPayload("opened", 100L, "owner/repo", 1);

        when(userRepositoryService.findActiveUserIdsByRepositoryId(100L))
                .thenReturn(List.of(1L, 2L));
        when(apiKeyService.getDecryptedApiKey(1L)).thenReturn("sk-ant-key1");
        when(apiKeyService.getDecryptedApiKey(2L)).thenReturn("sk-ant-key2");

        AggregatedReview review = AggregatedReview.builder()
                .feature("test-feature")
                .review("looks good")
                .build();
        when(prReviewService.reviewPullRequest(anyString(), anyLong(), anyString(),
                anyInt(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(review));
        when(gitHubReviewClient.createSimpleComment(anyString(), anyInt(), anyString()))
                .thenReturn(List.of(10L));

        // when
        ResponseEntity<String> response = webhookController.handleWebhookEvent(
                "delivery-1", "pull_request", payload);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(prReviewService, times(2)).reviewPullRequest(
                anyString(), eq(100L), eq("owner/repo"),
                eq(1), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(apiKeyService).getDecryptedApiKey(1L);
        verify(apiKeyService).getDecryptedApiKey(2L);
        // TenantContext should be cleared after processing
        assertThat(TenantContext.getCurrentUserId()).isNull();
    }

    @Test
    @DisplayName("PR 이벤트 - API Key 없는 사용자는 스킵")
    void handlePrEvent_userWithoutApiKey_skipped() throws IOException {
        // given
        WebhookPayload payload = createPrPayload("opened", 100L, "owner/repo", 1);

        when(userRepositoryService.findActiveUserIdsByRepositoryId(100L))
                .thenReturn(List.of(1L, 2L));
        when(apiKeyService.getDecryptedApiKey(1L)).thenReturn("sk-ant-key1");
        when(apiKeyService.getDecryptedApiKey(2L)).thenReturn(null); // no API key

        AggregatedReview review = AggregatedReview.builder()
                .feature("test-feature")
                .review("looks good")
                .build();
        when(prReviewService.reviewPullRequest(anyString(), anyLong(), anyString(),
                anyInt(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(review));
        when(gitHubReviewClient.createSimpleComment(anyString(), anyInt(), anyString()))
                .thenReturn(List.of(10L));

        // when
        ResponseEntity<String> response = webhookController.handleWebhookEvent(
                "delivery-2", "pull_request", payload);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        // Only user 1 should have reviews
        verify(prReviewService, times(1)).reviewPullRequest(
                eq("sk-ant-key1"), eq(100L), eq("owner/repo"),
                eq(1), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(prReviewService, never()).reviewPullRequest(
                eq((String) null), anyLong(), anyString(),
                anyInt(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("PR 이벤트 - 연결된 사용자 없으면 리뷰 스킵")
    void handlePrEvent_noUsers_noReview() {
        // given
        WebhookPayload payload = createPrPayload("opened", 100L, "owner/repo", 1);

        when(userRepositoryService.findActiveUserIdsByRepositoryId(100L))
                .thenReturn(List.of());

        // when
        ResponseEntity<String> response = webhookController.handleWebhookEvent(
                "delivery-3", "pull_request", payload);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(prReviewService, never()).reviewPullRequest(
                anyString(), anyLong(), anyString(),
                anyInt(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("TenantContext가 각 사용자 루프에서 올바르게 설정되는지 검증")
    void handlePrEvent_tenantContextSetPerUser() {
        // given
        WebhookPayload payload = createPrPayload("opened", 100L, "owner/repo", 1);

        when(userRepositoryService.findActiveUserIdsByRepositoryId(100L))
                .thenReturn(List.of(1L, 2L));
        when(apiKeyService.getDecryptedApiKey(1L)).thenReturn("sk-ant-key1");
        when(apiKeyService.getDecryptedApiKey(2L)).thenReturn("sk-ant-key2");

        // Capture TenantContext userId during each prReviewService call
        List<Long> capturedUserIds = new ArrayList<>();
        when(prReviewService.reviewPullRequest(anyString(), anyLong(), anyString(),
                anyInt(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    capturedUserIds.add(TenantContext.getCurrentUserId());
                    return List.of();
                });

        // when
        webhookController.handleWebhookEvent("delivery-4", "pull_request", payload);

        // then
        assertThat(capturedUserIds).containsExactly(1L, 2L);
        assertThat(TenantContext.getCurrentUserId()).isNull();
    }
}
