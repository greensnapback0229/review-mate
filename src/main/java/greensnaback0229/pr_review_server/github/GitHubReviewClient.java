package greensnaback0229.pr_review_server.github;

import greensnaback0229.pr_review_server.llm.dto.InlineComment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReview;
import org.kohsuke.github.GHPullRequestReviewBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * GitHub Review API 클라이언트
 * PR에 Review를 작성하는 기능 제공
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubReviewClient {
    
    private final GitHub github;
    
    /**
     * PR에 리뷰 작성
     * 
     * @param repoFullName 저장소 전체 이름 (예: "owner/repo")
     * @param prNumber PR 번호
     * @param generalComment 전반적인 코멘트 (PR 전체에 대한 리뷰)
     * @param inlineComments 특정 라인에 대한 코멘트 목록
     * @throws IOException GitHub API 호출 실패 시
     */
    public void createReview(String repoFullName, int prNumber, String generalComment, 
                           List<InlineComment> inlineComments) throws IOException {
        log.info("Creating review for PR {}/#{}", repoFullName, prNumber);
        log.info("General comment length: {}, Inline comments count: {}", 
                generalComment != null ? generalComment.length() : 0, 
                inlineComments != null ? inlineComments.size() : 0);
        
        GHRepository repository = github.getRepository(repoFullName);
        GHPullRequest pullRequest = repository.getPullRequest(prNumber);
        
        // Review Builder 생성
        GHPullRequestReviewBuilder reviewBuilder = pullRequest.createReview();
        
        // 전반적인 코멘트 추가
        if (generalComment != null && !generalComment.isEmpty()) {
            reviewBuilder.body(generalComment);
        }
        
        // Inline comments 추가
        if (inlineComments != null && !inlineComments.isEmpty()) {
            for (InlineComment comment : inlineComments) {
                try {
                    reviewBuilder.comment(comment.getBody(), comment.getPath(), comment.getLine());
                    log.debug("Added inline comment: {} at {}:{}", 
                            comment.getBody().substring(0, Math.min(50, comment.getBody().length())),
                            comment.getPath(), 
                            comment.getLine());
                } catch (Exception e) {
                    log.error("Failed to add inline comment at {}:{} - {}", 
                            comment.getPath(), comment.getLine(), e.getMessage());
                    // 개별 코멘트 실패는 무시하고 계속 진행
                }
            }
        }
        
        // Review 생성 (COMMENT 타입 - 승인/변경요청 없이 단순 코멘트)
        GHPullRequestReview review = reviewBuilder.create();
        
        log.info("Successfully created review #{} for PR {}/#{}", 
                review.getId(), repoFullName, prNumber);
    }
    
    /**
     * PR에 일반 코멘트만 작성 (inline comments 없이)
     * 
     * @param repoFullName 저장소 전체 이름 (예: "owner/repo")
     * @param prNumber PR 번호
     * @param comment 코멘트 내용
     * @throws IOException GitHub API 호출 실패 시
     */
    public void createSimpleComment(String repoFullName, int prNumber, String comment) throws IOException {
        createReview(repoFullName, prNumber, comment, List.of());
    }
}
