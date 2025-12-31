package greensnaback0229.pr_review_server;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Integration test - requires GitHub token")
class PrReviewServerApplicationTests {

	@Test
	void contextLoads() {
		// Spring Context가 정상적으로 로드되는지 확인
		// GitHub token이 필요하므로 통합 테스트 시에만 실행
	}

}
