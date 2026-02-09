package greensnaback0229.pr_review_server.comment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileContextData {
    private String path;
    private String diff;
    private String content;
    private List<Integer> keyLines;
}
