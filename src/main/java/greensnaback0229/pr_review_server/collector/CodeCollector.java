package greensnaback0229.pr_review_server.collector;

import greensnaback0229.pr_review_server.collector.dto.CollectedCode;
import greensnaback0229.pr_review_server.collector.dto.FileContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GitHub에서 PR 관련 코드를 수집하는 컴포넌트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeCollector {
    
    private final GitHub github;
    
    /**
     * PR의 변경된 파일들의 diff를 수집
     *
     * @param repoFullName 저장소 풀네임 (예: "owner/repo")
     * @param prNumber PR 번호
     * @param filteredPaths 필터링할 파일 경로 리스트 (Feature의 paths 기준으로 필터링된 것)
     * @return 수집된 변경 파일 목록
     */
    public List<FileContent> collectChangedFiles(String repoFullName, int prNumber, List<String> filteredPaths) {
        try {
            GHRepository repo = github.getRepository(repoFullName);
            GHPullRequest pr = repo.getPullRequest(prNumber);
            
            List<FileContent> changedFiles = new ArrayList<>();
            
            // PR의 변경된 파일들을 리스트로 가져옴
            List<GHPullRequestFileDetail> files = pr.listFiles().toList();
            
            for (GHPullRequestFileDetail file : files) {
                String filePath = file.getFilename();
                
                // filteredPaths에 포함된 파일만 수집
                if (filteredPaths.contains(filePath)) {
                    FileContent fileContent = FileContent.builder()
                            .path(filePath)
                            .diff(file.getPatch()) // GitHub API가 제공하는 diff
                            .type(FileContent.FileType.CHANGED)
                            .build();
                    
                    changedFiles.add(fileContent);
                    log.info("Collected changed file: {}", filePath);
                }
            }
            
            return changedFiles;
            
        } catch (IOException e) {
            log.error("Failed to collect changed files from PR: {}/{}", repoFullName, prNumber, e);
            throw new RuntimeException("Failed to collect changed files", e);
        }
    }
    
    /**
     * 핵심 파일들의 전체 코드를 수집
     *
     * @param repoFullName 저장소 풀네임
     * @param branch 브랜치명 (보통 PR의 base 브랜치)
     * @param coreFilePaths 핵심 파일 경로 리스트
     * @return 수집된 핵심 파일 목록
     */
    public List<FileContent> collectCoreFiles(String repoFullName, String branch, List<String> coreFilePaths) {
        return collectFiles(repoFullName, branch, coreFilePaths, FileContent.FileType.CORE);
    }
    
    /**
     * 추가 요청된 파일들의 전체 코드를 수집
     *
     * @param repoFullName 저장소 풀네임
     * @param branch 브랜치명
     * @param additionalFilePaths 추가 요청 파일 경로 리스트
     * @return 수집된 추가 파일 목록
     */
    public List<FileContent> collectAdditionalFiles(String repoFullName, String branch, List<String> additionalFilePaths) {
        return collectFiles(repoFullName, branch, additionalFilePaths, FileContent.FileType.ADDITIONAL);
    }
    
    /**
     * 지정된 파일들의 전체 코드를 수집 (공통 로직)
     */
    private List<FileContent> collectFiles(String repoFullName, String branch, List<String> filePaths, FileContent.FileType type) {
        try {
            GHRepository repo = github.getRepository(repoFullName);
            List<FileContent> files = new ArrayList<>();
            
            for (String filePath : filePaths) {
                try {
                    // 파일 내용 가져오기
                    GHContent content = repo.getFileContent(filePath, branch);
                    String fileContent = content.getContent(); // Base64 디코딩된 내용
                    
                    FileContent file = FileContent.builder()
                            .path(filePath)
                            .content(fileContent)
                            .type(type)
                            .build();
                    
                    files.add(file);
                    log.info("Collected {} file: {}", type, filePath);
                    
                } catch (IOException e) {
                    log.warn("Failed to collect file: {} ({})", filePath, e.getMessage());
                    // 개별 파일 실패는 무시하고 계속 진행
                }
            }
            
            return files;
            
        } catch (IOException e) {
            log.error("Failed to access repository: {}", repoFullName, e);
            throw new RuntimeException("Failed to collect files", e);
        }
    }
    
    /**
     * 전체 코드 수집 - 변경 파일 + 핵심 파일
     *
     * @param repoFullName 저장소 풀네임
     * @param prNumber PR 번호
     * @param branch 브랜치명 (base branch)
     * @param filteredPaths 필터링된 변경 파일 경로
     * @param coreFilePaths 핵심 파일 경로
     * @return 수집된 모든 코드
     */
    public CollectedCode collectAll(String repoFullName, int prNumber, String branch, 
                                     List<String> filteredPaths, List<String> coreFilePaths) {
        List<FileContent> changedFiles = collectChangedFiles(repoFullName, prNumber, filteredPaths);
        List<FileContent> coreFiles = collectCoreFiles(repoFullName, branch, coreFilePaths);
        
        return CollectedCode.builder()
                .changedFiles(changedFiles)
                .coreFiles(coreFiles)
                .additionalFiles(new ArrayList<>())
                .build();
    }
    
    /**
     * PR에서 변경된 모든 파일 경로 조회 (필터링 전)
     * 
     * @param repoFullName 저장소 풀네임
     * @param prNumber PR 번호
     * @return 변경된 파일 경로 리스트
     */
    public List<String> getChangedFilePaths(String repoFullName, int prNumber) {
        try {
            GHRepository repo = github.getRepository(repoFullName);
            GHPullRequest pr = repo.getPullRequest(prNumber);
            
            return pr.listFiles().toList().stream()
                    .map(GHPullRequestFileDetail::getFilename)
                    .collect(Collectors.toList());
                    
        } catch (IOException e) {
            log.error("Failed to get changed file paths from PR: {}/{}", repoFullName, prNumber, e);
            throw new RuntimeException("Failed to get changed file paths", e);
        }
    }
}
