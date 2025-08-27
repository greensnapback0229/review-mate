package greensnaback0229.pr_review_server.feature.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 기능 정의 DTO
 * Feature Registry에서 각 기능의 메타데이터를 표현
 */
@Getter
@Builder
public class FeatureDefinition {
    /**
     * 기능 식별자 (예: PAYMENT, AUTH)
     */
    private String name;
    
    /**
     * 기능 설명
     */
    private String description;
    
    /**
     * 기능 관련 디렉토리 경로들
     * 변경된 파일 필터링에 사용
     */
    private List<String> paths;
    
    /**
     * 핵심 파일들
     * 변경 여부와 무관하게 LLM이 참조해야 할 파일
     */
    private List<String> coreFiles;
}
