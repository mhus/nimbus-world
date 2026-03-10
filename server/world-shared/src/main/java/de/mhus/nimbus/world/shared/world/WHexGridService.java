package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.HexGrid;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.utils.TypeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Service for managing WHexGrid entities.
 * Provides business logic for hexagonal grid operations in the world.
 *
 * HexGrids exist separately for each world/zone.
 * Instances CANNOT have their own hex grids - always taken from the defined world.
 *
 * Multiple WHexGrid documents may exist at the same position with different epoches.
 *
 * Hex Position Key Format: "q;r"
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WHexGridService {

    private final WHexGridRepository repository;

    // --- Find by position ---

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    /**
     * Finds all hex grids at the given world and position (all epoch variants).
     */
    @Transactional(readOnly = true)
    public List<WHexGrid> findAllByWorldIdAndPosition(String worldId, HexVector2 hexPos) {
        if (Strings.isBlank(worldId)) {
            throw new IllegalArgumentException("worldId required");
        }
        if (hexPos == null) {
            throw new IllegalArgumentException("hexPos required");
        }

        String positionKey = TypeUtil.toStringHexCoord(hexPos);
        WorldId parsedWorldId = WorldId.of(worldId).orElseThrow();
        if (parsedWorldId.isCollection()) {
            throw new IllegalArgumentException("WHexGrid cannot be in a collection");
        }
        var lookupWorld = parsedWorldId.toBaseWorldId();

        return repository.findAllByWorldIdAndPosition(lookupWorld.getId(), positionKey);
    }

    /**
     * Finds a hex grid by world ID, position, and epoch.
     * Returns the hex grid variant that is active in the given epoch.
     */
    @Transactional(readOnly = true)
    public Optional<WHexGrid> findByWorldIdAndPosition(String worldId, HexVector2 hexPos, int epoch) {
        if (Strings.isBlank(worldId)) {
            throw new IllegalArgumentException("worldId required");
        }
        if (hexPos == null) {
            throw new IllegalArgumentException("hexPos required");
        }

        String positionKey = TypeUtil.toStringHexCoord(hexPos);
        WorldId parsedWorldId = WorldId.of(worldId).orElseThrow();
        if (parsedWorldId.isCollection()) {
            throw new IllegalArgumentException("WHexGrid cannot be in a collection");
        }
        var lookupWorld = parsedWorldId.toBaseWorldId();

        return repository.findByWorldIdAndPositionAndEpochesContaining(lookupWorld.getId(), positionKey, epoch);
    }

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    /**
     * Finds a hex grid by world ID and position.
     * If multiple epoch variants exist, returns the first one found.
     * Prefer {@link #findByWorldIdAndPosition(String, HexVector2, int)} for epoch-aware lookups.
     */
    @Transactional(readOnly = true)
    public Optional<WHexGrid> findByWorldIdAndPosition(String worldId, HexVector2 hexPos) {
        if (Strings.isBlank(worldId)) {
            throw new IllegalArgumentException("worldId required");
        }
        if (hexPos == null) {
            throw new IllegalArgumentException("hexPos required");
        }

        String positionKey = TypeUtil.toStringHexCoord(hexPos);
        WorldId parsedWorldId = WorldId.of(worldId).orElseThrow();
        if (parsedWorldId.isCollection()) {
            throw new IllegalArgumentException("WHexGrid cannot be in a collection");
        }
        var lookupWorld = parsedWorldId.toBaseWorldId();

        List<WHexGrid> all = repository.findAllByWorldIdAndPosition(lookupWorld.getId(), positionKey);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.getFirst());
    }

    // --- Find by world ---

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    /**
     * Finds all hex grids in a world (all epochs).
     */
    @Transactional(readOnly = true)
    public List<WHexGrid> findByWorldId(String worldId) {
        if (Strings.isBlank(worldId)) {
            throw new IllegalArgumentException("worldId required");
        }

        WorldId parsedWorldId = WorldId.of(worldId).orElseThrow();
        if (parsedWorldId.isCollection()) {
            throw new IllegalArgumentException("WHexGrid cannot be in a collection");
        }
        var lookupWorld = parsedWorldId.toBaseWorldId();

        return repository.findByWorldId(lookupWorld.getId());
    }

    /**
     * Finds all hex grids in a world filtered by epoch.
     */
    @Transactional(readOnly = true)
    public List<WHexGrid> findByWorldId(String worldId, int epoch) {
        if (Strings.isBlank(worldId)) {
            throw new IllegalArgumentException("worldId required");
        }

        WorldId parsedWorldId = WorldId.of(worldId).orElseThrow();
        if (parsedWorldId.isCollection()) {
            throw new IllegalArgumentException("WHexGrid cannot be in a collection");
        }
        var lookupWorld = parsedWorldId.toBaseWorldId();

        return repository.findByWorldIdAndEpochesContaining(lookupWorld.getId(), epoch);
    }

    // EPOCH-UNFILTERED: returns data across all epochs. Use the epoch-filtered overload for player/gameplay context.
    /**
     * Finds all enabled hex grids in a world (all epochs).
     */
    @Transactional(readOnly = true)
    public List<WHexGrid> findAllEnabled(String worldId) {
        if (Strings.isBlank(worldId)) {
            throw new IllegalArgumentException("worldId required");
        }

        WorldId parsedWorldId = WorldId.of(worldId).orElseThrow();
        if (parsedWorldId.isCollection()) {
            throw new IllegalArgumentException("WHexGrid cannot be in a collection");
        }
        var lookupWorld = parsedWorldId.toBaseWorldId();

        return repository.findByWorldIdAndEnabled(lookupWorld.getId(), true);
    }

    /**
     * Finds all enabled hex grids in a world filtered by epoch.
     */
    @Transactional(readOnly = true)
    public List<WHexGrid> findAllEnabled(String worldId, int epoch) {
        if (Strings.isBlank(worldId)) {
            throw new IllegalArgumentException("worldId required");
        }

        WorldId parsedWorldId = WorldId.of(worldId).orElseThrow();
        if (parsedWorldId.isCollection()) {
            throw new IllegalArgumentException("WHexGrid cannot be in a collection");
        }
        var lookupWorld = parsedWorldId.toBaseWorldId();

        return repository.findByWorldIdAndEnabledAndEpochesContaining(lookupWorld.getId(), true, epoch);
    }

    // --- Save / Create ---
    // See readme/EPOCH_ENTITY_MANAGEMENT.md – Epoch Pull/Validate Pattern

    /**
     * Saves a hex grid entity.
     * For new entities (no ID): pulls conflicting epochs from other documents at the same position,
     * throws EpochOverwriteException if this would make another document fully obsolete.
     * For existing entities (has ID): pulls conflicting epochs silently.
     *
     * See readme/EPOCH_ENTITY_MANAGEMENT.md – Epoch Pull/Validate Pattern
     */
    @Transactional
    public WHexGrid save(WHexGrid entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity required");
        }
        if (entity.getPublicData() == null) {
            throw new IllegalArgumentException("publicData required");
        }
        WorldId worldId = WorldId.of(entity.getWorldId()).orElseThrow();
        if (worldId.isCollection() || (worldId.isInstance() && !worldId.isEditorInstance())) {
            throw new IllegalArgumentException("WHexGrid cannot be in a collection or player instance");
        }

        // Ensure position key is synchronized
        entity.syncPositionKey();

        // Pull conflicting epochs; for new entities check for overwrites (Strategy B)
        boolean isNew = entity.getId() == null;
        pullConflictingEpochs(entity, !isNew);

        // Set timestamps if new entity
        if (entity.getCreatedAt() == null) {
            entity.touchCreate();
        } else {
            entity.touchUpdate();
        }

        WHexGrid saved = repository.save(entity);
        log.debug("Saved WHexGrid: worldId={}, position={}, epoches={}", saved.getWorldId(), saved.getPosition(), saved.getEpoches());
        return saved;
    }

    /**
     * Creates a new hex grid.
     * Pulls conflicting epochs from other documents at the same position.
     * Throws EpochOverwriteException if this would make another document fully obsolete.
     * Multiple hex grids at the same position with disjoint epoches are allowed.
     *
     * See readme/EPOCH_ENTITY_MANAGEMENT.md – Epoch Pull/Validate Pattern
     *
     * @param worldId    The world identifier
     * @param publicData The hex grid public data with position and metadata
     * @param parameters Optional generator parameters (can be null or empty)
     * @param areas      Optional area-specific parameter maps (can be null or empty)
     * @param epoches    Epoch list (null or empty = all epochs)
     * @return The created hex grid entity
     * @throws EpochOverwriteException if another document would become fully obsolete
     */
    @Transactional
    public WHexGrid create(String worldId, HexGrid publicData, Map<String, String> parameters,
                           Map<String, Map<String, String>> areas, List<Integer> epoches) {
        if (Strings.isBlank(worldId)) {
            throw new IllegalArgumentException("worldId required");
        }
        if (publicData == null || publicData.getPosition() == null) {
            throw new IllegalArgumentException("publicData with position required");
        }

        WorldId parsedWorldId = WorldId.of(worldId).orElseThrow();
        if (parsedWorldId.isCollection() || (parsedWorldId.isInstance() && !parsedWorldId.isEditorInstance())) {
            throw new IllegalArgumentException("WHexGrid cannot be in a collection or player instance");
        }

        String positionKey = TypeUtil.toStringHexCoord(publicData.getPosition());

        WHexGrid entity = WHexGrid.builder()
                .worldId(parsedWorldId.getId())
                .publicData(publicData)
                .position(positionKey)
                .parameters(parameters != null ? parameters : Map.of())
                .areas(areas != null ? areas : Map.of())
                .epoches(epoches != null ? epoches : List.of())
                .enabled(true)
                .build();

        // Pull conflicting epochs; create = strict overwrite check (Strategy A)
        pullConflictingEpochs(entity, false);

        entity.touchCreate();

        WHexGrid saved = repository.save(entity);
        log.info("Created WHexGrid: worldId={}, position={}, epoches={}", parsedWorldId.getId(), positionKey, saved.getEpoches());
        return saved;
    }

    /**
     * Creates a new hex grid (backward compatible, no explicit epoches → empty list = all epochs).
     */
    @Transactional
    public WHexGrid create(String worldId, HexGrid publicData, Map<String, String> parameters,
                           Map<String, Map<String, String>> areas) {
        return create(worldId, publicData, parameters, areas, null);
    }

    // --- Update / Delete ---

    /**
     * Updates a hex grid by its MongoDB ID using a consumer function.
     * Pulls conflicting epochs silently (update = known entity, Strategy A).
     *
     * See readme/EPOCH_ENTITY_MANAGEMENT.md – Epoch Pull/Validate Pattern
     */
    @Transactional
    public Optional<WHexGrid> updateById(String id, Consumer<WHexGrid> updater) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id required");
        }
        if (updater == null) {
            throw new IllegalArgumentException("updater required");
        }

        return repository.findById(id).map(entity -> {
            updater.accept(entity);
            entity.syncPositionKey();
            entity.touchUpdate();
            pullConflictingEpochs(entity, true);

            WHexGrid saved = repository.save(entity);
            log.debug("Updated WHexGrid: id={}, position={}, epoches={}", id, saved.getPosition(), saved.getEpoches());
            return saved;
        });
    }

    /**
     * Updates a hex grid using a consumer function.
     * If multiple epoch variants exist at this position, updates the first one found.
     * Pulls conflicting epochs silently (update = known entity, Strategy A).
     * Prefer {@link #updateById(String, Consumer)} for precise updates.
     *
     * See readme/EPOCH_ENTITY_MANAGEMENT.md – Epoch Pull/Validate Pattern
     */
    @Transactional
    public Optional<WHexGrid> update(String worldId, HexVector2 hexPos, Consumer<WHexGrid> updater) {
        if (worldId == null || worldId.isBlank()) {
            throw new IllegalArgumentException("worldId required");
        }
        if (hexPos == null) {
            throw new IllegalArgumentException("hexPos required");
        }
        if (updater == null) {
            throw new IllegalArgumentException("updater required");
        }

        WorldId parsedWorldId = WorldId.of(worldId).orElseThrow();
        if (parsedWorldId.isCollection() || (parsedWorldId.isInstance() && !parsedWorldId.isEditorInstance())) {
            throw new IllegalArgumentException("WHexGrid cannot be in a collection or player instance");
        }

        String positionKey = TypeUtil.toStringHexCoord(hexPos);

        List<WHexGrid> all = repository.findAllByWorldIdAndPosition(parsedWorldId.getId(), positionKey);
        if (all.isEmpty()) return Optional.empty();

        WHexGrid entity = all.getFirst();
        updater.accept(entity);
        entity.syncPositionKey();
        entity.touchUpdate();
        pullConflictingEpochs(entity, true);

        WHexGrid saved = repository.save(entity);
        log.debug("Updated WHexGrid: worldId={}, position={}, epoches={}", parsedWorldId.getId(), positionKey, saved.getEpoches());
        return Optional.of(saved);
    }

    /**
     * Disables a hex grid (soft delete).
     */
    @Transactional
    public boolean disable(String worldId, HexVector2 hexPos) {
        return update(worldId, hexPos, entity -> entity.setEnabled(false)).isPresent();
    }

    /**
     * Enables a hex grid.
     */
    @Transactional
    public boolean enable(String worldId, HexVector2 hexPos) {
        return update(worldId, hexPos, entity -> entity.setEnabled(true)).isPresent();
    }

    /**
     * Deletes a hex grid by MongoDB ID (hard delete).
     */
    @Transactional
    public boolean deleteById(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id required");
        }

        return repository.findById(id).map(entity -> {
            repository.delete(entity);
            log.info("Deleted WHexGrid: id={}, worldId={}, position={}, epoches={}",
                    id, entity.getWorldId(), entity.getPosition(), entity.getEpoches());
            return true;
        }).orElse(false);
    }

    /**
     * Deletes a hex grid (hard delete).
     * If multiple epoch variants exist at this position, deletes the first one found.
     * Prefer {@link #deleteById(String)} for precise deletes.
     */
    @Transactional
    public boolean delete(String worldId, HexVector2 hexPos) {
        if (worldId == null || worldId.isBlank()) {
            throw new IllegalArgumentException("worldId required");
        }
        if (hexPos == null) {
            throw new IllegalArgumentException("hexPos required");
        }

        WorldId parsedWorldId = WorldId.of(worldId).orElseThrow();
        if (parsedWorldId.isCollection() || (parsedWorldId.isInstance() && !parsedWorldId.isEditorInstance())) {
            throw new IllegalArgumentException("WHexGrid cannot be in a collection or player instance");
        }

        String positionKey = TypeUtil.toStringHexCoord(hexPos);

        List<WHexGrid> all = repository.findAllByWorldIdAndPosition(parsedWorldId.getId(), positionKey);
        if (all.isEmpty()) return false;

        WHexGrid entity = all.getFirst();
        repository.delete(entity);
        log.info("Deleted WHexGrid: worldId={}, position={}", parsedWorldId.getId(), positionKey);
        return true;
    }

    /**
     * Deletes all hex grid variants at a position (all epochs).
     */
    @Transactional
    public int deleteAllAtPosition(String worldId, HexVector2 hexPos) {
        if (worldId == null || worldId.isBlank()) {
            throw new IllegalArgumentException("worldId required");
        }
        if (hexPos == null) {
            throw new IllegalArgumentException("hexPos required");
        }

        WorldId parsedWorldId = WorldId.of(worldId).orElseThrow();
        if (parsedWorldId.isCollection() || (parsedWorldId.isInstance() && !parsedWorldId.isEditorInstance())) {
            throw new IllegalArgumentException("WHexGrid cannot be in a collection or player instance");
        }

        String positionKey = TypeUtil.toStringHexCoord(hexPos);
        List<WHexGrid> all = repository.findAllByWorldIdAndPosition(parsedWorldId.getId(), positionKey);
        repository.deleteAll(all);
        log.info("Deleted {} WHexGrid variants at worldId={}, position={}", all.size(), parsedWorldId.getId(), positionKey);
        return all.size();
    }

    // --- Epoch Pull/Validate ---
    // See readme/EPOCH_ENTITY_MANAGEMENT.md – Epoch Pull/Validate Pattern

    /**
     * Pulls conflicting epochs from other documents at the same natural key (worldId + position).
     * Documents that lose all their epochs are deleted.
     *
     * @param entity       the entity being saved
     * @param forceOverwrite if false, throws EpochOverwriteException when another document would
     *                       become fully obsolete; if true, allows overwrite silently
     */
    private void pullConflictingEpochs(WHexGrid entity, boolean forceOverwrite) {
        if (entity.getEpoches() == null || entity.getEpoches().isEmpty()) {
            // Empty epoches = not visible in any epoch, no conflicts possible
            return;
        }

        List<WHexGrid> existing = repository.findAllByWorldIdAndPosition(entity.getWorldId(), entity.getPosition());

        for (WHexGrid other : existing) {
            // Skip self (same MongoDB ID)
            if (other.getId() != null && other.getId().equals(entity.getId())) continue;

            // Find overlapping epochs
            List<Integer> overlapping = other.getEpoches().stream()
                    .filter(entity.getEpoches()::contains)
                    .toList();

            if (overlapping.isEmpty()) continue;

            // Check if pulling would make the other document fully obsolete
            List<Integer> remaining = other.getEpoches().stream()
                    .filter(e -> !overlapping.contains(e))
                    .toList();

            if (remaining.isEmpty() && !forceOverwrite) {
                throw new EpochOverwriteException(
                        "WHexGrid",
                        entity.getWorldId() + "/" + entity.getPosition(),
                        other.getId(),
                        "Saving WHexGrid at position=" + entity.getPosition() +
                        " with epoches=" + entity.getEpoches() +
                        " would make existing document id=" + other.getId() +
                        " (epoches=" + other.getEpoches() + ") fully obsolete. " +
                        "Use force/update to allow this overwrite."
                );
            }

            if (remaining.isEmpty()) {
                // Document becomes obsolete → delete
                repository.delete(other);
                log.info("Epoch pull: deleted obsolete WHexGrid id={} at position={} (had epoches={})",
                        other.getId(), other.getPosition(), other.getEpoches());
            } else {
                // Pull conflicting epochs
                other.setEpoches(new java.util.ArrayList<>(remaining));
                other.touchUpdate();
                repository.save(other);
                log.info("Epoch pull: removed epoches {} from WHexGrid id={} at position={}, remaining={}",
                        overlapping, other.getId(), other.getPosition(), remaining);
            }
        }
    }
}
