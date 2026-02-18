package greensnaback0229.pr_review_server.comment;

import greensnaback0229.pr_review_server.comment.entity.ReviewContext;
import greensnaback0229.pr_review_server.llm.LlmClient;
import greensnaback0229.pr_review_server.llm.dto.LlmCommentResponse;
import greensnaback0229.pr_review_server.prompt.PromptBuilder;
import greensnaback0229.pr_review_server.tenant.TenantContext;
import greensnaback0229.pr_review_server.usage.UsageService;
import greensnaback0229.pr_review_server.usage.entity.ReviewType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * PR 코멘트에 대한 응답을 생성하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentResponseService {

    private final ReviewContextService reviewContextService;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final UsageService usageService;

    /**
     * PR 코멘트에 대한 응답 생성
     *
     * @param apiKey Anthropic API 키
     * @param repositoryId 저장소 ID
     * @param prNumber PR 번호
     * @param commentBody 코멘트 내용
     * @return 생성된 응답 (없으면 empty)
     */
    public Optional<String> generateResponse(String apiKey, Long repositoryId, int prNumber, String commentBody) {
        try {
            log.info("Generating response for comment on PR {}/#{}", repositoryId, prNumber);

            // 1. 관련 리뷰 컨텍스트 조회
            List<ReviewContext> contexts = reviewContextService.findByRepositoryIdAndPrNumber(repositoryId, prNumber);

            if (contexts.isEmpty()) {
                log.warn("No review context found for PR {}/#{}", repositoryId, prNumber);
                return Optional.empty();
            }

            // 2. 프롬프트 생성
            String systemPrompt = promptBuilder.buildCommentResponseSystemPrompt();
            String userPrompt = promptBuilder.buildCommentResponsePrompt(commentBody, contexts);

            log.debug("System prompt length: {}", systemPrompt.length());
            log.debug("User prompt length: {}", userPrompt.length());

            // 3. LLM 호출
            LlmCommentResponse llmResponse = llmClient.generateCommentResponse(apiKey, systemPrompt, userPrompt);

            if (llmResponse == null || llmResponse.getContent() == null || llmResponse.getContent().trim().isEmpty()) {
                log.warn("LLM returned empty response");
                return Optional.empty();
            }

            // 사용량 기록
            try {
                Long userId = TenantContext.getCurrentUserId();
                if (userId != null) {
                    usageService.recordUsage(userId, repositoryId, prNumber, null,
                            llmResponse.getInputTokens(), llmResponse.getOutputTokens(),
                            ReviewType.COMMENT_REPLY);
                }
            } catch (Exception e) {
                log.warn("Failed to record comment reply usage: {}", e.getMessage());
            }

            log.info("Generated response with length: {}, tokens=({}/{})",
                    llmResponse.getContent().length(), llmResponse.getInputTokens(), llmResponse.getOutputTokens());
            return Optional.of(llmResponse.getContent());

        } catch (Exception e) {
            log.error("Failed to generate comment response: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
}
