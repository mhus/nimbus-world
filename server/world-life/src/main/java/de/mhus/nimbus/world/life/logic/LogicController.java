package de.mhus.nimbus.world.life.logic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the Logic Machine.
 * Provides endpoints for event processing and condition checking.
 */
@RestController
@RequestMapping("/life/logic")
@RequiredArgsConstructor
@Slf4j
public class LogicController {

    private final LogicMachineService logicMachineService;

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
}
