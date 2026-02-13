package greensnaback0229.pr_review_server.installation;

import greensnaback0229.pr_review_server.tenant.entity.UserRepository;
import greensnaback0229.pr_review_server.tenant.repository.UserRepositoryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RepositoryService {

    private final UserRepositoryJpaRepository userRepositoryJpaRepository;

    public List<UserRepository> findActiveRepositories(Long userId) {
        return userRepositoryJpaRepository.findByUserIdAndIsActive(userId, true);
    }

    public Optional<UserRepository> findById(Long id, Long userId) {
        return userRepositoryJpaRepository.findByIdAndUserId(id, userId);
    }

    @Transactional
    public void toggleActive(Long id, Long userId, Boolean isActive) {
        int updated = userRepositoryJpaRepository.updateIsActiveByIdAndUserId(id, userId, isActive);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found or not owned");
        }
    }

    @Transactional
    public void deactivate(Long id, Long userId) {
        int updated = userRepositoryJpaRepository.deactivateByIdAndUserId(id, userId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found or not owned");
        }
    }
}
