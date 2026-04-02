package de.mhus.nimbus.world.control.dialog.effects;

import de.mhus.nimbus.world.control.dialog.DialogContext;
import de.mhus.nimbus.world.control.dialog.DialogDtos.Effect;
import de.mhus.nimbus.world.control.dialog.DialogEffectHandler;
import de.mhus.nimbus.world.shared.world.WProgressService;
import de.mhus.nimbus.world.shared.world.WTrader;
import de.mhus.nimbus.world.shared.world.WTraderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Opens the trade widget for the current NPC's trader data.
 * Creates a WProgress for trade-access and sets the navigate URL.
 * Usage in playbook: {"type": "navigateToTrade"}
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NavigateToTradeEffectHandler implements DialogEffectHandler {

    private final WTraderService traderService;
    private final WProgressService progressService;

    @Override
    public String getEffectType() { return "navigateToTrade"; }

    @Override
    public void execute(Effect effect, DialogContext ctx) {
        String entityId = ctx.getNpcEntity() != null ? ctx.getNpcEntity().getEntityId() : null;
        if (entityId == null) {
            log.warn("navigateToTrade: no NPC entity in context");
            return;
        }

        var traderOpt = traderService.findByWorldIdAndEntityId(ctx.getWorldId(), entityId);
        if (traderOpt.isEmpty()) {
            log.warn("navigateToTrade: no WTrader found for entityId={} in world={}", entityId, ctx.getWorldId());
            return;
        }

        WTrader trader = traderOpt.get();

        traderService.syncPoolIfDue(trader);

        Map<String, Object> progressData = new HashMap<>();
        progressData.put("traderEntityId", trader.getEntityId());
        progressData.put("traderType", trader.getTraderType().name());
        progressData.put("chestId", trader.getChestId());

        var progress = progressService.save(
                ctx.getWorldId(),
                ctx.getPlayerId(),
                "trade-access",
                entityId,
                "Trade",
                progressData
        );

        String progressId = progress.getProgressId();
        ctx.setNavigate("trade-widget.html?progressId=" + progressId);
        log.debug("navigateToTrade: trader={}, progressId={}", entityId, progressId);
    }
}
