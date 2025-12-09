package greensnaback0229.pr_review_server.collector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 수집된 모든 코드를 담는 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectedCode {
    /**
     * 변경된 파일들 (diff 포함)
     */
    @Builder.Default
    private List<FileContent> changedFiles = new ArrayList<>();
    
    /**
     * 핵심 파일들 (전체 코드)
     */
    @Builder.Default
    private List<FileContent> coreFiles = new ArrayList<>();
    
    /**
     * 추가 요청 파일들 (전체 코드)
     */
    @Builder.Default
    private List<FileContent> additionalFiles = new ArrayList<>();
    
    /**
     * 전체 파일 수
     */
    public int getTotalFileCount() {
        return changedFiles.size() + coreFiles.size() + additionalFiles.size();
    }
}
