package de.mhus.nimbus.world.control.service.epoch.impl;

import de.mhus.nimbus.world.control.service.epoch.ResourceEpochService;
import de.mhus.nimbus.world.control.service.epoch.ResourceEpochType;
import de.mhus.nimbus.world.shared.world.EpochProcessResult;
import de.mhus.nimbus.world.shared.world.WEpochMeta;
import de.mhus.nimbus.world.shared.world.WItemPositionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemPositionEpochType implements ResourceEpochType {

    private final WItemPositionService itemPositionService;

    @Override
    public String name() {
        return "item_position";
    }

    @Override
    public ResourceEpochService.ProcessResult validate(String worldId, List<WEpochMeta> epochMetas) {
        return toProcessResult(itemPositionService.validateEpochs(worldId, epochMetas));
    }

    @Override
    public ResourceEpochService.ProcessResult create(String worldId, int sourceEpoch, int newEpoch) {
        return toProcessResult(itemPositionService.createEpoch(worldId, sourceEpoch, newEpoch));
    }

    @Override
    public ResourceEpochService.ProcessResult delete(String worldId, int epoch) {
        return toProcessResult(itemPositionService.deleteEpoch(worldId, epoch));
    }

    private ResourceEpochService.ProcessResult toProcessResult(EpochProcessResult result) {
        return new ResourceEpochService.ProcessResult(
                result.typeName(), result.success(), result.message(), result.timestamp());
    }
}
