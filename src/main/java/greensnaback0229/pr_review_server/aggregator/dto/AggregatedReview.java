package greensnaback0229.pr_review_server.aggregator.dto;

import greensnaback0229.pr_review_server.feature.dto.FeatureMemory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 집계된 리뷰 결과
 * 기능별 리뷰를 병합하고 Feature Memory를 업데이트한 최종 결과
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedReview {
    /**
     * 리뷰 대상 기능 이름
     */
    private String feature;
    
    /**
     * 병합된 리뷰 내용
     */
    private String review;
    
    /**
     * 리뷰 수행 시각
     */
    private LocalDateTime reviewedAt;
    
    /**
     * 업데이트된 Feature Memory (nullable)
     * LLM이 학습한 새로운 지식이 있을 경우에만 포함
     */
    private FeatureMemory updatedMemory;
}
