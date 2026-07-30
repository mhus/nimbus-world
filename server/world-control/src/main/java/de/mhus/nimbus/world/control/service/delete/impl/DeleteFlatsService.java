package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.generator.WFlatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteFlatsService implements DeleteWorldResources {

    private final WFlatService flatService;

    @Override
    public String name() {
        return "flats";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        flatService.deleteByWorldId(worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        return flatService.findDistinctWorldIds();
    }
}
