package de.mhus.nimbus.world.control.service.duplicate.impl;

import de.mhus.nimbus.world.control.service.duplicate.DuplicateToWorld;
import de.mhus.nimbus.world.shared.world.WBackdrop;
import de.mhus.nimbus.world.shared.world.WBackdropRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service to duplicate backdrops from source world to target world.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateBackdropsService implements DuplicateToWorld {

    private final WBackdropRepository backdropRepository;

    @Override
    public String name() {
        return "backdrops";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating backdrops from world {} to {}", sourceWorldId, targetWorldId);

        List<WBackdrop> sourceBackdrops = backdropRepository.findByWorldId(sourceWorldId);
        log.info("Found {} backdrops in source world {}", sourceBackdrops.size(), sourceWorldId);

        int duplicatedCount = 0;

        for (WBackdrop source : sourceBackdrops) {
            WBackdrop target = WBackdrop.builder()
                    .worldId(targetWorldId)
                    .backdropId(source.getBackdropId())
                    .publicData(source.getPublicData())
                    .enabled(source.isEnabled())
                    .build();

            target.touchCreate();
            backdropRepository.save(target);
            duplicatedCount++;
        }

        log.info("Duplicated {} backdrops from world {} to {}",
                duplicatedCount, sourceWorldId, targetWorldId);
    }
}
