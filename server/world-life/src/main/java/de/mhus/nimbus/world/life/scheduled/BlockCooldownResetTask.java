package de.mhus.nimbus.world.life.scheduled;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.service.WorldDiscoveryService;
import de.mhus.nimbus.world.shared.world.WBlockCooldown;
import de.mhus.nimbus.world.shared.world.WProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled task that resets collected elements after their cooldown expired.
 *
 * When a player collects from a block (see CollectAction in world-player), the block switches
 * to its collect status (default "empty") and a pending cooldown entry is stored in WProgress
 * (playerId="world", type="block-cooldown", quest=chunkKey, progressData: blockKey -> entry).
 *
 * This task sweeps those entries for all known worlds, removes the block status of expired
 * entries - which broadcasts the reset to all pods and clients - and forgets the entry.
 * The reset is not exact: it happens at the next sweep after the cooldown expired.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BlockCooldownResetTask {

    private final WProgressService progressService;
    private final WorldDiscoveryService worldDiscoveryService;

    @Scheduled(fixedDelayString = "#{${world.life.block-cooldown-interval-ms:1000}}")
    public void resetExpiredBlockCooldowns() {
        long now = System.currentTimeMillis();

        for (WorldId worldId : worldDiscoveryService.getKnownWorldIds()) {
            try {
                resetExpiredBlockCooldowns(worldId.getId(), now);
            } catch (Exception e) {
                log.error("World {}: error during block cooldown reset", worldId, e);
            }
        }
    }

    private void resetExpiredBlockCooldowns(String worldId, long now) {
        List<WBlockCooldown> expired = progressService.findExpiredBlockCooldowns(worldId, now);
        if (expired.isEmpty()) return;

        int resetCount = 0;
        for (WBlockCooldown cooldown : expired) {
            // Only the pod that removes the entry resets the block status
            if (!progressService.claimExpiredBlockCooldown(worldId, cooldown.chunkKey(), cooldown.blockKey(),
                    cooldown.expiresAt())) continue;

            resetBlockStatus(worldId, cooldown);
            resetCount++;
        }

        if (resetCount > 0) {
            progressService.deleteEmptyBlockCooldowns(worldId);
            progressService.deleteEmptyBlockStatuses(worldId);
            log.debug("World {}: reset {} collected elements after cooldown", worldId, resetCount);
        }
    }

    /**
     * Reset the block the cooldown belongs to. Entries that know their status only clear it while
     * the block still carries it, so a status somebody set in the meantime survives the reset.
     */
    private void resetBlockStatus(String worldId, WBlockCooldown cooldown) {
        if (cooldown.hasStatus()) {
            progressService.claimRemoveBlockStatus(worldId, cooldown.chunkKey(), cooldown.blockKey(), cooldown.status());
        } else {
            progressService.removeBlockStatus(worldId, cooldown.chunkKey(), cooldown.blockKey());
        }
    }
}
