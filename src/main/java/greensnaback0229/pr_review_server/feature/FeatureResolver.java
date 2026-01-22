package greensnaback0229.pr_review_server.feature;

import greensnaback0229.pr_review_server.feature.dto.FeatureDefinition;
import greensnaback0229.pr_review_server.feature.dto.FeatureMemory;
import greensnaback0229.pr_review_server.feature.dto.ResolvedFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Feature Resolver
 * Feature Definition(정적 명세)와 Feature Memory(동적 지식)를 조합하여 
 * 리뷰에 필요한 정보를 제공
 */
@Component
@RequiredArgsConstructor
public class FeatureResolver {
    
    private final FeatureMemoryRepository memoryRepository;

    /**
     * 기능명으로 ResolvedFeature 조회
     * Definition의 정적 정보 + Memory의 동적 지식 조합
     * 
     * @param repositoryId GitHub Repository ID
     * @param featureName 기능 식별자
     * @param definition 기능 정의
     * @return ResolvedFeature Optional
     */
    public Optional<ResolvedFeature> resolve(Long repositoryId, String featureName, FeatureDefinition definition) {
        // Memory에서 동적 지식 조회
        Optional<FeatureMemory> memory = memoryRepository.findByFeature(repositoryId, featureName);
        
        // 조합하여 ResolvedFeature 생성
        ResolvedFeature resolved = ResolvedFeature.builder()
                .definition(definition)
                .memory(memory.orElse(null))
                .build();
        
        return Optional.of(resolved);
    }

    /**
     * 변경된 파일 목록을 기반으로 관련 기능 필터링
     * 
     * @param definition 기능 정의
     * @param changedFiles 변경된 파일 목록
     * @return 기능 관련 변경 파일 목록
     */
    public List<String> filterRelatedFiles(FeatureDefinition definition, List<String> changedFiles) {
        List<String> paths = definition.getPaths();
        List<String> relatedFiles = new ArrayList<>();
        
        for (String changedFile : changedFiles) {
            for (String path : paths) {
                if (changedFile.startsWith(path)) {
                    relatedFiles.add(changedFile);
                    break;
                }
            }
        }
        
        return relatedFiles;
    }
}
