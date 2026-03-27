package de.mhus.nimbus.world.life.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.model.ChunkCoordinate;
import de.mhus.nimbus.world.shared.session.SessionCommandService;
import de.mhus.nimbus.world.shared.util.HexMathUtil;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldInstanceService;
import de.mhus.nimbus.world.shared.world.WWorldService;
import de.mhus.nimbus.world.shared.world.WorldTimeService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Weather simulation service for active hex grid regions.
 *
 * Runs as a scheduled service parallel to SimulatorService.
 * Tracks active hex grids (derived from active chunks) and simulates weather
 * phases based on the weather descriptor (w_{epoch} parameter in WHexGrid).
 *
 * Weather commands are broadcast to clients via SessionCommandService.sendToHexGrid().
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeatherSimulationService implements MultiWorldChunkService.WorldChunkChangeListener {

    private final MultiWorldChunkService multiWorldChunkService;
    private final SessionCommandService sessionCommandService;
    private final WHexGridService hexGridService;
    private final WWorldService worldService;
    private final WWorldInstanceService worldInstanceService;
    private final WorldTimeService worldTimeService;
    private final ObjectMapper objectMapper;

    /**
     * Weather state per hex grid per world.
     * Key: worldId -> "q;r" -> HexWeatherState
     */
    private final Map<String, Map<String, HexWeatherState>> worldWeatherStates = new ConcurrentHashMap<>();

    /**
     * Cached world data.
     */
    private final Map<String, WWorld> worldCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> epochCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        multiWorldChunkService.addWorldChunkChangeListener(this);
        log.info("WeatherSimulationService initialized");
    }

    @Override
    public void onChunksActivated(WorldId worldId, Set<ChunkCoordinate> added) {
        String wid = worldId.getFullId();
        WWorld world = getCachedWorld(wid);
        if (world == null) return;

        int hexGridSize = world.getPublicData() != null ? world.getPublicData().getHexGridSize() : 0;
        int chunkSize = world.getPublicData() != null ? world.getPublicData().getChunkSize() : 16;
        if (hexGridSize <= 0) return;

        Map<String, HexWeatherState> hexStates = worldWeatherStates.computeIfAbsent(wid, k -> new ConcurrentHashMap<>());

        // Determine hex grids for activated chunks
        Set<String> newHexKeys = new HashSet<>();
        for (ChunkCoordinate chunk : added) {
            HexVector2[] hexes = HexMathUtil.getHexesForChunk(hexGridSize, chunkSize, chunk.getCx(), chunk.getCz());
            for (HexVector2 hex : hexes) {
                String hexKey = hex.getQ() + ";" + hex.getR();
                newHexKeys.add(hexKey);
            }
        }

        // Initialize weather state for new hex grids
        int epoch = getCachedEpoch(wid);
        for (String hexKey : newHexKeys) {
            if (!hexStates.containsKey(hexKey)) {
                initializeHexWeather(wid, hexKey, epoch, hexStates);
            }
        }
    }

    @Override
    public void onChunksDeactivated(WorldId worldId, Set<ChunkCoordinate> removed) {
        // We keep weather states alive even when chunks deactivate
        // They will be cleaned up when no chunks reference them anymore
        // This avoids weather restart flicker when players move between chunks
    }

    /**
     * Main weather simulation loop. Runs every 5 seconds.
     */
    @Scheduled(fixedDelayString = "#{${world.life.weather-interval-ms:5000}}")
    public void weatherSimulationLoop() {
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<String, Map<String, HexWeatherState>> worldEntry : worldWeatherStates.entrySet()) {
            String worldId = worldEntry.getKey();
            Map<String, HexWeatherState> hexStates = worldEntry.getValue();

            // Check if world still has active chunks
            WorldId wid = WorldId.of(worldId).orElse(null);
            if (wid == null) continue;
            Set<ChunkCoordinate> activeChunks = multiWorldChunkService.getActiveChunks(wid);
            if (activeChunks.isEmpty()) {
                // No active chunks - clean up weather states
                if (!hexStates.isEmpty()) {
                    log.debug("No active chunks for world {}, clearing {} weather states", worldId, hexStates.size());
                    hexStates.clear();
                }
                continue;
            }

            for (Map.Entry<String, HexWeatherState> hexEntry : hexStates.entrySet()) {
                try {
                    simulateHexWeather(worldId, hexEntry.getKey(), hexEntry.getValue(), currentTime);
                } catch (Exception e) {
                    log.error("Error simulating weather for hex {} in world {}: {}",
                            hexEntry.getKey(), worldId, e.getMessage(), e);
                }
            }
        }
    }

    private void initializeHexWeather(String worldId, String hexKey, int epoch, Map<String, HexWeatherState> hexStates) {
        try {
            // Load weather descriptor from WHexGrid
            String descriptor = loadWeatherDescriptor(worldId, hexKey, epoch);
            if (descriptor == null) {
                log.debug("No weather descriptor for hex {} in world {}", hexKey, worldId);
                return;
            }

            JsonNode descriptorNode = objectMapper.readTree(descriptor);
            String baseScenario = descriptorNode.path("base").asText("clear");
            boolean permanent = descriptorNode.path("permanent").asBoolean(false);

            HexWeatherState state = new HexWeatherState();
            state.descriptor = descriptorNode;
            state.currentScenario = baseScenario;
            state.phaseEndTime = System.currentTimeMillis(); // Trigger immediate first weather
            state.permanent = permanent;

            hexStates.put(hexKey, state);
            log.info("Initialized weather for hex {} in world {}: base={}, permanent={}",
                    hexKey, worldId, baseScenario, permanent);

        } catch (Exception e) {
            log.error("Failed to initialize weather for hex {} in world {}: {}",
                    hexKey, worldId, e.getMessage(), e);
        }
    }

    private void simulateHexWeather(String worldId, String hexKey, HexWeatherState state, long currentTime) {
        if (state.descriptor == null) return;

        // Check if current phase has ended
        if (currentTime < state.phaseEndTime) return;

        // Determine next scenario
        String nextScenario;
        int durationSeconds;
        Map<String, String> params;

        if (state.permanent) {
            nextScenario = state.descriptor.path("base").asText("clear");
            durationSeconds = 1800; // 30 minutes for permanent weather
            params = resolveParams(state.descriptor, nextScenario);
        } else {
            nextScenario = selectNextScenario(worldId, state);
            durationSeconds = selectDuration(state.descriptor, nextScenario);
            params = resolveParams(state.descriptor, nextScenario);
        }

        // Update state
        state.currentScenario = nextScenario;
        state.phaseEndTime = currentTime + (durationSeconds * 1000L);

        // Build command args: startEnvironmentScript("rain", "intensity", "70", ...)
        List<String> args = new ArrayList<>();
        args.add(nextScenario);
        for (Map.Entry<String, String> param : params.entrySet()) {
            args.add(param.getKey());
            args.add(param.getValue());
        }

        // Parse hex coordinates
        String[] parts = hexKey.split(";");
        int hexQ = Integer.parseInt(parts[0]);
        int hexR = Integer.parseInt(parts[1]);

        // Broadcast to clients
        sessionCommandService.sendToHexGrid(worldId, hexQ, hexR,
                "startEnvironmentScript", args);

        log.info("Weather change in world {} hex {}: {} for {}s (params: {})",
                worldId, hexKey, nextScenario, durationSeconds, params);
    }

    /**
     * Select the next weather scenario using weighted random selection.
     * Considers: allowed transitions (next), base weight, season/daytime modifiers.
     */
    private String selectNextScenario(String worldId, HexWeatherState state) {
        JsonNode descriptor = state.descriptor;
        JsonNode scenarios = descriptor.path("scenarios");
        JsonNode currentScenarioNode = scenarios.path(state.currentScenario);
        double baseWeight = descriptor.path("baseWeight").asDouble(0.3);
        String baseScenario = descriptor.path("base").asText("clear");

        // Get allowed next scenarios
        List<String> candidates = new ArrayList<>();
        JsonNode nextNode = currentScenarioNode.path("next");
        if (nextNode.isArray()) {
            for (JsonNode n : nextNode) {
                candidates.add(n.asText());
            }
        }
        // Always add base as candidate
        if (!candidates.contains(baseScenario)) {
            candidates.add(baseScenario);
        }

        // Get season and daytime modifiers
        String season = getCurrentSeason(worldId);
        String daytime = getCurrentDaytime(worldId);
        JsonNode seasonMod = descriptor.path("seasonModifier").path(season);
        JsonNode daytimeMod = descriptor.path("daytimeModifier").path(daytime);

        // Calculate weights
        Map<String, Double> weights = new LinkedHashMap<>();
        for (String candidate : candidates) {
            JsonNode scenarioNode = scenarios.path(candidate);
            if (scenarioNode.isMissingNode()) continue;

            double weight = scenarioNode.path("weight").asDouble(0.5);

            // Apply season modifier
            if (!seasonMod.isMissingNode() && seasonMod.has(candidate)) {
                weight += seasonMod.path(candidate).asDouble(0);
            }

            // Apply daytime modifier
            if (!daytimeMod.isMissingNode() && daytimeMod.has(candidate)) {
                weight += daytimeMod.path(candidate).asDouble(0);
            }

            // Apply base weight bonus
            if (candidate.equals(baseScenario)) {
                weight += baseWeight;
            }

            // Clamp to zero
            weights.put(candidate, Math.max(0, weight));
        }

        // Weighted random selection
        double totalWeight = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalWeight <= 0) {
            return baseScenario;
        }

        double random = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (random <= cumulative) {
                return entry.getKey();
            }
        }

        return baseScenario;
    }

    /**
     * Select a random duration within the scenario's [min, max] range.
     */
    private int selectDuration(JsonNode descriptor, String scenario) {
        JsonNode scenarioNode = descriptor.path("scenarios").path(scenario);
        JsonNode durationNode = scenarioNode.path("duration");

        int min = 30;
        int max = 300;

        if (durationNode.isArray() && durationNode.size() >= 2) {
            min = durationNode.get(0).asInt(30);
            max = durationNode.get(1).asInt(300);
        }

        // Clamp to allowed range
        min = Math.max(30, min);
        max = Math.min(1800, max);

        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * Resolve random parameter values from the scenario's params definition.
     * Each param has a [min, max] range.
     */
    private Map<String, String> resolveParams(JsonNode descriptor, String scenario) {
        Map<String, String> result = new LinkedHashMap<>();
        JsonNode scenarioNode = descriptor.path("scenarios").path(scenario);
        JsonNode paramsNode = scenarioNode.path("params");

        if (paramsNode.isObject()) {
            var fields = paramsNode.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                JsonNode value = field.getValue();

                if (value.isArray() && value.size() >= 2) {
                    double min = value.get(0).asDouble();
                    double max = value.get(1).asDouble();
                    double randomValue = min + ThreadLocalRandom.current().nextDouble() * (max - min);

                    // Use integer if both bounds are integers and >= 1
                    if (min >= 1 && min == Math.floor(min) && max == Math.floor(max)) {
                        result.put(field.getKey(), String.valueOf((int) Math.round(randomValue)));
                    } else {
                        result.put(field.getKey(), String.format("%.2f", randomValue));
                    }
                } else if (value.isNumber()) {
                    result.put(field.getKey(), value.asText());
                }
            }
        }

        return result;
    }

    private String loadWeatherDescriptor(String worldId, String hexKey, int epoch) {
        WorldId wid = WorldId.of(worldId).orElse(null);
        if (wid == null) return null;

        // Parse hex key "q;r" to HexVector2
        String[] parts = hexKey.split(";");
        HexVector2 hexPos = HexVector2.builder()
                .q(Integer.parseInt(parts[0]))
                .r(Integer.parseInt(parts[1]))
                .build();

        List<WHexGrid> grids = hexGridService.findAllByWorldIdAndPosition(wid.getId(), hexPos);
        if (grids.isEmpty()) return null;

        // Find matching grid for epoch
        WHexGrid hexGrid = grids.stream()
                .filter(g -> g.getEpoches().isEmpty() || g.getEpoches().contains(epoch))
                .findFirst()
                .orElse(null);

        if (hexGrid == null || hexGrid.getParameters() == null) return null;

        // Epoch fallback: w_{epoch} -> w_{parentEpoch} -> ... -> w_0 -> w_
        var epochOrder = worldService.getEpochOrder(wid.getId(), epoch);
        for (var epochMeta : epochOrder) {
            String key = "w_" + epochMeta.getEpoch();
            String value = hexGrid.getParameters().get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        // Global fallback
        String globalFallback = hexGrid.getParameters().get("w_");
        if (globalFallback != null && !globalFallback.isBlank()) {
            return globalFallback;
        }

        return null;
    }

    private String getCurrentSeason(String worldId) {
        WWorld world = getCachedWorld(worldId);
        if (world == null || world.getPublicData() == null) return "summer";
        return worldTimeService.getCurrentSeason(world.getPublicData());
    }

    private String getCurrentDaytime(String worldId) {
        WWorld world = getCachedWorld(worldId);
        if (world == null || world.getPublicData() == null) return "day";
        return worldTimeService.getCurrentDaySection(world.getPublicData());
    }

    private WWorld getCachedWorld(String worldId) {
        return worldCache.computeIfAbsent(worldId, id -> {
            WorldId wid = WorldId.of(id).orElse(null);
            return wid != null ? worldService.getByWorldId(wid).orElse(null) : null;
        });
    }

    private int getCachedEpoch(String worldId) {
        return epochCache.computeIfAbsent(worldId, id -> {
            WorldId wid = WorldId.of(id).orElse(null);
            if (wid != null && wid.isInstance()) {
                return worldInstanceService.findByInstanceIdWithValidation(wid.getId())
                        .map(instance -> instance.getEpoch())
                        .orElse(0);
            }
            return 0;
        });
    }

    /**
     * Internal state for weather simulation of a single hex grid.
     */
    static class HexWeatherState {
        JsonNode descriptor;
        String currentScenario;
        long phaseEndTime;
        boolean permanent;
    }
}
