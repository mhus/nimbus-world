package de.mhus.nimbus.world.control.dialog.effects;

import de.mhus.nimbus.world.control.dialog.DialogContext;
import de.mhus.nimbus.world.control.dialog.DialogDtos.Effect;
import de.mhus.nimbus.world.control.dialog.DialogEffectHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Generic navigate effect — sets a path that the dialog widget will navigate to.
 * Usage in playbook: {"type": "navigate", "path": "some-widget.html?param=value"}
 */
@Component
@Slf4j
public class NavigateEffectHandler implements DialogEffectHandler {

    @Override
    public String getEffectType() { return "navigate"; }

    @Override
    public void execute(Effect effect, DialogContext ctx) {
        if (effect.path() == null || effect.path().isBlank()) {
            log.warn("navigate effect missing 'path'");
            return;
        }
        ctx.setNavigate(effect.path());
        log.debug("Set navigate path: {}", effect.path());
    }
}
