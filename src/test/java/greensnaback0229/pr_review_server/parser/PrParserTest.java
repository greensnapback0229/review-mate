package greensnaback0229.pr_review_server.parser;

import greensnaback0229.pr_review_server.parser.dto.PrContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrParserTest {

    private PrParser prParser;

    @BeforeEach
    void setUp() {
        prParser = new PrParser();
    }

    @Test
    void parse_정상적인_PR_본문_파싱() {
        // given
        String prTitle = "[Feat] 결제시 할인 기능 추가";
        String prBody = """
                ## summary
                상품 결제 할인
                ## feature
                main - PAYMENT
                related - ALERT
                ## description
                 - 17:00 ~ 18:00 결제시 할인 로직 추가
                 - 결제 전 시간 검증 로직 추가
                """;
        List<String> changedFiles = Arrays.asList(
                "src/main/java/com/app/payment/PaymentService.java",
                "src/main/java/com/app/alert/AlertService.java"
        );

        // when
        PrContext context = prParser.parse(prTitle, prBody, changedFiles);

        // then
        assertEquals("[Feat] 결제시 할인 기능 추가", context.getTitle());
        assertEquals("상품 결제 할인", context.getSummary());
        assertEquals(Arrays.asList("PAYMENT"), context.getMainFeatures());
        assertEquals(Arrays.asList("ALERT"), context.getRelatedFeatures());
        assertEquals(2, context.getDescription().size());
        assertEquals("17:00 ~ 18:00 결제시 할인 로직 추가", context.getDescription().get(0));
        assertEquals("결제 전 시간 검증 로직 추가", context.getDescription().get(1));
        assertEquals(2, context.getChangedFiles().size());
    }

    @Test
    void parse_여러_개의_메인_기능() {
        // given
        String prBody = """
                ## summary
                결제 및 인증 개선
                ## feature
                main - PAYMENT, AUTH, LOGGING
                related - ALERT
                ## description
                 - 기능 개선
                """;

        // when
        PrContext context = prParser.parse("title", prBody, List.of());

        // then
        assertEquals(3, context.getMainFeatures().size());
        assertTrue(context.getMainFeatures().contains("PAYMENT"));
        assertTrue(context.getMainFeatures().contains("AUTH"));
        assertTrue(context.getMainFeatures().contains("LOGGING"));
    }

    @Test
    void parse_여러_개의_연관_기능() {
        // given
        String prBody = """
                ## summary
                결제 기능
                ## feature
                main - PAYMENT
                related - ALERT, NOTIFICATION, EMAIL
                ## description
                 - 테스트
                """;

        // when
        PrContext context = prParser.parse("title", prBody, List.of());

        // then
        assertEquals(3, context.getRelatedFeatures().size());
        assertTrue(context.getRelatedFeatures().contains("ALERT"));
        assertTrue(context.getRelatedFeatures().contains("NOTIFICATION"));
        assertTrue(context.getRelatedFeatures().contains("EMAIL"));
    }

    @Test
    void parse_related_기능이_없는_경우() {
        // given
        String prBody = """
                ## summary
                결제 기능
                ## feature
                main - PAYMENT
                ## description
                 - 테스트
                """;

        // when
        PrContext context = prParser.parse("title", prBody, List.of());

        // then
        assertEquals(1, context.getMainFeatures().size());
        assertTrue(context.getRelatedFeatures().isEmpty());
    }

    @Test
    void parse_description이_없는_경우() {
        // given
        String prBody = """
                ## summary
                결제 기능
                ## feature
                main - PAYMENT
                related - ALERT
                """;

        // when
        PrContext context = prParser.parse("title", prBody, List.of());

        // then
        assertTrue(context.getDescription().isEmpty());
    }

    @Test
    void parse_빈_PR_본문() {
        // given
        String prBody = "";

        // when
        PrContext context = prParser.parse("title", prBody, List.of());

        // then
        assertEquals("", context.getSummary());
        assertTrue(context.getMainFeatures().isEmpty());
        assertTrue(context.getRelatedFeatures().isEmpty());
        assertTrue(context.getDescription().isEmpty());
    }
}
