package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.world.WTraderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteTradersService implements DeleteWorldResources {

    private final WTraderService traderService;

    @Override
    public String name() {
        return "traders";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting traders for world {}", worldId);
        int deleted = traderService.deleteAllByWorldId(worldId);
        log.info("Deleted {} traders for world {}", deleted, worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        return traderService.findDistinctWorldIds();
    }
}
