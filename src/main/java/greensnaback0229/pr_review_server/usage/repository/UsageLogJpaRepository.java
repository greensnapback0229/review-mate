package greensnaback0229.pr_review_server.usage.repository;

import greensnaback0229.pr_review_server.usage.entity.UsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface UsageLogJpaRepository extends JpaRepository<UsageLog, Long> {

    List<UsageLog> findByUserIdAndCreatedAtAfter(Long userId, LocalDateTime startDate);

    @Query("SELECT COUNT(u) FROM UsageLog u WHERE u.userId = :userId " +
           "AND u.createdAt >= :startDate")
    long countByUserIdAndCreatedAtAfter(@Param("userId") Long userId,
                                        @Param("startDate") LocalDateTime startDate);

    @Query("SELECT COALESCE(SUM(u.estimatedCost), 0) FROM UsageLog u WHERE u.userId = :userId " +
           "AND u.createdAt >= :startDate")
    BigDecimal sumCostByUserIdAndCreatedAtAfter(@Param("userId") Long userId,
                                                 @Param("startDate") LocalDateTime startDate);
}