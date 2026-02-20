package greensnaback0229.pr_review_server.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import greensnaback0229.pr_review_server.auth.dto.ApiKeyStatusResponse;
import greensnaback0229.pr_review_server.auth.dto.ApiKeySaveRequest;
import greensnaback0229.pr_review_server.auth.entity.User;
import greensnaback0229.pr_review_server.auth.exception.InvalidApiKeyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("ApiKeyController 테스트")
class ApiKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ApiKeyService apiKeyService;

    private SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor mockOAuth2User() {
        User user = User.builder()
                .id(1L)
                .githubId(12345L)
                .githubLogin("testuser")
                .githubToken("encrypted-token")
                .role("USER")
                .build();
        CustomOAuth2User customUser = new CustomOAuth2User(user, Map.of(
                "id", 12345,
                "login", "testuser"
        ));
        return oauth2Login().oauth2User(customUser);
    }

    @Test
    @DisplayName("GET_apiKey상태조회_성공")
    void GET_apiKey상태조회_성공() throws Exception {
        // given
        ApiKeyStatusResponse response = ApiKeyStatusResponse.builder()
                .hasApiKey(true)
                .maskedKey("sk-ant-****a3f2")
                .build();
        when(apiKeyService.getApiKeyStatus(1L)).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/settings/api-key").with(mockOAuth2User()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasApiKey").value(true))
                .andExpect(jsonPath("$.maskedKey").value("sk-ant-****a3f2"));
    }

    @Test
    @DisplayName("PUT_apiKey저장_성공")
    void PUT_apiKey저장_성공() throws Exception {
        // given
        ApiKeySaveRequest request = new ApiKeySaveRequest("sk-ant-api03-valid-key");
        doNothing().when(apiKeyService).saveApiKey(eq(1L), anyString());

        // when & then
        mockMvc.perform(put("/api/settings/api-key")
                        .with(mockOAuth2User())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(apiKeyService).saveApiKey(1L, "sk-ant-api03-valid-key");
    }

    @Test
    @DisplayName("PUT_apiKey유효하지않음_400")
    void PUT_apiKey유효하지않음_400() throws Exception {
        // given
        ApiKeySaveRequest request = new ApiKeySaveRequest("invalid-key");
        doThrow(new InvalidApiKeyException("유효하지 않은 API Key입니다."))
                .when(apiKeyService).saveApiKey(eq(1L), anyString());

        // when & then
        mockMvc.perform(put("/api/settings/api-key")
                        .with(mockOAuth2User())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE_apiKey삭제_성공")
    void DELETE_apiKey삭제_성공() throws Exception {
        // given
        doNothing().when(apiKeyService).deleteApiKey(1L);

        // when & then
        mockMvc.perform(delete("/api/settings/api-key").with(mockOAuth2User()))
                .andExpect(status().isOk());

        verify(apiKeyService).deleteApiKey(1L);
    }

    @Test
    @DisplayName("미인증요청_401또는리다이렉트")
    void 미인증요청_401또는리다이렉트() throws Exception {
        mockMvc.perform(get("/api/settings/api-key"))
                .andExpect(status().is3xxRedirection());
    }
}
