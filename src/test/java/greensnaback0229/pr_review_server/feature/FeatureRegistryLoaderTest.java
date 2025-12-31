package greensnaback0229.pr_review_server.feature;

import greensnaback0229.pr_review_server.feature.dto.FeatureDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FeatureRegistryLoaderTest {

    private FeatureRegistryLoader loader;

    @BeforeEach
    void setUp() {
        loader = new FeatureRegistryLoader();
    }

    @Test
    void parseYaml_정상적인_YAML_파싱() throws Exception {
        // given
        String yaml = """
                features:
                  PAYMENT:
                    description: "결제 및 금액 처리"
                    paths:
                      - "src/main/java/com/app/payment/"
                      - "src/main/java/com/app/ledger/"
                    coreFiles:
                      - "PaymentService.java"
                      - "PaymentValidator.java"
                  AUTH:
                    description: "인증 및 권한"
                    paths:
                      - "src/main/java/com/app/auth/"
                    coreFiles:
                      - "AuthService.java"
                """;

        // when
        Map<String, FeatureDefinition> result = parseYamlFromString(yaml);

        // then
        assertEquals(2, result.size());
        
        // PAYMENT 검증
        assertTrue(result.containsKey("PAYMENT"));
        FeatureDefinition payment = result.get("PAYMENT");
        assertEquals("PAYMENT", payment.getName());
        assertEquals("결제 및 금액 처리", payment.getDescription());
        assertEquals(2, payment.getPaths().size());
        assertTrue(payment.getPaths().contains("src/main/java/com/app/payment/"));
        assertEquals(2, payment.getCoreFiles().size());
        assertTrue(payment.getCoreFiles().contains("PaymentService.java"));
        
        // AUTH 검증
        assertTrue(result.containsKey("AUTH"));
        FeatureDefinition auth = result.get("AUTH");
        assertEquals("AUTH", auth.getName());
        assertEquals("인증 및 권한", auth.getDescription());
        assertEquals(1, auth.getPaths().size());
        assertEquals(1, auth.getCoreFiles().size());
    }

    @Test
    void parseYaml_빈_YAML() throws Exception {
        // given
        String yaml = """
                features: {}
                """;

        // when
        Map<String, FeatureDefinition> result = parseYamlFromString(yaml);

        // then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void parseYaml_단일_기능() throws Exception {
        // given
        String yaml = """
                features:
                  ALERT:
                    description: "알림 발송"
                    paths:
                      - "src/main/java/com/app/alert/"
                    coreFiles:
                      - "AlertService.java"
                """;

        // when
        Map<String, FeatureDefinition> result = parseYamlFromString(yaml);

        // then
        assertEquals(1, result.size());
        assertTrue(result.containsKey("ALERT"));
        FeatureDefinition alert = result.get("ALERT");
        assertEquals("알림 발송", alert.getDescription());
    }

    /**
     * 테스트용 헬퍼 메서드
     * String을 InputStream으로 변환하여 parseYaml 호출
     */
    private Map<String, FeatureDefinition> parseYamlFromString(String yaml) throws IOException {
        InputStream inputStream = new ByteArrayInputStream(yaml.getBytes());
        // Reflection을 사용하여 private 메서드 호출
        try {
            var method = FeatureRegistryLoader.class.getDeclaredMethod("parseYaml", InputStream.class);
            method.setAccessible(true);
            return (Map<String, FeatureDefinition>) method.invoke(loader, inputStream);
        } catch (Exception e) {
            throw new IOException("Failed to parse YAML", e);
        }
    }
}
