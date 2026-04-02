package de.mhus.nimbus.world.shared.instance;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.world.WLeaseService;
import de.mhus.nimbus.world.shared.world.WProgressService;
import de.mhus.nimbus.world.shared.world.WWorldInstance;
import de.mhus.nimbus.world.shared.world.WWorldInstanceListener;
import de.mhus.nimbus.world.shared.world.WorldInstanceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Cleans up WProgress and WLease instance-specific data when a world instance is deleted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WProgressInstanceListener implements WWorldInstanceListener {

    private final WProgressService progressService;
    private final WLeaseService leaseService;

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

        progressService.deleteByWorldId(instanceWorldId);
        leaseService.releaseByWorldId(instanceWorldId);
        log.info("Deleted WProgress and WLease data for instance {}", instanceWorldId);
    }
}
