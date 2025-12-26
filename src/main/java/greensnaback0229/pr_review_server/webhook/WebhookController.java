package greensnaback0229.pr_review_server.webhook;

import greensnaback0229.pr_review_server.webhook.dto.WebhookPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * GitHub Webhook 이벤트를 수신하는 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {
    
    private final PrReviewService prReviewService;
    
    /**
     * GitHub PR 이벤트 Webhook 엔드포인트
     *
     * @param payload GitHub webhook payload
     * @return 처리 결과
     */
    @PostMapping("/github/pr")
    public ResponseEntity<String> handlePullRequestEvent(@RequestBody WebhookPayload payload) {
        try {
            String action = payload.getAction();
            log.info("Received PR webhook event: action={}", action);
            
            // opened, synchronize 이벤트만 처리
            if (!isReviewableAction(action)) {
                log.info("Ignoring action: {}", action);
                return ResponseEntity.ok("Ignored action: " + action);
            }
            
            // PR 정보 추출
            WebhookPayload.PullRequest pr = payload.getPullRequest();
            WebhookPayload.Repository repo = payload.getRepository();
            
            String repoFullName = repo.getFullName();
            int prNumber = pr.getNumber();
            String prTitle = pr.getTitle();
            String prBody = pr.getBody();
            String baseBranch = pr.getBase().getRef();
            
            log.info("Processing PR: {}/#{} - {}", repoFullName, prNumber, prTitle);
            
            // 리뷰 수행
            String review = prReviewService.reviewPullRequest(
                    repoFullName, prNumber, prTitle, prBody, baseBranch);
            
            // TODO: GitHub에 코멘트 등록
            // 현재는 로그만 출력
            log.info("Review completed for {}/#{}", repoFullName, prNumber);
            log.debug("Review result:\n{}", review);
            
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
