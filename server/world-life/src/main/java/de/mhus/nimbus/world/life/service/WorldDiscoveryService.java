package de.mhus.nimbus.world.life.service;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service that discovers and tracks all enabled worlds from MongoDB.
 * Periodically refreshes the list of worlds to simulate.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorldDiscoveryService {

    private final WWorldRepository worldRepository;

    /**
     * Set of currently known world IDs.
     */
    private final Set<WorldId> knownWorldIds = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void initialize() {
        discoverWorlds();
    }

    /**
     * Discover all enabled worlds from MongoDB.
     * Runs on startup and periodically (every 5 minutes).
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 0)
    public void discoverWorlds() {
        List<WWorld> worlds = worldRepository.findAll();

        Set<WorldId> enabledWorldIds = worlds.stream()
                .filter(WWorld::isEnabled)
                .map(w -> WorldId.unchecked(w.getWorldId()))
                .collect(Collectors.toSet());

        Set<WorldId> added = enabledWorldIds.stream()
                .filter(worldId -> !knownWorldIds.contains(worldId))
                .collect(Collectors.toSet());

        Set<WorldId> removed = knownWorldIds.stream()
                .filter(worldId -> !enabledWorldIds.contains(worldId))
                .collect(Collectors.toSet());

        knownWorldIds.clear();
        knownWorldIds.addAll(enabledWorldIds);

        if (!added.isEmpty() || !removed.isEmpty()) {
            log.info("World discovery: {} total worlds, {} added, {} removed",
                    knownWorldIds.size(), added.size(), removed.size());

            if (!added.isEmpty()) {
                log.info("New worlds discovered: {}", added);
            }
            if (!removed.isEmpty()) {
                log.info("Worlds removed/disabled: {}", removed);
            }
        } else {
            log.debug("World discovery: {} worlds active (no changes)", knownWorldIds.size());
        }
    }

    /**
     * Get all currently known enabled world IDs.
     *
     * @return Set of world IDs
     */
    public Set<WorldId> getKnownWorldIds() {
        return Set.copyOf(knownWorldIds);
    }

    /**
     * Check if a world is known and enabled.
     *
     * @param worldId World ID
     * @return True if world is known
     */
    public boolean isWorldKnown(String worldId) {
        return knownWorldIds.contains(worldId);
    }
}
