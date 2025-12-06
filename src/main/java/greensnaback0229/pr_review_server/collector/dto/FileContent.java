package greensnaback0229.pr_review_server.collector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 파일 내용을 담는 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileContent {
    /**
     * 파일 경로
     */
    private String path;
    
    /**
     * 파일 전체 내용 (핵심 파일이나 추가 요청 파일용)
     */
    private String content;
    
    /**
     * 파일 변경 diff (변경된 파일용)
     */
    private String diff;
    
    /**
     * 파일 타입 (CHANGED: 변경됨, CORE: 핵심파일, ADDITIONAL: 추가요청)
     */
    private FileType type;
    
    public enum FileType {
        CHANGED,      // 변경된 파일 (diff만 포함)
        CORE,         // 핵심 파일 (전체 코드)
        ADDITIONAL    // 추가 요청 파일 (전체 코드)
    }
}
