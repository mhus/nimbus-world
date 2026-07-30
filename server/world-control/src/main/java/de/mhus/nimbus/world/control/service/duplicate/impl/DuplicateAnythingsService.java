package de.mhus.nimbus.world.control.service.duplicate.impl;

import de.mhus.nimbus.world.control.service.duplicate.DuplicateToWorld;
import de.mhus.nimbus.world.shared.world.WAnythingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service to duplicate WAnything entries from source world to target world.
 * Delegates to the owning WAnythingService which preserves the dynamic 'data'
 * field structure.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateAnythingsService implements DuplicateToWorld {

    private final WAnythingService anythingService;

    @Override
    public String name() {
        return "anythings";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating anythings from world {} to {}", sourceWorldId, targetWorldId);
        int duplicated = anythingService.duplicateToWorld(sourceWorldId, targetWorldId);
        log.info("Duplicated {} anythings from world {} to {}",
                duplicated, sourceWorldId, targetWorldId);
    }
}
