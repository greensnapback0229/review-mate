package greensnaback0229.pr_review_server.feature;

import greensnaback0229.pr_review_server.feature.dto.FeatureDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Feature Registry
 * 기능 정의를 조회하고 관리하는 컴포넌트
 */
@Component
@RequiredArgsConstructor
public class FeatureRegistry {
    
    private final FeatureRegistryLoader loader;
    private Map<String, FeatureDefinition> registry;

    /**
     * GitHub 저장소에서 feature-registry.yml을 로드하여 초기화
     * 
     * @param repoFullName 저장소 전체 이름 (예: "owner/repo")
     * @param githubToken GitHub API 토큰
     * @throws IOException 로드 실패 시
     */
    public void initialize(String repoFullName, String githubToken) throws IOException {
        initialize(repoFullName, githubToken, null);
    }

    /**
     * GitHub 저장소의 특정 브랜치에서 feature-registry.yml을 로드하여 초기화
     * 
     * @param repoFullName 저장소 전체 이름 (예: "owner/repo")
     * @param githubToken GitHub API 토큰
     * @param branch 브랜치명 (null이면 기본 브랜치)
     * @throws IOException 로드 실패 시
     */
    public void initialize(String repoFullName, String githubToken, String branch) throws IOException {
        this.registry = loader.loadFromRepository(repoFullName, githubToken, branch);
    }

    /**
     * 기능명으로 정의 조회
     * 
     * @param featureName 기능 식별자 (예: PAYMENT)
     * @return FeatureDefinition Optional
     */
    public Optional<FeatureDefinition> getFeature(String featureName) {
        if (registry == null) {
            throw new IllegalStateException("FeatureRegistry is not initialized. Call initialize() first.");
        }
        return Optional.ofNullable(registry.get(featureName));
    }

    /**
     * 모든 기능 조회
     * 
     * @return FeatureDefinition 리스트
     */
    public List<FeatureDefinition> getAllFeatures() {
        if (registry == null) {
            throw new IllegalStateException("FeatureRegistry is not initialized. Call initialize() first.");
        }
        return List.copyOf(registry.values());
    }

    /**
     * 기능 존재 여부 확인
     * 
     * @param featureName 기능 식별자
     * @return 존재 여부
     */
    public boolean hasFeature(String featureName) {
        if (registry == null) {
            throw new IllegalStateException("FeatureRegistry is not initialized. Call initialize() first.");
        }
        return registry.containsKey(featureName);
    }
}
