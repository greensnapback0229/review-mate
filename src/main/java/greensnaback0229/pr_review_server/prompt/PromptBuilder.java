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
                
                ## 리뷰 원칙
                1. 구체적이고 실행 가능한 피드백 제공
                2. 긍정적인 부분도 언급
                3. 우선순위 명시 (Critical, Major, Minor)
                
                ## 추가 파일 요청
                리뷰를 위해 추가 파일이 필요하다면 다음 형식으로 응답하세요:
                ```json
                {
                  "needMoreContext": true,
                  "requestedFiles": ["FileName.java"],
                  "reason": "이유 설명"
                }
                ```
                
                추가 파일이 필요 없다면:
                ```json
                {
                  "needMoreContext": false
                }
                ```
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
        
        // 핵심 파일 전체 코드
        if (!coreFilesContent.isEmpty()) {
            prompt.append("# 핵심 파일 (전체 코드)\n");
            for (Map.Entry<String, String> entry : coreFilesContent.entrySet()) {
                prompt.append("## ").append(entry.getKey()).append("\n");
                prompt.append("```java\n");
                prompt.append(entry.getValue());
                prompt.append("\n```\n\n");
            }
        }
        
        prompt.append("위 코드를 리뷰해주세요. 추가로 필요한 파일이 있다면 요청해주세요.");
        
        return prompt.toString();
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
}
