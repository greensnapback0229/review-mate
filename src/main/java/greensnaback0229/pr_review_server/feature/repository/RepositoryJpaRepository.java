package greensnaback0229.pr_review_server.feature.repository;

import greensnaback0229.pr_review_server.feature.entity.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository as RepositoryAnnotation;

@RepositoryAnnotation
public interface RepositoryJpaRepository extends JpaRepository<Repository, Long> {
}
