package de.mhus.nimbus.world.control.service.duplicate.impl;

import de.mhus.nimbus.world.control.service.duplicate.DuplicateToWorld;
import de.mhus.nimbus.world.shared.world.SAssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service to duplicate assets from source world to target world.
 * Also duplicates associated storage data if present.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateAssetsService implements DuplicateToWorld {

    private final SAssetService sAssetService;

    @Override
    public String name() {
        return "assets";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating assets from world {} to {}", sourceWorldId, targetWorldId);
        sAssetService.duplicateToWorld(sourceWorldId, targetWorldId);
    }
}
