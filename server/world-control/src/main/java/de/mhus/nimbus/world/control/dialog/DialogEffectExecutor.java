package de.mhus.nimbus.world.control.dialog;

import de.mhus.nimbus.world.control.dialog.DialogDtos.Effect;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes dialog effects by dispatching to registered {@link DialogEffectHandler} implementations.
 * Handlers are auto-discovered via Spring and registered by their effect type name.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DialogEffectExecutor {

    private final List<DialogEffectHandler> handlers;
    private final Map<String, DialogEffectHandler> handlerMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (DialogEffectHandler handler : handlers) {
            String type = handler.getEffectType();
            if (handlerMap.containsKey(type)) {
                log.warn("Duplicate DialogEffectHandler for type '{}': {} vs {}",
                        type, handlerMap.get(type).getClass().getSimpleName(), handler.getClass().getSimpleName());
            }
            handlerMap.put(type, handler);
            log.debug("Registered DialogEffectHandler: {} -> {}", type, handler.getClass().getSimpleName());
        }
        log.info("Registered {} dialog effect handlers: {}", handlerMap.size(), handlerMap.keySet());
    }

    /**
     * Execute all effects in order.
     */
    public void executeAll(List<Effect> effects, DialogContext ctx) {
        if (effects == null || effects.isEmpty()) return;
        for (Effect effect : effects) {
            try {
                execute(effect, ctx);
            } catch (Exception e) {
                log.error("Failed to execute effect type={} in dialog playbook={}: {}",
                        effect.type(), ctx.getPlaybookName(), e.getMessage(), e);
            }
        }
    }

    /**
     * Execute a single effect by dispatching to the registered handler.
     */
    public void execute(Effect effect, DialogContext ctx) {
        DialogEffectHandler handler = handlerMap.get(effect.type());
        if (handler == null) {
            log.warn("Unknown dialog effect type: {}", effect.type());
            return;
        }
        handler.execute(effect, ctx);
    }
}
