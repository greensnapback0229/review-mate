package greensnaback0229.pr_review_server.github;

import greensnaback0229.pr_review_server.config.GitHubConfig;
import greensnaback0229.pr_review_server.llm.dto.InlineComment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub Review API 클라이언트
 * PR에 Review를 작성하는 기능 제공
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubReviewClient {
    
    private final GitHubConfig githubConfig;
    
    /**
     * PR에 리뷰 작성
     *
     * @param repoFullName 저장소 전체 이름 (예: "owner/repo")
     * @param prNumber PR 번호
     * @param generalComment 전반적인 코멘트 (PR 전체에 대한 리뷰)
     * @param inlineComments 특정 라인에 대한 코멘트 목록
     * @return 생성된 리뷰 코멘트 ID 목록 (봇 답글 감지용)
     * @throws IOException GitHub API 호출 실패 시
     */
    public List<Long> createReview(String repoFullName, int prNumber, String generalComment,
                           List<InlineComment> inlineComments) throws IOException {
        log.info("Creating review for PR {}/#{}", repoFullName, prNumber);
        log.info("General comment length: {}, Inline comments count: {}", 
                generalComment != null ? generalComment.length() : 0, 
                inlineComments != null ? inlineComments.size() : 0);
        
        GitHub github = githubConfig.createGitHubClient(repoFullName);
        GHRepository repository = github.getRepository(repoFullName);
        GHPullRequest pullRequest = repository.getPullRequest(prNumber);
        
        // Inline comments가 있으면 diff position 매핑 생성
        Map<String, Map<Integer, Integer>> lineToPositionMap = new HashMap<>();
        if (inlineComments != null && !inlineComments.isEmpty()) {
            lineToPositionMap = buildLineToPositionMap(pullRequest);
        }
        
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
                    // 파일의 라인 → diff position 변환
                    Map<Integer, Integer> fileLineMap = lineToPositionMap.get(comment.getPath());
                    if (fileLineMap == null || !fileLineMap.containsKey(comment.getLine())) {
                        log.warn("Cannot find diff position for {}:{} - skipping inline comment", 
                                comment.getPath(), comment.getLine());
                        continue;
                    }
                    
                    int position = fileLineMap.get(comment.getLine());
                    
                    // GitHub API는 commitId와 position을 요구함
                    String commitId = pullRequest.getHead().getSha();
                    reviewBuilder.comment(comment.getBody(), comment.getPath(), position);
                    
                    log.info("Added inline comment at {}:{} (position={})", 
                            comment.getPath(), comment.getLine(), position);
                } catch (Exception e) {
                    log.error("Failed to add inline comment at {}:{} - {}", 
                            comment.getPath(), comment.getLine(), e.getMessage());
                    // 개별 코멘트 실패는 무시하고 계속 진행
                }
            }
        }
        
        // Review 생성 및 제출 (COMMENT 타입 - 승인/변경요청 없이 단순 코멘트)
        // GitHub API에서 Review 제출: comment() / approve() / requestChanges()
        GHPullRequestReview review = reviewBuilder.event(GHPullRequestReviewEvent.COMMENT).create();

        log.info("Successfully created review #{} for PR {}/#{}",
                review.getId(), repoFullName, prNumber);

        // 리뷰 코멘트 ID 수집 (봇 답글 감지용)
        List<Long> commentIds = new ArrayList<>();
        try {
            for (GHPullRequestReviewComment comment : review.listReviewComments()) {
                commentIds.add(comment.getId());
            }
            log.info("Collected {} comment IDs from review #{}", commentIds.size(), review.getId());
        } catch (Exception e) {
            log.warn("Failed to collect comment IDs from review: {}", e.getMessage());
        }

        return commentIds;
    }
    
    /**
     * PR diff를 파싱하여 파일별 라인 번호 → diff position 매핑 생성
     * 
     * @param pullRequest GitHub Pull Request
     * @return Map<파일경로, Map<라인번호, diff position>>
     */
    private Map<String, Map<Integer, Integer>> buildLineToPositionMap(GHPullRequest pullRequest) throws IOException {
        Map<String, Map<Integer, Integer>> result = new HashMap<>();
        
        try {
            // PR의 모든 파일 변경사항 가져오기
            PagedIterable<GHPullRequestFileDetail> files = pullRequest.listFiles();
            
            for (GHPullRequestFileDetail file : files) {
                String filename = file.getFilename();
                String patch = file.getPatch();
                
                if (patch == null || patch.isEmpty()) {
                    log.debug("No patch found for file: {}", filename);
                    continue;
                }
                
                Map<Integer, Integer> lineToPosition = parsePatch(patch);
                result.put(filename, lineToPosition);
                
                log.debug("Built line-to-position map for {}: {} mappings", filename, lineToPosition.size());
            }
            
        } catch (Exception e) {
            log.error("Failed to build line-to-position map: {}", e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * diff patch를 파싱하여 라인 번호 → position 매핑 생성
     * 
     * Diff 형식:
     * @@ -10,6 +10,9 @@ ... (hunk header)
     * context line
     * +added line
     * -removed line
     * 
     * Position은 diff에서의 라인 위치 (1-based, hunk header 포함)
     * 
     * @param patch diff patch 문자열
     * @return Map<라인번호, position>
     */
    private Map<Integer, Integer> parsePatch(String patch) {
        Map<Integer, Integer> lineToPosition = new HashMap<>();
        
        String[] lines = patch.split("\n");
        int position = 0;  // diff에서의 위치 (0부터 시작)
        int currentLine = 0;  // 파일에서의 현재 라인 번호
        
        // Hunk header 정규식: @@ -old_start,old_count +new_start,new_count @@
        Pattern hunkPattern = Pattern.compile("^@@\\s+-\\d+,?\\d*\\s+\\+(\\d+),?\\d*\\s+@@");
        
        for (String line : lines) {
            position++;  // diff에서의 위치는 1-based
            
            Matcher matcher = hunkPattern.matcher(line);
            if (matcher.find()) {
                // Hunk header에서 시작 라인 번호 추출
                currentLine = Integer.parseInt(matcher.group(1));
                log.trace("Found hunk header at position {}: starting at line {}", position, currentLine);
                continue;
            }
            
            if (line.startsWith("+")) {
                // 추가된 라인
                lineToPosition.put(currentLine, position);
                log.trace("Mapped line {} to position {} (added)", currentLine, position);
                currentLine++;
            } else if (line.startsWith("-")) {
                // 삭제된 라인 (파일에 존재하지 않으므로 매핑 안 함)
                log.trace("Skipped deleted line at position {}", position);
            } else if (!line.isEmpty()) {
                // Context 라인 (변경되지 않은 라인)
                lineToPosition.put(currentLine, position);
                log.trace("Mapped line {} to position {} (context)", currentLine, position);
                currentLine++;
            }
        }
        
        return lineToPosition;
    }
    
    /**
     * PR에 일반 코멘트만 작성 (inline comments 없이)
     *
     * @param repoFullName 저장소 전체 이름 (예: "owner/repo")
     * @param prNumber PR 번호
     * @param comment 코멘트 내용
     * @return 생성된 리뷰 코멘트 ID 목록 (봇 답글 감지용)
     * @throws IOException GitHub API 호출 실패 시
     */
    public List<Long> createSimpleComment(String repoFullName, int prNumber, String comment) throws IOException {
        return createReview(repoFullName, prNumber, comment, List.of());
    }

    /**
     * PR의 현재 HEAD SHA 조회 (코드 변경 감지용)
     *
     * @param repoFullName 저장소 전체 이름
     * @param prNumber PR 번호
     * @return HEAD SHA
     * @throws IOException GitHub API 호출 실패 시
     */
    public String getPrHeadSha(String repoFullName, int prNumber) throws IOException {
        GitHub github = githubConfig.createGitHubClient(repoFullName);
        GHRepository repository = github.getRepository(repoFullName);
        GHPullRequest pullRequest = repository.getPullRequest(prNumber);
        return pullRequest.getHead().getSha();
    }

    /**
     * 특정 SHA의 파일 내용 조회
     *
     * @param repoFullName 저장소 전체 이름
     * @param sha 커밋 SHA
     * @param filePath 파일 경로
     * @return 파일 내용
     * @throws IOException GitHub API 호출 실패 시
     */
    public String getFileContent(String repoFullName, String sha, String filePath) throws IOException {
        GitHub github = githubConfig.createGitHubClient(repoFullName);
        GHRepository repository = github.getRepository(repoFullName);
        GHContent content = repository.getFileContent(filePath, sha);
        return content.getContent();
    }

    /**
     * PR 리뷰 코멘트에 스레드 답글 작성
     *
     * @param repoFullName 저장소 전체 이름 (예: "owner/repo")
     * @param prNumber PR 번호
     * @param commentId 답글을 달 리뷰 코멘트 ID
     * @param replyBody 답글 내용
     * @return 새로 생성된 코멘트 ID (다중 턴 감지용)
     * @throws IOException GitHub API 호출 실패 시
     */
    public long replyToReviewComment(String repoFullName, int prNumber, long commentId, String replyBody) throws IOException {
        log.info("Replying to review comment #{} on PR {}/#{}", commentId, repoFullName, prNumber);

        GitHub github = githubConfig.createGitHubClient(repoFullName);
        GHRepository repository = github.getRepository(repoFullName);
        GHPullRequest pullRequest = repository.getPullRequest(prNumber);

        // 대상 리뷰 코멘트 찾기
        GHPullRequestReviewComment targetComment = null;
        for (GHPullRequestReviewComment comment : pullRequest.listReviewComments().toList()) {
            if (comment.getId() == commentId) {
                targetComment = comment;
                break;
            }
        }

        if (targetComment == null) {
            throw new IOException("Review comment not found: " + commentId);
        }

        // 리뷰 코멘트 스레드에 답글 작성
        GHPullRequestReviewComment reply = targetComment.reply(replyBody);

        log.info("Successfully posted reply to review comment #{}: new comment ID = {}",
                commentId, reply.getId());
        return reply.getId();
    }
}
