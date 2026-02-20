package greensnaback0229.pr_review_server.installation;

import com.fasterxml.jackson.databind.ObjectMapper;
import greensnaback0229.pr_review_server.auth.entity.User;
import greensnaback0229.pr_review_server.auth.repository.UserJpaRepository;
import greensnaback0229.pr_review_server.installation.dto.InstallationWebhookPayload;
import greensnaback0229.pr_review_server.installation.entity.PendingInstallation;
import greensnaback0229.pr_review_server.installation.repository.PendingInstallationJpaRepository;
import greensnaback0229.pr_review_server.tenant.entity.UserRepository;
import greensnaback0229.pr_review_server.tenant.repository.UserRepositoryJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InstallationHandler 테스트")
class InstallationHandlerTest {

    private static final Long GITHUB_ID = 67890L;
    private static final Long INSTALLATION_ID = 12345L;
    private static final Long USER_ID = 1L;

    @Mock
    private UserJpaRepository userJpaRepository;

    @Mock
    private UserRepositoryJpaRepository userRepositoryJpaRepository;

    @Mock
    private PendingInstallationJpaRepository pendingInstallationJpaRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private InstallationHandler installationHandler;

    @Test
    @DisplayName("handleCreated_사용자존재시_user_repositories생성")
    void handleCreated_사용자존재시_user_repositories생성() {
        // given
        User user = User.builder().id(USER_ID).githubId(GITHUB_ID).build();
        when(userJpaRepository.findByGithubId(GITHUB_ID)).thenReturn(Optional.of(user));
        when(userRepositoryJpaRepository.findByUserIdAndRepositoryId(eq(USER_ID), anyLong()))
                .thenReturn(Optional.empty());

        InstallationWebhookPayload payload = createPayload("created",
                Arrays.asList(
                        createRepoInfo(111L, "user/repo1"),
                        createRepoInfo(222L, "user/repo2")
                ));

        // when
        installationHandler.handleCreated(payload);

        // then
        ArgumentCaptor<UserRepository> captor = ArgumentCaptor.forClass(UserRepository.class);
        verify(userRepositoryJpaRepository, times(2)).save(captor.capture());

        List<UserRepository> saved = captor.getAllValues();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getUserId()).isEqualTo(USER_ID);
        assertThat(saved.get(0).getRepositoryId()).isEqualTo(111L);
        assertThat(saved.get(0).getRepoFullName()).isEqualTo("user/repo1");
        assertThat(saved.get(0).getInstallationId()).isEqualTo(INSTALLATION_ID);
        assertThat(saved.get(0).getIsActive()).isTrue();

        assertThat(saved.get(1).getRepositoryId()).isEqualTo(222L);
        assertThat(saved.get(1).getRepoFullName()).isEqualTo("user/repo2");

        verify(pendingInstallationJpaRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleCreated_사용자미존재시_pending저장")
    void handleCreated_사용자미존재시_pending저장() {
        // given
        when(userJpaRepository.findByGithubId(GITHUB_ID)).thenReturn(Optional.empty());
        when(pendingInstallationJpaRepository.findByInstallationId(INSTALLATION_ID))
                .thenReturn(Optional.empty());

        InstallationWebhookPayload payload = createPayload("created",
                Arrays.asList(createRepoInfo(111L, "user/repo1")));

        // when
        installationHandler.handleCreated(payload);

        // then
        ArgumentCaptor<PendingInstallation> captor = ArgumentCaptor.forClass(PendingInstallation.class);
        verify(pendingInstallationJpaRepository).save(captor.capture());

        PendingInstallation saved = captor.getValue();
        assertThat(saved.getGithubId()).isEqualTo(GITHUB_ID);
        assertThat(saved.getInstallationId()).isEqualTo(INSTALLATION_ID);
        assertThat(saved.getRepositories()).contains("user/repo1");

        verify(userRepositoryJpaRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleDeleted_installation삭제시_모든repo비활성화")
    void handleDeleted_installation삭제시_모든repo비활성화() {
        // given
        when(userRepositoryJpaRepository.deactivateByInstallationId(INSTALLATION_ID)).thenReturn(3);
        when(pendingInstallationJpaRepository.findByInstallationId(INSTALLATION_ID))
                .thenReturn(Optional.empty());

        InstallationWebhookPayload payload = createPayload("deleted", null);

        // when
        installationHandler.handleDeleted(payload);

        // then
        verify(userRepositoryJpaRepository).deactivateByInstallationId(INSTALLATION_ID);
    }

    @Test
    @DisplayName("handleRepositoriesAdded_사용자존재시_repo추가")
    void handleRepositoriesAdded_사용자존재시_repo추가() {
        // given
        User user = User.builder().id(USER_ID).githubId(GITHUB_ID).build();
        when(userJpaRepository.findByGithubId(GITHUB_ID)).thenReturn(Optional.of(user));
        when(userRepositoryJpaRepository.findByUserIdAndRepositoryId(eq(USER_ID), anyLong()))
                .thenReturn(Optional.empty());

        InstallationWebhookPayload payload = InstallationWebhookPayload.builder()
                .action("added")
                .installation(InstallationWebhookPayload.Installation.builder()
                        .id(INSTALLATION_ID)
                        .account(InstallationWebhookPayload.Account.builder()
                                .id(GITHUB_ID).login("user").build())
                        .build())
                .repositoriesAdded(Arrays.asList(createRepoInfo(333L, "user/repo3")))
                .build();

        // when
        installationHandler.handleRepositoriesAdded(payload);

        // then
        ArgumentCaptor<UserRepository> captor = ArgumentCaptor.forClass(UserRepository.class);
        verify(userRepositoryJpaRepository).save(captor.capture());

        UserRepository saved = captor.getValue();
        assertThat(saved.getRepositoryId()).isEqualTo(333L);
        assertThat(saved.getRepoFullName()).isEqualTo("user/repo3");
    }

    @Test
    @DisplayName("handleRepositoriesRemoved_repo비활성화")
    void handleRepositoriesRemoved_repo비활성화() {
        // given
        when(userRepositoryJpaRepository.deactivateByRepositoryIdAndInstallationId(333L, INSTALLATION_ID))
                .thenReturn(1);

        InstallationWebhookPayload payload = InstallationWebhookPayload.builder()
                .action("removed")
                .installation(InstallationWebhookPayload.Installation.builder()
                        .id(INSTALLATION_ID)
                        .account(InstallationWebhookPayload.Account.builder()
                                .id(GITHUB_ID).build())
                        .build())
                .repositoriesRemoved(Arrays.asList(createRepoInfo(333L, "user/repo3")))
                .build();

        // when
        installationHandler.handleRepositoriesRemoved(payload);

        // then
        verify(userRepositoryJpaRepository).deactivateByRepositoryIdAndInstallationId(333L, INSTALLATION_ID);
    }

    @Test
    @DisplayName("handleCreated_중복repo연결시_재활성화(UPSERT)")
    void handleCreated_중복repo연결시_재활성화() {
        // given
        User user = User.builder().id(USER_ID).githubId(GITHUB_ID).build();
        when(userJpaRepository.findByGithubId(GITHUB_ID)).thenReturn(Optional.of(user));

        UserRepository existingRepo = UserRepository.builder()
                .id(10L).userId(USER_ID).repositoryId(111L)
                .repoFullName("user/repo1").installationId(INSTALLATION_ID)
                .isActive(false)
                .build();
        when(userRepositoryJpaRepository.findByUserIdAndRepositoryId(USER_ID, 111L))
                .thenReturn(Optional.of(existingRepo));

        InstallationWebhookPayload payload = createPayload("created",
                Arrays.asList(createRepoInfo(111L, "user/repo1")));

        // when
        installationHandler.handleCreated(payload);

        // then
        ArgumentCaptor<UserRepository> captor = ArgumentCaptor.forClass(UserRepository.class);
        verify(userRepositoryJpaRepository).save(captor.capture());

        UserRepository saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(10L);
        assertThat(saved.getIsActive()).isTrue(); // 재활성화
    }

    @Test
    @DisplayName("convertPendingToActive_pending을active로전환")
    void convertPendingToActive_pending을active로전환() throws Exception {
        // given
        String reposJson = objectMapper.writeValueAsString(
                Arrays.asList(createRepoInfo(111L, "user/repo1")));

        PendingInstallation pending = PendingInstallation.builder()
                .id(1L)
                .githubId(GITHUB_ID)
                .installationId(INSTALLATION_ID)
                .repositories(reposJson)
                .build();

        when(userRepositoryJpaRepository.findByUserIdAndRepositoryId(eq(USER_ID), anyLong()))
                .thenReturn(Optional.empty());

        // when
        installationHandler.convertPendingToActive(USER_ID, pending);

        // then
        ArgumentCaptor<UserRepository> captor = ArgumentCaptor.forClass(UserRepository.class);
        verify(userRepositoryJpaRepository).save(captor.capture());

        UserRepository saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getRepositoryId()).isEqualTo(111L);
        assertThat(saved.getInstallationId()).isEqualTo(INSTALLATION_ID);
        assertThat(saved.getIsActive()).isTrue();
    }

    private InstallationWebhookPayload createPayload(String action,
                                                      List<InstallationWebhookPayload.RepositoryInfo> repos) {
        return InstallationWebhookPayload.builder()
                .action(action)
                .installation(InstallationWebhookPayload.Installation.builder()
                        .id(INSTALLATION_ID)
                        .account(InstallationWebhookPayload.Account.builder()
                                .id(GITHUB_ID).login("user").type("User").build())
                        .build())
                .repositories(repos)
                .build();
    }

    private InstallationWebhookPayload.RepositoryInfo createRepoInfo(Long id, String fullName) {
        return InstallationWebhookPayload.RepositoryInfo.builder()
                .id(id).fullName(fullName).name(fullName.split("/")[1]).build();
    }
}
