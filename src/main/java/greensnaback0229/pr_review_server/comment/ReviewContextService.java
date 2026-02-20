package greensnaback0229.pr_review_server.comment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import greensnaback0229.pr_review_server.aggregator.dto.AggregatedReview;
import greensnaback0229.pr_review_server.collector.dto.CollectedCode;
import greensnaback0229.pr_review_server.collector.dto.FileContent;
import greensnaback0229.pr_review_server.comment.dto.FileContextData;
import greensnaback0229.pr_review_server.comment.entity.ReviewContext;
import greensnaback0229.pr_review_server.comment.repository.ReviewContextJpaRepository;
import greensnaback0229.pr_review_server.llm.dto.InlineComment;
import greensnaback0229.pr_review_server.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewContextService {

    private final ReviewContextJpaRepository reviewContextJpaRepository;
    private final ObjectMapper objectMapper;

    /**
     * 1차 리뷰 완료 후 리뷰 컨텍스트 저장
     */
    @Transactional
    public void saveReviewContext(Long repositoryId, int prNumber, String featureName,
                                  String headSha, CollectedCode collectedCode,
                                  AggregatedReview review) {
        try {
            Long userId = TenantContext.getCurrentUserIdOrThrow();

            // file_contexts 생성
            List<FileContextData> fileContexts = buildFileContexts(collectedCode, review.getInlineComments());
            String fileContextsJson = objectMapper.writeValueAsString(fileContexts);

            // inline_comments JSON
            String inlineCommentsJson = review.getInlineComments() != null
                    ? objectMapper.writeValueAsString(review.getInlineComments())
                    : "[]";

            // 기존 데이터 확인 (동일 repo/PR/feature + userId → upsert)
            Optional<ReviewContext> existing = reviewContextJpaRepository
                    .findByRepositoryIdAndPrNumberAndFeatureNameAndUserId(repositoryId, prNumber, featureName, userId);

            ReviewContext entity;
            if (existing.isPresent()) {
                entity = ReviewContext.builder()
                        .id(existing.get().getId())
                        .repositoryId(repositoryId)
                        .userId(userId)
                        .prNumber(prNumber)
                        .featureName(featureName)
                        .headSha(headSha)
                        .fileContexts(fileContextsJson)
                        .generalReview(review.getReview())
                        .inlineComments(inlineCommentsJson)
                        .botCommentIds(existing.get().getBotCommentIds())
                        .build();
            } else {
                entity = ReviewContext.builder()
                        .repositoryId(repositoryId)
                        .userId(userId)
                        .prNumber(prNumber)
                        .featureName(featureName)
                        .headSha(headSha)
                        .fileContexts(fileContextsJson)
                        .generalReview(review.getReview())
                        .inlineComments(inlineCommentsJson)
                        .botCommentIds("[]")
                        .build();
            }

            reviewContextJpaRepository.save(entity);
            log.info("Saved review context: repositoryId={}, prNumber={}, feature={}, userId={}",
                    repositoryId, prNumber, featureName, userId);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize review context: {}", e.getMessage(), e);
        }
    }

    /**
     * bot_comment_ids 업데이트 (리뷰 게시 후 호출)
     */
    @Transactional
    public void updateBotCommentIds(Long repositoryId, int prNumber, List<Long> commentIds) {
        Long userId = TenantContext.getCurrentUserIdOrThrow();
        List<ReviewContext> contexts = reviewContextJpaRepository
                .findByRepositoryIdAndPrNumberAndUserId(repositoryId, prNumber, userId);

        for (ReviewContext context : contexts) {
            try {
                List<Long> existingIds = parseBotCommentIds(context.getBotCommentIds());
                existingIds.addAll(commentIds);
                String updatedJson = objectMapper.writeValueAsString(existingIds);

                ReviewContext updated = ReviewContext.builder()
                        .id(context.getId())
                        .repositoryId(context.getRepositoryId())
                        .prNumber(context.getPrNumber())
                        .featureName(context.getFeatureName())
                        .headSha(context.getHeadSha())
                        .fileContexts(context.getFileContexts())
                        .generalReview(context.getGeneralReview())
                        .inlineComments(context.getInlineComments())
                        .botCommentIds(updatedJson)
                        .build();

                reviewContextJpaRepository.save(updated);
            } catch (JsonProcessingException e) {
                log.error("Failed to update bot_comment_ids: {}", e.getMessage(), e);
            }
        }
        log.info("Updated bot_comment_ids for repositoryId={}, prNumber={}, newIds={}",
                repositoryId, prNumber, commentIds);
    }

    /**
     * 특정 PR의 리뷰 컨텍스트 조회 (댓글 파일 경로로 매칭되는 Feature)
     */
    public Optional<ReviewContext> findByCommentPath(Long repositoryId, int prNumber, String filePath) {
        Long userId = TenantContext.getCurrentUserIdOrThrow();
        List<ReviewContext> contexts = reviewContextJpaRepository
                .findByRepositoryIdAndPrNumberAndUserId(repositoryId, prNumber, userId);

        for (ReviewContext context : contexts) {
            try {
                List<FileContextData> fileContexts = objectMapper.readValue(
                        context.getFileContexts(), new TypeReference<List<FileContextData>>() {});
                boolean matches = fileContexts.stream()
                        .anyMatch(fc -> fc.getPath().equals(filePath));
                if (matches) {
                    return Optional.of(context);
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to parse file_contexts: {}", e.getMessage(), e);
            }
        }

        // 매칭 실패 시 첫 번째 컨텍스트 반환 (fallback)
        return contexts.isEmpty() ? Optional.empty() : Optional.of(contexts.get(0));
    }

    /**
     * 봇 코멘트 ID인지 확인
     */
    public boolean isBotComment(Long repositoryId, int prNumber, long commentId) {
        Long userId = TenantContext.getCurrentUserIdOrThrow();
        List<ReviewContext> contexts = reviewContextJpaRepository
                .findByRepositoryIdAndPrNumberAndUserId(repositoryId, prNumber, userId);

        for (ReviewContext context : contexts) {
            List<Long> botIds = parseBotCommentIds(context.getBotCommentIds());
            if (botIds.contains(commentId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 봇 응답 수 카운트 (다중 턴 제한용)
     */
    public int countBotReplies(Long repositoryId, int prNumber) {
        Long userId = TenantContext.getCurrentUserIdOrThrow();
        List<ReviewContext> contexts = reviewContextJpaRepository
                .findByRepositoryIdAndPrNumberAndUserId(repositoryId, prNumber, userId);

        int total = 0;
        for (ReviewContext context : contexts) {
            List<Long> botIds = parseBotCommentIds(context.getBotCommentIds());
            total += botIds.size();
        }
        return total;
    }

    /**
     * 단일 봇 코멘트 ID 추가 (답글 후 호출)
     */
    @Transactional
    public void addBotCommentId(Long repositoryId, int prNumber, String featureName, long newCommentId) {
        Long userId = TenantContext.getCurrentUserIdOrThrow();
        Optional<ReviewContext> contextOpt = reviewContextJpaRepository
                .findByRepositoryIdAndPrNumberAndFeatureNameAndUserId(repositoryId, prNumber, featureName, userId);

        if (contextOpt.isEmpty()) {
            log.warn("No review context found for repositoryId={}, prNumber={}, feature={}",
                    repositoryId, prNumber, featureName);
            return;
        }

        ReviewContext context = contextOpt.get();
        try {
            List<Long> botIds = parseBotCommentIds(context.getBotCommentIds());
            botIds.add(newCommentId);
            String updatedJson = objectMapper.writeValueAsString(botIds);

            ReviewContext updated = ReviewContext.builder()
                    .id(context.getId())
                    .repositoryId(context.getRepositoryId())
                    .prNumber(context.getPrNumber())
                    .featureName(context.getFeatureName())
                    .headSha(context.getHeadSha())
                    .fileContexts(context.getFileContexts())
                    .generalReview(context.getGeneralReview())
                    .inlineComments(context.getInlineComments())
                    .botCommentIds(updatedJson)
                    .build();

            reviewContextJpaRepository.save(updated);
            log.info("Added bot comment id {} for feature={}", newCommentId, featureName);
        } catch (JsonProcessingException e) {
            log.error("Failed to add bot comment id: {}", e.getMessage(), e);
        }
    }

    /**
     * 특정 PR의 모든 리뷰 컨텍스트 조회
     */
    public List<ReviewContext> findByRepositoryIdAndPrNumber(Long repositoryId, int prNumber) {
        Long userId = TenantContext.getCurrentUserIdOrThrow();
        return reviewContextJpaRepository.findByRepositoryIdAndPrNumberAndUserId(repositoryId, prNumber, userId);
    }

    // === private helpers ===

    private List<FileContextData> buildFileContexts(CollectedCode collectedCode,
                                                     List<InlineComment> inlineComments) {
        List<FileContextData> result = new ArrayList<>();

        // changed files → diff 저장
        if (collectedCode.getChangedFiles() != null) {
            for (FileContent file : collectedCode.getChangedFiles()) {
                List<Integer> keyLines = extractKeyLines(file.getPath(), inlineComments);
                result.add(FileContextData.builder()
                        .path(file.getPath())
                        .diff(file.getDiff())
                        .keyLines(keyLines)
                        .build());
            }
        }

        // core files → content 저장
        if (collectedCode.getCoreFiles() != null) {
            for (FileContent file : collectedCode.getCoreFiles()) {
                List<Integer> keyLines = extractKeyLines(file.getPath(), inlineComments);
                result.add(FileContextData.builder()
                        .path(file.getPath())
                        .content(file.getContent())
                        .keyLines(keyLines)
                        .build());
            }
        }

        return result;
    }

    private List<Integer> extractKeyLines(String path, List<InlineComment> inlineComments) {
        if (inlineComments == null) return List.of();
        return inlineComments.stream()
                .filter(c -> path.equals(c.getPath()) && c.getLine() != null)
                .map(InlineComment::getLine)
                .collect(Collectors.toList());
    }

    private List<Long> parseBotCommentIds(String json) {
        try {
            if (json == null || json.isBlank()) return new ArrayList<>();
            return new ArrayList<>(objectMapper.readValue(json, new TypeReference<List<Long>>() {}));
        } catch (JsonProcessingException e) {
            log.error("Failed to parse bot_comment_ids: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * file_contexts JSON 파싱
     */
    public List<FileContextData> parseFileContexts(String fileContextsJson) {
        try {
            return objectMapper.readValue(fileContextsJson, new TypeReference<List<FileContextData>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse file_contexts: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * inline_comments JSON 파싱
     */
    public List<InlineComment> parseInlineComments(String inlineCommentsJson) {
        try {
            if (inlineCommentsJson == null || inlineCommentsJson.isBlank()) return List.of();
            return objectMapper.readValue(inlineCommentsJson, new TypeReference<List<InlineComment>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse inline_comments: {}", e.getMessage(), e);
            return List.of();
        }
    }
}
