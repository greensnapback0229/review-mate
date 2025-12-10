package greensnaback0229.pr_review_server.config;

import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * GitHub API 클라이언트 설정
 */
@Configuration
public class GitHubConfig {
    
    @Value("${github.token}")
    private String githubToken;
    
    @Bean
    public GitHub github() throws IOException {
        return new GitHubBuilder()
                .withOAuthToken(githubToken)
                .build();
    }
}
