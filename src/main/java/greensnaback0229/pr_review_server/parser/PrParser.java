package greensnaback0229.pr_review_server.parser;

import greensnaback0229.pr_review_server.parser.dto.PrContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class PrParser {

    /**
     * PR 제목, 본문, 변경된 파일 리스트를 받아서 PrContext 객체로 변환
     * 각 추출 메서드를 호출해서 결과를 조합
     */
    public PrContext parse(String prTitle, String prBody, List<String> changedFiles) {
        return PrContext.builder()
                .title(prTitle)
                .summary(extractSummary(prBody))
                .mainFeatures(extractMainFeatures(prBody))
                .relatedFeatures(extractRelatedFeatures(prBody))
                .description(extractDescription(prBody))
                .changedFiles(changedFiles)
                .build();
    }

    /**
     * ## summary 섹션의 내용을 추출
     * 정규식으로 "## summary" 다음부터 다음 섹션(##) 또는 끝까지 읽음
     */
    private String extractSummary(String prBody) {
        Pattern pattern = Pattern.compile("## summary\\s*\\n(.+?)(?=\\n##|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(prBody);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    /**
     * main - PAYMENT, AUTH 형식에서 기능 리스트 추출
     * 쉼표(,)로 구분된 기능들을 List로 변환
     */
    private List<String> extractMainFeatures(String prBody) {
        Pattern pattern = Pattern.compile("main\\s*-\\s*(.+?)(?=\\n|related|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(prBody);
        if (matcher.find()) {
            String features = matcher.group(1).trim();
            return Arrays.stream(features.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    /**
     * related - ALERT 형식에서 연관 기능 리스트 추출
     * 메인 기능과 동일한 방식으로 파싱
     */
    private List<String> extractRelatedFeatures(String prBody) {
        Pattern pattern = Pattern.compile("related\\s*-\\s*(.+?)(?=\\n##|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(prBody);
        if (matcher.find()) {
            String features = matcher.group(1).trim();
            return Arrays.stream(features.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    /**
     * ## description 섹션의 항목들을 추출
     * - 로 시작하는 라인들만 필터링해서 List로 변환
     * - 기호는 제거하고 내용만 저장
     */
    private List<String> extractDescription(String prBody) {
        Pattern pattern = Pattern.compile("## description\\s*\\n(.+?)(?=\\n##|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(prBody);
        if (matcher.find()) {
            String desc = matcher.group(1).trim();
            return Arrays.stream(desc.split("\\n"))
                    .map(String::trim)
                    .filter(s -> s.startsWith("-"))
                    .map(s -> s.substring(1).trim())
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
