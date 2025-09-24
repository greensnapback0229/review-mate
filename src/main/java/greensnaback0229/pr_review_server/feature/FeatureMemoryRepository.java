package greensnaback0229.pr_review_server.feature;

import greensnaback0229.pr_review_server.feature.dto.FeatureMemory;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature Memory Repository
 * 기능별 메모리를 저장하고 조회하는 저장소
 * 현재는 In-Memory 방식으로 구현 (추후 DB로 확장 가능)
 */
@Repository
public class FeatureMemoryRepository {
    
    private final Map<String, FeatureMemory> memoryStore = new ConcurrentHashMap<>();

    /**
     * 기능 메모리 저장
     * 
     * @param memory 저장할 FeatureMemory
     */
    public void save(FeatureMemory memory) {
        memoryStore.put(memory.getFeature(), memory);
    }

    /**
     * 기능명으로 메모리 조회
     * 
     * @param feature 기능 식별자
     * @return FeatureMemory Optional
     */
    public Optional<FeatureMemory> findByFeature(String feature) {
        return Optional.ofNullable(memoryStore.get(feature));
    }

    /**
     * 기능 메모리 존재 여부 확인
     * 
     * @param feature 기능 식별자
     * @return 존재 여부
     */
    public boolean exists(String feature) {
        return memoryStore.containsKey(feature);
    }

    /**
     * 기능 메모리 삭제
     * 
     * @param feature 기능 식별자
     */
    public void delete(String feature) {
        memoryStore.remove(feature);
    }

    /**
     * 모든 메모리 삭제
     */
    public void clear() {
        memoryStore.clear();
    }
}
