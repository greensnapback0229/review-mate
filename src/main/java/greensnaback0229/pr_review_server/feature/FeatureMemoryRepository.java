package greensnaback0229.pr_review_server.feature;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import greensnaback0229.pr_review_server.feature.dto.FeatureMemory;
import greensnaback0229.pr_review_server.feature.repository.FeatureMemoryJpaRepository;
import greensnaback0229.pr_review_server.feature.repository.RepositoryJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Feature Memory Repository
 * 기능별 메모리를 저장하고 조회하는 저장소
 * RDB 기반으로 구현
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class FeatureMemoryRepository {
    
    private final FeatureMemoryJpaRepository featureMemoryJpaRepository;
    private final RepositoryJpaRepository repositoryJpaRepository;
    private final ObjectMapper objectMapper;

    /**
     * 기능 메모리 저장
     * 
     * @param repositoryId GitHub Repository ID
     * @param memory 저장할 FeatureMemory
     */
    @Transactional
    public void save(Long repositoryId, FeatureMemory memory) {
        try {
            // Repository Entity가 없으면 생성
            if (!repositoryJpaRepository.existsById(repositoryId)) {
                greensnaback0229.pr_review_server.feature.entity.Repository repo = 
                    greensnaback0229.pr_review_server.feature.entity.Repository.builder()
                        .repositoryId(repositoryId)
                        .build();
                repositoryJpaRepository.save(repo);
                log.info("Created new repository: {}", repositoryId);
            }
            
            // DTO를 JSON으로 변환
            String jsonContent = objectMapper.writeValueAsString(memory);
            
            // 기존 메모리가 있으면 업데이트, 없으면 생성
            Optional<greensnaback0229.pr_review_server.feature.entity.FeatureMemory> existingMemory = 
                featureMemoryJpaRepository.findByRepositoryIdAndFeatureName(repositoryId, memory.getFeature());
            
            if (existingMemory.isPresent()) {
                // 업데이트 (새로운 Entity 생성 후 저장)
                greensnaback0229.pr_review_server.feature.entity.FeatureMemory updated = 
                    greensnaback0229.pr_review_server.feature.entity.FeatureMemory.builder()
                        .featureMemoryId(existingMemory.get().getFeatureMemoryId())
                        .repositoryId(repositoryId)
                        .featureName(memory.getFeature())
                        .featureMemoryContent(jsonContent)
                        .build();
                featureMemoryJpaRepository.save(updated);
                log.info("Updated feature memory: repositoryId={}, feature={}", repositoryId, memory.getFeature());
            } else {
                // 생성
                greensnaback0229.pr_review_server.feature.entity.FeatureMemory newMemory = 
                    greensnaback0229.pr_review_server.feature.entity.FeatureMemory.builder()
                        .repositoryId(repositoryId)
                        .featureName(memory.getFeature())
                        .featureMemoryContent(jsonContent)
                        .build();
                featureMemoryJpaRepository.save(newMemory);
                log.info("Created new feature memory: repositoryId={}, feature={}", repositoryId, memory.getFeature());
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize FeatureMemory to JSON: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save feature memory", e);
        }
    }

    /**
     * 기능명으로 메모리 조회
     * 
     * @param repositoryId GitHub Repository ID
     * @param feature 기능 식별자
     * @return FeatureMemory Optional
     */
    public Optional<FeatureMemory> findByFeature(Long repositoryId, String feature) {
        return featureMemoryJpaRepository.findByRepositoryIdAndFeatureName(repositoryId, feature)
            .map(entity -> {
                try {
                    return objectMapper.readValue(entity.getFeatureMemoryContent(), FeatureMemory.class);
                } catch (JsonProcessingException e) {
                    log.error("Failed to deserialize FeatureMemory from JSON: {}", e.getMessage(), e);
                    return null;
                }
            });
    }

    /**
     * 기능 메모리 존재 여부 확인
     * 
     * @param repositoryId GitHub Repository ID
     * @param feature 기능 식별자
     * @return 존재 여부
     */
    public boolean exists(Long repositoryId, String feature) {
        return featureMemoryJpaRepository.existsByRepositoryIdAndFeatureName(repositoryId, feature);
    }

    /**
     * 기능 메모리 삭제
     * 
     * @param repositoryId GitHub Repository ID
     * @param feature 기능 식별자
     */
    @Transactional
    public void delete(Long repositoryId, String feature) {
        featureMemoryJpaRepository.findByRepositoryIdAndFeatureName(repositoryId, feature)
            .ifPresent(memory -> {
                featureMemoryJpaRepository.delete(memory);
                log.info("Deleted feature memory: repositoryId={}, feature={}", repositoryId, feature);
            });
    }

    /**
     * 특정 Repository의 모든 메모리 삭제
     * 
     * @param repositoryId GitHub Repository ID
     */
    @Transactional
    public void clearByRepository(Long repositoryId) {
        featureMemoryJpaRepository.findByRepositoryId(repositoryId).forEach(memory -> {
            featureMemoryJpaRepository.delete(memory);
            log.info("Deleted feature memory: repositoryId={}, feature={}", repositoryId, memory.getFeatureName());
        });
    }
}
