package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.WorldInfo;
import de.mhus.nimbus.shared.types.WorldId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WWorldService {

    private final WWorldRepository repository;
    private final WWorldCollectionRepository worldCollectionRepository;
    private final WWorldInstanceService instanceService;

    @Transactional(readOnly = true)
    public Optional<WWorld> getByWorldId(WorldId worldId) {
        return getByWorldId(worldId.getId());
    }

    @Transactional(readOnly = true)
    public Optional<WWorld> getByWorldId(String worldId) {
        if (worldId == null || worldId.isBlank()) {
            return Optional.empty();
        }

        // Parse worldId using WorldId class
        WorldId parsedWorldId = WorldId.unchecked(worldId);

        // Check if this is an instance world (format: worldId!instance per WorldId spec)
        if (parsedWorldId.isInstance()) {
            return loadInstanceWorld(worldId);
        }

        Optional<WWorld> world = repository.findByWorldId(worldId);

        // Enrich zone worlds with base world data
        if (parsedWorldId.isZone()) {
            return world.map(w -> enrichZoneWithBaseWorldData(parsedWorldId, w));
        }

        // Regular world (no zone, no instance)
        return world;
    }

    /**
     * Loads an instance world.
     * Steps:
     * 1. Validate instance exists and extract base worldId from instance
     * 2. Load base world (with zone enrichment if applicable)
     * 3. Load instance data
     * 4. Override worldId in WWorld with full instance ID
     *
     * @param fullInstanceId The full instance ID (format: worldId!instance, e.g. "main:terra!abc" or "main:terra:zone!abc")
     * @return The enriched world with instance data
     */
    private Optional<WWorld> loadInstanceWorld(String fullInstanceId) {
        try {
            // Load and validate instance data
            Optional<WWorldInstance> instanceOpt = instanceService.findByInstanceIdWithValidation(fullInstanceId);

            if (instanceOpt.isEmpty()) {
                log.warn("Instance not found or validation failed: {}", fullInstanceId);
                return Optional.empty();
            }

            WWorldInstance instance = instanceOpt.get();
            String baseWorldId = instance.getWorldId();

            log.debug("Loading instance world: instanceId={}, baseWorldId={}", fullInstanceId, baseWorldId);

            // Load base world (this will handle zone enrichment automatically)
            Optional<WWorld> baseWorldOpt = getByWorldId(baseWorldId);

            if (baseWorldOpt.isEmpty()) {
                log.warn("Base world not found for instance {}: {}", fullInstanceId, baseWorldId);
                return Optional.empty();
            }

            WWorld world = baseWorldOpt.get();

            // Override worldId in WWorld with the full instance ID
            world.setWorldId(fullInstanceId);

            log.debug("Enriched world with instance data: instanceId={}, title={}, creator={}",
                    fullInstanceId, instance.getTitle(), instance.getCreator());

            return Optional.of(world);

        } catch (Exception e) {
            log.error("Error loading instance world {}: {}", fullInstanceId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Enriches a zone world with time system and season data from the base world.
     * Copies: Time System (worldTime), seasonStatus, seasonProgress
     *
     * @param zoneWorldId The parsed zone worldId
     * @param zoneWorld The loaded zone world
     * @return The zone world enriched with base world data
     */
    private WWorld enrichZoneWithBaseWorldData(WorldId zoneWorldId, WWorld zoneWorld) {
        try {
            // Get base worldId (without zone and instance)
            WorldId baseWorldId = zoneWorldId.withoutInstanceAndZone();

            // Load base world
            Optional<WWorld> baseWorldOpt = repository.findByWorldId(baseWorldId.getId());

            if (baseWorldOpt.isEmpty()) {
                log.warn("Base world not found for zone {}: {}", zoneWorldId.getId(), baseWorldId.getId());
                return zoneWorld;
            }

            WWorld baseWorld = baseWorldOpt.get();

            // Check if both worlds have publicData
            if (zoneWorld.getPublicData() == null || baseWorld.getPublicData() == null) {
                log.warn("Cannot enrich zone world - missing publicData: zone={}, base={}",
                        zoneWorld.getPublicData() == null, baseWorld.getPublicData() == null);
                return zoneWorld;
            }

            // Copy time system data from base to zone
            copyTimeSystemData(baseWorld, zoneWorld);

            log.debug("Enriched zone world {} with data from base world {}", zoneWorldId.getId(), baseWorldId.getId());

            return zoneWorld;

        } catch (Exception e) {
            log.error("Error enriching zone world {}: {}", zoneWorldId.getId(), e.getMessage(), e);
            return zoneWorld; // Return zone world as-is on error
        }
    }

    /**
     * Copies time system and season data from base world to zone world.
     * Copies:
     * - worldTime (entire time system configuration)
     * - seasonStatus
     * - seasonProgress
     *
     * @param baseWorld The base world (source)
     * @param zoneWorld The zone world (target, will be modified)
     */
    private void copyTimeSystemData(WWorld baseWorld, WWorld zoneWorld) {
        var basePublicData = baseWorld.getPublicData();
        var zonePublicData = zoneWorld.getPublicData();

        // Copy seasonStatus (primitive byte, always copy)
        zonePublicData.setSeasonStatus(basePublicData.getSeasonStatus());

        // Copy seasonProgress (primitive double, always copy)
        zonePublicData.setSeasonProgress(basePublicData.getSeasonProgress());

        // Copy worldTime settings
        if (basePublicData.getSettings() != null && basePublicData.getSettings().getWorldTime() != null) {
            // Ensure zone has settings structure
            if (zonePublicData.getSettings() == null) {
                zonePublicData.setSettings(new de.mhus.nimbus.generated.types.WorldInfoSettingsDTO());
            }

            // Copy the entire worldTime object
            var baseWorldTime = basePublicData.getSettings().getWorldTime();
            zonePublicData.getSettings().setWorldTime(baseWorldTime);

            log.debug("Copied time system data: seasonStatus={}, seasonProgress={}, currentEra={}",
                    basePublicData.getSeasonStatus(),
                    basePublicData.getSeasonProgress(),
                    baseWorldTime.getCurrentEra());
        }
    }

    /**
     * Find all worlds (no filtering, no pagination).
     * WARNING: This loads ALL worlds into memory. Use searchWorlds() for large result sets.
     */
    @Transactional(readOnly = true)
    public List<WWorld> findAll() {
        return repository.findAll();
    }

    /**
     * Search worlds with database-level filtering and pagination.
     * Searches in worldId, name, and description fields (case-insensitive).
     *
     * @param searchQuery Optional search query (can be null/empty for no filter)
     * @param offset Pagination offset (0-based)
     * @param limit Pagination limit
     * @return WorldSearchResult with paginated worlds and total count
     */
    @Transactional(readOnly = true)
    public WorldSearchResult searchWorlds(String searchQuery, int offset, int limit) {
        log.debug("Searching worlds: query='{}', offset={}, limit={}", searchQuery, offset, limit);

        // Calculate page number from offset
        int pageNumber = offset / limit;
        Pageable pageable = PageRequest.of(pageNumber, limit);

        Page<WWorld> page;
        if (searchQuery != null && !searchQuery.isBlank()) {
            // Search with filter (MongoDB regex search across multiple fields)
            String searchPattern = searchQuery; // MongoDB regex, no need to add .*
            page = repository.findBySearchQuery(searchPattern, pageable);
        } else {
            // No filter, just pagination
            page = repository.findAllBy(pageable);
        }

        log.debug("Found {} worlds (total: {})", page.getNumberOfElements(), page.getTotalElements());

        return new WorldSearchResult(
                page.getContent(),
                (int) page.getTotalElements(),
                offset,
                limit
        );
    }

    @Transactional(readOnly = true)
    public List<WWorld> findByRegionId(String regionId) {
        return repository.findByRegionId(regionId);
    }

    /**
     * Initializes Era 1 for a new world by setting currentEra to 1
     * and linuxEpocheDeltaMinutes to the current time.
     *
     * @param info The WorldInfo to initialize (must not be null)
     */
    private void initializeEra(WorldInfo info) {
        if (info == null) {
            return;
        }

        // Ensure settings exist
        if (info.getSettings() == null) {
            info.setSettings(new de.mhus.nimbus.generated.types.WorldInfoSettingsDTO());
        }

        // Ensure worldTime exists
        if (info.getSettings().getWorldTime() == null) {
            info.getSettings().setWorldTime(new de.mhus.nimbus.generated.types.WorldInfoSettingsDTOWorldTimeDTO());
        }

        var worldTime = info.getSettings().getWorldTime();

        // Set Era 1
        worldTime.setCurrentEra(1);

        // Set epoch delta to current time (start of Era 1)
        long currentUnixMinutes = System.currentTimeMillis() / 60000L;
        worldTime.setLinuxEpocheDeltaMinutes(currentUnixMinutes);

        log.debug("Initialized Era 1 with epoch delta: {}", currentUnixMinutes);
    }

    @Transactional
    public WWorld createWorld(WorldId worldId, WorldInfo info) {
        if (repository.existsByWorldId(worldId.getId())) {
            throw new IllegalStateException("WorldId bereits vorhanden: " + worldId);
        }

        // Initialize Era 1 with current time
        initializeEra(info);

        WWorld entity = WWorld.builder()
                .worldId(worldId.getId())
                .publicData(info)
                .build();
        entity.touchForCreate();
        repository.save(entity);
        log.debug("WWorld angelegt: {} (Era 1 started)", worldId);
        return entity;
    }

    @Transactional
    public WWorld createWorld(WorldId worldId, WorldInfo info, String parent, Boolean enabled) {
        if (repository.existsByWorldId(worldId.getId())) {
            throw new IllegalStateException("WorldId bereits vorhanden: " + worldId);
        }

        // Initialize Era 1 with current time
        initializeEra(info);

        WWorld entity = WWorld.builder()
                .worldId(worldId.getId())
                .publicData(info)
                .parent(parent)
                .enabled(enabled == null ? true : enabled)
                .build();
        entity.touchForCreate();
        repository.save(entity);
        log.debug("WWorld angelegt (extended): {} (Era 1 started)", worldId);
        return entity;
    }

    @Transactional
    public Optional<WWorld> updateWorld(WorldId worldId, java.util.function.Consumer<WWorld> updater) {
        return repository.findByWorldId(worldId.getId()).map(existing -> {
            updater.accept(existing);
            existing.touchForUpdate();
            repository.save(existing);
            log.debug("WWorld aktualisiert: {}", worldId);
            return existing;
        });
    }

    @Transactional
    public WWorld save(WWorld world) {
        world.touchForUpdate();
        WWorld saved = repository.save(world);
        log.debug("WWorld gespeichert: {}", world.getWorldId());
        return saved;
    }

    @Transactional
    public boolean deleteWorld(WorldId worldId) {
        return repository.findByWorldId(worldId.getId()).map(e -> {
            repository.delete(e);
            log.debug("WWorld geloescht: {}", worldId);
            return true;
        }).orElse(false);
    }

    /**
     * Copy an existing world as a new zone.
     * Creates a new world with zone worldId (sourceWorldId:zoneName).
     *
     * @param sourceWorldId The worldId of the world to copy
     * @param zoneName The name of the zone to create
     * @return The created zone world
     * @throws IllegalArgumentException if sourceWorldId is invalid or zoneName is blank
     * @throws IllegalStateException if source world not found or zone already exists
     */
    @Transactional
    public WWorld copyWorldAsZone(WorldId sourceWorldId, String zoneName) {
        // Validation
        if (zoneName == null || zoneName.isBlank()) {
            throw new IllegalArgumentException("zoneName cannot be null or blank");
        }

        // Ensure source is not already a zone or instance
        if (sourceWorldId.isZone()) {
            throw new IllegalArgumentException("Source world is already a zone: " + sourceWorldId);
        }
        if (sourceWorldId.isInstance()) {
            throw new IllegalArgumentException("Cannot create zone from instance world: " + sourceWorldId);
        }

        // Load source world
        WWorld sourceWorld = repository.findByWorldId(sourceWorldId.getId())
                .orElseThrow(() -> new IllegalStateException("Source world not found: " + sourceWorldId));

        // Build zone worldId: sourceWorldId:zoneName
        String zoneWorldIdStr = sourceWorldId.getId() + ":" + zoneName;
        WorldId zoneWorldId = WorldId.of(zoneWorldIdStr)
                .orElseThrow(() -> new IllegalArgumentException("Invalid zone worldId: " + zoneWorldIdStr));

        // Check if zone already exists
        if (repository.existsByWorldId(zoneWorldId.getId())) {
            throw new IllegalStateException("Zone already exists: " + zoneWorldId);
        }

        // Copy world data
        // Copy publicData and update worldId to match zone worldId
        de.mhus.nimbus.generated.types.WorldInfo zonePublicData = sourceWorld.getPublicData();
        zonePublicData.setWorldId(zoneWorldId.getId());

        WWorld zoneWorld = WWorld.builder()
                .worldId(zoneWorldId.getId())
                .regionId(sourceWorld.getRegionId())
                .name(sourceWorld.getName() + " (Zone: " + zoneName + ")")
                .description(sourceWorld.getDescription())
                .publicData(zonePublicData)  // Copy publicData with updated worldId
                .enabled(sourceWorld.isEnabled())
                .parent(sourceWorld.getParent())
                .instanceable(sourceWorld.isInstanceable())
                .groundLevel(sourceWorld.getGroundLevel())
                .waterLevel(sourceWorld.getWaterLevel())
                .groundBlockType(sourceWorld.getGroundBlockType())
                .waterBlockType(sourceWorld.getWaterBlockType())
                .owner(sourceWorld.getOwner())
                .editor(sourceWorld.getEditor())
                .supporter(sourceWorld.getSupporter())
                .player(sourceWorld.getPlayer())
                .publicFlag(sourceWorld.isPublicFlag())
                .build();

        zoneWorld.touchForCreate();
        repository.save(zoneWorld);

        log.info("Created zone world: {} (copied from {})", zoneWorldId, sourceWorldId);
        return zoneWorld;
    }

    /**
     * Find all world collections.
     * World collections are identified by WorldId entries starting with '@'.
     *
     * @return List of distinct WorldIds representing collections
     */
    @Transactional(readOnly = true)
    public List<WorldId> findWorldCollections() {
        return worldCollectionRepository.findAll().stream()
                .map(WWorldCollection::getWorldId)
                .filter(worldId -> worldId != null && worldId.startsWith("@"))
                .distinct()
                .map(WorldId::unchecked)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Find a specific world collection by WorldId.
     * Checks if the given WorldId is a collection and if it exists.
     *
     * @param worldId The WorldId to search for (must be a collection starting with '@')
     * @return Optional containing the WorldId if found, empty otherwise
     */
    @Transactional(readOnly = true)
    public Optional<WorldId> findWorldCollection(WorldId worldId) {
        if (!worldId.isCollection()) {
            log.debug("WorldId is not a collection: {}", worldId);
            return Optional.empty();
        }

        return worldCollectionRepository.findByWorldId(worldId.getId())
                .map(collection -> worldId);
    }

    /**
     * Check if a world collection exists.
     * Verifies that the WorldId is a collection and that it exists.
     *
     * @param worldId The WorldId to check (must be a collection starting with '@')
     * @return true if the collection exists, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean existsWorldCollection(WorldId worldId) {
        if (!worldId.isCollection()) {
            return false;
        }

        return worldCollectionRepository.existsByWorldId(worldId.getId());
    }

    /**
     * Get the title for a world collection.
     * Returns the title if available, otherwise null.
     *
     * @param worldId The WorldId of the collection
     * @return The title if found and valid, null otherwise
     */
    @Transactional(readOnly = true)
    public String getWorldCollectionTitle(WorldId worldId) {
        if (!worldId.isCollection()) {
            return null;
        }

        return worldCollectionRepository.findByWorldId(worldId.getId())
                .map(WWorldCollection::getTitle)
                .orElse(null);
    }

    /**
     * Increments the current era for a world and resets the time epoch.
     * This sets the current time as the start of the new era by updating linuxEpocheDeltaMinutes
     * to the current Unix time in minutes. The duration of the completed era is recorded in eraHistory.
     *
     * @param worldId The WorldId to increment the era for
     * @return The updated WWorld, or empty if world not found or worldTime not configured
     * @throws IllegalStateException if publicData or worldTime settings are not configured
     */
    @Transactional
    public Optional<WWorld> incrementEra(WorldId worldId) {
        Optional<WWorld> worldOpt = repository.findByWorldId(worldId.getId());
        if (worldOpt.isEmpty()) {
            log.warn("Cannot increment era: World not found: {}", worldId);
            return Optional.empty();
        }

        WWorld world = worldOpt.get();
        WorldInfo publicData = world.getPublicData();

        if (publicData == null) {
            throw new IllegalStateException("Cannot increment era: publicData is null for world: " + worldId);
        }

        if (publicData.getSettings() == null || publicData.getSettings().getWorldTime() == null) {
            throw new IllegalStateException("Cannot increment era: worldTime settings not configured for world: " + worldId);
        }

        var worldTime = publicData.getSettings().getWorldTime();

        // Calculate current Unix time in minutes
        long currentUnixMinutes = System.currentTimeMillis() / 60000L;

        // Get current era (default to 1 if not set)
        Integer currentEra = worldTime.getCurrentEra();
        int newEra = (currentEra != null ? currentEra : 1) + 1;

        // Calculate era duration and add to history
        Long previousEpochDelta = worldTime.getLinuxEpocheDeltaMinutes();
        if (previousEpochDelta != null) {
            long eraDurationMinutes = currentUnixMinutes - previousEpochDelta;

            // Add to eraHistory (create mutable list if needed)
            List<Long> eraHistory = world.getEraHistory();
            if (eraHistory == null || eraHistory.isEmpty()) {
                eraHistory = new java.util.ArrayList<>();
            } else {
                eraHistory = new java.util.ArrayList<>(eraHistory);
            }
            eraHistory.add(eraDurationMinutes);
            world.setEraHistory(eraHistory);

            log.info("Era {} completed for world {}: duration {} minutes ({} days)",
                    currentEra, worldId, eraDurationMinutes, eraDurationMinutes / 1440.0);
        } else {
            log.warn("Cannot calculate era duration for world {}: no previous epoch delta", worldId);
        }

        // Update worldTime: increment era and set new epoch delta
        worldTime.setCurrentEra(newEra);
        worldTime.setLinuxEpocheDeltaMinutes(currentUnixMinutes);

        // Save changes
        world.touchForUpdate();
        repository.save(world);

        log.info("Incremented era for world {}: Era {} -> Era {}, new epoch delta: {} minutes",
                worldId, currentEra, newEra, currentUnixMinutes);

        return Optional.of(world);
    }

    /**
     * Skips time in a world by adjusting the linuxEpocheDeltaMinutes backwards.
     * This makes the world time advance without real time passing.
     * Uses the world's time configuration (minutesPerHour, hoursPerDay, etc.) for conversion.
     *
     * @param worldId The WorldId to skip time for
     * @param minutes Minutes to skip (optional, default 0)
     * @param hours Hours to skip (optional, default 0)
     * @param days Days to skip (optional, default 0)
     * @param months Months to skip (optional, default 0)
     * @param years Years to skip (optional, default 0)
     * @return The updated WWorld, or empty if world not found
     * @throws IllegalStateException if publicData or worldTime settings are not configured
     */
    @Transactional
    public Optional<WWorld> skipTime(WorldId worldId,
                                     Integer minutes,
                                     Integer hours,
                                     Integer days,
                                     Integer months,
                                     Integer years) {
        Optional<WWorld> worldOpt = repository.findByWorldId(worldId.getId());
        if (worldOpt.isEmpty()) {
            log.warn("Cannot skip time: World not found: {}", worldId);
            return Optional.empty();
        }

        WWorld world = worldOpt.get();
        WorldInfo publicData = world.getPublicData();

        if (publicData == null) {
            throw new IllegalStateException("Cannot skip time: publicData is null for world: " + worldId);
        }

        if (publicData.getSettings() == null || publicData.getSettings().getWorldTime() == null) {
            throw new IllegalStateException("Cannot skip time: worldTime settings not configured for world: " + worldId);
        }

        var worldTime = publicData.getSettings().getWorldTime();

        // Get time configuration (with defaults)
        int minutesPerHour = worldTime.getMinutesPerHour() != null ? worldTime.getMinutesPerHour() : 60;
        int hoursPerDay = worldTime.getHoursPerDay() != null ? worldTime.getHoursPerDay() : 24;
        int daysPerMonth = worldTime.getDaysPerMonth() != null ? worldTime.getDaysPerMonth() : 30;
        int monthsPerYear = worldTime.getMonthsPerYear() != null ? worldTime.getMonthsPerYear() : 12;

        // Calculate total minutes to skip
        long totalMinutes = 0;

        if (minutes != null && minutes > 0) {
            totalMinutes += minutes;
        }

        if (hours != null && hours > 0) {
            totalMinutes += (long) hours * minutesPerHour;
        }

        if (days != null && days > 0) {
            totalMinutes += (long) days * minutesPerHour * hoursPerDay;
        }

        if (months != null && months > 0) {
            totalMinutes += (long) months * daysPerMonth * minutesPerHour * hoursPerDay;
        }

        if (years != null && years > 0) {
            totalMinutes += (long) years * monthsPerYear * daysPerMonth * minutesPerHour * hoursPerDay;
        }

        if (totalMinutes == 0) {
            log.warn("Cannot skip time: no time specified for world: {}", worldId);
            return Optional.of(world);
        }

        // Get current epoch delta (initialize if not set)
        Long currentEpochDelta = worldTime.getLinuxEpocheDeltaMinutes();
        if (currentEpochDelta == null) {
            // Initialize to current time if not set
            currentEpochDelta = System.currentTimeMillis() / 60000L;
            log.info("Initializing linuxEpocheDeltaMinutes for world {}: {}", worldId, currentEpochDelta);
        }

        // Reduce epoch delta to advance world time
        long newEpochDelta = currentEpochDelta - totalMinutes;
        worldTime.setLinuxEpocheDeltaMinutes(newEpochDelta);

        // Save changes
        world.touchForUpdate();
        repository.save(world);

        log.info("Skipped time for world {}: {} minutes ({} hours, {} days) - epoch delta: {} -> {}",
                worldId, totalMinutes, totalMinutes / (double) minutesPerHour,
                totalMinutes / (double) (minutesPerHour * hoursPerDay),
                currentEpochDelta, newEpochDelta);

        return Optional.of(world);
    }

    /**
     * Result wrapper for world search with pagination info.
     */
    public record WorldSearchResult(
            List<WWorld> worlds,
            int totalCount,
            int offset,
            int limit
    ) {}
}
