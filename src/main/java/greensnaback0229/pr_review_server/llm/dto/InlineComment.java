package greensnaback0229.pr_review_server.llm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inline Comment
 * 특정 파일의 특정 라인에 대한 코멘트
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InlineComment {
    
    /**
     * 파일 경로 (예: "src/main/java/Payment.java")
     */
    private String path;
    
    /**
     * 코멘트를 달 라인 번호
     */
    private Integer line;
    
    /**
     * 코멘트 내용
     */
    private String body;
}
