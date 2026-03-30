package de.mhus.nimbus.world.life.logic;

import de.mhus.nimbus.shared.service.MetricService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics for the Logic Machine.
 * Combines:
 * - In-memory per-rule stats (for the /life/logic/metrics REST endpoint)
 * - Micrometer via shared MetricService (for Prometheus/Grafana via /actuator/metrics)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogicMetricsService {

    private final MetricService metricService;

    // In-memory per-rule tracking
    private final ConcurrentHashMap<String, RuleMetrics> metrics = new ConcurrentHashMap<>();
    private final AtomicLong totalEventsProcessed = new AtomicLong();
    private final AtomicLong totalRulesFired = new AtomicLong();
    private final AtomicLong totalErrors = new AtomicLong();
    private final AtomicLong totalDelayedEffects = new AtomicLong();

    public void recordEventProcessed() {
        totalEventsProcessed.incrementAndGet();
        metricService.counter("logic.events.processed").increment();
    }

    /**
     * Record that a rule fired successfully with its execution duration.
     */
    public void recordRuleFired(String worldId, String ruleName, long durationMs) {
        // In-memory
        String key = worldId + ":" + ruleName;
        metrics.computeIfAbsent(key, k -> new RuleMetrics(worldId, ruleName))
                .recordFired(durationMs);
        totalRulesFired.incrementAndGet();

        // Micrometer via MetricService
        metricService.counter("logic.rules.fired", "worldId", worldId, "ruleName", ruleName).increment();
        metricService.timer("logic.rule.execution", "worldId", worldId, "ruleName", ruleName)
                .record(Duration.ofMillis(durationMs));

        if (durationMs > 100) {
            log.warn("Logic Machine: slow rule '{}' in worldId={} took {}ms", ruleName, worldId, durationMs);
        }
    }

    public void recordRuleSkipped(String worldId, String ruleName) {
        String key = worldId + ":" + ruleName;
        metrics.computeIfAbsent(key, k -> new RuleMetrics(worldId, ruleName))
                .recordSkipped();
    }

    public void recordRuleError(String worldId, String ruleName) {
        String key = worldId + ":" + ruleName;
        metrics.computeIfAbsent(key, k -> new RuleMetrics(worldId, ruleName))
                .recordError();
        totalErrors.incrementAndGet();
        metricService.counter("logic.rules.errors", "worldId", worldId, "ruleName", ruleName).increment();
    }

    public void recordDelayedEffect() {
        totalDelayedEffects.incrementAndGet();
        metricService.counter("logic.effects.delayed").increment();
    }

    /**
     * Get all metrics, optionally filtered by worldId.
     * Sorted by maxDurationMs descending (slowest rules first).
     */
    public Map<String, Object> getMetrics(String worldId) {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("totalEventsProcessed", totalEventsProcessed.get());
        result.put("totalRulesFired", totalRulesFired.get());
        result.put("totalErrors", totalErrors.get());
        result.put("totalDelayedEffects", totalDelayedEffects.get());

        List<Map<String, Object>> ruleMetrics = metrics.values().stream()
                .filter(m -> worldId == null || worldId.equals(m.worldId))
                .sorted(Comparator.comparingLong(RuleMetrics::getMaxDurationMs).reversed())
                .map(RuleMetrics::toMap)
                .toList();

        result.put("rules", ruleMetrics);
        return result;
    }

    public void reset() {
        metrics.clear();
        totalEventsProcessed.set(0);
        totalRulesFired.set(0);
        totalErrors.set(0);
        totalDelayedEffects.set(0);
    }

    static class RuleMetrics {
        final String worldId;
        final String ruleName;
        final AtomicLong fireCount = new AtomicLong();
        final AtomicLong skipCount = new AtomicLong();
        final AtomicLong errorCount = new AtomicLong();
        final AtomicLong totalDurationMs = new AtomicLong();
        volatile long minDurationMs = Long.MAX_VALUE;
        volatile long maxDurationMs = 0;
        volatile long lastDurationMs = 0;
        volatile Instant lastFired;
        volatile Instant lastError;

        RuleMetrics(String worldId, String ruleName) {
            this.worldId = worldId;
            this.ruleName = ruleName;
        }

        synchronized void recordFired(long durationMs) {
            fireCount.incrementAndGet();
            totalDurationMs.addAndGet(durationMs);
            lastDurationMs = durationMs;
            if (durationMs < minDurationMs) minDurationMs = durationMs;
            if (durationMs > maxDurationMs) maxDurationMs = durationMs;
            lastFired = Instant.now();
        }

        void recordSkipped() {
            skipCount.incrementAndGet();
        }

        void recordError() {
            errorCount.incrementAndGet();
            lastError = Instant.now();
        }

        long getMaxDurationMs() {
            return maxDurationMs;
        }

        Map<String, Object> toMap() {
            long fires = fireCount.get();
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("worldId", worldId);
            map.put("ruleName", ruleName);
            map.put("fireCount", fires);
            map.put("skipCount", skipCount.get());
            map.put("errorCount", errorCount.get());
            map.put("lastDurationMs", lastDurationMs);
            map.put("minDurationMs", fires > 0 ? minDurationMs : 0);
            map.put("maxDurationMs", maxDurationMs);
            map.put("avgDurationMs", fires > 0 ? totalDurationMs.get() / fires : 0);
            map.put("totalDurationMs", totalDurationMs.get());
            map.put("lastFired", lastFired);
            map.put("lastError", lastError);
            return map;
        }
    }
}
