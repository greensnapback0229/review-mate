package greensnaback0229.pr_review_server.webhook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GitHub Webhook Payload DTO
 * PR 이벤트를 수신하기 위한 데이터 구조
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookPayload {
    /**
     * 이벤트 액션 (opened, synchronize, reopened 등)
     */
    private String action;
    
    /**
     * PR 정보
     */
    @JsonProperty("pull_request")
    private PullRequest pullRequest;
    
    /**
     * 저장소 정보
     */
    private Repository repository;
    
    /**
     * PR 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PullRequest {
        /**
         * PR 번호
         */
        private int number;
        
        /**
         * PR 제목
         */
        private String title;
        
        /**
         * PR 본문
         */
        private String body;
        
        /**
         * Base 브랜치
         */
        private Branch base;
        
        /**
         * Head 브랜치
         */
        private Branch head;
    }
    
    /**
     * 브랜치 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Branch {
        /**
         * 브랜치명
         */
        private String ref;
        
        /**
         * SHA
         */
        private String sha;
    }
    
    /**
     * 저장소 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Repository {
        /**
         * 저장소 풀네임 (owner/repo)
         */
        @JsonProperty("full_name")
        private String fullName;
        
        /**
         * 저장소명
         */
        private String name;
        
        /**
         * Owner 정보
         */
        private Owner owner;
    }
    
    /**
     * Owner 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Owner {
        /**
         * Owner 로그인명
         */
        private String login;
    }
}
