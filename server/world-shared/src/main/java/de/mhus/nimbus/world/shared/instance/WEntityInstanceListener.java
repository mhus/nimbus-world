package de.mhus.nimbus.world.shared.instance;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.world.WEntityService;
import de.mhus.nimbus.world.shared.world.WWorldInstance;
import de.mhus.nimbus.world.shared.world.WWorldInstanceListener;
import de.mhus.nimbus.world.shared.world.WorldInstanceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Cleans up WEntity COW overlays and tombstones when a world instance is deleted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WEntityInstanceListener implements WWorldInstanceListener {

    private final WEntityService entityService;

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

        entityService.deleteByWorldId(instanceWorldId);
        log.info("Deleted WEntity data for instance {}", instanceWorldId);
    }
}
