package greensnaback0229.pr_review_server.tenant;

import greensnaback0229.pr_review_server.auth.CustomOAuth2User;
import greensnaback0229.pr_review_server.auth.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantFilterTest {

    private TenantFilter tenantFilter;
    private FilterChain filterChain;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        tenantFilter = new TenantFilter();
        filterChain = mock(FilterChain.class);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증된 요청 → TenantContext에 userId 설정")
    void authenticatedRequest_setsTenantContext() throws ServletException, IOException {
        // given
        User user = User.builder().githubId(123L).githubLogin("test").githubToken("token").role("USER").build();
        // User의 id는 @GeneratedValue이므로 리플렉션으로 설정
        setUserId(user, 42L);

        CustomOAuth2User oAuth2User = new CustomOAuth2User(user, Map.of("id", 123L, "login", "test"));
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(oAuth2User, null, oAuth2User.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        request.setRequestURI("/api/repositories");

        // filterChain에서 TenantContext 값을 캡처
        final Long[] capturedUserId = {null};
        doAnswer(invocation -> {
            capturedUserId[0] = TenantContext.getCurrentUserId();
            return null;
        }).when(filterChain).doFilter(request, response);

        // when
        tenantFilter.doFilter(request, response, filterChain);

        // then
        assertThat(capturedUserId[0]).isEqualTo(42L);
        // 필터 종료 후 TenantContext가 정리되었는지 확인
        assertThat(TenantContext.getCurrentUserId()).isNull();
    }

    @Test
    @DisplayName("Webhook 경로 → TenantContext 설정 안 함 (필터 스킵)")
    void webhookPath_skipsTenantContext() throws ServletException, IOException {
        // given
        request.setRequestURI("/api/webhook/github/pr");

        // when
        tenantFilter.doFilter(request, response, filterChain);

        // then
        verify(filterChain).doFilter(request, response);
        assertThat(TenantContext.getCurrentUserId()).isNull();
    }

    @Test
    @DisplayName("미인증 요청 → TenantContext 미설정")
    void unauthenticatedRequest_noTenantContext() throws ServletException, IOException {
        // given
        request.setRequestURI("/api/repositories");

        final Long[] capturedUserId = {999L}; // 초기값으로 non-null 설정
        doAnswer(invocation -> {
            capturedUserId[0] = TenantContext.getCurrentUserId();
            return null;
        }).when(filterChain).doFilter(request, response);

        // when
        tenantFilter.doFilter(request, response, filterChain);

        // then
        assertThat(capturedUserId[0]).isNull();
    }

    @Test
    @DisplayName("filterChain 예외 발생 시에도 TenantContext 정리")
    void exceptionInChain_stillClearsTenantContext() throws ServletException, IOException {
        // given
        User user = User.builder().githubId(123L).githubLogin("test").githubToken("token").role("USER").build();
        setUserId(user, 42L);
        CustomOAuth2User oAuth2User = new CustomOAuth2User(user, Map.of("id", 123L, "login", "test"));
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(oAuth2User, null, oAuth2User.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        request.setRequestURI("/dashboard");
        doThrow(new ServletException("test error")).when(filterChain).doFilter(request, response);

        // when & then
        assertThatThrownBy(() -> tenantFilter.doFilter(request, response, filterChain))
                .isInstanceOf(ServletException.class);
        assertThat(TenantContext.getCurrentUserId()).isNull();
    }

    private void setUserId(User user, Long id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
