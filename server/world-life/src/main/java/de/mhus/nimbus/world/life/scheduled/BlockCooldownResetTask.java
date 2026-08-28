package de.mhus.nimbus.world.life.scheduled;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.service.WorldDiscoveryService;
import de.mhus.nimbus.world.shared.world.WProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Scheduled task that resets collected elements after their cooldown expired.
 *
 * When a player collects from a block (see CollectAction in world-player), the block switches
 * to its collect status (default "empty") and a pending cooldown entry is stored in WProgress
 * (playerId="world", type="block-cooldown", quest=chunkKey, progressData: blockKey -> expiry).
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
        Map<String, Map<String, Object>> cooldowns = progressService.findBlockCooldowns(worldId);
        if (cooldowns.isEmpty()) return;

        int resetCount = 0;
        for (var chunkEntry : cooldowns.entrySet()) {
            String chunkKey = chunkEntry.getKey();
            for (var blockEntry : chunkEntry.getValue().entrySet()) {
                String blockKey = blockEntry.getKey();
                Object value = blockEntry.getValue();

                // A value that is not a timestamp is treated as expired, so it never piles up
                if (value instanceof Number expiresAt && expiresAt.longValue() > now) continue;

                // Only the pod that removes the entry resets the block status
                if (!progressService.claimExpiredBlockCooldown(worldId, chunkKey, blockKey, value)) continue;

                progressService.removeBlockStatus(worldId, chunkKey, blockKey);
                resetCount++;
            }
        }

        if (resetCount > 0) {
            progressService.deleteEmptyBlockCooldowns(worldId);
            log.debug("World {}: reset {} collected elements after cooldown", worldId, resetCount);
        }
    }
}
