package greensnaback0229.pr_review_server.llm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM이 제안하는 Feature Memory 업데이트 내용
 * 리뷰를 통해 학습한 내용을 구조화
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemorySuggestion {
    /**
     * 이번 리뷰의 한 줄 요약
     * 예: "할인 로직 추가 및 검증 강화"
     */
    private String summary;
    
    /**
     * 핵심 학습 내용 (LLM이 자유롭게 작성)
     * 예: ["MoneyUtils와 결합도 높음", "엣지케이스 처리 필요"]
     */
    @Builder.Default
    private List<String> keyPoints = new ArrayList<>();
    
    /**
     * 관련된 파일들
     * 이번 변경과 자주 함께 수정되는 파일들
     */
    @Builder.Default
    private List<String> relatedFiles = new ArrayList<>();
}
