package greensnaback0229.pr_review_server.feature.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Feature Memory DTO
 * 기능에 대한 누적 지식을 저장
 */
@Getter
@Builder
public class FeatureMemory {
    /**
     * 기능 식별자 (예: PAYMENT)
     */
    private String feature;
    
    /**
     * 기능에 대한 전체 요약
     * LLM이 생성한 누적 지식
     */
    private String summary;
    
    /**
     * 핵심 포인트 리스트
     */
    private List<String> keyPoints;
    
    /**
     * 자주 참조되는 연관 파일들
     */
    private List<String> relatedFiles;
    
    /**
     * 마지막 업데이트 시간
     */
    private LocalDateTime updatedAt;
}
