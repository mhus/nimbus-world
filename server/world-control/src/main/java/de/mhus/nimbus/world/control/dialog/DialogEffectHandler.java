package de.mhus.nimbus.world.control.dialog;

import de.mhus.nimbus.world.control.dialog.DialogDtos.Effect;

/**
 * Interface for dialog effect handlers. Implementations are auto-discovered by Spring
 * and registered by their effect type name.
 */
public interface DialogEffectHandler {

    /**
     * The effect type this handler handles (e.g., "giveItem", "navigate", "navigateToTrade").
     */
    String getEffectType();

    /**
     * Execute the effect.
     */
    void execute(Effect effect, DialogContext ctx);
}
