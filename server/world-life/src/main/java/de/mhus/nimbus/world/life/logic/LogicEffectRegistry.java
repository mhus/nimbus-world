package de.mhus.nimbus.world.life.logic;

import de.mhus.nimbus.world.shared.world.LogicEffect;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry for Logic Machine effect handlers.
 * Auto-discovers all LogicEffectHandler beans via Spring injection.
 *
 * Effects with a "delay" parameter (in seconds) are not executed immediately.
 * Instead, they are collected in the LogicContext for deferred scheduling
 * after the instance lock is released.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogicEffectRegistry {

    private final List<LogicEffectHandler> handlerList;
    private final Map<String, LogicEffectHandler> handlers = new HashMap<>();

    @PostConstruct
    public void init() {
        for (LogicEffectHandler handler : handlerList) {
            handlers.put(handler.getType(), handler);
            log.info("Registered LogicEffectHandler: {}", handler.getType());
        }
    }

    /**
     * Execute a single effect immediately, or defer it if a "delay" parameter is present.
     *
     * @return set of changed flag names (empty for delayed effects, since they run later)
     * @throws LogicEvaluationException if the effect type is unknown
     */
    public Set<String> executeEffect(LogicEffect effect, LogicContext context) {
        LogicEffectHandler handler = handlers.get(effect.getType());
        if (handler == null) {
            throw new LogicEvaluationException(
                    "Unknown LogicEffect type: " + effect.getType(), null);
        }

        // Check for delay parameter
        int delay = parseDelay(effect.getParameters());
        if (delay > 0) {
            context.addDelayedEffect(effect, context.getRulePackage(), delay);
            log.debug("Deferred effect '{}' with delay={}s", effect.getType(), delay);
            return Set.of();
        }

        return handler.execute(effect.getParameters(), context);
    }

    /**
     * Execute an effect directly (used for delayed execution after lock release).
     */
    public Set<String> executeEffectDirect(LogicEffect effect, LogicContext context) {
        LogicEffectHandler handler = handlers.get(effect.getType());
        if (handler == null) {
            log.error("Unknown LogicEffect type for delayed execution: {}", effect.getType());
            return Set.of();
        }
        return handler.execute(effect.getParameters(), context);
    }

    private int parseDelay(Map<String, String> parameters) {
        if (parameters == null) return 0;
        String delayStr = parameters.get("delay");
        if (delayStr == null || delayStr.isBlank()) return 0;
        try {
            int delay = Integer.parseInt(delayStr.trim());
            return Math.max(0, delay);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
