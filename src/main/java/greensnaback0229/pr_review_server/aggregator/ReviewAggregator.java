package greensnaback0229.pr_review_server.aggregator;

import greensnaback0229.pr_review_server.aggregator.dto.AggregatedReview;
import greensnaback0229.pr_review_server.feature.FeatureMemoryRepository;
import greensnaback0229.pr_review_server.feature.dto.FeatureMemory;
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
        
        // Feature Memory 업데이트 (리뷰에서 학습할 내용이 있으면)
        FeatureMemory updatedMemory = extractAndUpdateMemory(feature, reviewResponse);
        if (updatedMemory != null) {
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
     * 리뷰 결과에서 학습할 내용을 추출하여 Feature Memory 업데이트
     *
     * @param feature 기능 이름
     * @param reviewResponse LLM 리뷰 응답
     * @return 업데이트된 Feature Memory (변경사항이 없으면 null)
     */
    private FeatureMemory extractAndUpdateMemory(String feature, ReviewResponse reviewResponse) {
        // 리뷰 내용에서 중요한 패턴이나 개선사항 추출
        String review = reviewResponse.getReview();
        
        // 간단한 키워드 기반 추출 (추후 LLM 기반으로 개선 가능)
        List<String> keyPoints = extractKeyPoints(review);
        
        if (keyPoints.isEmpty()) {
            log.debug("No key points to update for feature: {}", feature);
            return null;
        }
        
        // 기존 메모리 조회
        FeatureMemory existingMemory = featureMemoryRepository.findByFeature(feature)
                .orElse(null);
        
        // 새 메모리 생성 또는 업데이트
        FeatureMemory.FeatureMemoryBuilder builder = FeatureMemory.builder()
                .feature(feature)
                .updatedAt(LocalDateTime.now());
        
        if (existingMemory != null) {
            // 기존 메모리와 병합
            String updatedSummary = existingMemory.getSummary() + " | " + "리뷰 반영";
            builder.summary(updatedSummary);
            
            List<String> mergedKeyPoints = new ArrayList<>(existingMemory.getKeyPoints());
            mergedKeyPoints.addAll(keyPoints);
            builder.keyPoints(mergedKeyPoints);
            
            if (existingMemory.getRelatedFiles() != null) {
                builder.relatedFiles(new ArrayList<>(existingMemory.getRelatedFiles()));
            }
        } else {
            // 새 메모리 생성
            builder.summary("리뷰를 통해 학습된 내용")
                    .keyPoints(keyPoints);
        }
        
        FeatureMemory updatedMemory = builder.build();
        featureMemoryRepository.save(updatedMemory);
        
        log.info("Updated feature memory for: {}", feature);
        return updatedMemory;
    }
    
    /**
     * 리뷰 내용에서 핵심 포인트 추출
     *
     * @param review 리뷰 내용
     * @return 추출된 핵심 포인트 리스트
     */
    private List<String> extractKeyPoints(String review) {
        List<String> keyPoints = new ArrayList<>();
        
        // 주요 키워드 패턴 체크
        if (review.contains("버그") || review.contains("오류")) {
            keyPoints.add("버그 수정 필요");
        }
        if (review.contains("성능") || review.contains("최적화")) {
            keyPoints.add("성능 개선 가능");
        }
        if (review.contains("보안") || review.contains("취약")) {
            keyPoints.add("보안 검토 필요");
        }
        if (review.contains("테스트")) {
            keyPoints.add("테스트 커버리지 확인");
        }
        if (review.contains("리팩토링")) {
            keyPoints.add("코드 구조 개선 제안");
        }
        
        return keyPoints;
    }
}
