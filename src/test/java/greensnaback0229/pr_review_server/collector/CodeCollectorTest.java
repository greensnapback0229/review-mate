package greensnaback0229.pr_review_server.collector;

import greensnaback0229.pr_review_server.collector.dto.CollectedCode;
import greensnaback0229.pr_review_server.collector.dto.FileContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CodeCollector 테스트")
class CodeCollectorTest {
    
    @Mock
    private GitHub github;
    
    @Mock
    private GHRepository repository;
    
    @Mock
    private GHPullRequest pullRequest;
    
    @Mock
    private GHContent ghContent;
    
    @Mock
    private PagedIterable<GHPullRequestFileDetail> pagedIterable;
    
    private CodeCollector codeCollector;
    
    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        codeCollector = new CodeCollector(github);
        
        // PagedIterable toList() mock 설정
        when(pagedIterable.toList()).thenReturn(Arrays.asList());
    }
    
    @Test
    @DisplayName("PR의 변경된 파일 diff를 수집한다")
    void collectChangedFiles() throws IOException {
        // given
        String repoFullName = "owner/repo";
        int prNumber = 1;
        List<String> filteredPaths = Arrays.asList(
                "src/main/java/PaymentService.java",
                "src/main/java/OrderService.java"
        );
        
        GHPullRequestFileDetail file1 = mock(GHPullRequestFileDetail.class);
        when(file1.getFilename()).thenReturn("src/main/java/PaymentService.java");
        when(file1.getPatch()).thenReturn("@@ -1,3 +1,4 @@\n+added line\n existing line");
        
        GHPullRequestFileDetail file2 = mock(GHPullRequestFileDetail.class);
        when(file2.getFilename()).thenReturn("src/main/java/OrderService.java");
        when(file2.getPatch()).thenReturn("@@ -5,2 +5,3 @@\n+another added line");
        
        GHPullRequestFileDetail file3 = mock(GHPullRequestFileDetail.class);
        when(file3.getFilename()).thenReturn("src/main/java/UserService.java");
        
        when(github.getRepository(repoFullName)).thenReturn(repository);
        when(repository.getPullRequest(prNumber)).thenReturn(pullRequest);
        when(pullRequest.listFiles()).thenReturn(pagedIterable);
        when(pagedIterable.toList()).thenReturn(Arrays.asList(file1, file2, file3));
        
        // when
        List<FileContent> result = codeCollector.collectChangedFiles(repoFullName, prNumber, filteredPaths);
        
        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPath()).isEqualTo("src/main/java/PaymentService.java");
        assertThat(result.get(0).getDiff()).contains("added line");
        assertThat(result.get(0).getType()).isEqualTo(FileContent.FileType.CHANGED);
        assertThat(result.get(1).getPath()).isEqualTo("src/main/java/OrderService.java");
    }
    
    @Test
    @DisplayName("핵심 파일의 전체 코드를 수집한다")
    void collectCoreFiles() throws IOException {
        // given
        String repoFullName = "owner/repo";
        String branch = "main";
        List<String> coreFilePaths = Arrays.asList(
                "src/main/java/PaymentValidator.java"
        );
        
        when(github.getRepository(repoFullName)).thenReturn(repository);
        when(repository.getFileContent("src/main/java/PaymentValidator.java", branch)).thenReturn(ghContent);
        when(ghContent.getContent()).thenReturn("public class PaymentValidator {}");
        
        // when
        List<FileContent> result = codeCollector.collectCoreFiles(repoFullName, branch, coreFilePaths);
        
        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPath()).isEqualTo("src/main/java/PaymentValidator.java");
        assertThat(result.get(0).getContent()).isEqualTo("public class PaymentValidator {}");
        assertThat(result.get(0).getType()).isEqualTo(FileContent.FileType.CORE);
    }
    
    @Test
    @DisplayName("추가 요청 파일의 전체 코드를 수집한다")
    void collectAdditionalFiles() throws IOException {
        // given
        String repoFullName = "owner/repo";
        String branch = "main";
        List<String> additionalFilePaths = Arrays.asList(
                "src/main/java/MoneyUtils.java"
        );
        
        when(github.getRepository(repoFullName)).thenReturn(repository);
        when(repository.getFileContent("src/main/java/MoneyUtils.java", branch)).thenReturn(ghContent);
        when(ghContent.getContent()).thenReturn("public class MoneyUtils {}");
        
        // when
        List<FileContent> result = codeCollector.collectAdditionalFiles(repoFullName, branch, additionalFilePaths);
        
        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPath()).isEqualTo("src/main/java/MoneyUtils.java");
        assertThat(result.get(0).getContent()).isEqualTo("public class MoneyUtils {}");
        assertThat(result.get(0).getType()).isEqualTo(FileContent.FileType.ADDITIONAL);
    }
    
    @Test
    @DisplayName("전체 코드를 한번에 수집한다")
    void collectAll() throws IOException {
        // given
        String repoFullName = "owner/repo";
        int prNumber = 1;
        String branch = "main";
        List<String> filteredPaths = Arrays.asList("src/main/java/PaymentService.java");
        List<String> coreFilePaths = Arrays.asList("src/main/java/PaymentValidator.java");
        
        // PR 변경 파일 mock
        GHPullRequestFileDetail file1 = mock(GHPullRequestFileDetail.class);
        when(file1.getFilename()).thenReturn("src/main/java/PaymentService.java");
        when(file1.getPatch()).thenReturn("@@ -1,3 +1,4 @@\n+added line");
        
        when(github.getRepository(repoFullName)).thenReturn(repository);
        when(repository.getPullRequest(prNumber)).thenReturn(pullRequest);
        when(pullRequest.listFiles()).thenReturn(pagedIterable);
        when(pagedIterable.toList()).thenReturn(Arrays.asList(file1));
        
        // 핵심 파일 mock
        when(repository.getFileContent("src/main/java/PaymentValidator.java", branch)).thenReturn(ghContent);
        when(ghContent.getContent()).thenReturn("public class PaymentValidator {}");
        
        // when
        CollectedCode result = codeCollector.collectAll(repoFullName, prNumber, branch, filteredPaths, coreFilePaths);
        
        // then
        assertThat(result.getChangedFiles()).hasSize(1);
        assertThat(result.getCoreFiles()).hasSize(1);
        assertThat(result.getAdditionalFiles()).isEmpty();
        assertThat(result.getTotalFileCount()).isEqualTo(2);
    }
    
    @Test
    @DisplayName("PR의 변경된 모든 파일 경로를 조회한다")
    void getChangedFilePaths() throws IOException {
        // given
        String repoFullName = "owner/repo";
        int prNumber = 1;
        
        GHPullRequestFileDetail file1 = mock(GHPullRequestFileDetail.class);
        when(file1.getFilename()).thenReturn("src/main/java/PaymentService.java");
        
        GHPullRequestFileDetail file2 = mock(GHPullRequestFileDetail.class);
        when(file2.getFilename()).thenReturn("src/main/java/OrderService.java");
        
        when(github.getRepository(repoFullName)).thenReturn(repository);
        when(repository.getPullRequest(prNumber)).thenReturn(pullRequest);
        when(pullRequest.listFiles()).thenReturn(pagedIterable);
        when(pagedIterable.toList()).thenReturn(Arrays.asList(file1, file2));
        
        // when
        List<String> result = codeCollector.getChangedFilePaths(repoFullName, prNumber);
        
        // then
        assertThat(result).hasSize(2);
        assertThat(result).contains("src/main/java/PaymentService.java", "src/main/java/OrderService.java");
    }
    
    @Test
    @DisplayName("파일 수집 실패시 예외를 던진다")
    void collectChangedFiles_throwsException() throws IOException {
        // given
        String repoFullName = "owner/repo";
        int prNumber = 1;
        List<String> filteredPaths = Arrays.asList("src/main/java/PaymentService.java");
        
        when(github.getRepository(repoFullName)).thenThrow(new IOException("GitHub API error"));
        
        // when & then
        assertThatThrownBy(() -> codeCollector.collectChangedFiles(repoFullName, prNumber, filteredPaths))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to collect changed files");
    }
    
    @Test
    @DisplayName("개별 핵심 파일 조회 실패시 해당 파일만 제외하고 계속 진행한다")
    void collectCoreFiles_continuesOnIndividualFileError() throws IOException {
        // given
        String repoFullName = "owner/repo";
        String branch = "main";
        List<String> coreFilePaths = Arrays.asList(
                "src/main/java/PaymentValidator.java",
                "src/main/java/NonExistent.java"
        );
        
        when(github.getRepository(repoFullName)).thenReturn(repository);
        when(repository.getFileContent("src/main/java/PaymentValidator.java", branch)).thenReturn(ghContent);
        when(ghContent.getContent()).thenReturn("public class PaymentValidator {}");
        when(repository.getFileContent("src/main/java/NonExistent.java", branch))
                .thenThrow(new IOException("File not found"));
        
        // when
        List<FileContent> result = codeCollector.collectCoreFiles(repoFullName, branch, coreFilePaths);
        
        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPath()).isEqualTo("src/main/java/PaymentValidator.java");
    }
}
