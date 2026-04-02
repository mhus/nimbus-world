package de.mhus.nimbus.world.control.dialog.effects;

import de.mhus.nimbus.world.control.dialog.DialogContext;
import de.mhus.nimbus.world.control.dialog.DialogDtos.Effect;
import de.mhus.nimbus.world.control.dialog.DialogEffectHandler;
import de.mhus.nimbus.world.shared.world.WLeaseService;
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
    private final WLeaseService leaseService;

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

        Map<String, Object> leaseData = new HashMap<>();
        leaseData.put("traderEntityId", trader.getEntityId());
        leaseData.put("traderType", trader.getTraderType().name());
        leaseData.put("chestId", trader.getChestId());

        var lease = leaseService.acquire(
                ctx.getWorldId(),
                ctx.getPlayerId(),
                "trade-access",
                entityId,
                "Trade",
                leaseData
        );

        String leaseId = lease.getLeaseId();
        ctx.setNavigate("trade-widget.html?progressId=" + leaseId);
        log.debug("navigateToTrade: trader={}, leaseId={}", entityId, leaseId);
    }
}
