package greensnaback0229.pr_review_server.feature;

import greensnaback0229.pr_review_server.feature.dto.FeatureMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FeatureMemoryRepositoryTest {

    private FeatureMemoryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new FeatureMemoryRepository();
    }

    @Test
    void save_메모리_저장() {
        // given
        FeatureMemory memory = FeatureMemory.builder()
                .feature("PAYMENT")
                .summary("결제 할인 로직 추가됨")
                .keyPoints(List.of("시간 기반 할인", "금액 검증"))
                .relatedFiles(List.of("MoneyUtils.java"))
                .updatedAt(LocalDateTime.now())
                .build();

        // when
        repository.save(memory);

        // then
        assertTrue(repository.exists("PAYMENT"));
    }

    @Test
    void findByFeature_존재하는_메모리() {
        // given
        FeatureMemory memory = FeatureMemory.builder()
                .feature("PAYMENT")
                .summary("결제 할인 로직 추가됨")
                .keyPoints(List.of("시간 기반 할인"))
                .relatedFiles(List.of("MoneyUtils.java"))
                .updatedAt(LocalDateTime.now())
                .build();
        repository.save(memory);

        // when
        Optional<FeatureMemory> result = repository.findByFeature("PAYMENT");

        // then
        assertTrue(result.isPresent());
        assertEquals("PAYMENT", result.get().getFeature());
        assertEquals("결제 할인 로직 추가됨", result.get().getSummary());
        assertEquals(1, result.get().getKeyPoints().size());
    }

    @Test
    void findByFeature_존재하지_않는_메모리() {
        // when
        Optional<FeatureMemory> result = repository.findByFeature("NONEXISTENT");

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    void save_같은_기능_업데이트() {
        // given
        FeatureMemory memory1 = FeatureMemory.builder()
                .feature("PAYMENT")
                .summary("첫 번째 요약")
                .keyPoints(List.of("포인트1"))
                .relatedFiles(List.of())
                .updatedAt(LocalDateTime.now())
                .build();
        repository.save(memory1);

        FeatureMemory memory2 = FeatureMemory.builder()
                .feature("PAYMENT")
                .summary("두 번째 요약")
                .keyPoints(List.of("포인트1", "포인트2"))
                .relatedFiles(List.of("MoneyUtils.java"))
                .updatedAt(LocalDateTime.now())
                .build();

        // when
        repository.save(memory2);

        // then
        Optional<FeatureMemory> result = repository.findByFeature("PAYMENT");
        assertTrue(result.isPresent());
        assertEquals("두 번째 요약", result.get().getSummary());
        assertEquals(2, result.get().getKeyPoints().size());
    }

    @Test
    void exists_메모리_존재_여부() {
        // given
        FeatureMemory memory = FeatureMemory.builder()
                .feature("AUTH")
                .summary("인증 로직")
                .keyPoints(List.of())
                .relatedFiles(List.of())
                .updatedAt(LocalDateTime.now())
                .build();
        repository.save(memory);

        // when & then
        assertTrue(repository.exists("AUTH"));
        assertFalse(repository.exists("PAYMENT"));
    }

    @Test
    void delete_메모리_삭제() {
        // given
        FeatureMemory memory = FeatureMemory.builder()
                .feature("PAYMENT")
                .summary("결제 로직")
                .keyPoints(List.of())
                .relatedFiles(List.of())
                .updatedAt(LocalDateTime.now())
                .build();
        repository.save(memory);

        // when
        repository.delete("PAYMENT");

        // then
        assertFalse(repository.exists("PAYMENT"));
        assertTrue(repository.findByFeature("PAYMENT").isEmpty());
    }

    @Test
    void clear_모든_메모리_삭제() {
        // given
        FeatureMemory memory1 = FeatureMemory.builder()
                .feature("PAYMENT")
                .summary("결제")
                .keyPoints(List.of())
                .relatedFiles(List.of())
                .updatedAt(LocalDateTime.now())
                .build();
        FeatureMemory memory2 = FeatureMemory.builder()
                .feature("AUTH")
                .summary("인증")
                .keyPoints(List.of())
                .relatedFiles(List.of())
                .updatedAt(LocalDateTime.now())
                .build();
        repository.save(memory1);
        repository.save(memory2);

        // when
        repository.clear();

        // then
        assertFalse(repository.exists("PAYMENT"));
        assertFalse(repository.exists("AUTH"));
    }
}
