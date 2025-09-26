package greensnaback0229.pr_review_server.feature.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Resolved Feature DTO
 * Feature Registry(정적)와 Feature Memory(동적)를 조합한 결과
 */
@Getter
@Builder
public class ResolvedFeature {
    /**
     * 기능 정의 (Registry에서 조회)
     */
    private FeatureDefinition definition;
    
    /**
     * 기능 메모리 (Memory에서 조회)
     * 없을 수 있음 (null 가능)
     */
    private FeatureMemory memory;
}
