package de.mhus.nimbus.world.control.service.delete;

import de.mhus.nimbus.shared.storage.StorageService;
import de.mhus.nimbus.world.shared.world.SAsset;
import de.mhus.nimbus.world.shared.world.SAssetRepository;
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

    private final SAssetRepository assetRepository;
    private final StorageService storageService;

    @Override
    public String name() {
        return "assets";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting assets for world {}", worldId);

        List<SAsset> assets = assetRepository.findByWorldId(worldId);
        log.info("Found {} assets in world {}", assets.size(), worldId);

        int deletedCount = 0;
        int storageCount = 0;

        for (SAsset asset : assets) {
            // Delete storage data if present
            if (asset.getStorageId() != null) {
                try {
                    storageService.delete(asset.getStorageId());
                    storageCount++;
                } catch (Exception e) {
                    log.warn("Failed to delete storage {} for asset {}: {}",
                            asset.getStorageId(), asset.getId(), e.getMessage());
                }
            }

            assetRepository.delete(asset);
            deletedCount++;
        }

        log.info("Deleted {} assets (including {} storage items) for world {}",
                deletedCount, storageCount, worldId);
    }
}
