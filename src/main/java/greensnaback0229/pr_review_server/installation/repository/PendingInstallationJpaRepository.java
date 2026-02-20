package greensnaback0229.pr_review_server.installation.repository;

import greensnaback0229.pr_review_server.installation.entity.PendingInstallation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PendingInstallationJpaRepository extends JpaRepository<PendingInstallation, Long> {

    List<PendingInstallation> findByGithubId(Long githubId);

    Optional<PendingInstallation> findByInstallationId(Long installationId);
}
