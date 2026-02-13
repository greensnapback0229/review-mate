package greensnaback0229.pr_review_server.comment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import greensnaback0229.pr_review_server.aggregator.dto.AggregatedReview;
import greensnaback0229.pr_review_server.collector.dto.CollectedCode;
import greensnaback0229.pr_review_server.collector.dto.FileContent;
import greensnaback0229.pr_review_server.comment.dto.FileContextData;
import greensnaback0229.pr_review_server.comment.entity.ReviewContext;
import greensnaback0229.pr_review_server.comment.repository.ReviewContextJpaRepository;
import greensnaback0229.pr_review_server.llm.dto.InlineComment;
import greensnaback0229.pr_review_server.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewContextService 테스트")
class ReviewContextServiceTest {

    private static final Long TEST_USER_ID = 42L;
    private static final Long TEST_REPOSITORY_ID = 123L;
    private static final int TEST_PR_NUMBER = 1;
    private static final String TEST_FEATURE_NAME = "PAYMENT";
    private static final String TEST_HEAD_SHA = "abc123";

    @Mock
    private ReviewContextJpaRepository reviewContextJpaRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ReviewContextService reviewContextService;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentUserId(TEST_USER_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("saveReviewContext_새리뷰컨텍스트저장")
    void saveReviewContext_새리뷰컨텍스트저장() throws JsonProcessingException {
        // given
        CollectedCode collectedCode = CollectedCode.builder()
                .changedFiles(Arrays.asList(
                        FileContent.builder()
                                .path("src/Payment.java")
                                .diff("@@ -1,3 +1,4 @@\n+new line")
                                .type(FileContent.FileType.CHANGED)
                                .build()
                ))
                .coreFiles(Arrays.asList())
                .additionalFiles(Arrays.asList())
                .build();

        InlineComment inlineComment = InlineComment.builder()
                .path("src/Payment.java")
                .line(10)
                .body("리뷰 코멘트")
                .build();

        AggregatedReview review = AggregatedReview.builder()
                .review("전체 리뷰 내용")
                .inlineComments(Arrays.asList(inlineComment))
                .build();

        when(reviewContextJpaRepository.findByRepositoryIdAndPrNumberAndFeatureNameAndUserId(
                TEST_REPOSITORY_ID, TEST_PR_NUMBER, TEST_FEATURE_NAME, TEST_USER_ID))
                .thenReturn(Optional.empty());

        // when
        reviewContextService.saveReviewContext(
                TEST_REPOSITORY_ID, TEST_PR_NUMBER, TEST_FEATURE_NAME,
                TEST_HEAD_SHA, collectedCode, review);

        // then
        ArgumentCaptor<ReviewContext> captor = ArgumentCaptor.forClass(ReviewContext.class);
        verify(reviewContextJpaRepository).save(captor.capture());

        ReviewContext saved = captor.getValue();
        assertThat(saved.getRepositoryId()).isEqualTo(TEST_REPOSITORY_ID);
        assertThat(saved.getPrNumber()).isEqualTo(TEST_PR_NUMBER);
        assertThat(saved.getFeatureName()).isEqualTo(TEST_FEATURE_NAME);
        assertThat(saved.getHeadSha()).isEqualTo(TEST_HEAD_SHA);
        assertThat(saved.getGeneralReview()).isEqualTo("전체 리뷰 내용");
        assertThat(saved.getBotCommentIds()).isEqualTo("[]");
        assertThat(saved.getUserId()).isEqualTo(TEST_USER_ID);

        // JSON 필드 검증
        List<FileContextData> fileContexts = objectMapper.readValue(
                saved.getFileContexts(), objectMapper.getTypeFactory().constructCollectionType(List.class, FileContextData.class));
        assertThat(fileContexts).hasSize(1);
        assertThat(fileContexts.get(0).getPath()).isEqualTo("src/Payment.java");
        assertThat(fileContexts.get(0).getKeyLines()).contains(10);
    }

    @Test
    @DisplayName("saveReviewContext_기존컨텍스트업데이트")
    void saveReviewContext_기존컨텍스트업데이트() throws JsonProcessingException {
        // given
        ReviewContext existingContext = ReviewContext.builder()
                .id(100L)
                .repositoryId(TEST_REPOSITORY_ID)
                .prNumber(TEST_PR_NUMBER)
                .featureName(TEST_FEATURE_NAME)
                .headSha("old_sha")
                .fileContexts("[]")
                .generalReview("기존 리뷰")
                .inlineComments("[]")
                .botCommentIds("[100, 200]")
                .build();

        CollectedCode collectedCode = CollectedCode.builder()
                .changedFiles(Arrays.asList(
                        FileContent.builder()
                                .path("src/NewPayment.java")
                                .diff("@@ -1,3 +1,4 @@")
                                .type(FileContent.FileType.CHANGED)
                                .build()
                ))
                .coreFiles(Arrays.asList())
                .additionalFiles(Arrays.asList())
                .build();

        AggregatedReview review = AggregatedReview.builder()
                .review("업데이트된 리뷰")
                .inlineComments(Arrays.asList())
                .build();

        when(reviewContextJpaRepository.findByRepositoryIdAndPrNumberAndFeatureNameAndUserId(
                TEST_REPOSITORY_ID, TEST_PR_NUMBER, TEST_FEATURE_NAME, TEST_USER_ID))
                .thenReturn(Optional.of(existingContext));

        // when
        reviewContextService.saveReviewContext(
                TEST_REPOSITORY_ID, TEST_PR_NUMBER, TEST_FEATURE_NAME,
                TEST_HEAD_SHA, collectedCode, review);

        // then
        ArgumentCaptor<ReviewContext> captor = ArgumentCaptor.forClass(ReviewContext.class);
        verify(reviewContextJpaRepository).save(captor.capture());

        ReviewContext updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(100L); // 기존 ID 유지
        assertThat(updated.getHeadSha()).isEqualTo(TEST_HEAD_SHA); // 업데이트
        assertThat(updated.getGeneralReview()).isEqualTo("업데이트된 리뷰");
        assertThat(updated.getBotCommentIds()).isEqualTo("[100, 200]"); // 기존 봇 코멘트 ID 유지
    }

    @Test
    @DisplayName("updateBotCommentIds_코멘트ID추가")
    void updateBotCommentIds_코멘트ID추가() throws JsonProcessingException {
        // given
        ReviewContext context = ReviewContext.builder()
                .id(1L)
                .repositoryId(TEST_REPOSITORY_ID)
                .prNumber(TEST_PR_NUMBER)
                .featureName(TEST_FEATURE_NAME)
                .headSha(TEST_HEAD_SHA)
                .fileContexts("[]")
                .generalReview("리뷰")
                .inlineComments("[]")
                .botCommentIds("[100, 200]")
                .build();

        when(reviewContextJpaRepository.findByRepositoryIdAndPrNumberAndUserId(TEST_REPOSITORY_ID, TEST_PR_NUMBER, TEST_USER_ID))
                .thenReturn(Arrays.asList(context));

        List<Long> newCommentIds = Arrays.asList(300L, 400L);

        // when
        reviewContextService.updateBotCommentIds(TEST_REPOSITORY_ID, TEST_PR_NUMBER, newCommentIds);

        // then
        ArgumentCaptor<ReviewContext> captor = ArgumentCaptor.forClass(ReviewContext.class);
        verify(reviewContextJpaRepository).save(captor.capture());

        ReviewContext updated = captor.getValue();
        List<Long> botCommentIds = objectMapper.readValue(
                updated.getBotCommentIds(), objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
        assertThat(botCommentIds).containsExactly(100L, 200L, 300L, 400L);
    }

    @Test
    @DisplayName("isBotComment_봇코멘트확인_true")
    void isBotComment_봇코멘트확인_true() {
        // given
        ReviewContext context = ReviewContext.builder()
                .id(1L)
                .repositoryId(TEST_REPOSITORY_ID)
                .prNumber(TEST_PR_NUMBER)
                .featureName(TEST_FEATURE_NAME)
                .headSha(TEST_HEAD_SHA)
                .fileContexts("[]")
                .generalReview("리뷰")
                .inlineComments("[]")
                .botCommentIds("[100, 200, 300]")
                .build();

        when(reviewContextJpaRepository.findByRepositoryIdAndPrNumberAndUserId(TEST_REPOSITORY_ID, TEST_PR_NUMBER, TEST_USER_ID))
                .thenReturn(Arrays.asList(context));

        // when
        boolean result = reviewContextService.isBotComment(TEST_REPOSITORY_ID, TEST_PR_NUMBER, 200L);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isBotComment_봇코멘트아님_false")
    void isBotComment_봇코멘트아님_false() {
        // given
        ReviewContext context = ReviewContext.builder()
                .id(1L)
                .repositoryId(TEST_REPOSITORY_ID)
                .prNumber(TEST_PR_NUMBER)
                .featureName(TEST_FEATURE_NAME)
                .headSha(TEST_HEAD_SHA)
                .fileContexts("[]")
                .generalReview("리뷰")
                .inlineComments("[]")
                .botCommentIds("[100, 200]")
                .build();

        when(reviewContextJpaRepository.findByRepositoryIdAndPrNumberAndUserId(TEST_REPOSITORY_ID, TEST_PR_NUMBER, TEST_USER_ID))
                .thenReturn(Arrays.asList(context));

        // when
        boolean result = reviewContextService.isBotComment(TEST_REPOSITORY_ID, TEST_PR_NUMBER, 999L);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("countBotReplies_총답변수카운트")
    void countBotReplies_총답변수카운트() {
        // given
        ReviewContext context1 = ReviewContext.builder()
                .id(1L)
                .repositoryId(TEST_REPOSITORY_ID)
                .prNumber(TEST_PR_NUMBER)
                .featureName("PAYMENT")
                .headSha(TEST_HEAD_SHA)
                .fileContexts("[]")
                .generalReview("리뷰1")
                .inlineComments("[]")
                .botCommentIds("[100, 200]")
                .build();

        ReviewContext context2 = ReviewContext.builder()
                .id(2L)
                .repositoryId(TEST_REPOSITORY_ID)
                .prNumber(TEST_PR_NUMBER)
                .featureName("ALERT")
                .headSha(TEST_HEAD_SHA)
                .fileContexts("[]")
                .generalReview("리뷰2")
                .inlineComments("[]")
                .botCommentIds("[300, 400, 500]")
                .build();

        when(reviewContextJpaRepository.findByRepositoryIdAndPrNumberAndUserId(TEST_REPOSITORY_ID, TEST_PR_NUMBER, TEST_USER_ID))
                .thenReturn(Arrays.asList(context1, context2));

        // when
        int count = reviewContextService.countBotReplies(TEST_REPOSITORY_ID, TEST_PR_NUMBER);

        // then
        assertThat(count).isEqualTo(5); // 2 + 3
    }

    @Test
    @DisplayName("addBotCommentId_단일ID추가")
    void addBotCommentId_단일ID추가() throws JsonProcessingException {
        // given
        ReviewContext context = ReviewContext.builder()
                .id(1L)
                .repositoryId(TEST_REPOSITORY_ID)
                .prNumber(TEST_PR_NUMBER)
                .featureName(TEST_FEATURE_NAME)
                .headSha(TEST_HEAD_SHA)
                .fileContexts("[]")
                .generalReview("리뷰")
                .inlineComments("[]")
                .botCommentIds("[100]")
                .build();

        when(reviewContextJpaRepository.findByRepositoryIdAndPrNumberAndFeatureNameAndUserId(
                TEST_REPOSITORY_ID, TEST_PR_NUMBER, TEST_FEATURE_NAME, TEST_USER_ID))
                .thenReturn(Optional.of(context));

        // when
        reviewContextService.addBotCommentId(TEST_REPOSITORY_ID, TEST_PR_NUMBER, TEST_FEATURE_NAME, 200L);

        // then
        ArgumentCaptor<ReviewContext> captor = ArgumentCaptor.forClass(ReviewContext.class);
        verify(reviewContextJpaRepository).save(captor.capture());

        ReviewContext updated = captor.getValue();
        List<Long> botCommentIds = objectMapper.readValue(
                updated.getBotCommentIds(), objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
        assertThat(botCommentIds).containsExactly(100L, 200L);
    }

    @Test
    @DisplayName("findByCommentPath_파일경로매칭")
    void findByCommentPath_파일경로매칭() throws JsonProcessingException {
        // given
        FileContextData fileContext1 = FileContextData.builder()
                .path("src/Payment.java")
                .diff("...")
                .build();

        FileContextData fileContext2 = FileContextData.builder()
                .path("src/Order.java")
                .diff("...")
                .build();

        String fileContextsJson1 = objectMapper.writeValueAsString(Arrays.asList(fileContext1));
        String fileContextsJson2 = objectMapper.writeValueAsString(Arrays.asList(fileContext2));

        ReviewContext context1 = ReviewContext.builder()
                .id(1L)
                .repositoryId(TEST_REPOSITORY_ID)
                .prNumber(TEST_PR_NUMBER)
                .featureName("PAYMENT")
                .headSha(TEST_HEAD_SHA)
                .fileContexts(fileContextsJson1)
                .generalReview("결제 리뷰")
                .inlineComments("[]")
                .botCommentIds("[]")
                .build();

        ReviewContext context2 = ReviewContext.builder()
                .id(2L)
                .repositoryId(TEST_REPOSITORY_ID)
                .prNumber(TEST_PR_NUMBER)
                .featureName("ORDER")
                .headSha(TEST_HEAD_SHA)
                .fileContexts(fileContextsJson2)
                .generalReview("주문 리뷰")
                .inlineComments("[]")
                .botCommentIds("[]")
                .build();

        when(reviewContextJpaRepository.findByRepositoryIdAndPrNumberAndUserId(TEST_REPOSITORY_ID, TEST_PR_NUMBER, TEST_USER_ID))
                .thenReturn(Arrays.asList(context1, context2));

        // when
        Optional<ReviewContext> result = reviewContextService.findByCommentPath(
                TEST_REPOSITORY_ID, TEST_PR_NUMBER, "src/Order.java");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getFeatureName()).isEqualTo("ORDER");
    }

    @Test
    @DisplayName("findByCommentPath_매칭실패시첫번째반환")
    void findByCommentPath_매칭실패시첫번째반환() throws JsonProcessingException {
        // given
        FileContextData fileContext = FileContextData.builder()
                .path("src/Payment.java")
                .diff("...")
                .build();

        String fileContextsJson = objectMapper.writeValueAsString(Arrays.asList(fileContext));

        ReviewContext context = ReviewContext.builder()
                .id(1L)
                .repositoryId(TEST_REPOSITORY_ID)
                .prNumber(TEST_PR_NUMBER)
                .featureName("PAYMENT")
                .headSha(TEST_HEAD_SHA)
                .fileContexts(fileContextsJson)
                .generalReview("리뷰")
                .inlineComments("[]")
                .botCommentIds("[]")
                .build();

        when(reviewContextJpaRepository.findByRepositoryIdAndPrNumberAndUserId(TEST_REPOSITORY_ID, TEST_PR_NUMBER, TEST_USER_ID))
                .thenReturn(Arrays.asList(context));

        // when
        Optional<ReviewContext> result = reviewContextService.findByCommentPath(
                TEST_REPOSITORY_ID, TEST_PR_NUMBER, "src/NonExistent.java");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getFeatureName()).isEqualTo("PAYMENT"); // fallback to first
    }
}
