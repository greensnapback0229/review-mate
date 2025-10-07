package greensnaback0229.pr_review_server.feature;

import greensnaback0229.pr_review_server.feature.dto.FeatureDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatureRegistryTest {

    @Mock
    private FeatureRegistryLoader loader;

    @InjectMocks
    private FeatureRegistry registry;

    private Map<String, FeatureDefinition> mockRegistry;

    @BeforeEach
    void setUp() {
        FeatureDefinition payment = FeatureDefinition.builder()
                .name("PAYMENT")
                .description("결제 및 금액 처리")
                .paths(List.of("src/main/java/com/app/payment/"))
                .coreFiles(List.of("PaymentService.java"))
                .build();

        FeatureDefinition auth = FeatureDefinition.builder()
                .name("AUTH")
                .description("인증 및 권한")
                .paths(List.of("src/main/java/com/app/auth/"))
                .coreFiles(List.of("AuthService.java"))
                .build();

        mockRegistry = Map.of(
                "PAYMENT", payment,
                "AUTH", auth
        );
    }

    @Test
    void initialize_성공() throws IOException {
        // given
        when(loader.loadFromRepository(anyString(), anyString())).thenReturn(mockRegistry);

        // when
        registry.initialize("owner/repo", "token");

        // then
        assertTrue(registry.hasFeature("PAYMENT"));
        assertTrue(registry.hasFeature("AUTH"));
    }

    @Test
    void getFeature_존재하는_기능() throws IOException {
        // given
        when(loader.loadFromRepository(anyString(), anyString())).thenReturn(mockRegistry);
        registry.initialize("owner/repo", "token");

        // when
        Optional<FeatureDefinition> result = registry.getFeature("PAYMENT");

        // then
        assertTrue(result.isPresent());
        assertEquals("PAYMENT", result.get().getName());
        assertEquals("결제 및 금액 처리", result.get().getDescription());
    }

    @Test
    void getFeature_존재하지_않는_기능() throws IOException {
        // given
        when(loader.loadFromRepository(anyString(), anyString())).thenReturn(mockRegistry);
        registry.initialize("owner/repo", "token");

        // when
        Optional<FeatureDefinition> result = registry.getFeature("NONEXISTENT");

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    void getAllFeatures_모든_기능_조회() throws IOException {
        // given
        when(loader.loadFromRepository(anyString(), anyString())).thenReturn(mockRegistry);
        registry.initialize("owner/repo", "token");

        // when
        List<FeatureDefinition> result = registry.getAllFeatures();

        // then
        assertEquals(2, result.size());
    }

    @Test
    void hasFeature_존재_여부_확인() throws IOException {
        // given
        when(loader.loadFromRepository(anyString(), anyString())).thenReturn(mockRegistry);
        registry.initialize("owner/repo", "token");

        // when & then
        assertTrue(registry.hasFeature("PAYMENT"));
        assertTrue(registry.hasFeature("AUTH"));
        assertFalse(registry.hasFeature("NONEXISTENT"));
    }

    @Test
    void getFeature_초기화_안됨() {
        // when & then
        assertThrows(IllegalStateException.class, () -> registry.getFeature("PAYMENT"));
    }

    @Test
    void getAllFeatures_초기화_안됨() {
        // when & then
        assertThrows(IllegalStateException.class, () -> registry.getAllFeatures());
    }

    @Test
    void hasFeature_초기화_안됨() {
        // when & then
        assertThrows(IllegalStateException.class, () -> registry.hasFeature("PAYMENT"));
    }
}
