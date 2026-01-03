package greensnaback0229.pr_review_server.config;

import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * GitHub API 클라이언트 설정
 */
@Slf4j
@Configuration
public class GitHubConfig {
    
    @Value("${github.token}")
    private String githubToken;
    
    @Bean
    public GitHub github() throws IOException {
        // 토큰의 앞 10글자만 로그 (보안)
        String tokenPreview = githubToken != null && githubToken.length() > 10 
            ? githubToken.substring(0, 10) + "..." 
            : "null or empty";
        log.info("Initializing GitHub client with token: {}", tokenPreview);
        
        GitHub github = new GitHubBuilder()
                .withOAuthToken(githubToken)
                .build();
        
        // GitHub 연결 테스트
        try {
            String login = github.getMyself().getLogin();
            log.info("GitHub client initialized successfully. Authenticated as: {}", login);
        } catch (IOException e) {
            log.error("Failed to authenticate with GitHub: {}", e.getMessage());
            throw e;
        }
        
        return github;
    }
}
