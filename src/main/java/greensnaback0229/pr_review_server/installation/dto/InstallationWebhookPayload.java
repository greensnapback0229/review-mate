package greensnaback0229.pr_review_server.installation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class InstallationWebhookPayload {

    private String action;

    private Installation installation;

    private List<RepositoryInfo> repositories;

    @JsonProperty("repositories_added")
    private List<RepositoryInfo> repositoriesAdded;

    @JsonProperty("repositories_removed")
    private List<RepositoryInfo> repositoriesRemoved;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Installation {
        private Long id;
        private Account account;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Account {
        private Long id;
        private String login;
        private String type;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RepositoryInfo {
        private Long id;

        @JsonProperty("full_name")
        private String fullName;

        private String name;
    }
}
