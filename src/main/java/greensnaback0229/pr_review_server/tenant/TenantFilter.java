package greensnaback0229.pr_review_server.tenant;

import greensnaback0229.pr_review_server.auth.CustomOAuth2User;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증된 요청에서 TenantContext를 자동 설정하는 필터.
 * Webhook 경로는 제외 (별도 매핑 로직 사용).
 */
@Slf4j
@Component
public class TenantFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String requestURI = httpRequest.getRequestURI();

        // Webhook 경로는 제외 (WebhookController에서 직접 TenantContext 설정)
        if (requestURI.startsWith("/api/webhook/")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                Long userId = extractUserId(auth);
                if (userId != null) {
                    TenantContext.setCurrentUserId(userId);
                    log.debug("TenantContext set: userId={}, uri={}", userId, requestURI);
                }
            }

            chain.doFilter(request, response);

        } finally {
            TenantContext.clear();
        }
    }

    private Long extractUserId(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof CustomOAuth2User oAuth2User) {
            return oAuth2User.getUserId();
        }
        return null;
    }
}
