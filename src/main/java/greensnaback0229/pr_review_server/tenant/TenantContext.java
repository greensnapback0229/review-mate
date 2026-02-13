package greensnaback0229.pr_review_server.tenant;

/**
 * ThreadLocal 기반 테넌트 컨텍스트
 * 요청 스레드에 현재 사용자 ID를 바인딩하여 데이터 격리를 보장한다.
 */
public class TenantContext {

    private static final ThreadLocal<Long> currentUserId = new ThreadLocal<>();

    public static void setCurrentUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        currentUserId.set(userId);
    }

    public static Long getCurrentUserId() {
        return currentUserId.get();
    }

    public static Long getCurrentUserIdOrThrow() {
        Long userId = currentUserId.get();
        if (userId == null) {
            throw new TenantContextException("TenantContext not initialized - userId is null");
        }
        return userId;
    }

    public static void clear() {
        currentUserId.remove();
    }
}
