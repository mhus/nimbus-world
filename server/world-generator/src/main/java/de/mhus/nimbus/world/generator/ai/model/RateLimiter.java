package de.mhus.nimbus.world.generator.ai.model;

/**
 * Rate limiter for AI requests.
 * Prevents exceeding API rate limits.
 */
public interface RateLimiter {

    /**
     * Wait if necessary to respect rate limits.
     * Blocks the current thread until it's safe to make the next request.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    void waitIfNeeded() throws InterruptedException;

    /**
     * Record a successful request.
     * Updates internal counters for rate limiting.
     */
    void recordRequest();

    /**
     * Get current requests per minute.
     *
     * @return Number of requests in the last minute
     */
    int getCurrentRequestsPerMinute();

    /**
     * Get maximum allowed requests per minute.
     *
     * @return Maximum requests per minute
     */
    int getMaxRequestsPerMinute();
}
