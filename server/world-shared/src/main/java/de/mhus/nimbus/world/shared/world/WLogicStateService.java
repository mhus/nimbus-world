package de.mhus.nimbus.world.shared.world;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Owner service for {@link WLogicStateDef} entities (Logic Machine state definitions).
 * Holds the data authority (DATENHOHEIT) over the {@code w_logic_states} collection;
 * all access to state definitions must go through this service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WLogicStateService {

    private final WLogicStateDefRepository repository;

    public List<WLogicStateDef> findByWorldId(String worldId) {
        return repository.findByWorldId(worldId);
    }

    public Optional<WLogicStateDef> findById(String id) {
        return repository.findById(id);
    }

    public Optional<WLogicStateDef> findByWorldIdAndName(String worldId, String name) {
        return repository.findByWorldIdAndName(worldId, name);
    }

    /**
     * Persist a state definition. Sets {@code createdAt} if it is not already set,
     * mirroring the historical controller behavior.
     */
    public WLogicStateDef save(WLogicStateDef def) {
        if (def.getCreatedAt() == null) {
            def.setCreatedAt(Instant.now());
        }
        return repository.save(def);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    public void delete(WLogicStateDef def) {
        repository.delete(def);
    }

    /**
     * Bulk-delete all state definitions of a world.
     * Owner bulk operation used for world teardown.
     *
     * @param worldId the world whose state definitions should be removed
     * @return the number of deleted state definitions
     */
    public int deleteAllByWorldId(String worldId) {
        List<WLogicStateDef> flags = repository.findByWorldId(worldId);
        repository.deleteAll(flags);
        log.info("Deleted {} state definitions for world {}", flags.size(), worldId);
        return flags.size();
    }

    /**
     * Duplicate all state definitions from a source world into a target world.
     * Copies are persisted via the repository directly, matching the historical
     * duplication semantics.
     *
     * @param sourceWorldId world to copy from
     * @param targetWorldId world to copy to (must already exist)
     * @return the number of duplicated state definitions
     */
    public int duplicateToWorld(String sourceWorldId, String targetWorldId) {
        List<WLogicStateDef> sourceFlags = repository.findByWorldId(sourceWorldId);
        int flagCount = 0;
        for (WLogicStateDef source : sourceFlags) {
            WLogicStateDef target = WLogicStateDef.builder()
                    .worldId(targetWorldId)
                    .name(source.getName())
                    .defaultValue(source.getDefaultValue())
                    .type(source.getType())
                    .description(source.getDescription())
                    .autoCreated(source.isAutoCreated())
                    .createdAt(Instant.now())
                    .build();
            repository.save(target);
            flagCount++;
        }
        log.info("Duplicated {} state definitions from {} to {}",
                flagCount, sourceWorldId, targetWorldId);
        return flagCount;
    }
}
