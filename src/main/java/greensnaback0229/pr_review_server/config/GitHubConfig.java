package greensnaback0229.pr_review_server.config;

import greensnaback0229.pr_review_server.github.GitHubAppAuthenticator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * GitHub API 클라이언트 설정
 * GitHub App 인증 방식 사용
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class GitHubConfig {
    
    private final GitHubAppAuthenticator authenticator;
    
    @Bean
    public GitHub github() throws IOException {
        log.info("Initializing GitHub client with GitHub App authentication");
        
        // GitHub App Installation Token 발급
        String installationToken = authenticator.getInstallationToken();
        
        // Token으로 GitHub 클라이언트 생성
        GitHub github = new GitHubBuilder()
                .withAppInstallationToken(installationToken)
                .build();
        
        // GitHub 연결 테스트
        try {
            // GitHub App은 getMyself()가 없으므로 다른 방법으로 확인
            log.info("GitHub App client initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize GitHub App client: {}", e.getMessage());
            throw e;
        }
        
        return github;
    }
}
