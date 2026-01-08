package greensnaback0229.pr_review_server.feature.repository;

import greensnaback0229.pr_review_server.feature.entity.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

@org.springframework.stereotype.Repository
public interface RepositoryJpaRepository extends JpaRepository<Repository, Long> {
}
