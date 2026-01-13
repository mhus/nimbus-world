package de.mhus.nimbus.world.generator.ai.model;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Simple rate limiter implementation using a sliding window.
 * Thread-safe and suitable for multi-threaded environments.
 */
@Slf4j
public class SimpleRateLimiter implements RateLimiter {

    private final int maxRequestsPerMinute;
    private final long windowMillis = 60_000; // 1 minute
    private final ConcurrentLinkedDeque<Long> requestTimestamps = new ConcurrentLinkedDeque<>();

    public SimpleRateLimiter(int maxRequestsPerMinute) {
        if (maxRequestsPerMinute <= 0) {
            throw new IllegalArgumentException("maxRequestsPerMinute must be positive");
        }
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        log.info("Initialized rate limiter: {} requests per minute", maxRequestsPerMinute);
    }

    @Override
    public synchronized void waitIfNeeded() throws InterruptedException {
        cleanupOldRequests();

        while (requestTimestamps.size() >= maxRequestsPerMinute) {
            // Calculate wait time until oldest request expires
            Long oldestTimestamp = requestTimestamps.peekFirst();
            if (oldestTimestamp == null) break;

            long now = System.currentTimeMillis();
            long expiryTime = oldestTimestamp + windowMillis;
            long waitTime = expiryTime - now;

            if (waitTime > 0) {
                log.debug("Rate limit reached ({}/{}), waiting {}ms",
                        requestTimestamps.size(), maxRequestsPerMinute, waitTime);
                Thread.sleep(waitTime);
            }

            cleanupOldRequests();
        }
    }

    @Override
    public synchronized void recordRequest() {
        requestTimestamps.addLast(System.currentTimeMillis());
        cleanupOldRequests();
    }

    @Override
    public synchronized int getCurrentRequestsPerMinute() {
        cleanupOldRequests();
        return requestTimestamps.size();
    }

    @Override
    public int getMaxRequestsPerMinute() {
        return maxRequestsPerMinute;
    }

    private void cleanupOldRequests() {
        long now = System.currentTimeMillis();
        long cutoff = now - windowMillis;

        while (!requestTimestamps.isEmpty()) {
            Long oldest = requestTimestamps.peekFirst();
            if (oldest != null && oldest < cutoff) {
                requestTimestamps.pollFirst();
            } else {
                break;
            }
        }
    }
}
