package greensnaback0229.pr_review_server.feature;

import greensnaback0229.pr_review_server.feature.dto.FeatureDefinition;
import greensnaback0229.pr_review_server.feature.dto.FeatureMemory;
import greensnaback0229.pr_review_server.feature.dto.ResolvedFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatureResolverTest {

    @Mock
    private FeatureRegistry registry;

    @Mock
    private FeatureMemoryRepository memoryRepository;

    @InjectMocks
    private FeatureResolver resolver;

    private FeatureDefinition paymentDefinition;
    private FeatureMemory paymentMemory;

    @BeforeEach
    void setUp() {
        paymentDefinition = FeatureDefinition.builder()
                .name("PAYMENT")
                .description("결제 및 금액 처리")
                .paths(List.of(
                        "src/main/java/com/app/payment/",
                        "src/main/java/com/app/ledger/"
                ))
                .coreFiles(List.of("PaymentService.java", "PaymentValidator.java"))
                .build();

        paymentMemory = FeatureMemory.builder()
                .feature("PAYMENT")
                .summary("할인 정책 로직 추가됨")
                .keyPoints(List.of("시간 기반 할인", "금액 검증"))
                .relatedFiles(List.of("MoneyUtils.java"))
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void resolve_Definition과_Memory_모두_있는_경우() {
        // given
        when(registry.getFeature("PAYMENT")).thenReturn(Optional.of(paymentDefinition));
        when(memoryRepository.findByFeature("PAYMENT")).thenReturn(Optional.of(paymentMemory));

        // when
        Optional<ResolvedFeature> result = resolver.resolve("PAYMENT");

        // then
        assertTrue(result.isPresent());
        assertNotNull(result.get().getDefinition());
        assertNotNull(result.get().getMemory());
        assertEquals("PAYMENT", result.get().getDefinition().getName());
        assertEquals("할인 정책 로직 추가됨", result.get().getMemory().getSummary());
    }

    @Test
    void resolve_Definition만_있고_Memory_없는_경우() {
        // given
        when(registry.getFeature("PAYMENT")).thenReturn(Optional.of(paymentDefinition));
        when(memoryRepository.findByFeature("PAYMENT")).thenReturn(Optional.empty());

        // when
        Optional<ResolvedFeature> result = resolver.resolve("PAYMENT");

        // then
        assertTrue(result.isPresent());
        assertNotNull(result.get().getDefinition());
        assertNull(result.get().getMemory());
    }

    @Test
    void resolve_Definition이_없는_경우() {
        // given
        when(registry.getFeature("NONEXISTENT")).thenReturn(Optional.empty());

        // when
        Optional<ResolvedFeature> result = resolver.resolve("NONEXISTENT");

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    void filterRelatedFiles_경로_일치하는_파일_필터링() {
        // given
        when(registry.getFeature("PAYMENT")).thenReturn(Optional.of(paymentDefinition));
        List<String> changedFiles = List.of(
                "src/main/java/com/app/payment/PaymentService.java",
                "src/main/java/com/app/payment/PaymentDto.java",
                "src/main/java/com/app/ledger/LedgerService.java",
                "src/main/java/com/app/auth/AuthService.java",
                "src/main/java/com/app/common/Utils.java"
        );

        // when
        List<String> result = resolver.filterRelatedFiles("PAYMENT", changedFiles);

        // then
        assertEquals(3, result.size());
        assertTrue(result.contains("src/main/java/com/app/payment/PaymentService.java"));
        assertTrue(result.contains("src/main/java/com/app/payment/PaymentDto.java"));
        assertTrue(result.contains("src/main/java/com/app/ledger/LedgerService.java"));
        assertFalse(result.contains("src/main/java/com/app/auth/AuthService.java"));
    }

    @Test
    void filterRelatedFiles_일치하는_파일_없음() {
        // given
        when(registry.getFeature("PAYMENT")).thenReturn(Optional.of(paymentDefinition));
        List<String> changedFiles = List.of(
                "src/main/java/com/app/auth/AuthService.java",
                "src/main/java/com/app/common/Utils.java"
        );

        // when
        List<String> result = resolver.filterRelatedFiles("PAYMENT", changedFiles);

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    void filterRelatedFiles_존재하지_않는_기능() {
        // given
        when(registry.getFeature("NONEXISTENT")).thenReturn(Optional.empty());
        List<String> changedFiles = List.of(
                "src/main/java/com/app/payment/PaymentService.java"
        );

        // when
        List<String> result = resolver.filterRelatedFiles("NONEXISTENT", changedFiles);

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    void getCoreFiles_핵심_파일_조회() {
        // given
        when(registry.getFeature("PAYMENT")).thenReturn(Optional.of(paymentDefinition));

        // when
        List<String> result = resolver.getCoreFiles("PAYMENT");

        // then
        assertEquals(2, result.size());
        assertTrue(result.contains("PaymentService.java"));
        assertTrue(result.contains("PaymentValidator.java"));
    }

    @Test
    void getCoreFiles_존재하지_않는_기능() {
        // given
        when(registry.getFeature("NONEXISTENT")).thenReturn(Optional.empty());

        // when
        List<String> result = resolver.getCoreFiles("NONEXISTENT");

        // then
        assertTrue(result.isEmpty());
    }
}
