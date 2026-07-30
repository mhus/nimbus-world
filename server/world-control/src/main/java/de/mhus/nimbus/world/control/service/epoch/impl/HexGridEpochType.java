package de.mhus.nimbus.world.control.service.epoch.impl;

import de.mhus.nimbus.world.control.service.epoch.ResourceEpochService;
import de.mhus.nimbus.world.control.service.epoch.ResourceEpochType;
import de.mhus.nimbus.world.shared.world.EpochProcessResult;
import de.mhus.nimbus.world.shared.world.WEpochMeta;
import de.mhus.nimbus.world.shared.world.WHexGridService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HexGridEpochType implements ResourceEpochType {

    private final WHexGridService hexGridService;

    @Override
    public String name() {
        return "hexgrid";
    }

    @Override
    public ResourceEpochService.ProcessResult validate(String worldId, List<WEpochMeta> epochMetas) {
        return toProcessResult(hexGridService.validateEpochs(worldId, epochMetas));
    }

    @Override
    public ResourceEpochService.ProcessResult create(String worldId, int sourceEpoch, int newEpoch) {
        return toProcessResult(hexGridService.createEpoch(worldId, sourceEpoch, newEpoch));
    }

    @Override
    public ResourceEpochService.ProcessResult delete(String worldId, int epoch) {
        return toProcessResult(hexGridService.deleteEpoch(worldId, epoch));
    }

    private ResourceEpochService.ProcessResult toProcessResult(EpochProcessResult result) {
        return new ResourceEpochService.ProcessResult(
                result.typeName(), result.success(), result.message(), result.timestamp());
    }
}
