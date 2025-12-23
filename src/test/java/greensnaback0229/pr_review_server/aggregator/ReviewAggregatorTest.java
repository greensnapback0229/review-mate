package greensnaback0229.pr_review_server.aggregator;

import greensnaback0229.pr_review_server.aggregator.dto.AggregatedReview;
import greensnaback0229.pr_review_server.feature.FeatureMemoryRepository;
import greensnaback0229.pr_review_server.feature.dto.FeatureMemory;
import greensnaback0229.pr_review_server.llm.dto.MemorySuggestion;
import greensnaback0229.pr_review_server.llm.dto.ReviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewAggregator 테스트")
class ReviewAggregatorTest {
    
    @Mock
    private FeatureMemoryRepository featureMemoryRepository;
    
    @InjectMocks
    private ReviewAggregator reviewAggregator;
    
    @BeforeEach
    void setUp() {
        // 기본 설정
    }
    
    @Test
    @DisplayName("LLM 제안 없이 리뷰만 집계한다")
    void aggregate_withoutMemorySuggestion() {
        // given
        String feature = "PAYMENT";
        ReviewResponse reviewResponse = ReviewResponse.builder()
                .review("코드 리뷰 내용")
                .memorySuggestion(null)
                .build();
        
        // when
        AggregatedReview result = reviewAggregator.aggregate(feature, reviewResponse);
        
        // then
        assertThat(result.getFeature()).isEqualTo("PAYMENT");
        assertThat(result.getReview()).isEqualTo("코드 리뷰 내용");
        assertThat(result.getReviewedAt()).isNotNull();
        assertThat(result.getUpdatedMemory()).isNull();
        
        verify(featureMemoryRepository, never()).save(any());
    }
    
    @Test
    @DisplayName("LLM 제안으로 새로운 Feature Memory를 생성한다")
    void aggregate_createNewMemory() {
        // given
        String feature = "PAYMENT";
        MemorySuggestion suggestion = MemorySuggestion.builder()
                .summary("할인 로직 추가")
                .keyPoints(Arrays.asList("시간 기반 할인", "금액 검증"))
                .relatedFiles(Arrays.asList("MoneyUtils.java"))
                .build();
        
        ReviewResponse reviewResponse = ReviewResponse.builder()
                .review("리뷰 내용")
                .memorySuggestion(suggestion)
                .build();
        
        when(featureMemoryRepository.findByFeature(feature)).thenReturn(Optional.empty());
        
        // when
        AggregatedReview result = reviewAggregator.aggregate(feature, reviewResponse);
        
        // then
        assertThat(result.getUpdatedMemory()).isNotNull();
        assertThat(result.getUpdatedMemory().getFeature()).isEqualTo("PAYMENT");
        assertThat(result.getUpdatedMemory().getSummary()).isEqualTo("할인 로직 추가");
        assertThat(result.getUpdatedMemory().getKeyPoints()).hasSize(2);
        assertThat(result.getUpdatedMemory().getRelatedFiles()).contains("MoneyUtils.java");
        
        verify(featureMemoryRepository).save(any(FeatureMemory.class));
    }
    
    @Test
    @DisplayName("기존 Feature Memory와 LLM 제안을 병합한다")
    void aggregate_mergeWithExistingMemory() {
        // given
        String feature = "PAYMENT";
        
        // 기존 메모리
        FeatureMemory existingMemory = FeatureMemory.builder()
                .feature("PAYMENT")
                .summary("기존 요약")
                .keyPoints(Arrays.asList("기존 포인트1", "기존 포인트2"))
                .relatedFiles(Arrays.asList("ExistingFile.java"))
                .build();
        
        // LLM 제안
        MemorySuggestion suggestion = MemorySuggestion.builder()
                .summary("새 요약")
                .keyPoints(Arrays.asList("새 포인트1", "새 포인트2"))
                .relatedFiles(Arrays.asList("NewFile.java"))
                .build();
        
        ReviewResponse reviewResponse = ReviewResponse.builder()
                .review("리뷰 내용")
                .memorySuggestion(suggestion)
                .build();
        
        when(featureMemoryRepository.findByFeature(feature)).thenReturn(Optional.of(existingMemory));
        
        // when
        AggregatedReview result = reviewAggregator.aggregate(feature, reviewResponse);
        
        // then
        FeatureMemory updated = result.getUpdatedMemory();
        assertThat(updated.getSummary()).isEqualTo("기존 요약 | 새 요약");
        assertThat(updated.getKeyPoints()).hasSize(4)
                .contains("기존 포인트1", "기존 포인트2", "새 포인트1", "새 포인트2");
        assertThat(updated.getRelatedFiles()).hasSize(2)
                .contains("ExistingFile.java", "NewFile.java");
        
        verify(featureMemoryRepository).save(any(FeatureMemory.class));
    }
    
    @Test
    @DisplayName("중복된 파일은 제거하고 병합한다")
    void aggregate_removeDuplicateFiles() {
        // given
        String feature = "PAYMENT";
        
        FeatureMemory existingMemory = FeatureMemory.builder()
                .feature("PAYMENT")
                .summary("기존")
                .keyPoints(Arrays.asList("포인트1"))
                .relatedFiles(Arrays.asList("File1.java", "File2.java"))
                .build();
        
        MemorySuggestion suggestion = MemorySuggestion.builder()
                .summary("새")
                .keyPoints(Arrays.asList("포인트2"))
                .relatedFiles(Arrays.asList("File2.java", "File3.java"))
                .build();
        
        ReviewResponse reviewResponse = ReviewResponse.builder()
                .review("리뷰")
                .memorySuggestion(suggestion)
                .build();
        
        when(featureMemoryRepository.findByFeature(feature)).thenReturn(Optional.of(existingMemory));
        
        // when
        AggregatedReview result = reviewAggregator.aggregate(feature, reviewResponse);
        
        // then
        assertThat(result.getUpdatedMemory().getRelatedFiles()).hasSize(3)
                .contains("File1.java", "File2.java", "File3.java");
    }
    
    @Test
    @DisplayName("단일 리뷰를 그대로 반환한다")
    void mergeReviews_singleReview() {
        // given
        AggregatedReview review = AggregatedReview.builder()
                .feature("PAYMENT")
                .review("단일 리뷰 내용")
                .build();
        
        // when
        String result = reviewAggregator.mergeReviews(Arrays.asList(review));
        
        // then
        assertThat(result).isEqualTo("단일 리뷰 내용");
    }
    
    @Test
    @DisplayName("여러 기능의 리뷰를 병합한다")
    void mergeReviews_multipleReviews() {
        // given
        AggregatedReview review1 = AggregatedReview.builder()
                .feature("PAYMENT")
                .review("결제 리뷰")
                .build();
        
        AggregatedReview review2 = AggregatedReview.builder()
                .feature("ALERT")
                .review("알림 리뷰")
                .build();
        
        // when
        String result = reviewAggregator.mergeReviews(Arrays.asList(review1, review2));
        
        // then
        assertThat(result).contains("# 전체 리뷰 결과");
        assertThat(result).contains("## PAYMENT 기능");
        assertThat(result).contains("결제 리뷰");
        assertThat(result).contains("## ALERT 기능");
        assertThat(result).contains("알림 리뷰");
    }
    
    @Test
    @DisplayName("빈 리뷰 목록은 기본 메시지를 반환한다")
    void mergeReviews_emptyList() {
        // when
        String result = reviewAggregator.mergeReviews(Arrays.asList());
        
        // then
        assertThat(result).isEqualTo("리뷰 결과가 없습니다.");
    }
}
