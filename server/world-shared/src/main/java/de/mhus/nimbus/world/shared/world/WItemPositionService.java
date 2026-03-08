package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.ItemBlockRef;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.utils.TypeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing item positions in the world.
 * Items are stored per chunk for efficient spatial queries.
 *
 * Item positions exist separately for each world/zone/instance.
 * Each world context is treated as a separate instance.
 * No storage functionality supported (always world-instance-specific).
 * List loading does NOT fall back to main world.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WItemPositionService {

    private final WItemPositionRepository repository;
    private final WWorldService worldService;

    /**
     * Save or update an item position.
     * Automatically calculates chunk key from item position.
     * For instance worlds: creates a COW copy in the instance layer.
     *
     * @param worldId World identifier (can be main world, instance, or zone)
     * @param itemBlockRef ItemBlockRef containing position and display data
     * @return Saved item position entity
     */
    @Transactional
    public WItemPosition saveItemPosition(WorldId worldId, ItemBlockRef itemBlockRef) {
        if (worldId == null) {
            throw new IllegalArgumentException("worldId required");
        }
        if (itemBlockRef == null) {
            throw new IllegalArgumentException("itemBlockRef required");
        }
        if (itemBlockRef.getName() == null || itemBlockRef.getName().isBlank()) {
            throw new IllegalArgumentException("itemBlockRef.id required");
        }
        if (itemBlockRef.getPosition() == null) {
            throw new IllegalArgumentException("itemBlockRef.position required");
        }
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("WItemPosition cannot be in a collection");
        }

        String itemId = itemBlockRef.getName();
        Vector3 position = itemBlockRef.getPosition();
        WWorld world = worldService.getByWorldId(worldId.toBaseWorldId().getId()).get();
        String chunk = world.getChunkKey((int)position.getX(), (int)position.getZ());

        // For instance worlds: always write to instance layer
        WItemPosition itemPosition = repository.findByWorldIdAndItemId(worldId.getId(), itemId)
                .orElseGet(() -> {
                    WItemPosition neu = WItemPosition.builder()
                            .worldId(worldId.getId())
                            .itemId(itemId)
                            .chunk(chunk)
                            .enabled(true)
                            .build();
                    neu.touchCreate();
                    log.debug("Creating new item position: world={}, itemId={}, chunk={}",
                            worldId, itemId, chunk);
                    return neu;
                });

        itemPosition.setPublicData(itemBlockRef);
        itemPosition.setChunk(chunk);
        itemPosition.touchUpdate();

        WItemPosition saved = repository.save(itemPosition);
        log.debug("Saved item position: world={}, itemId={}, chunk={}",
                worldId, itemId, chunk);
        return saved;
    }

    /**
     * Get all items in a specific chunk.
     * Returns only enabled items.
     * For instance worlds: merges base world items with instance overrides (COW).
     *
     * @param worldId World identifier (can be main world, instance, or zone)
     * @param cx Chunk X coordinate
     * @param cz Chunk Z coordinate
     * @return List of ItemBlockRef objects for the chunk
     */
    @Transactional(readOnly = true)
    public List<ItemBlockRef> getItemsInChunk(WorldId worldId, int cx, int cz) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("WItemPosition cannot be in a collection");
        }
        String chunk = TypeUtil.toStringChunkCoord(cx, cz);

        List<WItemPosition> positions;
        if (worldId.isInstance()) {
            var baseList = repository.findByWorldIdAndChunkAndEnabled(
                    worldId.toBaseWorldId().getId(), chunk, true);
            var instanceList = repository.findByWorldIdAndChunk(
                    worldId.getId(), chunk);
            positions = CowUtil.merge(baseList, instanceList);
        } else {
            positions = repository.findByWorldIdAndChunkAndEnabled(
                    worldId.getId(), chunk, true);
        }

        return positions.stream()
                .map(WItemPosition::getPublicData)
                .filter(data -> data != null)
                .toList();
    }

    /**
     * Get all items in a world.
     * Returns only enabled items.
     * For instance worlds: merges base world items with instance overrides (COW).
     *
     * @param worldId World identifier (can be main world, instance, or zone)
     * @return List of all item positions
     */
    @Transactional(readOnly = true)
    public List<WItemPosition> getAllItems(WorldId worldId) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("WItemPosition cannot be in a collection");
        }
        if (worldId.isInstance()) {
            var baseList = repository.findByWorldId(worldId.toBaseWorldId().getId());
            var instanceList = repository.findByWorldId(worldId.getId());
            return CowUtil.merge(baseList, instanceList);
        }
        return repository.findByWorldId(worldId.getId());
    }

    /**
     * Find a specific item by ID.
     * For instance worlds: checks instance layer first, falls back to base world (COW).
     *
     * @param worldId World identifier (can be main world, instance, or zone)
     * @param itemId Item identifier
     * @return Optional containing the item position if found (empty if tombstoned or not found)
     */
    @Transactional(readOnly = true)
    public Optional<WItemPosition> findItem(WorldId worldId, String itemId) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("WItemPosition cannot be in a collection");
        }
        if (worldId.isInstance()) {
            var instanceEntry = repository.findByWorldIdAndItemId(worldId.getId(), itemId).orElse(null);
            var baseEntry = repository.findByWorldIdAndItemId(worldId.toBaseWorldId().getId(), itemId).orElse(null);
            return Optional.ofNullable(CowUtil.findOne(instanceEntry, baseEntry));
        }
        return repository.findByWorldIdAndItemId(worldId.getId(), itemId);
    }

    /**
     * Delete an item position.
     * Performs soft delete by setting enabled=false.
     * For instance worlds: creates a tombstone in the instance layer (COW).
     *
     * @param worldId World identifier (can be main world, instance, or zone)
     * @param itemId Item identifier
     * @return True if item was found and disabled
     */
    @Transactional
    public boolean deleteItemPosition(WorldId worldId, String itemId) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("WItemPosition cannot be in a collection");
        }

        // Check if item exists in instance layer
        Optional<WItemPosition> itemOpt = repository.findByWorldIdAndItemId(worldId.getId(), itemId);
        if (itemOpt.isPresent()) {
            WItemPosition item = itemOpt.get();
            item.setEnabled(false);
            item.touchUpdate();
            repository.save(item);
            log.info("Soft deleted item: world={}, itemId={}", worldId, itemId);
            return true;
        }

        // For instance worlds: check if item exists in base world and create tombstone
        if (worldId.isInstance()) {
            Optional<WItemPosition> baseOpt = repository.findByWorldIdAndItemId(
                    worldId.toBaseWorldId().getId(), itemId);
            if (baseOpt.isPresent()) {
                WItemPosition tombstone = WItemPosition.builder()
                        .worldId(worldId.getId())
                        .itemId(itemId)
                        .chunk(baseOpt.get().getChunk())
                        .enabled(false)
                        .build();
                tombstone.touchCreate();
                repository.save(tombstone);
                log.info("Created COW tombstone for item: world={}, itemId={}", worldId, itemId);
                return true;
            }
        }

        log.debug("Item not found for deletion: world={}, itemId={}", worldId, itemId);
        return false;
    }

    /**
     * Permanently delete an item position.
     *
     * @param worldId World identifier (can be main world, instance, or zone)
     * @param itemId Item identifier
     */
    @Transactional
    public void hardDeleteItemPosition(WorldId worldId, String itemId) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("WItemPosition cannot be in a collection");
        }
        repository.deleteByWorldIdAndItemId(worldId.getId(), itemId);
        log.info("Hard deleted item: world={}, itemId={}",
                worldId, itemId);
    }

    /**
     * Save multiple item positions in batch.
     *
     * @param items List of item positions to save
     * @return List of saved item positions
     */
    @Transactional
    public List<WItemPosition> saveAll(WorldId worldId, List<WItemPosition> items) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("WItemPosition cannot be in a collection");
        }
        items.forEach(item -> {
            if (item.getCreatedAt() == null) {
                item.touchCreate();
            }
            item.touchUpdate();
        });

        List<WItemPosition> saved = repository.saveAll(items);
        log.debug("Saved {} item positions", saved.size());
        return saved;
    }

    /**
     * Count items in a chunk.
     * For instance worlds: counts merged result (COW).
     *
     * @param worldId World identifier (can be main world, instance, or zone)
     * @param cx Chunk X coordinate
     * @param cz Chunk Z coordinate
     * @return Number of items in the chunk
     */
    @Transactional(readOnly = true)
    public long countItemsInChunk(WorldId worldId, int cx, int cz) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("WItemPosition cannot be in a collection");
        }
        String chunk = TypeUtil.toStringChunkCoord(cx, cz);
        if (worldId.isInstance()) {
            var baseList = repository.findByWorldIdAndChunkAndEnabled(
                    worldId.toBaseWorldId().getId(), chunk, true);
            var instanceList = repository.findByWorldIdAndChunk(
                    worldId.getId(), chunk);
            return CowUtil.merge(baseList, instanceList).size();
        }
        return repository.findByWorldIdAndChunkAndEnabled(
                worldId.getId(), chunk, true).size();
    }
}
