package greensnaback0229.pr_review_server.feature.repository;

import greensnaback0229.pr_review_server.feature.entity.FeatureMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeatureMemoryJpaRepository extends JpaRepository<FeatureMemory, Long> {

    List<FeatureMemory> findByRepositoryId(Long repositoryId);

    Optional<FeatureMemory> findByRepositoryIdAndFeatureName(Long repositoryId, String featureName);

    boolean existsByRepositoryIdAndFeatureName(Long repositoryId, String featureName);

    // F11: userId 격리 쿼리
    List<FeatureMemory> findByRepositoryIdAndUserId(Long repositoryId, Long userId);

    Optional<FeatureMemory> findByRepositoryIdAndFeatureNameAndUserId(Long repositoryId, String featureName, Long userId);

    boolean existsByRepositoryIdAndFeatureNameAndUserId(Long repositoryId, String featureName, Long userId);

    @Modifying
    @Query("UPDATE FeatureMemory fm SET fm.userId = :userId WHERE fm.userId IS NULL")
    int updateUserIdWhereNull(@Param("userId") Long userId);
}
