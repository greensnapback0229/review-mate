package greensnaback0229.pr_review_server.auth.repository;

import greensnaback0229.pr_review_server.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserJpaRepository extends JpaRepository<User, Long> {

    Optional<User> findByGithubId(Long githubId);
}
