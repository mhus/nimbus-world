package de.mhus.nimbus.world.life.behavior;

import de.mhus.nimbus.generated.types.EntityPathway;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.model.SimulationState;
import de.mhus.nimbus.world.shared.world.WEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Behavior for remotely controlled entities.
 * Polls pathways from RemotePathwayQueue (fed by Redis from external servers).
 * Remote servers determine timing and movement; this behavior just forwards.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RemoteBehavior implements EntityBehavior {

    private static final String BEHAVIOR_TYPE = "RemoteBehavior";

    private final RemotePathwayQueue remotePathwayQueue;

    @Override
    public String getBehaviorType() {
        return BEHAVIOR_TYPE;
    }

    @Override
    public EntityPathway update(WEntity entity, SimulationState state, long currentTime, WorldId worldId, int epoch) {
        EntityPathway pathway = remotePathwayQueue.poll(worldId.getId(), entity.getEntityId());
        if (pathway != null) {
            log.trace("Remote pathway received for entity {} in world {}", entity.getEntityId(), worldId);
        }
        return pathway;
    }

    @Override
    public boolean needsNewPathway(SimulationState state, long currentTime) {
        // Always check — remote server determines timing
        return true;
    }
}
