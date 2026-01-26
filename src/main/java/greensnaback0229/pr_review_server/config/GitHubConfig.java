package greensnaback0229.pr_review_server.config;

import greensnaback0229.pr_review_server.github.GitHubAppAuthenticator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * GitHub API 클라이언트 설정
 * GitHub App 인증 방식 사용
 * 
 * Note: GitHub App은 Repository별로 다른 Installation Token이 필요하므로
 * Bean으로 미리 생성하지 않고, 필요할 때마다 동적으로 생성합니다.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class GitHubConfig {
    
    private final GitHubAppAuthenticator authenticator;
    
    /**
     * Repository별로 GitHub 클라이언트 생성
     * 
     * @param repoFullName Repository 전체 이름 (예: "owner/repo")
     * @return GitHub 클라이언트
     * @throws IOException GitHub API 호출 실패 시
     */
    public GitHub createGitHubClient(String repoFullName) throws IOException {
        log.debug("Creating GitHub client for repository: {}", repoFullName);
        
        // Repository별 Installation Token 발급
        String installationToken = authenticator.getInstallationToken(repoFullName);
        
        // Token으로 GitHub 클라이언트 생성
        return new GitHubBuilder()
                .withAppInstallationToken(installationToken)
                .build();
    }
}
