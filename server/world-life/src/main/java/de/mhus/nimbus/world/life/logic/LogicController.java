package de.mhus.nimbus.world.life.logic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for the Logic Machine.
 * Provides endpoints for event processing, condition checking, and metrics.
 */
@RestController
@RequestMapping("/life/logic")
@RequiredArgsConstructor
@Slf4j
public class LogicController {

    private final LogicMachineService logicMachineService;
    private final LogicMetricsService metricsService;
    private final LogicTestService testService;

    /**
     * Process a logic event asynchronously.
     * Returns 202 Accepted immediately, processing happens in background.
     */
    @PostMapping("/event")
    public ResponseEntity<Void> processEvent(@RequestBody LogicEvent event) {
        log.debug("Received logic event: worldId={}, source={}", event.getWorldId(), event.getSource());
        logicMachineService.processEventAsync(event);
        return ResponseEntity.accepted().build();
    }

    /**
     * Check a condition against the current flag state.
     * Synchronous call, returns boolean result.
     */
    @PostMapping("/condition")
    public ResponseEntity<LogicConditionResult> checkCondition(@RequestBody LogicCondition condition) {
        log.debug("Received condition check: worldId={}, expression={}",
                condition.getWorldId(), condition.getSpelExpression());
        LogicConditionResult result = logicMachineService.checkCondition(condition);
        return ResponseEntity.ok(result);
    }

    /**
     * Test: evaluate rule condition against live flags of a world instance.
     * Read-only, no state changes.
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testCondition(@RequestBody Map<String, Object> request) {
        String worldId = (String) request.get("worldId");
        String ruleId = (String) request.get("ruleId");
        if (worldId == null || worldId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "worldId required"));
        }
        return ResponseEntity.ok(testService.testCondition(worldId, ruleId, request));
    }

    /**
     * Simulate: dry-run a rule with user-provided flags (pure sandbox).
     * No DB access for flags, no persistence, no broadcasts.
     * Body: { ruleId, flags: { "pkg": { "flag": "value" } } }
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/simulate")
    public ResponseEntity<Map<String, Object>> simulate(@RequestBody Map<String, Object> request) {
        String ruleId = (String) request.get("ruleId");
        Map<String, Object> flags = (Map<String, Object>) request.get("flags");
        if (ruleId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "ruleId required"));
        }
        return ResponseEntity.ok(testService.simulate(ruleId, flags));
    }

    /**
     * Execute: run a rule live against a world instance.
     * Persists changes, triggers cascade.
     */
    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> execute(@RequestBody Map<String, Object> request) {
        String worldId = (String) request.get("worldId");
        String ruleId = (String) request.get("ruleId");
        if (worldId == null || worldId.isBlank() || ruleId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "worldId and ruleId required"));
        }
        return ResponseEntity.ok(testService.execute(worldId, ruleId));
    }

    /**
     * Get Logic Machine metrics.
     * Optional worldId filter.
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics(
            @RequestParam(required = false) String worldId) {
        return ResponseEntity.ok(metricsService.getMetrics(worldId));
    }

    /**
     * Reset all metrics counters.
     */
    @PostMapping("/metrics/reset")
    public ResponseEntity<Void> resetMetrics() {
        metricsService.reset();
        log.info("Logic Machine metrics reset");
        return ResponseEntity.ok().build();
    }
}
