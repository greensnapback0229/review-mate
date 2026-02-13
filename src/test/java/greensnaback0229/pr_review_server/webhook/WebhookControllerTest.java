package greensnaback0229.pr_review_server.webhook;

import greensnaback0229.pr_review_server.aggregator.dto.AggregatedReview;
import greensnaback0229.pr_review_server.auth.ApiKeyService;
import greensnaback0229.pr_review_server.comment.CommentResponseService;
import greensnaback0229.pr_review_server.comment.ReviewContextService;
import greensnaback0229.pr_review_server.comment.entity.ReviewContext;
import greensnaback0229.pr_review_server.github.GitHubReviewClient;
import greensnaback0229.pr_review_server.installation.InstallationHandler;
import greensnaback0229.pr_review_server.tenant.TenantContext;
import greensnaback0229.pr_review_server.tenant.UserRepositoryService;
import greensnaback0229.pr_review_server.webhook.dto.WebhookPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookController 테스트")
class WebhookControllerTest {

    private static final Long TEST_REPOSITORY_ID = 123L;
    private static final String TEST_REPO_FULL_NAME = "owner/repo";
    private static final int TEST_PR_NUMBER = 1;
    private static final String TEST_DELIVERY_ID = "test-delivery-id";

    @Mock
    private PrReviewService prReviewService;

    @Mock
    private GitHubReviewClient gitHubReviewClient;

    @Mock
    private ReviewContextService reviewContextService;

    @Mock
    private CommentResponseService commentResponseService;

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private UserRepositoryService userRepositoryService;

    @Mock
    private InstallationHandler installationHandler;

    @InjectMocks
    private WebhookController webhookController;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private WebhookPayload.PullRequest testPullRequest;
    private WebhookPayload.Repository testRepository;

    @BeforeEach
    void setUp() {
        testPullRequest = WebhookPayload.PullRequest.builder()
                .number(TEST_PR_NUMBER)
                .title("Test PR")
                .body("Test PR body")
                .base(WebhookPayload.Branch.builder().ref("main").build())
                .head(WebhookPayload.Branch.builder().ref("feature").sha("abc123").build())
                .build();

        testRepository = WebhookPayload.Repository.builder()
                .id(TEST_REPOSITORY_ID)
                .fullName(TEST_REPO_FULL_NAME)
                .build();
    }

    @Test
    @DisplayName("handleWebhookEvent_PR_opened_리뷰수행")
    void handleWebhookEvent_PR_opened_리뷰수행() throws Exception {
        // given
        WebhookPayload payload = WebhookPayload.builder()
                .action("opened")
                .pullRequest(testPullRequest)
                .repository(testRepository)
                .build();

        AggregatedReview mockReview = AggregatedReview.builder()
                .feature("TEST_FEATURE")
                .review("Test review content")
                .build();

        when(userRepositoryService.findActiveUserIdsByRepositoryId(TEST_REPOSITORY_ID))
                .thenReturn(List.of(1L));
        when(apiKeyService.getDecryptedApiKey(1L))
                .thenReturn("sk-ant-api03-test-key");
        when(prReviewService.reviewPullRequest(
                eq("sk-ant-api03-test-key"),
                eq(TEST_REPOSITORY_ID),
                eq(TEST_REPO_FULL_NAME),
                eq(TEST_PR_NUMBER),
                eq("Test PR"),
                eq("Test PR body"),
                eq("main"),
                eq("feature"),
                eq("abc123")
        )).thenReturn(List.of(mockReview));

        // when
        ResponseEntity<String> response = webhookController.handleWebhookEvent(
                TEST_DELIVERY_ID,
                "pull_request",
                payload
        );

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("Review completed for PR #1");
        verify(userRepositoryService).findActiveUserIdsByRepositoryId(TEST_REPOSITORY_ID);
        verify(apiKeyService).getDecryptedApiKey(1L);
        verify(prReviewService).reviewPullRequest(
                eq("sk-ant-api03-test-key"),
                eq(TEST_REPOSITORY_ID),
                eq(TEST_REPO_FULL_NAME),
                eq(TEST_PR_NUMBER),
                eq("Test PR"),
                eq("Test PR body"),
                eq("main"),
                eq("feature"),
                eq("abc123")
        );
    }

    @Test
    @DisplayName("handleWebhookEvent_중복delivery무시")
    void handleWebhookEvent_중복delivery무시() {
        // given
        WebhookPayload payload = WebhookPayload.builder()
                .action("opened")
                .pullRequest(testPullRequest)
                .repository(testRepository)
                .build();

        // when - 첫 번째 호출
        ResponseEntity<String> firstResponse = webhookController.handleWebhookEvent(
                TEST_DELIVERY_ID,
                "pull_request",
                payload
        );

        // when - 동일한 deliveryId로 두 번째 호출
        ResponseEntity<String> secondResponse = webhookController.handleWebhookEvent(
                TEST_DELIVERY_ID,
                "pull_request",
                payload
        );

        // then
        assertThat(secondResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(secondResponse.getBody()).isEqualTo("Duplicate delivery ignored");
    }

    @Test
    @DisplayName("handleWebhookEvent_봇코멘트무시")
    void handleWebhookEvent_봇코멘트무시() {
        // given
        WebhookPayload payload = WebhookPayload.builder()
                .action("created")
                .pullRequest(testPullRequest)
                .repository(testRepository)
                .comment(WebhookPayload.Comment.builder()
                        .id(100L)
                        .body("Bot comment")
                        .user(WebhookPayload.User.builder()
                                .login("github-bot")
                                .type("Bot")
                                .build())
                        .inReplyToId(50L)
                        .path("src/Main.java")
                        .build())
                .build();

        // when
        ResponseEntity<String> response = webhookController.handleWebhookEvent(
                TEST_DELIVERY_ID,
                "pull_request_review_comment",
                payload
        );

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo("Bot comment ignored");
        verify(reviewContextService, never()).isBotComment(anyLong(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("handleWebhookEvent_비답글코멘트무시")
    void handleWebhookEvent_비답글코멘트무시() {
        // given
        WebhookPayload payload = WebhookPayload.builder()
                .action("created")
                .pullRequest(testPullRequest)
                .repository(testRepository)
                .comment(WebhookPayload.Comment.builder()
                        .id(100L)
                        .body("User comment")
                        .user(WebhookPayload.User.builder()
                                .login("developer")
                                .type("User")
                                .build())
                        .inReplyToId(null)  // 답글이 아님
                        .path("src/Main.java")
                        .build())
                .build();

        // when
        ResponseEntity<String> response = webhookController.handleWebhookEvent(
                TEST_DELIVERY_ID,
                "pull_request_review_comment",
                payload
        );

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo("Not a reply comment");
        verify(reviewContextService, never()).isBotComment(anyLong(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("handleWebhookEvent_봇답글감지_응답생성")
    void handleWebhookEvent_봇답글감지_응답생성() throws Exception {
        // given
        Long commentId = 500L;
        Long inReplyToId = 100L;
        String commentPath = "src/A.java";

        WebhookPayload payload = WebhookPayload.builder()
                .action("created")
                .pullRequest(testPullRequest)
                .repository(testRepository)
                .comment(WebhookPayload.Comment.builder()
                        .id(commentId)
                        .body("질문입니다")
                        .user(WebhookPayload.User.builder()
                                .login("developer")
                                .type("User")
                                .build())
                        .inReplyToId(inReplyToId)
                        .path(commentPath)
                        .build())
                .build();

        ReviewContext mockContext = ReviewContext.builder()
                .repositoryId(TEST_REPOSITORY_ID)
                .prNumber(TEST_PR_NUMBER)
                .featureName("TEST_FEATURE")
                .headSha("abc123")
                .fileContexts("[]")
                .botCommentIds("[]")
                .build();

        when(userRepositoryService.findActiveUserIdsByRepositoryId(TEST_REPOSITORY_ID))
                .thenReturn(List.of(1L));
        when(apiKeyService.getDecryptedApiKey(1L))
                .thenReturn("sk-ant-api03-test-key");
        when(reviewContextService.isBotComment(TEST_REPOSITORY_ID, TEST_PR_NUMBER, inReplyToId))
                .thenReturn(true);
        when(reviewContextService.countBotReplies(TEST_REPOSITORY_ID, TEST_PR_NUMBER))
                .thenReturn(3);
        when(commentResponseService.generateResponse(eq("sk-ant-api03-test-key"), eq(TEST_REPOSITORY_ID), eq(TEST_PR_NUMBER), eq("질문입니다")))
                .thenReturn(Optional.of("답변입니다"));
        when(gitHubReviewClient.replyToReviewComment(TEST_REPO_FULL_NAME, TEST_PR_NUMBER, commentId, "답변입니다"))
                .thenReturn(600L);
        when(reviewContextService.findByCommentPath(TEST_REPOSITORY_ID, TEST_PR_NUMBER, commentPath))
                .thenReturn(Optional.of(mockContext));

        // when
        ResponseEntity<String> response = webhookController.handleWebhookEvent(
                TEST_DELIVERY_ID,
                "pull_request_review_comment",
                payload
        );

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo("Reply processed");
        verify(userRepositoryService).findActiveUserIdsByRepositoryId(TEST_REPOSITORY_ID);
        verify(apiKeyService).getDecryptedApiKey(1L);
        verify(commentResponseService).generateResponse(eq("sk-ant-api03-test-key"), eq(TEST_REPOSITORY_ID), eq(TEST_PR_NUMBER), eq("질문입니다"));
        verify(gitHubReviewClient).replyToReviewComment(TEST_REPO_FULL_NAME, TEST_PR_NUMBER, commentId, "답변입니다");
        verify(reviewContextService).addBotCommentId(TEST_REPOSITORY_ID, TEST_PR_NUMBER, "TEST_FEATURE", 600L);
    }

    @Test
    @DisplayName("handleWebhookEvent_답글상한도달")
    void handleWebhookEvent_답글상한도달() throws IOException {
        // given
        WebhookPayload payload = WebhookPayload.builder()
                .action("created")
                .pullRequest(testPullRequest)
                .repository(testRepository)
                .comment(WebhookPayload.Comment.builder()
                        .id(500L)
                        .body("질문입니다")
                        .user(WebhookPayload.User.builder()
                                .login("developer")
                                .type("User")
                                .build())
                        .inReplyToId(100L)
                        .path("src/A.java")
                        .build())
                .build();

        when(userRepositoryService.findActiveUserIdsByRepositoryId(TEST_REPOSITORY_ID))
                .thenReturn(List.of(1L));
        when(reviewContextService.isBotComment(TEST_REPOSITORY_ID, TEST_PR_NUMBER, 100L))
                .thenReturn(true);
        when(reviewContextService.countBotReplies(TEST_REPOSITORY_ID, TEST_PR_NUMBER))
                .thenReturn(10);  // MAX_BOT_REPLIES_PER_PR = 10

        // when
        ResponseEntity<String> response = webhookController.handleWebhookEvent(
                TEST_DELIVERY_ID,
                "pull_request_review_comment",
                payload
        );

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo("Reply processed");
        verify(commentResponseService, never()).generateResponse(any(), anyLong(), anyInt(), anyString());
        verify(gitHubReviewClient, never()).replyToReviewComment(anyString(), anyInt(), anyLong(), anyString());
    }

    @Test
    @DisplayName("handleWebhookEvent_PR_API키미설정_코멘트작성")
    void handleWebhookEvent_PR_API키미설정_코멘트작성() throws Exception {
        // given
        WebhookPayload payload = WebhookPayload.builder()
                .action("opened")
                .pullRequest(testPullRequest)
                .repository(testRepository)
                .build();

        when(userRepositoryService.findActiveUserIdsByRepositoryId(TEST_REPOSITORY_ID))
                .thenReturn(List.of(1L));
        when(apiKeyService.getDecryptedApiKey(1L))
                .thenReturn(null);

        // when
        ResponseEntity<String> response = webhookController.handleWebhookEvent(
                TEST_DELIVERY_ID,
                "pull_request",
                payload
        );

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("Review completed for PR #1");
        verify(userRepositoryService).findActiveUserIdsByRepositoryId(TEST_REPOSITORY_ID);
        verify(apiKeyService).getDecryptedApiKey(1L);
        verify(prReviewService, never()).reviewPullRequest(any(), anyLong(), anyString(), anyInt(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("handleWebhookEvent_Comment_API키미설정_스킵")
    void handleWebhookEvent_Comment_API키미설정_스킵() throws Exception {
        // given
        WebhookPayload payload = WebhookPayload.builder()
                .action("created")
                .pullRequest(testPullRequest)
                .repository(testRepository)
                .comment(WebhookPayload.Comment.builder()
                        .id(500L)
                        .body("질문입니다")
                        .user(WebhookPayload.User.builder()
                                .login("developer")
                                .type("User")
                                .build())
                        .inReplyToId(100L)
                        .path("src/A.java")
                        .build())
                .build();

        when(userRepositoryService.findActiveUserIdsByRepositoryId(TEST_REPOSITORY_ID))
                .thenReturn(List.of(1L));
        when(reviewContextService.isBotComment(TEST_REPOSITORY_ID, TEST_PR_NUMBER, 100L))
                .thenReturn(true);
        when(reviewContextService.countBotReplies(TEST_REPOSITORY_ID, TEST_PR_NUMBER))
                .thenReturn(3);
        when(apiKeyService.getDecryptedApiKey(1L))
                .thenReturn(null);

        // when
        ResponseEntity<String> response = webhookController.handleWebhookEvent(
                TEST_DELIVERY_ID,
                "pull_request_review_comment",
                payload
        );

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo("Reply processed");
        verify(userRepositoryService).findActiveUserIdsByRepositoryId(TEST_REPOSITORY_ID);
        verify(apiKeyService).getDecryptedApiKey(1L);
        verify(commentResponseService, never()).generateResponse(any(), anyLong(), anyInt(), anyString());
        verify(gitHubReviewClient, never()).replyToReviewComment(anyString(), anyInt(), anyLong(), anyString());
    }
}
