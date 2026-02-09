package greensnaback0229.pr_review_server.github;

import greensnaback0229.pr_review_server.config.GitHubConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
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
    private PagedIterable<GHPullRequestReviewComment> reviewComments;

    @InjectMocks
    private GitHubReviewClient gitHubReviewClient;

    @BeforeEach
    void setUp() throws IOException {
        when(githubConfig.createGitHubClient(TEST_REPO_FULL_NAME)).thenReturn(github);
        when(github.getRepository(TEST_REPO_FULL_NAME)).thenReturn(repository);
        when(repository.getPullRequest(TEST_PR_NUMBER)).thenReturn(pullRequest);
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
