package greensnaback0229.pr_review_server.tenant;

import greensnaback0229.pr_review_server.tenant.entity.UserRepository;
import greensnaback0229.pr_review_server.tenant.repository.UserRepositoryJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * user_repositories 테이블을 통한 사용자-Repository N:N 매핑 서비스.
 * Webhook에서 repository_id로 연결된 사용자 목록을 조회하는 데 사용.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRepositoryService {

    private final UserRepositoryJpaRepository userRepositoryJpaRepository;

    /**
     * repository_id로 연결된 활성 사용자 ID 목록 조회
     */
    public List<Long> findActiveUserIdsByRepositoryId(Long repositoryId) {
        return userRepositoryJpaRepository.findByRepositoryIdAndIsActive(repositoryId, true)
                .stream()
                .map(UserRepository::getUserId)
                .toList();
    }

    /**
     * 사용자의 활성 Repository 목록 조회
     */
    public List<UserRepository> findActiveRepositoriesByUserId(Long userId) {
        return userRepositoryJpaRepository.findByUserIdAndIsActive(userId, true);
    }

    /**
     * 사용자-Repository 연결 존재 여부 확인
     */
    public boolean existsMapping(Long userId, Long repositoryId) {
        return userRepositoryJpaRepository.existsByUserIdAndRepositoryId(userId, repositoryId);
    }

    /**
     * Repository 활성/비활성 토글
     * @return 변경 후 isActive 값
     */
    @Transactional
    public boolean toggleActive(Long userId, Long repositoryId, boolean active) {
        UserRepository userRepo = userRepositoryJpaRepository
                .findByUserIdAndRepositoryId(userId, repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));
        userRepositoryJpaRepository.updateIsActiveByIdAndUserId(userRepo.getId(), userId, active);
        return active;
    }
}
