package greensnaback0229.pr_review_server.github;

import greensnaback0229.pr_review_server.llm.dto.InlineComment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReview;
import org.kohsuke.github.GHPullRequestReviewBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * GitHub Review API 클라이언트
 * PR에 리뷰를 작성하는 기능 제공
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubReviewClient {
    
    private final GitHubAppAuthenticator authenticator;

    /**
     * PR에 리뷰 작성
     * 
     * @param repoFullName 저장소 전체 이름 (예: "owner/repo")
     * @param prNumber PR 번호
     * @param generalReview 전반적인 리뷰 내용
     * @param inlineComments 특정 라인에 대한 코멘트 목록
     */
    public void createReview(String repoFullName, int prNumber, String generalReview, List<InlineComment> inlineComments) {
        try {
            // 최신 토큰으로 GitHub 클라이언트 생성
            String installationToken = authenticator.getInstallationToken();
            GitHub github = new GitHubBuilder()
                    .withAppInstallationToken(installationToken)
                    .build();
            
            GHRepository repository = github.getRepository(repoFullName);
            GHPullRequest pullRequest = repository.getPullRequest(prNumber);
            
            // Review Builder 생성
            GHPullRequestReviewBuilder reviewBuilder = pullRequest.createReview();
            
            // 전반적인 리뷰 추가
            if (generalReview != null && !generalReview.isEmpty()) {
                reviewBuilder.body(generalReview);
            }
            
            // Inline comments 추가
            if (inlineComments != null && !inlineComments.isEmpty()) {
                for (InlineComment comment : inlineComments) {
                    try {
                        reviewBuilder.comment(comment.getBody(), comment.getPath(), comment.getLine());
                        log.info("Added inline comment: {}:{} - {}", 
                                comment.getPath(), comment.getLine(), comment.getBody());
                    } catch (Exception e) {
                        log.error("Failed to add inline comment at {}:{}: {}", 
                                comment.getPath(), comment.getLine(), e.getMessage());
                        // 개별 코멘트 실패해도 계속 진행
                    }
                }
            }
            
            // Review 제출 (COMMENT 타입 - approve나 request changes 없이 단순 코멘트)
            GHPullRequestReview review = reviewBuilder.create();
            
            log.info("Successfully created review for PR #{} in {}", prNumber, repoFullName);
            log.info("Review ID: {}, State: {}", review.getId(), review.getState());
            
        } catch (IOException e) {
            log.error("Failed to create review for PR #{} in {}: {}", 
                    prNumber, repoFullName, e.getMessage(), e);
            throw new RuntimeException("Failed to create GitHub review", e);
        }
    }

    /**
     * PR에 단순 코멘트 작성 (Review 없이)
     * inline comments가 없을 때 사용
     * 
     * @param repoFullName 저장소 전체 이름
     * @param prNumber PR 번호
     * @param comment 코멘트 내용
     */
    public void createComment(String repoFullName, int prNumber, String comment) {
        try {
            // 최신 토큰으로 GitHub 클라이언트 생성
            String installationToken = authenticator.getInstallationToken();
            GitHub github = new GitHubBuilder()
                    .withAppInstallationToken(installationToken)
                    .build();
            
            GHRepository repository = github.getRepository(repoFullName);
            GHPullRequest pullRequest = repository.getPullRequest(prNumber);
            
            // Issue comment로 추가 (PR도 issue의 일종)
            pullRequest.comment(comment);
            
            log.info("Successfully created comment for PR #{} in {}", prNumber, repoFullName);
            
        } catch (IOException e) {
            log.error("Failed to create comment for PR #{} in {}: {}", 
                    prNumber, repoFullName, e.getMessage(), e);
            throw new RuntimeException("Failed to create GitHub comment", e);
        }
    }
}
