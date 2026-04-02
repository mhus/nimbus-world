package de.mhus.nimbus.world.player.gameplay;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WTrader;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Action handler for opening a trade dialog with an NPC trader.
 *
 * Server parameters:
 * - trader: entityId of the WTrader (linked WEntity)
 *
 * Flow:
 * 1. Resolve WTrader by entityId and worldId
 * 2. Trigger pool sync if interval has elapsed
 * 3. Create WProgress (type="trade-access") with trade configuration
 * 4. Send openComponent command to client with the progressId
 */
@Slf4j
public class OpenTradeAction extends AbstractGamplayAction {

    public OpenTradeAction(BasicGameplay basic) {
        super(basic);
    }

    @Override
    public boolean handleAction(PlayerSession session, Map<String, String> serverParameters, JsonNode params) {
        if (session.getWorldId() == null) return false;

        String traderEntityId = serverParameters.get("trader");
        if (Strings.isBlank(traderEntityId)) {
            log.warn("open.trade action missing 'trader' parameter");
            return false;
        }

        String worldId = session.getWorldId().getId();

        Optional<WTrader> traderOpt = basic.getTraderService().findByWorldIdAndEntityId(worldId, traderEntityId);
        if (traderOpt.isEmpty()) {
            log.warn("Trader not found: {} in world {}", traderEntityId, worldId);
            basic.getBasicClientService().sendNotification(session, 0, "", "Trader not found", null);
            return false;
        }

        WTrader trader = traderOpt.get();

        // Trigger pool sync if due
        basic.getTraderService().syncPoolIfDue(trader);

        String playerId = session.getEntityId();

        // Store trade configuration in progress data
        Map<String, Object> progressData = new HashMap<>();
        progressData.put("traderEntityId", trader.getEntityId());
        progressData.put("traderType", trader.getTraderType().name());
        progressData.put("chestId", trader.getChestId());

        var progress = basic.getProgressService().save(
                worldId,
                playerId,
                "trade-access",
                traderEntityId,
                "Trade",
                progressData
        );

        basic.getBasicClientService().sendCommand(session, "openComponent",
                List.of("trade", progress.getProgressId()));

        log.debug("Sent open.trade to player {}: trader={}, progressId={}",
                playerId, traderEntityId, progress.getProgressId());
        return true;
    }
}
