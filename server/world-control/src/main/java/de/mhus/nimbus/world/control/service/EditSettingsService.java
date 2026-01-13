package de.mhus.nimbus.world.control.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing world editor settings per user.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EditSettingsService {

    private final WWorldEditSettingsRepository repository;

    /**
     * Get editor settings for a world and user.
     * @param worldId World identifier
     * @param userId User identifier (not player ID)
     * @return Optional containing the settings if found
     */
    public Optional<WWorldEditSettings> getSettings(String worldId, String userId) {
        return repository.findByWorldIdAndUserId(worldId, userId);
    }

    /**
     * Get or create editor settings for a world and user.
     * @param worldId World identifier
     * @param userId User identifier (not player ID)
     * @return The settings (existing or newly created)
     */
    public WWorldEditSettings getOrCreateSettings(String worldId, String userId) {
        return repository.findByWorldIdAndUserId(worldId, userId)
                .orElseGet(() -> {
                    WWorldEditSettings settings = WWorldEditSettings.builder()
                            .worldId(worldId)
                            .userId(userId)
                            .build();
                    settings.touchCreate();
                    WWorldEditSettings saved = repository.save(settings);
                    log.info("Created new editor settings: worldId={}, userId={}", worldId, userId);
                    return saved;
                });
    }

    /**
     * Update the palette for a world and user.
     * Always replaces the entire palette list.
     * @param worldId World identifier
     * @param userId User identifier (not player ID)
     * @param palette New palette block definitions
     * @return Updated settings
     */
    public WWorldEditSettings updatePalette(String worldId, String userId, List<PaletteBlockDefinition> palette) {
        WWorldEditSettings settings = getOrCreateSettings(worldId, userId);
        settings.setPalette(palette);
        settings.touchUpdate();
        WWorldEditSettings saved = repository.save(settings);
        log.info("Updated palette: worldId={}, userId={}, paletteSize={}", worldId, userId, palette.size());
        return saved;
    }

    /**
     * Delete settings for a world and user.
     * @param worldId World identifier
     * @param userId User identifier
     */
    public void deleteSettings(String worldId, String userId) {
        repository.deleteByWorldIdAndUserId(worldId, userId);
        log.info("Deleted editor settings: worldId={}, userId={}", worldId, userId);
    }
}
