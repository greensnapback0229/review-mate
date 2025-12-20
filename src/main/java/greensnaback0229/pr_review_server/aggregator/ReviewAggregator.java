package greensnaback0229.pr_review_server.aggregator;

import greensnaback0229.pr_review_server.aggregator.dto.AggregatedReview;
import greensnaback0229.pr_review_server.feature.FeatureMemoryRepository;
import greensnaback0229.pr_review_server.feature.dto.FeatureMemory;
import greensnaback0229.pr_review_server.llm.dto.MemorySuggestion;
import greensnaback0229.pr_review_server.llm.dto.ReviewResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 리뷰 결과를 집계하고 Feature Memory를 업데이트하는 컴포넌트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewAggregator {
    
    private final FeatureMemoryRepository featureMemoryRepository;
    
    /**
     * 단일 기능의 리뷰 결과를 집계
     *
     * @param feature 기능 이름
     * @param reviewResponse LLM 리뷰 응답
     * @return 집계된 리뷰 결과
     */
    public AggregatedReview aggregate(String feature, ReviewResponse reviewResponse) {
        log.info("Aggregating review for feature: {}", feature);
        
        AggregatedReview.AggregatedReviewBuilder builder = AggregatedReview.builder()
                .feature(feature)
                .review(reviewResponse.getReview())
                .reviewedAt(LocalDateTime.now());
        
        // LLM이 제안한 Feature Memory 업데이트
        if (reviewResponse.getMemorySuggestion() != null) {
            FeatureMemory updatedMemory = updateMemoryFromSuggestion(feature, reviewResponse.getMemorySuggestion());
            builder.updatedMemory(updatedMemory);
        }
        
        return builder.build();
    }
    
    /**
     * 여러 기능의 리뷰 결과를 병합하여 집계
     *
     * @param reviews 기능별 리뷰 결과 리스트
     * @return 병합된 리뷰 결과
     */
    public String mergeReviews(List<AggregatedReview> reviews) {
        if (reviews.isEmpty()) {
            return "리뷰 결과가 없습니다.";
        }
        
        if (reviews.size() == 1) {
            return reviews.get(0).getReview();
        }
        
        // 여러 기능의 리뷰를 병합
        StringBuilder merged = new StringBuilder();
        merged.append("# 전체 리뷰 결과\n\n");
        
        for (AggregatedReview review : reviews) {
            merged.append("## ").append(review.getFeature()).append(" 기능\n\n");
            merged.append(review.getReview()).append("\n\n");
            merged.append("---\n\n");
        }
        
        return merged.toString();
    }
    
    /**
     * LLM의 제안을 기반으로 Feature Memory 업데이트
     *
     * @param feature 기능 이름
     * @param suggestion LLM이 제안한 메모리 업데이트 내용
     * @return 업데이트된 Feature Memory
     */
    private FeatureMemory updateMemoryFromSuggestion(String feature, MemorySuggestion suggestion) {
        // 기존 메모리 조회
        FeatureMemory existingMemory = featureMemoryRepository.findByFeature(feature)
                .orElse(null);
        
        // 새 메모리 생성 또는 업데이트
        FeatureMemory.FeatureMemoryBuilder builder = FeatureMemory.builder()
                .feature(feature)
                .updatedAt(LocalDateTime.now());
        
        if (existingMemory != null) {
            // 기존 메모리와 병합
            String updatedSummary = existingMemory.getSummary() + " | " + suggestion.getSummary();
            builder.summary(updatedSummary);
            
            // keyPoints 병합
            List<String> mergedKeyPoints = new ArrayList<>(existingMemory.getKeyPoints());
            mergedKeyPoints.addAll(suggestion.getKeyPoints());
            builder.keyPoints(mergedKeyPoints);
            
            // relatedFiles 병합
            List<String> mergedFiles = new ArrayList<>();
            if (existingMemory.getRelatedFiles() != null) {
                mergedFiles.addAll(existingMemory.getRelatedFiles());
            }
            if (suggestion.getRelatedFiles() != null) {
                for (String file : suggestion.getRelatedFiles()) {
                    if (!mergedFiles.contains(file)) {
                        mergedFiles.add(file);
                    }
                }
            }
            if (!mergedFiles.isEmpty()) {
                builder.relatedFiles(mergedFiles);
            }
        } else {
            // 새 메모리 생성
            builder.summary(suggestion.getSummary())
                    .keyPoints(suggestion.getKeyPoints())
                    .relatedFiles(suggestion.getRelatedFiles());
        }
        
        FeatureMemory updatedMemory = builder.build();
        featureMemoryRepository.save(updatedMemory);
        
        log.info("Updated feature memory for: {} with LLM suggestion", feature);
        return updatedMemory;
    }
}
