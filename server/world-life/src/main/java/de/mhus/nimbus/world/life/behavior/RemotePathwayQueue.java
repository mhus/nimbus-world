package de.mhus.nimbus.world.life.behavior;

import de.mhus.nimbus.generated.types.EntityPathway;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Queue for pathways received from remote servers via Redis.
 * Key format: "worldId:entityId" (entityIds can collide across worlds).
 */
@Service
public class RemotePathwayQueue {

    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<EntityPathway>> queues = new ConcurrentHashMap<>();

    private static String key(String worldId, String entityId) {
        return worldId + ":" + entityId;
    }

    public void offer(String worldId, String entityId, EntityPathway pathway) {
        queues.computeIfAbsent(key(worldId, entityId), k -> new ConcurrentLinkedQueue<>()).offer(pathway);
    }

    public EntityPathway poll(String worldId, String entityId) {
        var queue = queues.get(key(worldId, entityId));
        return queue != null ? queue.poll() : null;
    }

    public void remove(String worldId, String entityId) {
        queues.remove(key(worldId, entityId));
    }

    public boolean hasPending(String worldId, String entityId) {
        var queue = queues.get(key(worldId, entityId));
        return queue != null && !queue.isEmpty();
    }
}
