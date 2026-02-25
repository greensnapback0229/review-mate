package greensnaback0229.pr_review_server.comment;

import greensnaback0229.pr_review_server.comment.dto.FileContextData;
import greensnaback0229.pr_review_server.comment.entity.ReviewContext;
import greensnaback0229.pr_review_server.github.GitHubReviewClient;
import greensnaback0229.pr_review_server.llm.LlmClient;
import greensnaback0229.pr_review_server.llm.dto.LlmCommentResponse;
import greensnaback0229.pr_review_server.prompt.PromptBuilder;
import greensnaback0229.pr_review_server.usage.UsageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentResponseService 테스트")
class CommentResponseServiceTest {

    private static final String TEST_REPO_FULL_NAME = "owner/repo";
    private static final Long TEST_REPOSITORY_ID = 123456789L;
    private static final int TEST_PR_NUMBER = 1;

    @Mock
    private ReviewContextService reviewContextService;

    @Mock
    private PromptBuilder promptBuilder;

    @Mock
    private LlmClient llmClient;

    @Mock
    private UsageService usageService;

    @Mock
    private GitHubReviewClient gitHubReviewClient;

    @InjectMocks
    private CommentResponseService commentResponseService;

    @Test
    @DisplayName("성공적으로 응답 생성")
    void generateResponse_성공적으로응답생성() throws Exception {
        // given
        String apiKey = "test-api-key";
        String commentBody = "이 부분 설명 부탁드립니다";

        ReviewContext context = ReviewContext.builder()
                .id(1L)
                .repositoryId(TEST_REPOSITORY_ID)
                .prNumber(TEST_PR_NUMBER)
                .featureName("PAYMENT")
                .headSha("abc123")
                .fileContexts("[]")
                .generalReview("리뷰 내용")
                .inlineComments("[]")
                .botCommentIds("[]")
                .build();

        List<ReviewContext> contexts = Arrays.asList(context);
        String systemPrompt = "시스템 프롬프트";
        String userPrompt = "사용자 프롬프트";
        LlmCommentResponse llmResponse = new LlmCommentResponse("생성된 응답입니다", 1000, 500);

        when(reviewContextService.findByRepositoryIdAndPrNumber(TEST_REPOSITORY_ID, TEST_PR_NUMBER)).thenReturn(contexts);
        when(gitHubReviewClient.getPrHeadSha(TEST_REPO_FULL_NAME, TEST_PR_NUMBER)).thenReturn("abc123"); // SHA 동일 → parseFileContexts 미호출
        when(promptBuilder.buildCommentResponseSystemPrompt()).thenReturn(systemPrompt);
        when(promptBuilder.buildCommentResponsePrompt(eq(commentBody), eq(contexts), eq("abc123"), anyMap())).thenReturn(userPrompt);
        when(llmClient.generateCommentResponse(apiKey, systemPrompt, userPrompt)).thenReturn(llmResponse);

        // when
        Optional<String> result = commentResponseService.generateResponse(apiKey, TEST_REPO_FULL_NAME, TEST_REPOSITORY_ID, TEST_PR_NUMBER, commentBody);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("생성된 응답입니다");

        verify(reviewContextService).findByRepositoryIdAndPrNumber(TEST_REPOSITORY_ID, TEST_PR_NUMBER);
        verify(gitHubReviewClient).getPrHeadSha(TEST_REPO_FULL_NAME, TEST_PR_NUMBER);
        verify(promptBuilder).buildCommentResponseSystemPrompt();
        verify(promptBuilder).buildCommentResponsePrompt(eq(commentBody), eq(contexts), eq("abc123"), anyMap());
        verify(llmClient).generateCommentResponse(apiKey, systemPrompt, userPrompt);
    }

    @Test
    @DisplayName("컨텍스트 없으면 빈 값 반환")
    void generateResponse_컨텍스트없으면빈값반환() {
        // given
        String apiKey = "test-api-key";
        String commentBody = "질문입니다";

        when(reviewContextService.findByRepositoryIdAndPrNumber(TEST_REPOSITORY_ID, TEST_PR_NUMBER)).thenReturn(List.of());

        // when
        Optional<String> result = commentResponseService.generateResponse(apiKey, TEST_REPO_FULL_NAME, TEST_REPOSITORY_ID, TEST_PR_NUMBER, commentBody);

        // then
        assertThat(result).isEmpty();

        verify(reviewContextService).findByRepositoryIdAndPrNumber(TEST_REPOSITORY_ID, TEST_PR_NUMBER);
        verify(promptBuilder, never()).buildCommentResponseSystemPrompt();
        verify(promptBuilder, never()).buildCommentResponsePrompt(anyString(), anyList(), anyString(), anyMap());
        verify(llmClient, never()).generateCommentResponse(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("LLM 빈 응답 시 빈 값 반환")
    void generateResponse_LLM빈응답시빈값반환() throws Exception {
        // given
        String apiKey = "test-api-key";
        String commentBody = "질문";

        ReviewContext context = ReviewContext.builder()
                .id(1L)
                .repositoryId(TEST_REPOSITORY_ID)
                .prNumber(TEST_PR_NUMBER)
                .featureName("PAYMENT")
                .headSha("abc123")
                .fileContexts("[]")
                .generalReview("리뷰")
                .inlineComments("[]")
                .botCommentIds("[]")
                .build();

        List<ReviewContext> contexts = Arrays.asList(context);

        when(reviewContextService.findByRepositoryIdAndPrNumber(TEST_REPOSITORY_ID, TEST_PR_NUMBER)).thenReturn(contexts);
        when(gitHubReviewClient.getPrHeadSha(TEST_REPO_FULL_NAME, TEST_PR_NUMBER)).thenReturn("abc123");
        when(promptBuilder.buildCommentResponseSystemPrompt()).thenReturn("시스템");
        when(promptBuilder.buildCommentResponsePrompt(eq(commentBody), eq(contexts), eq("abc123"), anyMap())).thenReturn("사용자");
        when(llmClient.generateCommentResponse(eq(apiKey), anyString(), anyString())).thenReturn(null);

        // when
        Optional<String> result = commentResponseService.generateResponse(apiKey, TEST_REPO_FULL_NAME, TEST_REPOSITORY_ID, TEST_PR_NUMBER, commentBody);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("예외 발생 시 빈 값 반환")
    void generateResponse_예외발생시빈값반환() {
        // given
        String apiKey = "test-api-key";
        String commentBody = "질문";

        when(reviewContextService.findByRepositoryIdAndPrNumber(TEST_REPOSITORY_ID, TEST_PR_NUMBER))
                .thenThrow(new RuntimeException("DB 오류"));

        // when
        Optional<String> result = commentResponseService.generateResponse(apiKey, TEST_REPO_FULL_NAME, TEST_REPOSITORY_ID, TEST_PR_NUMBER, commentBody);

        // then
        assertThat(result).isEmpty();

        verify(reviewContextService).findByRepositoryIdAndPrNumber(TEST_REPOSITORY_ID, TEST_PR_NUMBER);
        verify(promptBuilder, never()).buildCommentResponseSystemPrompt();
        verify(llmClient, never()).generateCommentResponse(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("TC9: HEAD SHA 변경 시 최신 코드 조회 + 변경 감지 프롬프트 포함")
    void generateResponse_SHA변경시최신코드조회() throws Exception {
        // given
        String apiKey = "test-api-key";
        String commentBody = "이 부분 왜 이렇게 했나요?";
        String oldSha = "old_sha_abc";
        String newSha = "new_sha_def";
        String filePath = "src/main/java/Payment.java";
        String latestCode = "public class Payment { /* 최신 코드 */ }";

        FileContextData fileContext = FileContextData.builder()
                .path(filePath)
                .diff("@@ -1,3 +1,5 @@")
                .build();

        ReviewContext context = ReviewContext.builder()
                .id(1L)
                .repositoryId(TEST_REPOSITORY_ID)
                .prNumber(TEST_PR_NUMBER)
                .featureName("PAYMENT")
                .headSha(oldSha)  // 리뷰 시점 SHA
                .fileContexts("[{\"path\":\"" + filePath + "\",\"diff\":\"@@ -1,3 +1,5 @@\"}]")
                .generalReview("리뷰 내용")
                .inlineComments("[]")
                .botCommentIds("[]")
                .build();

        List<ReviewContext> contexts = Arrays.asList(context);

        when(reviewContextService.findByRepositoryIdAndPrNumber(TEST_REPOSITORY_ID, TEST_PR_NUMBER)).thenReturn(contexts);
        when(gitHubReviewClient.getPrHeadSha(TEST_REPO_FULL_NAME, TEST_PR_NUMBER)).thenReturn(newSha); // SHA 다름
        when(reviewContextService.parseFileContexts(anyString())).thenReturn(Arrays.asList(fileContext));
        when(gitHubReviewClient.getFileContent(TEST_REPO_FULL_NAME, newSha, filePath)).thenReturn(latestCode);
        when(promptBuilder.buildCommentResponseSystemPrompt()).thenReturn("시스템");
        when(promptBuilder.buildCommentResponsePrompt(eq(commentBody), eq(contexts), eq(newSha), anyMap())).thenReturn("프롬프트");
        when(llmClient.generateCommentResponse(eq(apiKey), anyString(), eq("프롬프트"))).thenReturn(
                new LlmCommentResponse("답변", 500, 200));

        // when
        Optional<String> result = commentResponseService.generateResponse(
                apiKey, TEST_REPO_FULL_NAME, TEST_REPOSITORY_ID, TEST_PR_NUMBER, commentBody);

        // then
        assertThat(result).isPresent();

        // 최신 파일 조회 호출 검증
        verify(gitHubReviewClient).getFileContent(TEST_REPO_FULL_NAME, newSha, filePath);

        // 변경 감지 정보(newSha + latestFileContents)가 PromptBuilder에 전달되었는지 검증
        verify(promptBuilder).buildCommentResponsePrompt(
                eq(commentBody), eq(contexts), eq(newSha),
                argThat(map -> map.containsKey(filePath) && latestCode.equals(map.get(filePath))));
    }

    @Test
    @DisplayName("TC10: HEAD SHA 동일 시 저장된 컨텍스트만 사용, 최신 코드 미조회")
    void generateResponse_SHA동일시최신코드미조회() throws Exception {
        // given
        String apiKey = "test-api-key";
        String commentBody = "이 부분 왜 이렇게 했나요?";
        String sameSha = "same_sha_abc";

        ReviewContext context = ReviewContext.builder()
                .id(1L)
                .repositoryId(TEST_REPOSITORY_ID)
                .prNumber(TEST_PR_NUMBER)
                .featureName("PAYMENT")
                .headSha(sameSha)  // 현재 SHA와 동일
                .fileContexts("[]")
                .generalReview("리뷰 내용")
                .inlineComments("[]")
                .botCommentIds("[]")
                .build();

        List<ReviewContext> contexts = Arrays.asList(context);

        when(reviewContextService.findByRepositoryIdAndPrNumber(TEST_REPOSITORY_ID, TEST_PR_NUMBER)).thenReturn(contexts);
        when(gitHubReviewClient.getPrHeadSha(TEST_REPO_FULL_NAME, TEST_PR_NUMBER)).thenReturn(sameSha); // SHA 동일 → parseFileContexts 미호출
        when(promptBuilder.buildCommentResponseSystemPrompt()).thenReturn("시스템");
        when(promptBuilder.buildCommentResponsePrompt(eq(commentBody), eq(contexts), eq(sameSha), anyMap())).thenReturn("프롬프트");
        when(llmClient.generateCommentResponse(eq(apiKey), anyString(), eq("프롬프트"))).thenReturn(
                new LlmCommentResponse("답변", 500, 200));

        // when
        Optional<String> result = commentResponseService.generateResponse(
                apiKey, TEST_REPO_FULL_NAME, TEST_REPOSITORY_ID, TEST_PR_NUMBER, commentBody);

        // then
        assertThat(result).isPresent();

        // SHA가 동일하면 최신 파일 조회 없음
        verify(gitHubReviewClient, never()).getFileContent(anyString(), anyString(), anyString());

        // 빈 map으로 PromptBuilder 호출 (변경 없음)
        verify(promptBuilder).buildCommentResponsePrompt(
                eq(commentBody), eq(contexts), eq(sameSha),
                argThat(Map::isEmpty));
    }
}