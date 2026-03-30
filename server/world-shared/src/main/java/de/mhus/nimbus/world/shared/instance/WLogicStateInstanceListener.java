package de.mhus.nimbus.world.shared.instance;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.world.WProgressService;
import de.mhus.nimbus.world.shared.world.WWorldInstance;
import de.mhus.nimbus.world.shared.world.WWorldInstanceListener;
import de.mhus.nimbus.world.shared.world.WorldInstanceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Cleans up Logic Machine state (WProgress with playerId="logic") when a world instance is deleted.
 * Logic state is stored per instance in WProgress, not in the rules themselves.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WLogicStateInstanceListener implements WWorldInstanceListener {

    private static final String LOGIC_PLAYER_ID = "logic";

    private final WProgressService progressService;

    @Override
    public void worldInstanceCreated(WorldInstanceEvent event) {
    }

    @Override
    public void worldInstanceDeleted(WorldInstanceEvent event) {
        WWorldInstance instance = event.getInstance();
        if (instance == null) return;

        String instanceWorldId = instance.getWorldWithInstanceId();
        if (instanceWorldId == null || instanceWorldId.isBlank()) return;
        if (!WorldId.unchecked(instanceWorldId).isInstance()) return;

        // Delete logic state (playerId="logic") for this instance
        progressService.deleteByWorldIdAndPlayerId(instanceWorldId, LOGIC_PLAYER_ID);
        log.info("Deleted Logic Machine state for instance {}", instanceWorldId);
    }
}
