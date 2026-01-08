package greensnaback0229.pr_review_server.feature.repository;

import greensnaback0229.pr_review_server.feature.entity.FeatureMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeatureMemoryJpaRepository extends JpaRepository<FeatureMemory, Long> {
    
    List<FeatureMemory> findByRepositoryId(Long repositoryId);
    
    Optional<FeatureMemory> findByRepositoryIdAndFeatureName(Long repositoryId, String featureName);
    
    boolean existsByRepositoryIdAndFeatureName(Long repositoryId, String featureName);
}
