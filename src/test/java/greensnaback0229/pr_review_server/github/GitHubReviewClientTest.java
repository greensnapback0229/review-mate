package greensnaback0229.pr_review_server.github;

import greensnaback0229.pr_review_server.config.GitHubConfig;
import greensnaback0229.pr_review_server.llm.dto.InlineComment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GitHubReviewClient 테스트")
class GitHubReviewClientTest {

    private static final String TEST_REPO_FULL_NAME = "owner/repo";
    private static final int TEST_PR_NUMBER = 1;

    @Mock
    private GitHubConfig githubConfig;

    @Mock
    private GitHub github;

    @Mock
    private GHRepository repository;

    @Mock
    private GHPullRequest pullRequest;

    @Mock
    private GHPullRequestReviewBuilder reviewBuilder;

    @Mock
    private GHPullRequestReview review;

    @Mock
    private PagedIterable<GHPullRequestReviewComment> reviewComments;

    @InjectMocks
    private GitHubReviewClient gitHubReviewClient;

    @BeforeEach
    void setUp() throws IOException {
        when(githubConfig.createGitHubClient(TEST_REPO_FULL_NAME)).thenReturn(github);
        when(github.getRepository(TEST_REPO_FULL_NAME)).thenReturn(repository);
        when(repository.getPullRequest(TEST_PR_NUMBER)).thenReturn(pullRequest);
    }

    // ──────────────────────────────────────────────────────────
    // createReview — singleLineComment (DL-01)
    // ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("createReview_인라인코멘트_singleLineComment호출검증")
    void createReview_인라인코멘트_singleLineComment호출검증() throws IOException {
        // given
        InlineComment comment = InlineComment.builder()
                .path("src/main/java/Foo.java")
                .line(45)
                .body("null 체크가 필요합니다.")
                .build();

        when(pullRequest.createReview()).thenReturn(reviewBuilder);
        when(reviewBuilder.body(any())).thenReturn(reviewBuilder);
        when(reviewBuilder.singleLineComment(any(), any(), anyInt())).thenReturn(reviewBuilder);
        when(reviewBuilder.event(any())).thenReturn(reviewBuilder);
        when(reviewBuilder.create()).thenReturn(review);

        when(review.listReviewComments()).thenReturn(reviewComments);
        when(reviewComments.toList()).thenReturn(Collections.emptyList());

        // when
        gitHubReviewClient.createReview(TEST_REPO_FULL_NAME, TEST_PR_NUMBER,
                "전반적인 리뷰", List.of(comment));

        // then: position이 아닌 실제 라인 번호로 singleLineComment 호출
        verify(reviewBuilder).singleLineComment("null 체크가 필요합니다.", "src/main/java/Foo.java", 45);
        verify(reviewBuilder, never()).comment(any(), any(), anyInt());
    }

    @Test
    @DisplayName("createReview_인라인코멘트_여러개_모두singleLineComment호출")
    void createReview_인라인코멘트_여러개_모두singleLineComment호출() throws IOException {
        // given
        List<InlineComment> comments = List.of(
                InlineComment.builder().path("A.java").line(10).body("코멘트A").build(),
                InlineComment.builder().path("B.java").line(200).body("코멘트B").build(),
                InlineComment.builder().path("A.java").line(137).body("코멘트C").build()  // hunk 밖 라인
        );

        when(pullRequest.createReview()).thenReturn(reviewBuilder);
        when(reviewBuilder.body(any())).thenReturn(reviewBuilder);
        when(reviewBuilder.singleLineComment(any(), any(), anyInt())).thenReturn(reviewBuilder);
        when(reviewBuilder.event(any())).thenReturn(reviewBuilder);
        when(reviewBuilder.create()).thenReturn(review);

        when(review.listReviewComments()).thenReturn(reviewComments);
        when(reviewComments.toList()).thenReturn(Collections.emptyList());

        // when
        gitHubReviewClient.createReview(TEST_REPO_FULL_NAME, TEST_PR_NUMBER, "리뷰", comments);

        // then: diff hunk 여부와 무관하게 3개 모두 호출
        verify(reviewBuilder, times(3)).singleLineComment(any(), any(), anyInt());
        verify(reviewBuilder).singleLineComment("코멘트A", "A.java", 10);
        verify(reviewBuilder).singleLineComment("코멘트B", "B.java", 200);
        verify(reviewBuilder).singleLineComment("코멘트C", "A.java", 137);
    }

    @Test
    @DisplayName("createReview_인라인코멘트없으면_singleLineComment미호출")
    void createReview_인라인코멘트없으면_singleLineComment미호출() throws IOException {
        // given
        when(pullRequest.createReview()).thenReturn(reviewBuilder);
        when(reviewBuilder.body(any())).thenReturn(reviewBuilder);
        when(reviewBuilder.event(any())).thenReturn(reviewBuilder);
        when(reviewBuilder.create()).thenReturn(review);

        when(review.listReviewComments()).thenReturn(reviewComments);
        when(reviewComments.toList()).thenReturn(Collections.emptyList());

        // when
        gitHubReviewClient.createReview(TEST_REPO_FULL_NAME, TEST_PR_NUMBER, "리뷰", List.of());

        // then
        verify(reviewBuilder, never()).singleLineComment(any(), any(), anyInt());
        verify(reviewBuilder, never()).comment(any(), any(), anyInt());
    }

    @Test
    @DisplayName("리뷰 코멘트 목록이 비어있으면 IOException 발생")
    void replyToReviewComment_코멘트목록비어있으면예외() throws IOException {
        // given
        when(pullRequest.listReviewComments()).thenReturn(reviewComments);
        when(reviewComments.toList()).thenReturn(List.of());

        // when & then
        assertThatThrownBy(() -> gitHubReviewClient.replyToReviewComment(
                TEST_REPO_FULL_NAME,
                TEST_PR_NUMBER,
                999L,
                "답글"
        ))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Review comment not found: 999");

        verify(pullRequest).listReviewComments();
    }

    @Test
    @DisplayName("GitHub API 클라이언트 생성 및 PR 조회 체인 검증")
    void replyToReviewComment_API호출체인검증() throws IOException {
        // given
        when(pullRequest.listReviewComments()).thenReturn(reviewComments);
        when(reviewComments.toList()).thenReturn(List.of());

        // when (코멘트를 찾지 못해 예외 발생하지만, API 호출 체인은 검증 가능)
        try {
            gitHubReviewClient.replyToReviewComment(
                    TEST_REPO_FULL_NAME, TEST_PR_NUMBER, 1L, "답글");
        } catch (IOException ignored) {
            // 예상된 예외
        }

        // then - GitHub API 호출 체인 검증
        verify(githubConfig).createGitHubClient(TEST_REPO_FULL_NAME);
        verify(github).getRepository(TEST_REPO_FULL_NAME);
        verify(repository).getPullRequest(TEST_PR_NUMBER);
        verify(pullRequest).listReviewComments();
    }
}
