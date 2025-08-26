package greensnaback0229.pr_review_server.parser.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PrContext {
    private String title;
    private String summary;
    private List<String> mainFeatures;
    private List<String> relatedFeatures;
    private List<String> description;
    private List<String> changedFiles;
}
