package de.mhus.nimbus.world.shared.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.ItemBlockRef;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Publishes item block updates via Redis to all world-player pods.
 * Channel: world:{worldId}:b.iu
 *
 * Clients receive these as b.iu messages containing ItemBlockRef arrays.
 * Deleted items have texture='__deleted__'.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ItemBlockUpdatePublisher {

    private static final String CHANNEL = "b.iu";
    private static final String DELETED_TEXTURE = "__deleted__";

    private final WorldRedisMessagingService redisMessaging;
    private final ObjectMapper objectMapper;
    private final WWorldService worldService;

    /**
     * Publish an item placement (add/update).
     *
     * @param worldId      World identifier
     * @param itemBlockRef The item to add/update
     */
    public void publishItemAdded(WorldId worldId, ItemBlockRef itemBlockRef) {
        try {
            WWorld world = worldService.getByWorldId(worldId.toBaseWorldId().getId()).orElse(null);
            if (world == null) {
                log.warn("World not found for item broadcast: {}", worldId);
                return;
            }

            int cx = world.getChunkX((int) itemBlockRef.getPosition().getX());
            int cz = world.getChunkZ((int) itemBlockRef.getPosition().getZ());

            ItemBlockUpdateBroadcastMessage message = ItemBlockUpdateBroadcastMessage.builder()
                    .worldId(worldId.getId())
                    .cx(cx)
                    .cz(cz)
                    .items(List.of(itemBlockRef))
                    .build();

            String json = objectMapper.writeValueAsString(message);
            redisMessaging.publish(worldId.getId(), CHANNEL, json);

            log.debug("Published item added: worldId={}, item={}, chunk=({},{})",
                    worldId, itemBlockRef.getName(), cx, cz);
        } catch (Exception e) {
            log.error("Failed to publish item added: worldId={}, item={}",
                    worldId, itemBlockRef.getName(), e);
        }
    }

    /**
     * Publish an item removal.
     * Sends an ItemBlockRef with texture='__deleted__' so the client removes it.
     *
     * @param worldId  World identifier
     * @param itemName Item name to remove
     * @param x        World X coordinate of the item
     * @param y        World Y coordinate of the item
     * @param z        World Z coordinate of the item
     */
    public void publishItemRemoved(WorldId worldId, String itemName, int x, int y, int z) {
        try {
            WWorld world = worldService.getByWorldId(worldId.toBaseWorldId().getId()).orElse(null);
            if (world == null) {
                log.warn("World not found for item broadcast: {}", worldId);
                return;
            }

            int cx = world.getChunkX(x);
            int cz = world.getChunkZ(z);

            ItemBlockRef deletedRef = ItemBlockRef.builder()
                    .name(itemName)
                    .position(Vector3.builder().x(x).y(y).z(z).build())
                    .texture(DELETED_TEXTURE)
                    .build();

            ItemBlockUpdateBroadcastMessage message = ItemBlockUpdateBroadcastMessage.builder()
                    .worldId(worldId.getId())
                    .cx(cx)
                    .cz(cz)
                    .items(List.of(deletedRef))
                    .build();

            String json = objectMapper.writeValueAsString(message);
            redisMessaging.publish(worldId.getId(), CHANNEL, json);

            log.debug("Published item removed: worldId={}, item={}, chunk=({},{})",
                    worldId, itemName, cx, cz);
        } catch (Exception e) {
            log.error("Failed to publish item removed: worldId={}, item={}",
                    worldId, itemName, e);
        }
    }
}
