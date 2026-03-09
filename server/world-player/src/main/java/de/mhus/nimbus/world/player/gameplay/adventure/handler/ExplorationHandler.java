package de.mhus.nimbus.world.player.gameplay.adventure.handler;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.player.gameplay.AdventureData;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.gameplay.AdventureSkills;
import de.mhus.nimbus.world.shared.util.HexMathUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Handles hex grid exploration tracking and fall damage.
 */
@Slf4j
public class ExplorationHandler {

    private static final double FALL_DAMAGE_PER_METER = 3.0;

    private final AdventureGameplay gameplay;

    public ExplorationHandler(AdventureGameplay gameplay) {
        this.gameplay = gameplay;
    }

    /**
     * Check if the player entered a new hex and track exploration.
     * Also updates the cached gameMode in AdventureData.
     */
    public void checkHexExploration(PlayerSession session, AdventureData data) {
        Vector3 pos = session.getLastPosition();
        if (pos == null || session.getWorldId() == null) return;

        int hexGridSize = session.getHexGridSize();
        if (hexGridSize <= 0) return;

        int worldX = (int) pos.getX();
        int worldZ = (int) pos.getZ();
        HexVector2 hexPos = HexMathUtil.flatToHex(TypeUtil.vector2int(worldX, worldZ), hexGridSize);
        String hexKey = hexPos.getQ() + ";" + hexPos.getR();

        // Same hex as last check? Nothing to do
        if (hexKey.equals(data.getLastCheckedHexKey())) return;
        data.setLastCheckedHexKey(hexKey);

        // Update gameMode cache
        gameplay.resolveGameMode(session);

        // Check exploration progress
        try {
            String worldId = session.getWorldId().getId();
            String playerId = session.getEntityId();
            if (playerId == null) return;

            var existing = gameplay.getProgressService().findByWorldIdAndPlayerIdAndTypeAndQuest(
                    worldId, playerId, "EXPLORED_HEX", hexKey);

            if (existing.isEmpty()) {
                gameplay.getProgressService().save(worldId, playerId, "EXPLORED_HEX", hexKey, Map.of(
                        "q", hexPos.getQ(),
                        "r", hexPos.getR(),
                        "discoveredAt", System.currentTimeMillis()
                ));
                gameplay.getClientService().sendNotification(session, 3, "", "New Area Discovered", null);
                log.info("Player {} discovered new hex {} in world {}", playerId, hexKey, worldId);
            }
        } catch (Exception e) {
            log.warn("Failed to check hex exploration for session {}: {}", session.getSessionId(), e.getMessage());
        }
    }

    /**
     * Handle fall damage based on fall height and acrobatics skill.
     * Safe fall height = acrobatics skill level (start=2, min=2, max=100).
     * Damage = 3 per block exceeding safe height.
     */
    public void handleFallDamage(PlayerSession session, AdventureData data, JsonNode messageData) {
        double fallHeight = messageData != null && messageData.has("fallHeight")
                ? messageData.get("fallHeight").asDouble(0) : 0;
        if (fallHeight <= 0) return;

        int safeFallHeight = AdventureSkills.SURVIVAL_ACROBATICS.getValue(data.getCachedSkills());
        if (fallHeight <= safeFallHeight) {
            log.trace("Player {} fell {} blocks (safe: {}), no damage",
                    session.getEntityId(), fallHeight, safeFallHeight);
            return;
        }

        double excessBlocks = fallHeight - safeFallHeight;
        double damage = excessBlocks * FALL_DAMAGE_PER_METER;

        log.debug("Player {} fell {} blocks (safe: {}), taking {} fall damage",
                session.getEntityId(), fallHeight, safeFallHeight, damage);

        gameplay.getVitalsHandler().applyDamage(session, data, damage);
    }
}
