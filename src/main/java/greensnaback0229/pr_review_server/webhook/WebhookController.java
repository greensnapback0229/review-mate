package greensnaback0229.pr_review_server.webhook;

import greensnaback0229.pr_review_server.webhook.dto.WebhookPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GitHub Webhook 이벤트를 수신하는 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {
    
    private final PrReviewService prReviewService;
    private final greensnaback0229.pr_review_server.github.GitHubCommentService gitHubCommentService;
    
    /**
     * 처리된 webhook delivery ID를 추적하는 Set (중복 이벤트 방지)
     * X-GitHub-Delivery 헤더 값을 저장하여 동일한 이벤트가 재전송되는 것을 방지
     */
    private final Set<String> processedDeliveryIds = ConcurrentHashMap.newKeySet();
    
    /**
     * GitHub PR 이벤트 Webhook 엔드포인트
     *
     * @param deliveryId GitHub webhook delivery ID (X-GitHub-Delivery 헤더)
     * @param payload GitHub webhook payload
     * @return 처리 결과
     */
    @PostMapping("/github/pr")
    public ResponseEntity<String> handlePullRequestEvent(
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestBody WebhookPayload payload) {
        try {
            // 중복 이벤트 체크
            if (deliveryId != null && !processedDeliveryIds.add(deliveryId)) {
                log.info("Duplicate webhook delivery ignored: {}", deliveryId);
                return ResponseEntity.ok("Duplicate delivery ignored");
            }
            
            String action = payload.getAction();
            log.info("Received PR webhook event: action={}, deliveryId={}", action, deliveryId);
            
            // opened, synchronize 이벤트만 처리
            if (!isReviewableAction(action)) {
                log.info("Ignoring action: {}", action);
                return ResponseEntity.ok("Ignored action: " + action);
            }
            
            // PR 정보 추출
            WebhookPayload.PullRequest pr = payload.getPullRequest();
            WebhookPayload.Repository repo = payload.getRepository();
            
            Long repositoryId = repo.getId();
            String repoFullName = repo.getFullName();
            int prNumber = pr.getNumber();
            String prTitle = pr.getTitle();
            String prBody = pr.getBody();
            String baseBranch = pr.getBase().getRef();
            String headBranch = pr.getHead().getRef();
            
            log.info("Processing PR: {}/#{} - {} (repositoryId={})", repoFullName, prNumber, prTitle, repositoryId);
            
            // 리뷰 수행
            String review = prReviewService.reviewPullRequest(
                    repositoryId, repoFullName, prNumber, prTitle, prBody, baseBranch, headBranch);
            
            // GitHub에 코멘트 작성
            try {
                gitHubCommentService.postReviewComment(repoFullName, prNumber, review);
                log.info("Review comment posted successfully for {}/#{}", repoFullName, prNumber);
            } catch (Exception e) {
                log.error("Failed to post comment, but review completed: {}", e.getMessage());
                // 코멘트 작성 실패해도 리뷰는 완료된 것으로 처리
            }
            
            log.info("Review completed for {}/#{}", repoFullName, prNumber);
            
            return ResponseEntity.ok("Review completed for PR #" + prNumber);
            
        } catch (Exception e) {
            log.error("Failed to process webhook: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Failed to process webhook: " + e.getMessage());
        }
    }
    
    /**
     * 리뷰 가능한 액션인지 확인
     *
     * @param action PR 액션
     * @return 리뷰 가능 여부
     */
    private boolean isReviewableAction(String action) {
        return "opened".equals(action) || "synchronize".equals(action);
    }
    
    /**
     * Health check 엔드포인트
     *
     * @return 상태 메시지
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("PR Review Server is running");
    }
}
