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
     * 코멘트 정보 (issue_comment 이벤트용)
     */
    private Comment comment;

    /**
     * 이슈 정보 (issue_comment 이벤트의 경우 PR도 이슈로 취급)
     */
    private Issue issue;

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
         * 저장소 ID
         */
        private Long id;
        
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

    /**
     * 코멘트 정보 (pull_request_review_comment 이벤트)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Comment {
        /**
         * 코멘트 ID
         */
        private Long id;

        /**
         * 코멘트 내용
         */
        private String body;

        /**
         * 작성자 정보
         */
        private User user;

        /**
         * 답글 대상 코멘트 ID (스레드 답글인 경우)
         */
        @JsonProperty("in_reply_to_id")
        private Long inReplyToId;

        /**
         * 코멘트 대상 파일 경로
         */
        private String path;

        /**
         * 코멘트 대상 라인 번호
         */
        private Integer line;

        /**
         * PR 리뷰 ID
         */
        @JsonProperty("pull_request_review_id")
        private Long pullRequestReviewId;
    }

    /**
     * 이슈 정보 (PR도 이슈로 취급됨)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Issue {
        /**
         * 이슈/PR 번호
         */
        private int number;

        /**
         * PR 여부 확인용 (pull_request 필드가 존재하면 PR)
         */
        @JsonProperty("pull_request")
        private Object pullRequest;
    }

    /**
     * 사용자 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class User {
        /**
         * 사용자 로그인명
         */
        private String login;

        /**
         * 봇 여부
         */
        @JsonProperty("type")
        private String type;
    }
}
