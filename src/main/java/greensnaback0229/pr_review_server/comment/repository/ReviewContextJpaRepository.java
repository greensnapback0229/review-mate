package greensnaback0229.pr_review_server.comment.repository;

import greensnaback0229.pr_review_server.comment.entity.ReviewContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewContextJpaRepository extends JpaRepository<ReviewContext, Long> {

    Optional<ReviewContext> findByRepositoryIdAndPrNumberAndFeatureName(
            Long repositoryId, Integer prNumber, String featureName);

    List<ReviewContext> findByRepositoryIdAndPrNumber(Long repositoryId, Integer prNumber);

    boolean existsByRepositoryIdAndPrNumber(Long repositoryId, Integer prNumber);

    // F11: userId 격리 쿼리
    Optional<ReviewContext> findByRepositoryIdAndPrNumberAndFeatureNameAndUserId(
            Long repositoryId, Integer prNumber, String featureName, Long userId);

    List<ReviewContext> findByRepositoryIdAndPrNumberAndUserId(
            Long repositoryId, Integer prNumber, Long userId);

    boolean existsByRepositoryIdAndPrNumberAndUserId(
            Long repositoryId, Integer prNumber, Long userId);

    @Modifying
    @Query("UPDATE ReviewContext rc SET rc.userId = :userId WHERE rc.userId IS NULL")
    int updateUserIdWhereNull(@Param("userId") Long userId);
}
