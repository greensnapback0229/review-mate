package greensnaback0229.pr_review_server.llm.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * LLM 리뷰 응답 DTO
 */
@Getter
@Builder
public class ReviewResponse {
    /**
     * 전반적인 리뷰 내용 (PR 전체 코멘트용)
     */
    private String generalReview;
    
    /**
     * 특정 라인에 대한 코멘트 목록
     */
    private List<InlineComment> inlineComments;
    
    /**
     * 추가 컨텍스트 필요 여부
     */
    private boolean needMoreContext;
    
    /**
     * 요청하는 파일 목록
     */
    private List<String> requestedFiles;
    
    /**
     * 파일 요청 이유
     */
    private String reason;
    
    /**
     * Feature Memory 업데이트 제안 (nullable)
     * LLM이 리뷰를 통해 학습한 내용을 구조화하여 제안
     */
    private MemorySuggestion memorySuggestion;
}
