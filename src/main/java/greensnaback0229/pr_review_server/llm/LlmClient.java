package greensnaback0229.pr_review_server.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Model;
import com.fasterxml.jackson.databind.ObjectMapper;
import greensnaback0229.pr_review_server.llm.dto.ReviewResponse;
import greensnaback0229.pr_review_server.llm.dto.MemorySuggestion;
import greensnaback0229.pr_review_server.llm.dto.InlineComment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM Client
 * Claude API를 호출하여 코드 리뷰를 수행
 */
@Slf4j
@Component
public class LlmClient {
    
    private final AnthropicClient client;
    private final ObjectMapper objectMapper;
    
    public LlmClient(@Value("${anthropic.api.key}") String apiKey, ObjectMapper objectMapper) {
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
        this.objectMapper = objectMapper;
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
            log.info("LLM Response: {}", content);
            
            // JSON 추출 및 파싱
            return parseResponse(content);
            
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

    /**
     * LLM 응답에서 JSON을 추출하고 파싱
     * 
     * @param content LLM 응답 텍스트
     * @return ReviewResponse
     */
    private ReviewResponse parseResponse(String content) {
        try {
            // JSON 블록 추출 (```json ... ``` 형식)
            Pattern jsonPattern = Pattern.compile("```json\\s*\\n(.*?)\\n```", Pattern.DOTALL);
            Matcher matcher = jsonPattern.matcher(content);
            
            if (matcher.find()) {
                String jsonStr = matcher.group(1).trim();
                log.info("Extracted JSON: {}", jsonStr);
                
                // JSON 파싱
                Map<String, Object> jsonMap = objectMapper.readValue(jsonStr, Map.class);
                
                boolean needMoreContext = (Boolean) jsonMap.getOrDefault("needMoreContext", false);
                
                if (needMoreContext) {
                    // 추가 파일 요청
                    List<String> requestedFiles = (List<String>) jsonMap.get("requestedFiles");
                    String reason = (String) jsonMap.get("reason");
                    
                    return ReviewResponse.builder()
                            .needMoreContext(true)
                            .requestedFiles(requestedFiles != null ? requestedFiles : List.of())
                            .reason(reason)
                            .build();
                } else {
                    // 최종 리뷰
                    String generalReview = (String) jsonMap.get("generalReview");
                    
                    // Inline Comments 파싱
                    List<InlineComment> inlineComments = new ArrayList<>();
                    List<Map<String, Object>> inlineCommentsMap = (List<Map<String, Object>>) jsonMap.get("inlineComments");
                    if (inlineCommentsMap != null) {
                        for (Map<String, Object> commentMap : inlineCommentsMap) {
                            InlineComment comment = InlineComment.builder()
                                    .path((String) commentMap.get("path"))
                                    .line(((Number) commentMap.get("line")).intValue())
                                    .body((String) commentMap.get("body"))
                                    .build();
                            inlineComments.add(comment);
                        }
                    }
                    
                    // Memory Suggestion 파싱
                    Map<String, Object> memorySuggestionMap = (Map<String, Object>) jsonMap.get("memorySuggestion");
                    MemorySuggestion memorySuggestion = null;
                    if (memorySuggestionMap != null) {
                        memorySuggestion = MemorySuggestion.builder()
                                .summary((String) memorySuggestionMap.get("summary"))
                                .keyPoints((List<String>) memorySuggestionMap.get("keyPoints"))
                                .relatedFiles((List<String>) memorySuggestionMap.get("relatedFiles"))
                                .build();
                    }
                    
                    return ReviewResponse.builder()
                            .generalReview(generalReview != null ? generalReview : content)
                            .inlineComments(inlineComments)
                            .needMoreContext(false)
                            .memorySuggestion(memorySuggestion)
                            .build();
                }
            } else {
                // JSON이 없는 경우 전체 텍스트를 리뷰로 사용
                log.warn("No JSON block found in LLM response, using entire content as review");
                return ReviewResponse.builder()
                        .generalReview(content)
                        .inlineComments(List.of())
                        .needMoreContext(false)
                        .build();
            }
        } catch (Exception e) {
            log.error("Failed to parse LLM response", e);
            // 파싱 실패 시 전체 텍스트를 리뷰로 사용
            return ReviewResponse.builder()
                    .generalReview(content)
                    .inlineComments(List.of())
                    .needMoreContext(false)
                    .build();
        }
    }
}
