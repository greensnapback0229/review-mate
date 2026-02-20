package greensnaback0229.pr_review_server.tenant;

import greensnaback0229.pr_review_server.tenant.entity.UserRepository;
import greensnaback0229.pr_review_server.tenant.repository.UserRepositoryJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRepositoryServiceTest {

    @Mock
    private UserRepositoryJpaRepository userRepositoryJpaRepository;

    @InjectMocks
    private UserRepositoryService userRepositoryService;

    @Test
    @DisplayName("활성 사용자 ID 목록 조회 - 여러 사용자")
    void findActiveUserIds_multipleUsers() {
        // given
        Long repoId = 100L;
        UserRepository ur1 = UserRepository.builder().userId(1L).repositoryId(repoId).repoFullName("owner/repo").isActive(true).build();
        UserRepository ur2 = UserRepository.builder().userId(2L).repositoryId(repoId).repoFullName("owner/repo").isActive(true).build();
        when(userRepositoryJpaRepository.findByRepositoryIdAndIsActive(repoId, true))
                .thenReturn(List.of(ur1, ur2));

        // when
        List<Long> userIds = userRepositoryService.findActiveUserIdsByRepositoryId(repoId);

        // then
        assertThat(userIds).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("활성 사용자 ID 목록 조회 - 매핑 없음")
    void findActiveUserIds_empty() {
        // given
        when(userRepositoryJpaRepository.findByRepositoryIdAndIsActive(999L, true))
                .thenReturn(List.of());

        // when
        List<Long> userIds = userRepositoryService.findActiveUserIdsByRepositoryId(999L);

        // then
        assertThat(userIds).isEmpty();
    }

    @Test
    @DisplayName("사용자의 활성 Repository 목록 조회")
    void findActiveRepositories_byUserId() {
        // given
        Long userId = 1L;
        UserRepository ur1 = UserRepository.builder().userId(userId).repositoryId(100L).repoFullName("owner/repo1").isActive(true).build();
        UserRepository ur2 = UserRepository.builder().userId(userId).repositoryId(200L).repoFullName("owner/repo2").isActive(true).build();
        when(userRepositoryJpaRepository.findByUserIdAndIsActive(userId, true))
                .thenReturn(List.of(ur1, ur2));

        // when
        List<UserRepository> repos = userRepositoryService.findActiveRepositoriesByUserId(userId);

        // then
        assertThat(repos).hasSize(2);
        assertThat(repos.get(0).getRepoFullName()).isEqualTo("owner/repo1");
    }

    @Test
    @DisplayName("매핑 존재 여부 확인 - 존재")
    void existsMapping_true() {
        when(userRepositoryJpaRepository.existsByUserIdAndRepositoryId(1L, 100L)).thenReturn(true);
        assertThat(userRepositoryService.existsMapping(1L, 100L)).isTrue();
    }

    @Test
    @DisplayName("매핑 존재 여부 확인 - 미존재")
    void existsMapping_false() {
        when(userRepositoryJpaRepository.existsByUserIdAndRepositoryId(1L, 999L)).thenReturn(false);
        assertThat(userRepositoryService.existsMapping(1L, 999L)).isFalse();
    }
}
