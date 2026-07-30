package de.mhus.nimbus.world.control.service.duplicate.impl;

import de.mhus.nimbus.world.control.service.duplicate.DuplicateToWorld;
import de.mhus.nimbus.world.shared.world.WTraderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateTradersService implements DuplicateToWorld {

    private final WTraderService traderService;

    @Override
    public String name() {
        return "traders";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating traders from world {} to {}", sourceWorldId, targetWorldId);
        int duplicatedCount = traderService.duplicateToWorld(sourceWorldId, targetWorldId);
        log.info("Duplicated {} traders from world {} to {}",
                duplicatedCount, sourceWorldId, targetWorldId);
    }
}
