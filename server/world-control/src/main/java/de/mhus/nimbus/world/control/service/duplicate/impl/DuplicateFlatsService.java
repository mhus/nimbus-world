package de.mhus.nimbus.world.control.service.duplicate.impl;

import de.mhus.nimbus.world.control.service.duplicate.DuplicateToWorld;
import de.mhus.nimbus.world.shared.generator.WFlatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service to duplicate flats from source world to target world.
 * Delegates to the owning {@link WFlatService} which holds data ownership over WFlat.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateFlatsService implements DuplicateToWorld {

    private final WFlatService flatService;

    @Override
    public String name() {
        return "flats";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        flatService.duplicateToWorld(sourceWorldId, targetWorldId);
    }
}
