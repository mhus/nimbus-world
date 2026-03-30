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
     * Execute a single effect and return the set of changed flag names.
     *
     * @throws LogicEvaluationException if the effect type is unknown
     */
    public Set<String> executeEffect(LogicEffect effect, LogicContext context) {
        LogicEffectHandler handler = handlers.get(effect.getType());
        if (handler == null) {
            throw new LogicEvaluationException(
                    "Unknown LogicEffect type: " + effect.getType(), null);
        }
        return handler.execute(effect.getParameters(), context);
    }
}
