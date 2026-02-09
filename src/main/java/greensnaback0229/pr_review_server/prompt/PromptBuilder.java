package greensnaback0229.pr_review_server.prompt;

import greensnaback0229.pr_review_server.feature.dto.FeatureMemory;
import greensnaback0229.pr_review_server.feature.dto.ResolvedFeature;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Prompt Builder
 * LLM에 전송할 프롬프트를 생성
 */
@Component
public class PromptBuilder {

    /**
     * 시스템 프롬프트 생성
     * 
     * @return 시스템 프롬프트
     */
    public String buildSystemPrompt() {
        return """
                당신은 전문 코드 리뷰어입니다.
                
                ## 역할
                - 변경된 코드를 분석하여 문제점과 개선사항을 제시합니다.
                - 버그, 성능 이슈, 보안 취약점, 코드 품질을 검토합니다.
                - 리뷰 후 Feature Memory를 업데이트하기 위한 제안을 생성합니다.
                
                ## 리뷰 원칙
                1. 구체적이고 실행 가능한 피드백 제공
                2. 긍정적인 부분도 언급
                3. 우선순위 명시 (Critical, Major, Minor)
                4. 특정 코드 라인 지적 시 반드시 파일 경로와 라인 번호 명시
                
                ## 응답 형식
                리뷰를 완료한 후 반드시 다음 형식으로 응답하세요:
                
                ### 추가 파일이 필요한 경우:
                ```json
                {
                  "needMoreContext": true,
                  "requestedFiles": ["FileName.java"],
                  "reason": "이유 설명"
                }
                ```
                
                ### 최종 리뷰 시:
                ```json
                {
                  "needMoreContext": false,
                  "generalReview": "전반적인 리뷰 내용 (마크다운 형식)",
                  "inlineComments": [
                    {
                      "path": "src/main/java/Payment.java",
                      "line": 45,
                      "body": "이 부분은 null 체크가 필요합니다."
                    }
                  ],
                  "memorySuggestion": {
                    "summary": "이 기능에 대한 간단한 요약",
                    "keyPoints": ["핵심 포인트 1", "핵심 포인트 2"],
                    "relatedFiles": ["변경된 파일 경로"]
                  }
                }
                ```
                
                **inlineComments 작성 규칙:**
                - path: 파일의 전체 경로 (예: "src/main/java/Payment.java")
                - line: 지적할 라인 번호 (라인 번호가 표시된 코드 기준)
                - body: 구체적인 코멘트 내용
                - 일반적인 내용은 generalReview에, 특정 라인 지적은 inlineComments에 작성
                - inlineComments가 없으면 빈 배열 []로 전달
                
                memorySuggestion은 이 기능에 대해 향후 리뷰 시 참고할 중요한 정보를 포함해야 합니다.
                """;
    }

    /**
     * 초기 리뷰 요청 프롬프트 생성
     * 
     * @param resolvedFeature 해석된 기능 정보
     * @param changedFiles 변경된 파일과 diff 맵
     * @param coreFilesContent 핵심 파일 전체 코드 맵
     * @return 사용자 메시지
     */
    public String buildInitialPrompt(
            ResolvedFeature resolvedFeature,
            Map<String, String> changedFiles,
            Map<String, String> coreFilesContent
    ) {
        StringBuilder prompt = new StringBuilder();
        
        // 기능 정보
        prompt.append("# 기능 정보\n");
        prompt.append("- 기능: ").append(resolvedFeature.getDefinition().getName()).append("\n");
        prompt.append("- 설명: ").append(resolvedFeature.getDefinition().getDescription()).append("\n\n");
        
        // Feature Memory (있는 경우)
        if (resolvedFeature.getMemory() != null) {
            FeatureMemory memory = resolvedFeature.getMemory();
            prompt.append("# 기능 메모리 (과거 지식)\n");
            prompt.append("- 요약: ").append(memory.getSummary()).append("\n");
            
            if (memory.getKeyPoints() != null && !memory.getKeyPoints().isEmpty()) {
                prompt.append("- 핵심 포인트:\n");
                for (String point : memory.getKeyPoints()) {
                    prompt.append("  * ").append(point).append("\n");
                }
            }
            prompt.append("\n");
        }
        
        // 변경된 파일 diff
        prompt.append("# 변경된 파일\n");
        for (Map.Entry<String, String> entry : changedFiles.entrySet()) {
            prompt.append("## ").append(entry.getKey()).append("\n");
            prompt.append("```diff\n");
            prompt.append(entry.getValue());
            prompt.append("\n```\n\n");
        }
        
        // 핵심 파일 전체 코드 (라인 번호 포함)
        if (!coreFilesContent.isEmpty()) {
            prompt.append("# 핵심 파일 (전체 코드 - 라인 번호 포함)\n");
            for (Map.Entry<String, String> entry : coreFilesContent.entrySet()) {
                prompt.append("## ").append(entry.getKey()).append("\n");
                prompt.append("```java\n");
                prompt.append(addLineNumbers(entry.getValue()));
                prompt.append("\n```\n\n");
            }
        }
        
        prompt.append("위 코드를 리뷰해주세요.\n\n");
        prompt.append("**중요:** 특정 코드 라인에 대한 지적사항이 있다면 반드시 파일 경로와 라인 번호를 명시해주세요.");
        
        return prompt.toString();
    }

    /**
     * 코드에 라인 번호 추가
     * 
     * @param content 원본 코드
     * @return 라인 번호가 추가된 코드
     */
    private String addLineNumbers(String content) {
        String[] lines = content.split("\n");
        StringBuilder numbered = new StringBuilder();
        
        for (int i = 0; i < lines.length; i++) {
            numbered.append(String.format("%4d: %s\n", i + 1, lines[i]));
        }
        
        return numbered.toString();
    }

    /**
     * 추가 파일 제공 프롬프트 생성
     * 
     * @param requestedFilesContent 요청된 파일 내용 맵
     * @return 사용자 메시지
     */
    public String buildFollowUpPrompt(Map<String, String> requestedFilesContent) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("# 요청하신 추가 파일\n");
        for (Map.Entry<String, String> entry : requestedFilesContent.entrySet()) {
            prompt.append("## ").append(entry.getKey()).append("\n");
            prompt.append("```java\n");
            prompt.append(entry.getValue());
            prompt.append("\n```\n\n");
        }
        
        prompt.append("이제 최종 리뷰를 진행해주세요.");

        return prompt.toString();
    }

    /**
     * 코멘트 응답 시스템 프롬프트 생성
     *
     * @return 시스템 프롬프트
     */
    public String buildCommentResponseSystemPrompt() {
        return """
                당신은 PR 리뷰 봇입니다. 사용자가 리뷰 코멘트에 질문하면 리뷰 컨텍스트를 기반으로 답변합니다.

                ## 역할
                - 이전에 작성한 리뷰 내용과 코드 컨텍스트를 참고하여 질문에 답변
                - 구체적이고 명확한 설명 제공
                - 필요시 코드 예시 포함

                ## 응답 원칙
                1. 리뷰 컨텍스트에 기반한 정확한 답변
                2. 코드 예시를 들 때는 실제 파일 경로와 라인 번호 참조
                3. 질문이 리뷰 범위를 벗어나면 정중히 안내
                4. 간결하고 명확한 표현 사용

                ## 응답 형식
                - 일반 텍스트로 답변 (JSON 형식 불필요)
                - 마크다운 형식 사용 가능
                - 코드 블록 사용 시 적절한 언어 지정
                """;
    }

    /**
     * 코멘트 응답 프롬프트 생성
     *
     * @param commentBody 사용자 코멘트
     * @param contexts 리뷰 컨텍스트 목록
     * @return 사용자 메시지
     */
    public String buildCommentResponsePrompt(String commentBody, List<greensnaback0229.pr_review_server.comment.entity.ReviewContext> contexts) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("# 리뷰 컨텍스트\n");
        prompt.append("이전에 작성한 리뷰 내용과 코드 정보입니다.\n\n");

        for (greensnaback0229.pr_review_server.comment.entity.ReviewContext context : contexts) {
            prompt.append("## 기능: ").append(context.getFeatureName()).append("\n\n");

            // 리뷰 내용
            if (context.getGeneralReview() != null) {
                prompt.append("### 리뷰 내용\n");
                prompt.append(context.getGeneralReview()).append("\n\n");
            }

            // 파일 컨텍스트 (JSON → 텍스트)
            if (context.getFileContexts() != null) {
                prompt.append("### 파일 컨텍스트\n");
                prompt.append(context.getFileContexts()).append("\n\n");
            }

            // 인라인 코멘트
            if (context.getInlineComments() != null) {
                prompt.append("### 인라인 코멘트\n");
                prompt.append(context.getInlineComments()).append("\n\n");
            }

            prompt.append("---\n\n");
        }

        prompt.append("# 새 댓글\n");
        prompt.append(commentBody).append("\n\n");
        prompt.append("위 댓글에 대해 답변해주세요.");

        return prompt.toString();
    }
}
