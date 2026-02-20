package greensnaback0229.pr_review_server.feature.repository;

import greensnaback0229.pr_review_server.feature.entity.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@org.springframework.stereotype.Repository
public interface RepositoryJpaRepository extends JpaRepository<Repository, Long> {

    @Modifying
    @Query("UPDATE Repository r SET r.userId = :userId WHERE r.userId IS NULL")
    int updateUserIdWhereNull(@Param("userId") Long userId);
}
