package greensnaback0229.pr_review_server.comment.repository;

import greensnaback0229.pr_review_server.comment.entity.ReviewContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewContextJpaRepository extends JpaRepository<ReviewContext, Long> {

    Optional<ReviewContext> findByRepositoryIdAndPrNumberAndFeatureName(
            Long repositoryId, Integer prNumber, String featureName);

    List<ReviewContext> findByRepositoryIdAndPrNumber(Long repositoryId, Integer prNumber);

    boolean existsByRepositoryIdAndPrNumber(Long repositoryId, Integer prNumber);
}
