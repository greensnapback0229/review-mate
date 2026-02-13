package greensnaback0229.pr_review_server.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("setCurrentUserId → getCurrentUserId 정상 반환")
    void setAndGet() {
        TenantContext.setCurrentUserId(42L);
        assertThat(TenantContext.getCurrentUserId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("setCurrentUserId(null) → IllegalArgumentException")
    void setNull_throwsIllegalArgument() {
        assertThatThrownBy(() -> TenantContext.setCurrentUserId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    @Test
    @DisplayName("미설정 상태에서 getCurrentUserId → null")
    void getBeforeSet_returnsNull() {
        assertThat(TenantContext.getCurrentUserId()).isNull();
    }

    @Test
    @DisplayName("미설정 상태에서 getCurrentUserIdOrThrow → TenantContextException")
    void getOrThrowBeforeSet_throwsTenantContextException() {
        assertThatThrownBy(TenantContext::getCurrentUserIdOrThrow)
                .isInstanceOf(TenantContextException.class)
                .hasMessageContaining("TenantContext not initialized");
    }

    @Test
    @DisplayName("설정 후 getCurrentUserIdOrThrow → 정상 반환")
    void getOrThrowAfterSet_returnsValue() {
        TenantContext.setCurrentUserId(99L);
        assertThat(TenantContext.getCurrentUserIdOrThrow()).isEqualTo(99L);
    }

    @Test
    @DisplayName("clear() 후 getCurrentUserId → null")
    void clearResetsToNull() {
        TenantContext.setCurrentUserId(1L);
        TenantContext.clear();
        assertThat(TenantContext.getCurrentUserId()).isNull();
    }

    @Test
    @DisplayName("clear() 후 getCurrentUserIdOrThrow → TenantContextException")
    void clearThenGetOrThrow_throwsException() {
        TenantContext.setCurrentUserId(1L);
        TenantContext.clear();
        assertThatThrownBy(TenantContext::getCurrentUserIdOrThrow)
                .isInstanceOf(TenantContextException.class);
    }

    @Test
    @DisplayName("다른 스레드에서 TenantContext 격리 확인")
    void threadIsolation() throws InterruptedException {
        TenantContext.setCurrentUserId(100L);

        Thread otherThread = new Thread(() -> {
            // 다른 스레드에서는 userId가 null이어야 함
            assertThat(TenantContext.getCurrentUserId()).isNull();
        });
        otherThread.start();
        otherThread.join();

        // 원래 스레드는 여전히 100L
        assertThat(TenantContext.getCurrentUserId()).isEqualTo(100L);
    }
}
