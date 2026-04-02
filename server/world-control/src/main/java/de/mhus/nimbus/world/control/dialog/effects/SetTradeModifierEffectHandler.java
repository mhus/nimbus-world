package de.mhus.nimbus.world.control.dialog.effects;

import de.mhus.nimbus.world.control.dialog.DialogContext;
import de.mhus.nimbus.world.control.dialog.DialogDtos.Effect;
import de.mhus.nimbus.world.control.dialog.DialogEffectHandler;
import de.mhus.nimbus.world.shared.world.WProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Sets the individual trade modifier for the current player-NPC relationship.
 * This affects buy/sell prices at this specific trader.
 *
 * Usage in playbook: {"type": "setTradeModifier", "value": -0.1}
 * Negative value = cheaper prices (discount), positive = more expensive.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SetTradeModifierEffectHandler implements DialogEffectHandler {

    private static final String INDIVIDUAL_MODIFIER_TYPE = "trade-individual";

    private final WProgressService progressService;

    @Override
    public String getEffectType() { return "setTradeModifier"; }

    @Override
    public void execute(Effect effect, DialogContext ctx) {
        String entityId = ctx.getNpcEntity() != null ? ctx.getNpcEntity().getEntityId() : null;
        if (entityId == null) {
            log.warn("setTradeModifier: no NPC entity in context");
            return;
        }
        if (effect.value() == null) {
            log.warn("setTradeModifier: missing 'value'");
            return;
        }

        double modifier;
        if (effect.value() instanceof Number number) {
            modifier = number.doubleValue();
        } else {
            try {
                modifier = Double.parseDouble(effect.value().toString());
            } catch (NumberFormatException e) {
                log.warn("setTradeModifier: invalid value '{}'", effect.value());
                return;
            }
        }

        progressService.save(
                ctx.getWorldId(),
                ctx.getPlayerId(),
                INDIVIDUAL_MODIFIER_TYPE,
                entityId,
                "Trade Modifier",
                Map.of("modifier", modifier)
        );

        log.debug("Set trade individual modifier {} for trader={}, player={}",
                modifier, entityId, ctx.getPlayerId());
    }
}
