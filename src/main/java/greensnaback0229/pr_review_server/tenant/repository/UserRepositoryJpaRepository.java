package greensnaback0229.pr_review_server.tenant.repository;

import greensnaback0229.pr_review_server.tenant.entity.UserRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepositoryJpaRepository extends JpaRepository<UserRepository, Long> {

    List<UserRepository> findByRepositoryIdAndIsActive(Long repositoryId, Boolean isActive);

    List<UserRepository> findByUserIdAndIsActive(Long userId, Boolean isActive);

    Optional<UserRepository> findByUserIdAndRepositoryId(Long userId, Long repositoryId);

    boolean existsByUserIdAndRepositoryId(Long userId, Long repositoryId);
}
