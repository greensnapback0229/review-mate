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
     * 리뷰 내용
     */
    private String review;
    
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
}
