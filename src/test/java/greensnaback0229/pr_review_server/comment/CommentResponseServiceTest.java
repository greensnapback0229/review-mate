package greensnaback0229.pr_review_server.comment;

import greensnaback0229.pr_review_server.comment.entity.ReviewContext;
import greensnaback0229.pr_review_server.llm.LlmClient;
import greensnaback0229.pr_review_server.prompt.PromptBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentResponseService 테스트")
class CommentResponseServiceTest {

    @Mock
    private ReviewContextService reviewContextService;

    @Mock
    private PromptBuilder promptBuilder;

    @Mock
    private LlmClient llmClient;

    @InjectMocks
    private CommentResponseService commentResponseService;

    @Test
    @DisplayName("성공적으로 응답 생성")
    void generateResponse_성공적으로응답생성() {
        // given
        String apiKey = "test-api-key";
        Long repositoryId = 123456789L;
        int prNumber = 1;
        String commentBody = "이 부분 설명 부탁드립니다";

        ReviewContext context = ReviewContext.builder()
                .id(1L)
                .repositoryId(repositoryId)
                .prNumber(prNumber)
                .featureName("PAYMENT")
                .headSha("abc123")
                .fileContexts("{}")
                .generalReview("리뷰 내용")
                .inlineComments("[]")
                .botCommentIds("[]")
                .build();

        List<ReviewContext> contexts = Arrays.asList(context);
        String systemPrompt = "시스템 프롬프트";
        String userPrompt = "사용자 프롬프트";
        String llmResponse = "생성된 응답입니다";

        when(reviewContextService.findByRepositoryIdAndPrNumber(repositoryId, prNumber)).thenReturn(contexts);
        when(promptBuilder.buildCommentResponseSystemPrompt()).thenReturn(systemPrompt);
        when(promptBuilder.buildCommentResponsePrompt(commentBody, contexts)).thenReturn(userPrompt);
        when(llmClient.generateCommentResponse(apiKey, systemPrompt, userPrompt)).thenReturn(llmResponse);

        // when
        Optional<String> result = commentResponseService.generateResponse(apiKey, repositoryId, prNumber, commentBody);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("생성된 응답입니다");

        verify(reviewContextService).findByRepositoryIdAndPrNumber(repositoryId, prNumber);
        verify(promptBuilder).buildCommentResponseSystemPrompt();
        verify(promptBuilder).buildCommentResponsePrompt(commentBody, contexts);
        verify(llmClient).generateCommentResponse(apiKey, systemPrompt, userPrompt);
    }

    @Test
    @DisplayName("컨텍스트 없으면 빈 값 반환")
    void generateResponse_컨텍스트없으면빈값반환() {
        // given
        String apiKey = "test-api-key";
        Long repositoryId = 123456789L;
        int prNumber = 1;
        String commentBody = "질문입니다";

        when(reviewContextService.findByRepositoryIdAndPrNumber(repositoryId, prNumber)).thenReturn(List.of());

        // when
        Optional<String> result = commentResponseService.generateResponse(apiKey, repositoryId, prNumber, commentBody);

        // then
        assertThat(result).isEmpty();

        verify(reviewContextService).findByRepositoryIdAndPrNumber(repositoryId, prNumber);
        verify(promptBuilder, never()).buildCommentResponseSystemPrompt();
        verify(promptBuilder, never()).buildCommentResponsePrompt(anyString(), anyList());
        verify(llmClient, never()).generateCommentResponse(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("LLM 빈 응답 시 빈 값 반환")
    void generateResponse_LLM빈응답시빈값반환() {
        // given
        String apiKey = "test-api-key";
        Long repositoryId = 123456789L;
        int prNumber = 1;
        String commentBody = "질문";

        ReviewContext context = ReviewContext.builder()
                .id(1L)
                .repositoryId(repositoryId)
                .prNumber(prNumber)
                .featureName("PAYMENT")
                .headSha("abc123")
                .fileContexts("{}")
                .generalReview("리뷰")
                .inlineComments("[]")
                .botCommentIds("[]")
                .build();

        List<ReviewContext> contexts = Arrays.asList(context);
        String systemPrompt = "시스템";
        String userPrompt = "사용자";

        when(reviewContextService.findByRepositoryIdAndPrNumber(repositoryId, prNumber)).thenReturn(contexts);
        when(promptBuilder.buildCommentResponseSystemPrompt()).thenReturn(systemPrompt);
        when(promptBuilder.buildCommentResponsePrompt(commentBody, contexts)).thenReturn(userPrompt);
        when(llmClient.generateCommentResponse(apiKey, systemPrompt, userPrompt)).thenReturn(null);

        // when
        Optional<String> result = commentResponseService.generateResponse(apiKey, repositoryId, prNumber, commentBody);

        // then
        assertThat(result).isEmpty();

        verify(llmClient).generateCommentResponse(apiKey, systemPrompt, userPrompt);
    }

    @Test
    @DisplayName("예외 발생 시 빈 값 반환")
    void generateResponse_예외발생시빈값반환() {
        // given
        String apiKey = "test-api-key";
        Long repositoryId = 123456789L;
        int prNumber = 1;
        String commentBody = "질문";

        when(reviewContextService.findByRepositoryIdAndPrNumber(repositoryId, prNumber))
                .thenThrow(new RuntimeException("DB 오류"));

        // when
        Optional<String> result = commentResponseService.generateResponse(apiKey, repositoryId, prNumber, commentBody);

        // then
        assertThat(result).isEmpty();

        verify(reviewContextService).findByRepositoryIdAndPrNumber(repositoryId, prNumber);
        verify(promptBuilder, never()).buildCommentResponseSystemPrompt();
        verify(llmClient, never()).generateCommentResponse(anyString(), anyString(), anyString());
    }
}
