package greensnaback0229.pr_review_server.webhook;

import greensnaback0229.pr_review_server.aggregator.dto.AggregatedReview;
import greensnaback0229.pr_review_server.auth.ApiKeyService;
import greensnaback0229.pr_review_server.comment.CommentResponseService;
import greensnaback0229.pr_review_server.comment.ReviewContextService;
import greensnaback0229.pr_review_server.github.GitHubReviewClient;
import greensnaback0229.pr_review_server.llm.dto.InlineComment;
import greensnaback0229.pr_review_server.tenant.TenantContext;
import greensnaback0229.pr_review_server.tenant.UserRepositoryService;
import greensnaback0229.pr_review_server.webhook.dto.WebhookPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GitHub Webhook 이벤트를 수신하는 컨트롤러
 * X-GitHub-Event 헤더로 이벤트 유형을 구분하여 라우팅
 */
@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private static final int MAX_BOT_REPLIES_PER_PR = 10;

    private final PrReviewService prReviewService;
    private final GitHubReviewClient gitHubReviewClient;
    private final ReviewContextService reviewContextService;
    private final CommentResponseService commentResponseService;
    private final ApiKeyService apiKeyService;
    private final UserRepositoryService userRepositoryService;

    /**
     * 처리된 webhook delivery ID를 추적하는 Set (중복 이벤트 방지)
     */
    private final Set<String> processedDeliveryIds = ConcurrentHashMap.newKeySet();

    /**
     * GitHub Webhook 통합 엔드포인트
     * X-GitHub-Event 헤더로 이벤트 유형을 구분하여 처리
     */
    @PostMapping("/github/pr")
    public ResponseEntity<String> handleWebhookEvent(
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestBody WebhookPayload payload) {

        // 중복 이벤트 체크
        if (deliveryId != null && !processedDeliveryIds.add(deliveryId)) {
            log.info("Duplicate webhook delivery ignored: {}", deliveryId);
            return ResponseEntity.ok("Duplicate delivery ignored");
        }

        log.info("Received webhook: event={}, action={}, deliveryId={}", eventType, payload.getAction(), deliveryId);

        // 이벤트 유형별 라우팅
        if ("pull_request".equals(eventType)) {
            return handlePullRequestEvent(payload);
        } else if ("pull_request_review_comment".equals(eventType)) {
            return handleReviewCommentEvent(payload);
        } else {
            log.info("Ignoring event type: {}", eventType);
            return ResponseEntity.ok("Ignored event: " + eventType);
        }
    }

    /**
     * PR 이벤트 처리 (opened, synchronize → 1차 리뷰)
     * N:N 다중 사용자 지원: repository에 연결된 모든 활성 사용자에 대해 각각 리뷰 수행
     */
    private ResponseEntity<String> handlePullRequestEvent(WebhookPayload payload) {
        try {
            String action = payload.getAction();

            if (!isReviewableAction(action)) {
                log.info("Ignoring PR action: {}", action);
                return ResponseEntity.ok("Ignored action: " + action);
            }

            WebhookPayload.PullRequest pr = payload.getPullRequest();
            WebhookPayload.Repository repo = payload.getRepository();

            Long repositoryId = repo.getId();
            String repoFullName = repo.getFullName();
            int prNumber = pr.getNumber();
            String prTitle = pr.getTitle();
            String prBody = pr.getBody();
            String baseBranch = pr.getBase().getRef();
            String headBranch = pr.getHead().getRef();
            String headSha = pr.getHead().getSha();

            log.info("Processing PR: {}/#{} - {} (repositoryId={})", repoFullName, prNumber, prTitle, repositoryId);

            // N:N: repository에 연결된 모든 활성 사용자 조회
            List<Long> activeUserIds = userRepositoryService.findActiveUserIdsByRepositoryId(repositoryId);
            if (activeUserIds.isEmpty()) {
                log.warn("No active users found for repository {} ({})", repoFullName, repositoryId);
                return ResponseEntity.ok("No active users for repository");
            }

            int reviewedCount = 0;
            for (Long userId : activeUserIds) {
                try {
                    TenantContext.setCurrentUserId(userId);

                    // 사용자별 API Key 확인
                    String apiKey = apiKeyService.getDecryptedApiKey(userId);
                    if (apiKey == null) {
                        log.warn("API Key not configured for userId={}, skipping review", userId);
                        continue;
                    }

                    // 리뷰 수행
                    List<AggregatedReview> reviews = prReviewService.reviewPullRequest(
                            apiKey, repositoryId, repoFullName, prNumber, prTitle, prBody, baseBranch, headBranch, headSha);

                    if (reviews.isEmpty()) {
                        log.warn("No reviews generated for {}/#{} (userId={})", repoFullName, prNumber, userId);
                        continue;
                    }

                    // GitHub에 리뷰 작성
                    try {
                        postReviews(repositoryId, repoFullName, prNumber, reviews);
                        reviewedCount++;
                        log.info("Reviews posted successfully for {}/#{} (userId={})", repoFullName, prNumber, userId);
                    } catch (Exception e) {
                        log.error("Failed to post reviews for userId={}: {}", userId, e.getMessage(), e);
                    }
                } finally {
                    TenantContext.clear();
                }
            }

            return ResponseEntity.ok("Review completed for PR #" + prNumber + " (" + reviewedCount + " users)");

        } catch (Exception e) {
            log.error("Failed to process PR webhook: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Failed to process webhook: " + e.getMessage());
        }
    }

    /**
     * 리뷰 코멘트 이벤트 처리 (pull_request_review_comment created → 봇 답글 감지 → 대화형 응답)
     * N:N 다중 사용자 지원: 각 사용자의 봇 코멘트에 대해 해당 사용자 컨텍스트로 응답
     */
    private ResponseEntity<String> handleReviewCommentEvent(WebhookPayload payload) {
        try {
            if (!"created".equals(payload.getAction())) {
                log.info("Ignoring review comment action: {}", payload.getAction());
                return ResponseEntity.ok("Ignored action: " + payload.getAction());
            }

            WebhookPayload.Comment comment = payload.getComment();
            WebhookPayload.Repository repo = payload.getRepository();
            WebhookPayload.PullRequest pr = payload.getPullRequest();

            if (comment == null || repo == null || pr == null) {
                log.warn("Missing required payload fields");
                return ResponseEntity.ok("Missing payload fields");
            }

            Long repositoryId = repo.getId();
            String repoFullName = repo.getFullName();
            int prNumber = pr.getNumber();

            // 1. 봇이 작성한 코멘트는 무시 (무한 루프 방지)
            if (comment.getUser() != null && "Bot".equalsIgnoreCase(comment.getUser().getType())) {
                log.info("Ignoring bot comment from: {}", comment.getUser().getLogin());
                return ResponseEntity.ok("Bot comment ignored");
            }

            // 2. in_reply_to_id 확인 (스레드 답글이 아니면 무시)
            Long inReplyToId = comment.getInReplyToId();
            if (inReplyToId == null) {
                log.info("Ignoring non-reply comment (no in_reply_to_id)");
                return ResponseEntity.ok("Not a reply comment");
            }

            // N:N: repository에 연결된 모든 활성 사용자에 대해 봇 답글 감지
            List<Long> activeUserIds = userRepositoryService.findActiveUserIdsByRepositoryId(repositoryId);

            for (Long userId : activeUserIds) {
                try {
                    TenantContext.setCurrentUserId(userId);

                    // 3. bot_comment_ids 기반 봇 답글 감지 (사용자별)
                    boolean isBotReply = reviewContextService.isBotComment(repositoryId, prNumber, inReplyToId);
                    if (!isBotReply) {
                        continue;
                    }

                    // 4. 다중 턴 상한 체크
                    int botReplyCount = reviewContextService.countBotReplies(repositoryId, prNumber);
                    if (botReplyCount >= MAX_BOT_REPLIES_PER_PR) {
                        log.info("Bot reply limit reached ({}/{}) for {}/#{} (userId={})",
                                botReplyCount, MAX_BOT_REPLIES_PER_PR, repoFullName, prNumber, userId);
                        continue;
                    }

                    // 5. API Key 확인
                    String apiKey = apiKeyService.getDecryptedApiKey(userId);
                    if (apiKey == null) {
                        log.warn("API Key not configured for userId={}", userId);
                        continue;
                    }

                    log.info("Processing bot reply: {}/#{} commentId={}, in_reply_to={}, path={}, userId={}",
                            repoFullName, prNumber, comment.getId(), inReplyToId, comment.getPath(), userId);

                    // 6. 댓글 파일 경로로 해당 Feature 컨텍스트 조회 + 응답 생성
                    Optional<String> responseOpt = commentResponseService.generateResponse(
                            apiKey, repositoryId, prNumber, comment.getBody());

                    if (responseOpt.isEmpty()) {
                        log.warn("No response generated for comment on {}/#{} (userId={})", repoFullName, prNumber, userId);
                        continue;
                    }

                    // 7. GitHub 스레드에 답글 게시
                    String responseText = responseOpt.get();
                    try {
                        long newCommentId = gitHubReviewClient.replyToReviewComment(
                                repoFullName, prNumber, comment.getId(), responseText);

                        // 8. 새 봇 코멘트 ID를 review_context에 추가 (다중 턴 지원)
                        Optional<greensnaback0229.pr_review_server.comment.entity.ReviewContext> contextOpt =
                                reviewContextService.findByCommentPath(repositoryId, prNumber, comment.getPath());
                        if (contextOpt.isPresent()) {
                            reviewContextService.addBotCommentId(
                                    repositoryId, prNumber, contextOpt.get().getFeatureName(), newCommentId);
                        }

                        log.info("Successfully posted reply (newId={}) to {}/#{} (userId={})",
                                newCommentId, repoFullName, prNumber, userId);
                    } catch (Exception e) {
                        log.error("Failed to post reply to GitHub for userId={}: {}", userId, e.getMessage(), e);
                    }
                } finally {
                    TenantContext.clear();
                }
            }

            return ResponseEntity.ok("Reply processed");

        } catch (Exception e) {
            log.error("Failed to process review comment webhook: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Failed to process webhook: " + e.getMessage());
        }
    }

    /**
     * 리뷰 결과를 GitHub에 작성
     */
    private void postReviews(Long repositoryId, String repoFullName, int prNumber, List<AggregatedReview> reviews) {
        StringBuilder generalReview = new StringBuilder();
        generalReview.append("# 🔍 전체 리뷰 결과\n\n");

        List<InlineComment> allInlineComments = new ArrayList<>();

        for (AggregatedReview review : reviews) {
            generalReview.append("## ").append(review.getFeature()).append(" 기능\n\n");
            if (review.getReview() != null && !review.getReview().isEmpty()) {
                generalReview.append(review.getReview()).append("\n\n");
            }
            generalReview.append("---\n\n");

            if (review.getInlineComments() != null && !review.getInlineComments().isEmpty()) {
                allInlineComments.addAll(review.getInlineComments());
            }
        }

        try {
            List<Long> commentIds;
            if (allInlineComments.isEmpty()) {
                commentIds = gitHubReviewClient.createSimpleComment(repoFullName, prNumber, generalReview.toString());
                log.info("Posted review as simple comment for {}/#{}", repoFullName, prNumber);
            } else {
                commentIds = gitHubReviewClient.createReview(repoFullName, prNumber, generalReview.toString(), allInlineComments);
                log.info("Posted review with {} inline comments for {}/#{}",
                        allInlineComments.size(), repoFullName, prNumber);
            }

            // 봇 코멘트 ID를 review_context에 저장
            if (commentIds != null && !commentIds.isEmpty()) {
                try {
                    reviewContextService.updateBotCommentIds(repositoryId, prNumber, commentIds);
                } catch (Exception e) {
                    log.warn("Failed to update bot comment IDs: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to post review to GitHub: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to post review to GitHub", e);
        }
    }

    private boolean isReviewableAction(String action) {
        return "opened".equals(action) || "synchronize".equals(action);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("PR Review Server is running");
    }
}
