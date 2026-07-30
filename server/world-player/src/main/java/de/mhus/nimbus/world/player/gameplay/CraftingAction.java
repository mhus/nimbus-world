package de.mhus.nimbus.world.player.gameplay;

import tools.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.player.session.PlayerSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Action handler for opening a crafting station.
 *
 * Server parameters (on block/entity/item):
 * - category: crafting category / station type (e.g. "smithing", "weaving", "alchemy", "writing", "woodworking")
 * - slots: number of material slots (default: 4)
 * - allowSpells: whether spell words can be used (default: "false")
 *
 * Flow:
 * 1. Read station parameters from server info
 * 2. Create WProgress as contract item with station config
 * 3. Send openComponent "crafting" command to client with progressId
 */
@Slf4j
public class CraftingAction extends AbstractGamplayAction {

    public CraftingAction(BasicGameplay basic) {
        super(basic);
    }

    @Override
    public boolean handleAction(PlayerSession session, Map<String, String> serverParameters, JsonNode params) {
        if (session.getWorldId() == null) return false;

        String category = serverParameters.get("category");
        if (Strings.isBlank(category)) {
            log.warn("crafting action missing 'category' parameter");
            return false;
        }

        int slots = 4;
        try {
            String slotsStr = serverParameters.get("slots");
            if (slotsStr != null) slots = Integer.parseInt(slotsStr);
        } catch (NumberFormatException e) {
            log.warn("Invalid slots parameter, using default 4");
        }

        boolean allowSpells = "true".equalsIgnoreCase(serverParameters.get("allowSpells"));

        WorldId worldId = session.getWorldId();
        String playerId = session.getEntityId();

        // Acquire lease for crafting station access
        Map<String, Object> leaseData = new HashMap<>();
        leaseData.put("category", category);
        leaseData.put("slots", slots);
        leaseData.put("allowSpells", allowSpells);

        var lease = basic.getLeaseService().acquire(
                worldId.getId(),
                playerId,
                "crafting-station",
                category,
                "Crafting: " + category,
                leaseData
        );

        // Send openComponent command to client
        basic.getBasicClientService().sendCommand(session, "openComponent",
                List.of("crafting", lease.getLeaseId()));

        log.debug("Sent crafting to player {}: category={}, slots={}, allowSpells={}, leaseId={}",
                playerId, category, slots, allowSpells, lease.getLeaseId());
        return true;
    }
}
