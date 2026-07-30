package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.world.SAssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service to delete assets for a given world.
 * Also deletes associated storage data if present.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteAssetsService implements DeleteWorldResources {

    private final SAssetService sAssetService;

    @Override
    public String name() {
        return "assets";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting assets for world {}", worldId);
        sAssetService.deleteAllByWorldId(worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        return sAssetService.findDistinctWorldIds();
    }

}
