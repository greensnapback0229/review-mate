package greensnaback0229.pr_review_server.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("SecurityConfig 통합 테스트")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("webhook경로_인증없이접근가능")
    void webhook경로_인증없이접근가능() throws Exception {
        mockMvc.perform(get("/api/webhook/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("인증필요경로_미인증시리다이렉트")
    void 인증필요경로_미인증시리다이렉트() throws Exception {
        mockMvc.perform(get("/api/settings/api-key"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("health경로_인증없이접근가능")
    void health경로_인증없이접근가능() throws Exception {
        mockMvc.perform(get("/api/webhook/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("webhook경로_CSRF없이POST가능")
    void webhook경로_CSRF없이POST가능() throws Exception {
        mockMvc.perform(post("/api/webhook/github/pr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}
