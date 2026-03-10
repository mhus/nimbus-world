package de.mhus.nimbus.world.control.service.epoch.impl;

import de.mhus.nimbus.world.control.service.epoch.ResourceEpochService;
import de.mhus.nimbus.world.control.service.epoch.ResourceEpochType;
import de.mhus.nimbus.world.shared.world.WEpochMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemPositionEpochType implements ResourceEpochType {

    private static final String COLLECTION = "w_item_positions";
    private final MongoTemplate mongoTemplate;

    @Override
    public String name() {
        return "item_position";
    }

    @Override
    public ResourceEpochService.ProcessResult validate(String worldId, List<WEpochMeta> epochMetas) {
        return EpochTypeHelper.validate(mongoTemplate, COLLECTION, name(), worldId, epochMetas);
    }

    @Override
    public ResourceEpochService.ProcessResult create(String worldId, int sourceEpoch, int newEpoch) {
        return EpochTypeHelper.create(mongoTemplate, COLLECTION, name(), worldId, sourceEpoch, newEpoch);
    }

    @Override
    public ResourceEpochService.ProcessResult delete(String worldId, int epoch) {
        return EpochTypeHelper.delete(mongoTemplate, COLLECTION, name(), worldId, epoch);
    }
}
