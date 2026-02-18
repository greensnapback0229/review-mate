package greensnaback0229.pr_review_server.review.repository;

import greensnaback0229.pr_review_server.review.entity.ReviewHistory;
import greensnaback0229.pr_review_server.review.entity.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReviewHistoryJpaRepository extends JpaRepository<ReviewHistory, Long> {

    Page<ReviewHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<ReviewHistory> findByUserIdAndRepositoryIdOrderByCreatedAtDesc(
            Long userId, Long repositoryId, Pageable pageable);

    List<ReviewHistory> findByUserIdAndRepositoryIdAndPrNumberOrderByCreatedAtDesc(
            Long userId, Long repositoryId, Integer prNumber);

    long countByUserIdAndRepositoryId(Long userId, Long repositoryId);

    long countByUserIdAndRepositoryIdAndStatus(Long userId, Long repositoryId, ReviewStatus status);

    long countByUserIdAndRepositoryIdAndCreatedAtAfter(
            Long userId, Long repositoryId, LocalDateTime after);

    @Query("SELECT COALESCE(AVG(r.inlineCommentCount), 0) FROM ReviewHistory r " +
           "WHERE r.userId = :userId AND r.repositoryId = :repositoryId")
    double avgInlineCommentCountByUserIdAndRepositoryId(
            @Param("userId") Long userId, @Param("repositoryId") Long repositoryId);

    @Query("SELECT COALESCE(AVG(r.reviewDurationMs), 0) FROM ReviewHistory r " +
           "WHERE r.userId = :userId AND r.repositoryId = :repositoryId AND r.reviewDurationMs IS NOT NULL")
    long avgReviewDurationMsByUserIdAndRepositoryId(
            @Param("userId") Long userId, @Param("repositoryId") Long repositoryId);

    List<ReviewHistory> findByUserIdAndRepositoryId(Long userId, Long repositoryId);
}
