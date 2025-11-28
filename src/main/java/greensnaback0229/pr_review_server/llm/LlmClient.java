package greensnaback0229.pr_review_server.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Model;
import greensnaback0229.pr_review_server.llm.dto.ReviewResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM Client
 * Claude API를 호출하여 코드 리뷰를 수행
 */
@Slf4j
@Component
public class LlmClient {
    
    private final AnthropicClient client;
    
    public LlmClient(@Value("${anthropic.api.key}") String apiKey) {
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }

    /**
     * 리뷰 시작 (1차 요청)
     * 
     * @param systemPrompt 시스템 프롬프트
     * @param userMessage 사용자 메시지
     * @return ReviewResponse
     */
    public ReviewResponse startReview(String systemPrompt, String userMessage) {
        List<MessageParam> messages = new ArrayList<>();
        messages.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(userMessage)
                .build());
        
        return sendRequest(systemPrompt, messages);
    }

    /**
     * 리뷰 계속하기 (2차+ 요청)
     * 
     * @param systemPrompt 시스템 프롬프트
     * @param conversationHistory 대화 내역
     * @param additionalContext 추가 컨텍스트
     * @return ReviewResponse
     */
    public ReviewResponse continueReview(
            String systemPrompt,
            List<MessageParam> conversationHistory,
            String additionalContext
    ) {
        List<MessageParam> messages = new ArrayList<>(conversationHistory);
        messages.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(additionalContext)
                .build());
        
        return sendRequest(systemPrompt, messages);
    }

    /**
     * Claude API 요청 전송
     * 
     * @param systemPrompt 시스템 프롬프트
     * @param messages 메시지 리스트
     * @return ReviewResponse
     */
    private ReviewResponse sendRequest(String systemPrompt, List<MessageParam> messages) {
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(Model.CLAUDE_SONNET_4_20250514)
                    .maxTokens(4000L)
                    .system(systemPrompt)
                    .messages(messages)
                    .build();
            
            Message response = client.messages().create(params);
            
            // 응답 파싱
            String content = extractContent(response);
            
            // TODO: 응답에서 needMoreContext, requestedFiles, reason 파싱
            return ReviewResponse.builder()
                    .review(content)
                    .needMoreContext(false)
                    .requestedFiles(List.of())
                    .reason(null)
                    .build();
            
        } catch (Exception e) {
            log.error("LLM request failed", e);
            throw new RuntimeException("Failed to get review from LLM", e);
        }
    }

    /**
     * Message에서 텍스트 컨텐츠 추출
     * 
     * @param message Claude API 응답
     * @return 텍스트 컨텐츠
     */
    private String extractContent(Message message) {
        return message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .findFirst()
                .orElse("");
    }
}
