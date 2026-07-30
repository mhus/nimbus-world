package de.mhus.nimbus.world.control.service.epoch.impl;

import de.mhus.nimbus.world.control.service.epoch.ResourceEpochService;
import de.mhus.nimbus.world.control.service.epoch.ResourceEpochType;
import de.mhus.nimbus.world.shared.world.EpochProcessResult;
import de.mhus.nimbus.world.shared.world.WEntityService;
import de.mhus.nimbus.world.shared.world.WEpochMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntityEpochType implements ResourceEpochType {

    private final WEntityService entityService;

    @Override
    public String name() {
        return "entity";
    }

    @Override
    public ResourceEpochService.ProcessResult validate(String worldId, List<WEpochMeta> epochMetas) {
        return toProcessResult(entityService.validateEpochs(worldId, epochMetas));
    }

    @Override
    public ResourceEpochService.ProcessResult create(String worldId, int sourceEpoch, int newEpoch) {
        return toProcessResult(entityService.createEpoch(worldId, sourceEpoch, newEpoch));
    }

    @Override
    public ResourceEpochService.ProcessResult delete(String worldId, int epoch) {
        return toProcessResult(entityService.deleteEpoch(worldId, epoch));
    }

    private ResourceEpochService.ProcessResult toProcessResult(EpochProcessResult result) {
        return new ResourceEpochService.ProcessResult(
                result.typeName(), result.success(), result.message(), result.timestamp());
    }
}
