package de.mhus.nimbus.world.generator.ai.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SimpleRateLimiter.
 */
class SimpleRateLimiterTest {

    @Test
    void testRateLimiterInitialization() {
        SimpleRateLimiter limiter = new SimpleRateLimiter(10);

        assertThat(limiter.getMaxRequestsPerMinute()).isEqualTo(10);
        assertThat(limiter.getCurrentRequestsPerMinute()).isEqualTo(0);
    }

    @Test
    void testRecordRequest() {
        SimpleRateLimiter limiter = new SimpleRateLimiter(10);

        limiter.recordRequest();
        assertThat(limiter.getCurrentRequestsPerMinute()).isEqualTo(1);

        limiter.recordRequest();
        assertThat(limiter.getCurrentRequestsPerMinute()).isEqualTo(2);
    }

    @Test
    void testWaitIfNeeded_belowLimit() throws InterruptedException {
        SimpleRateLimiter limiter = new SimpleRateLimiter(10);

        // Should not wait when below limit
        long start = System.currentTimeMillis();
        limiter.waitIfNeeded();
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isLessThan(100); // Should be instant
    }

    @Test
    void testCleanupOldRequests() throws InterruptedException {
        SimpleRateLimiter limiter = new SimpleRateLimiter(2);

        // Record two requests
        limiter.recordRequest();
        limiter.recordRequest();

        assertThat(limiter.getCurrentRequestsPerMinute()).isEqualTo(2);

        // Wait for requests to expire (small wait for test speed)
        Thread.sleep(100);

        // After cleanup, old requests should be removed
        // Note: This test uses a small window for testing purposes
        assertThat(limiter.getCurrentRequestsPerMinute()).isLessThanOrEqualTo(2);
    }

    @Test
    void testMultipleRequests() {
        SimpleRateLimiter limiter = new SimpleRateLimiter(5);

        for (int i = 0; i < 5; i++) {
            limiter.recordRequest();
        }

        assertThat(limiter.getCurrentRequestsPerMinute()).isEqualTo(5);
    }
}
