package greensnaback0229.pr_review_server.installation;

import greensnaback0229.pr_review_server.installation.dto.RepositoryDto;
import greensnaback0229.pr_review_server.installation.dto.RepositoryListResponse;
import greensnaback0229.pr_review_server.tenant.TenantContext;
import greensnaback0229.pr_review_server.tenant.entity.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RepositoryController 테스트")
class RepositoryControllerTest {

    private static final Long USER_ID = 1L;

    @Mock
    private RepositoryService repositoryService;

    @InjectMocks
    private RepositoryController repositoryController;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentUserId(USER_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET_repositories_본인활성repo목록반환")
    void getRepositories_본인활성repo목록반환() {
        // given
        UserRepository repo1 = UserRepository.builder()
                .id(1L).userId(USER_ID).repositoryId(111L)
                .repoFullName("user/repo1").installationId(100L).isActive(true)
                .build();
        UserRepository repo2 = UserRepository.builder()
                .id(2L).userId(USER_ID).repositoryId(222L)
                .repoFullName("user/repo2").installationId(100L).isActive(true)
                .build();

        when(repositoryService.findActiveRepositories(USER_ID))
                .thenReturn(Arrays.asList(repo1, repo2));

        // when
        ResponseEntity<RepositoryListResponse> response = repositoryController.getRepositories();

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotal()).isEqualTo(2);
        assertThat(response.getBody().getRepositories()).hasSize(2);
        assertThat(response.getBody().getRepositories().get(0).getFullName()).isEqualTo("user/repo1");
        assertThat(response.getBody().getRepositories().get(1).getFullName()).isEqualTo("user/repo2");
    }

    @Test
    @DisplayName("GET_repository_상세조회성공")
    void getRepository_상세조회성공() {
        // given
        UserRepository repo = UserRepository.builder()
                .id(1L).userId(USER_ID).repositoryId(111L)
                .repoFullName("user/repo1").installationId(100L).isActive(true)
                .build();

        when(repositoryService.findById(1L, USER_ID)).thenReturn(Optional.of(repo));

        // when
        ResponseEntity<RepositoryDto> response = repositoryController.getRepository(1L);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getFullName()).isEqualTo("user/repo1");
        assertThat(response.getBody().getRepositoryId()).isEqualTo(111L);
    }

    @Test
    @DisplayName("GET_repository_존재하지않으면404")
    void getRepository_존재하지않으면404() {
        // given
        when(repositoryService.findById(999L, USER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> repositoryController.getRepository(999L))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("PATCH_repository_토글성공")
    void toggleRepository_토글성공() {
        // given
        greensnaback0229.pr_review_server.installation.dto.ToggleRequest request =
                new greensnaback0229.pr_review_server.installation.dto.ToggleRequest();
        request.setIsActive(false);

        doNothing().when(repositoryService).toggleActive(1L, USER_ID, false);

        // when
        ResponseEntity<Void> response = repositoryController.toggleRepository(1L, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(repositoryService).toggleActive(1L, USER_ID, false);
    }

    @Test
    @DisplayName("DELETE_repository_softDelete성공")
    void deleteRepository_softDelete성공() {
        // given
        doNothing().when(repositoryService).deactivate(1L, USER_ID);

        // when
        ResponseEntity<Void> response = repositoryController.deleteRepository(1L);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(repositoryService).deactivate(1L, USER_ID);
    }

    @Test
    @DisplayName("DELETE_다른사용자repo_접근불가")
    void deleteRepository_다른사용자repo_접근불가() {
        // given
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND))
                .when(repositoryService).deactivate(999L, USER_ID);

        // when & then
        assertThatThrownBy(() -> repositoryController.deleteRepository(999L))
                .isInstanceOf(ResponseStatusException.class);
    }
}
