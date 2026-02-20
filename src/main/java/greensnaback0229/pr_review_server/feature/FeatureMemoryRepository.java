package greensnaback0229.pr_review_server.feature;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import greensnaback0229.pr_review_server.feature.dto.FeatureMemory;
import greensnaback0229.pr_review_server.feature.repository.FeatureMemoryJpaRepository;
import greensnaback0229.pr_review_server.feature.repository.RepositoryJpaRepository;
import greensnaback0229.pr_review_server.tenant.TenantContext;
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
            Long userId = TenantContext.getCurrentUserIdOrThrow();

            // Repository Entity가 없으면 생성
            if (!repositoryJpaRepository.existsById(repositoryId)) {
                greensnaback0229.pr_review_server.feature.entity.Repository repo =
                    greensnaback0229.pr_review_server.feature.entity.Repository.builder()
                        .repositoryId(repositoryId)
                        .userId(userId)
                        .build();
                repositoryJpaRepository.save(repo);
                log.info("Created new repository: {} (userId={})", repositoryId, userId);
            }

            // DTO를 JSON으로 변환
            String jsonContent = objectMapper.writeValueAsString(memory);

            // 기존 메모리 조회 (userId 격리)
            Optional<greensnaback0229.pr_review_server.feature.entity.FeatureMemory> existingMemory =
                featureMemoryJpaRepository.findByRepositoryIdAndFeatureNameAndUserId(repositoryId, memory.getFeature(), userId);

            if (existingMemory.isPresent()) {
                greensnaback0229.pr_review_server.feature.entity.FeatureMemory updated =
                    greensnaback0229.pr_review_server.feature.entity.FeatureMemory.builder()
                        .featureMemoryId(existingMemory.get().getFeatureMemoryId())
                        .repositoryId(repositoryId)
                        .userId(userId)
                        .featureName(memory.getFeature())
                        .featureMemoryContent(jsonContent)
                        .build();
                featureMemoryJpaRepository.save(updated);
                log.info("Updated feature memory: repositoryId={}, feature={}, userId={}", repositoryId, memory.getFeature(), userId);
            } else {
                greensnaback0229.pr_review_server.feature.entity.FeatureMemory newMemory =
                    greensnaback0229.pr_review_server.feature.entity.FeatureMemory.builder()
                        .repositoryId(repositoryId)
                        .userId(userId)
                        .featureName(memory.getFeature())
                        .featureMemoryContent(jsonContent)
                        .build();
                featureMemoryJpaRepository.save(newMemory);
                log.info("Created new feature memory: repositoryId={}, feature={}, userId={}", repositoryId, memory.getFeature(), userId);
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
        Long userId = TenantContext.getCurrentUserIdOrThrow();

        Optional<greensnaback0229.pr_review_server.feature.entity.FeatureMemory> entity =
            featureMemoryJpaRepository.findByRepositoryIdAndFeatureNameAndUserId(repositoryId, feature, userId);

        return entity.map(e -> {
            try {
                return objectMapper.readValue(e.getFeatureMemoryContent(), FeatureMemory.class);
            } catch (JsonProcessingException ex) {
                log.error("Failed to deserialize FeatureMemory from JSON: {}", ex.getMessage(), ex);
                return null;
            }
        });
    }

    /**
     * 기능 메모리 존재 여부 확인
     */
    public boolean exists(Long repositoryId, String feature) {
        Long userId = TenantContext.getCurrentUserIdOrThrow();
        return featureMemoryJpaRepository.existsByRepositoryIdAndFeatureNameAndUserId(repositoryId, feature, userId);
    }

    /**
     * 기능 메모리 삭제
     */
    @Transactional
    public void delete(Long repositoryId, String feature) {
        Long userId = TenantContext.getCurrentUserIdOrThrow();

        Optional<greensnaback0229.pr_review_server.feature.entity.FeatureMemory> entity =
            featureMemoryJpaRepository.findByRepositoryIdAndFeatureNameAndUserId(repositoryId, feature, userId);

        entity.ifPresent(memory -> {
            featureMemoryJpaRepository.delete(memory);
            log.info("Deleted feature memory: repositoryId={}, feature={}, userId={}", repositoryId, feature, userId);
        });
    }

    /**
     * 특정 Repository의 모든 메모리 삭제
     */
    @Transactional
    public void clearByRepository(Long repositoryId) {
        Long userId = TenantContext.getCurrentUserIdOrThrow();

        var memories = featureMemoryJpaRepository.findByRepositoryIdAndUserId(repositoryId, userId);

        memories.forEach(memory -> {
            featureMemoryJpaRepository.delete(memory);
            log.info("Deleted feature memory: repositoryId={}, feature={}, userId={}", repositoryId, memory.getFeatureName(), userId);
        });
    }
}
