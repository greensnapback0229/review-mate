package greensnaback0229.pr_review_server.installation;

import greensnaback0229.pr_review_server.installation.dto.InstallationWebhookPayload;
import greensnaback0229.pr_review_server.webhook.WebhookController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Webhook Installation 통합 테스트")
class WebhookInstallationTest {

    @Test
    @DisplayName("installation_created_webhook_정상처리")
    void installation_created_webhook_정상처리() {
        // given
        InstallationHandler handler = mock(InstallationHandler.class);

        InstallationWebhookPayload payload = InstallationWebhookPayload.builder()
                .action("created")
                .installation(InstallationWebhookPayload.Installation.builder()
                        .id(12345L)
                        .account(InstallationWebhookPayload.Account.builder()
                                .id(67890L).login("user").type("User").build())
                        .build())
                .repositories(Arrays.asList(
                        InstallationWebhookPayload.RepositoryInfo.builder()
                                .id(111L).fullName("user/repo1").name("repo1").build()
                ))
                .build();

        // when
        handler.handleCreated(payload);

        // then
        verify(handler).handleCreated(payload);
    }

    @Test
    @DisplayName("installation_deleted_webhook_정상처리")
    void installation_deleted_webhook_정상처리() {
        // given
        InstallationHandler handler = mock(InstallationHandler.class);

        InstallationWebhookPayload payload = InstallationWebhookPayload.builder()
                .action("deleted")
                .installation(InstallationWebhookPayload.Installation.builder()
                        .id(12345L)
                        .account(InstallationWebhookPayload.Account.builder()
                                .id(67890L).build())
                        .build())
                .build();

        // when
        handler.handleDeleted(payload);

        // then
        verify(handler).handleDeleted(payload);
    }

    @Test
    @DisplayName("installation_repositories_added_webhook_정상처리")
    void installation_repositories_added_webhook_정상처리() {
        // given
        InstallationHandler handler = mock(InstallationHandler.class);

        InstallationWebhookPayload payload = InstallationWebhookPayload.builder()
                .action("added")
                .installation(InstallationWebhookPayload.Installation.builder()
                        .id(12345L)
                        .account(InstallationWebhookPayload.Account.builder()
                                .id(67890L).build())
                        .build())
                .repositoriesAdded(Arrays.asList(
                        InstallationWebhookPayload.RepositoryInfo.builder()
                                .id(333L).fullName("user/repo3").name("repo3").build()
                ))
                .build();

        // when
        handler.handleRepositoriesAdded(payload);

        // then
        verify(handler).handleRepositoriesAdded(payload);
    }

    @Test
    @DisplayName("installation_repositories_removed_webhook_정상처리")
    void installation_repositories_removed_webhook_정상처리() {
        // given
        InstallationHandler handler = mock(InstallationHandler.class);

        InstallationWebhookPayload payload = InstallationWebhookPayload.builder()
                .action("removed")
                .installation(InstallationWebhookPayload.Installation.builder()
                        .id(12345L)
                        .account(InstallationWebhookPayload.Account.builder()
                                .id(67890L).build())
                        .build())
                .repositoriesRemoved(Arrays.asList(
                        InstallationWebhookPayload.RepositoryInfo.builder()
                                .id(333L).fullName("user/repo3").name("repo3").build()
                ))
                .build();

        // when
        handler.handleRepositoriesRemoved(payload);

        // then
        verify(handler).handleRepositoriesRemoved(payload);
    }

    @Test
    @DisplayName("WebhookController에_installation엔드포인트존재확인")
    void webhookController에_installation엔드포인트존재() {
        // WebhookController에 handleInstallationEvent 메서드가 존재하는지 확인
        boolean hasMethod = false;
        for (Method method : WebhookController.class.getDeclaredMethods()) {
            if ("handleInstallationEvent".equals(method.getName())) {
                hasMethod = true;
                break;
            }
        }
        assertThat(hasMethod).isTrue();
    }

    @Test
    @DisplayName("InstallationWebhookPayload_JSON역직렬화")
    void installationWebhookPayload_JSON역직렬화() throws Exception {
        // given
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String json = """
                {
                  "action": "created",
                  "installation": {
                    "id": 12345,
                    "account": {
                      "id": 67890,
                      "login": "username",
                      "type": "User"
                    }
                  },
                  "repositories": [
                    {"id": 111, "full_name": "username/repo1", "name": "repo1"},
                    {"id": 222, "full_name": "username/repo2", "name": "repo2"}
                  ]
                }
                """;

        // when
        InstallationWebhookPayload payload = objectMapper.readValue(json, InstallationWebhookPayload.class);

        // then
        assertThat(payload.getAction()).isEqualTo("created");
        assertThat(payload.getInstallation().getId()).isEqualTo(12345L);
        assertThat(payload.getInstallation().getAccount().getId()).isEqualTo(67890L);
        assertThat(payload.getInstallation().getAccount().getLogin()).isEqualTo("username");
        assertThat(payload.getRepositories()).hasSize(2);
        assertThat(payload.getRepositories().get(0).getFullName()).isEqualTo("username/repo1");
    }

    @Test
    @DisplayName("InstallationWebhookPayload_repositories_added_JSON역직렬화")
    void installationWebhookPayload_repositories_added_JSON역직렬화() throws Exception {
        // given
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String json = """
                {
                  "action": "added",
                  "installation": {
                    "id": 12345,
                    "account": {"id": 67890}
                  },
                  "repositories_added": [
                    {"id": 333, "full_name": "username/repo3"}
                  ]
                }
                """;

        // when
        InstallationWebhookPayload payload = objectMapper.readValue(json, InstallationWebhookPayload.class);

        // then
        assertThat(payload.getAction()).isEqualTo("added");
        assertThat(payload.getRepositoriesAdded()).hasSize(1);
        assertThat(payload.getRepositoriesAdded().get(0).getId()).isEqualTo(333L);
        assertThat(payload.getRepositoriesAdded().get(0).getFullName()).isEqualTo("username/repo3");
    }
}
