package greensnaback0229.pr_review_server.llm.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 댓글 응답 + 토큰 사용량 래퍼
 */
@Getter
@AllArgsConstructor
public class LlmCommentResponse {

    private final String content;
    private final int inputTokens;
    private final int outputTokens;
}
