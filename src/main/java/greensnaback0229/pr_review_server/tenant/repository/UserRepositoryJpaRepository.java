package greensnaback0229.pr_review_server.tenant.repository;

import greensnaback0229.pr_review_server.tenant.entity.UserRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepositoryJpaRepository extends JpaRepository<UserRepository, Long> {

    List<UserRepository> findByRepositoryIdAndIsActive(Long repositoryId, Boolean isActive);

    List<UserRepository> findByUserIdAndIsActive(Long userId, Boolean isActive);

    Optional<UserRepository> findByUserIdAndRepositoryId(Long userId, Long repositoryId);

    boolean existsByUserIdAndRepositoryId(Long userId, Long repositoryId);

    // F12: Repository Management queries
    Optional<UserRepository> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("UPDATE UserRepository ur SET ur.isActive = false WHERE ur.installationId = :installationId")
    int deactivateByInstallationId(@Param("installationId") Long installationId);

    @Modifying
    @Query("UPDATE UserRepository ur SET ur.isActive = false WHERE ur.id = :id AND ur.userId = :userId")
    int deactivateByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying
    @Query("UPDATE UserRepository ur SET ur.isActive = :isActive WHERE ur.id = :id AND ur.userId = :userId")
    int updateIsActiveByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId, @Param("isActive") Boolean isActive);

    @Modifying
    @Query("UPDATE UserRepository ur SET ur.isActive = false WHERE ur.repositoryId = :repositoryId AND ur.installationId = :installationId")
    int deactivateByRepositoryIdAndInstallationId(@Param("repositoryId") Long repositoryId, @Param("installationId") Long installationId);
}
