package greensnaback0229.pr_review_server.feature;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import greensnaback0229.pr_review_server.feature.dto.FeatureDefinition;
import lombok.RequiredArgsConstructor;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * GitHub 저장소에서 feature-registry.yml 파일을 읽어와 파싱하는 로더
 */
@Component
@RequiredArgsConstructor
public class FeatureRegistryLoader {
    
    private static final String REGISTRY_PATH = ".github/pr-review/feature-registry.yml";
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /**
     * GitHub 저장소에서 feature-registry.yml 파일을 읽어와 FeatureDefinition Map으로 변환
     * 
     * @param repoFullName 저장소 전체 이름 (예: "owner/repo")
     * @param githubToken GitHub API 토큰
     * @return 기능명을 키로 하는 FeatureDefinition Map
     * @throws IOException GitHub API 호출 또는 YAML 파싱 실패 시
     */
    public Map<String, FeatureDefinition> loadFromRepository(String repoFullName, String githubToken) throws IOException {
        GitHub github = GitHub.connectUsingOAuth(githubToken);
        GHRepository repository = github.getRepository(repoFullName);
        
        GHContent fileContent = repository.getFileContent(REGISTRY_PATH);
        
        try (InputStream inputStream = fileContent.read()) {
            return parseYaml(inputStream);
        }
    }

    /**
     * YAML 형식의 InputStream을 파싱하여 FeatureDefinition Map으로 변환
     * 
     * @param inputStream YAML 파일의 InputStream
     * @return 기능명을 키로 하는 FeatureDefinition Map
     * @throws IOException YAML 파싱 실패 시
     */
    @SuppressWarnings("unchecked")
    private Map<String, FeatureDefinition> parseYaml(InputStream inputStream) throws IOException {
        Map<String, Object> yaml = yamlMapper.readValue(inputStream, Map.class);
        Map<String, Object> featuresMap = (Map<String, Object>) yaml.get("features");
        
        Map<String, FeatureDefinition> result = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : featuresMap.entrySet()) {
            String featureName = entry.getKey();
            Map<String, Object> featureData = (Map<String, Object>) entry.getValue();
            
            FeatureDefinition definition = FeatureDefinition.builder()
                    .name(featureName)
                    .description((String) featureData.get("description"))
                    .paths((java.util.List<String>) featureData.get("paths"))
                    .coreFiles((java.util.List<String>) featureData.get("coreFiles"))
                    .build();
            
            result.put(featureName, definition);
        }
        
        return result;
    }
}
