package de.mhus.nimbus.world.shared.world;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing WProgress entities.
 * Provides business logic for player progress tracking including quests, achievements, and skills.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WProgressService {

    private final WProgressRepository repository;

    /**
     * Find all progress entries for a world.
     */
    @Transactional(readOnly = true)
    public List<WProgress> findByWorldId(String worldId) {
        return repository.findByWorldId(worldId);
    }

    /**
     * Find a specific progress entry by ID.
     */
    @Transactional(readOnly = true)
    public Optional<WProgress> findById(String id) {
        return repository.findById(id);
    }

    /**
     * Find all progress entries for a player in a world.
     */
    @Transactional(readOnly = true)
    public List<WProgress> findByWorldIdAndPlayerId(String worldId, String playerId) {
        return repository.findByWorldIdAndPlayerId(worldId, playerId);
    }

    /**
     * Find all progress entries for a player in a world with a specific type.
     */
    @Transactional(readOnly = true)
    public List<WProgress> findByWorldIdAndPlayerIdAndType(String worldId, String playerId, String type) {
        return repository.findByWorldIdAndPlayerIdAndType(worldId, playerId, type);
    }

    /**
     * Find all progress entries for a player in a world for a specific quest.
     */
    @Transactional(readOnly = true)
    public List<WProgress> findByWorldIdAndPlayerIdAndQuest(String worldId, String playerId, String quest) {
        return repository.findByWorldIdAndPlayerIdAndQuest(worldId, playerId, quest);
    }

    /**
     * Find a specific progress entry by world, player, type, and quest.
     */
    @Transactional(readOnly = true)
    public Optional<WProgress> findByWorldIdAndPlayerIdAndTypeAndQuest(String worldId, String playerId, String type, String quest) {
        return repository.findByWorldIdAndPlayerIdAndTypeAndQuest(worldId, playerId, type, quest);
    }

    /**
     * Create or update a progress entry.
     * If a matching entry (worldId + playerId + type + quest) exists, it is updated.
     * Otherwise a new entry is created.
     */
    @Transactional
    public WProgress save(String worldId, String playerId, String type, String quest, Map<String, Object> progressData) {
        if (worldId == null || worldId.isBlank()) {
            throw new IllegalArgumentException("worldId is required");
        }
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("playerId is required");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type is required");
        }

        Optional<WProgress> existing = repository.findByWorldIdAndPlayerIdAndTypeAndQuest(worldId, playerId, type, quest);
        if (existing.isPresent()) {
            WProgress progress = existing.get();
            progress.setProgressData(progressData);
            progress.touchUpdate();
            log.debug("Updated progress: worldId={}, playerId={}, type={}, quest={}", worldId, playerId, type, quest);
            return repository.save(progress);
        }

        WProgress progress = WProgress.builder()
                .worldId(worldId)
                .playerId(playerId)
                .type(type)
                .quest(quest)
                .progressData(progressData)
                .build();
        progress.touchCreate();

        log.debug("Created progress: worldId={}, playerId={}, type={}, quest={}", worldId, playerId, type, quest);
        return repository.save(progress);
    }

    /**
     * Delete a specific progress entry.
     */
    @Transactional
    public boolean delete(String id) {
        return repository.findById(id).map(progress -> {
            repository.delete(progress);
            log.debug("Deleted progress: id={}", id);
            return true;
        }).orElse(false);
    }

    /**
     * Delete all progress for a player in a world.
     */
    @Transactional
    public void deleteByWorldIdAndPlayerId(String worldId, String playerId) {
        repository.deleteByWorldIdAndPlayerId(worldId, playerId);
        log.debug("Deleted all progress: worldId={}, playerId={}", worldId, playerId);
    }
}
