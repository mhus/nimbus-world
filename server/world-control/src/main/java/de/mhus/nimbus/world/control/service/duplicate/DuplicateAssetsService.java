package de.mhus.nimbus.world.control.service.duplicate;

import de.mhus.nimbus.shared.storage.StorageService;
import de.mhus.nimbus.world.shared.world.SAsset;
import de.mhus.nimbus.world.shared.world.SAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Service to duplicate assets from source world to target world.
 * Also duplicates associated storage data if present.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateAssetsService implements DuplicateToWorld {

    private final SAssetRepository assetRepository;
    private final StorageService storageService;

    @Override
    public String name() {
        return "assets";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating assets from world {} to {}", sourceWorldId, targetWorldId);

        List<SAsset> sourceAssets = assetRepository.findByWorldId(sourceWorldId);
        log.info("Found {} assets in source world {}", sourceAssets.size(), sourceWorldId);

        int duplicatedCount = 0;
        int storageCount = 0;

        for (SAsset sourceAsset : sourceAssets) {
            SAsset targetAsset = SAsset.builder()
                    .path(sourceAsset.getPath())
                    .name(sourceAsset.getName())
                    .size(sourceAsset.getSize())
                    .compressed(sourceAsset.isCompressed())
                    .publicData(sourceAsset.getPublicData())
                    .createdAt(Instant.now())
                    .createdBy(sourceAsset.getCreatedBy())
                    .enabled(sourceAsset.isEnabled())
                    .worldId(targetWorldId)
                    .build();

            // Duplicate storage data if present
            if (sourceAsset.getStorageId() != null) {
                String newStorageId = storageService.duplicate(sourceAsset.getStorageId(), targetWorldId);
                targetAsset.setStorageId(newStorageId);
                storageCount++;
            }

            assetRepository.save(targetAsset);
            duplicatedCount++;
        }

        log.info("Duplicated {} assets (including {} storage items) from world {} to {}",
                duplicatedCount, storageCount, sourceWorldId, targetWorldId);
    }
}
