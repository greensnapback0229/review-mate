package greensnaback0229.pr_review_server.installation;

import greensnaback0229.pr_review_server.tenant.entity.UserRepository;
import greensnaback0229.pr_review_server.tenant.repository.UserRepositoryJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RepositoryService 테스트")
class RepositoryServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepositoryJpaRepository userRepositoryJpaRepository;

    @InjectMocks
    private RepositoryService repositoryService;

    @Test
    @DisplayName("findActiveRepositories_활성repo만반환")
    void findActiveRepositories_활성repo만반환() {
        // given
        UserRepository repo1 = UserRepository.builder()
                .id(1L).userId(USER_ID).repositoryId(111L)
                .repoFullName("user/repo1").isActive(true).build();
        UserRepository repo2 = UserRepository.builder()
                .id(2L).userId(USER_ID).repositoryId(222L)
                .repoFullName("user/repo2").isActive(true).build();

        when(userRepositoryJpaRepository.findByUserIdAndIsActive(USER_ID, true))
                .thenReturn(Arrays.asList(repo1, repo2));

        // when
        List<UserRepository> result = repositoryService.findActiveRepositories(USER_ID);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRepoFullName()).isEqualTo("user/repo1");
        assertThat(result.get(1).getRepoFullName()).isEqualTo("user/repo2");
    }

    @Test
    @DisplayName("findById_본인repo조회성공")
    void findById_본인repo조회성공() {
        // given
        UserRepository repo = UserRepository.builder()
                .id(1L).userId(USER_ID).repositoryId(111L)
                .repoFullName("user/repo1").isActive(true).build();

        when(userRepositoryJpaRepository.findByIdAndUserId(1L, USER_ID))
                .thenReturn(Optional.of(repo));

        // when
        Optional<UserRepository> result = repositoryService.findById(1L, USER_ID);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getRepoFullName()).isEqualTo("user/repo1");
    }

    @Test
    @DisplayName("findById_다른사용자repo조회시_empty")
    void findById_다른사용자repo조회시_empty() {
        // given
        when(userRepositoryJpaRepository.findByIdAndUserId(1L, USER_ID))
                .thenReturn(Optional.empty());

        // when
        Optional<UserRepository> result = repositoryService.findById(1L, USER_ID);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("toggleActive_활성화토글성공")
    void toggleActive_활성화토글성공() {
        // given
        when(userRepositoryJpaRepository.updateIsActiveByIdAndUserId(1L, USER_ID, false))
                .thenReturn(1);

        // when & then
        assertThatCode(() -> repositoryService.toggleActive(1L, USER_ID, false))
                .doesNotThrowAnyException();

        verify(userRepositoryJpaRepository).updateIsActiveByIdAndUserId(1L, USER_ID, false);
    }

    @Test
    @DisplayName("toggleActive_존재하지않는repo시_404")
    void toggleActive_존재하지않는repo시_404() {
        // given
        when(userRepositoryJpaRepository.updateIsActiveByIdAndUserId(999L, USER_ID, false))
                .thenReturn(0);

        // when & then
        assertThatThrownBy(() -> repositoryService.toggleActive(999L, USER_ID, false))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("deactivate_softDelete성공")
    void deactivate_softDelete성공() {
        // given
        when(userRepositoryJpaRepository.deactivateByIdAndUserId(1L, USER_ID))
                .thenReturn(1);

        // when & then
        assertThatCode(() -> repositoryService.deactivate(1L, USER_ID))
                .doesNotThrowAnyException();

        verify(userRepositoryJpaRepository).deactivateByIdAndUserId(1L, USER_ID);
    }

    @Test
    @DisplayName("deactivate_존재하지않는repo시_404")
    void deactivate_존재하지않는repo시_404() {
        // given
        when(userRepositoryJpaRepository.deactivateByIdAndUserId(999L, USER_ID))
                .thenReturn(0);

        // when & then
        assertThatThrownBy(() -> repositoryService.deactivate(999L, USER_ID))
                .isInstanceOf(ResponseStatusException.class);
    }
}
