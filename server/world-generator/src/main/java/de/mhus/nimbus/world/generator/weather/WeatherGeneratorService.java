package de.mhus.nimbus.world.generator.weather;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates weather descriptors for hex grids based on biome configuration.
 *
 * Weather descriptors are stored in WHexGrid.parameters with the key prefix "w_".
 * The key is epoch-based: w_0 for epoch 0, w_1 for epoch 1, etc.
 *
 * The weather descriptor JSON comes from the biome default parameter "ge_weather"
 * which is set on the WHexGrid during composition (via BiomeType defaults).
 *
 * Weather can come from:
 * 1. Default biome weather (ge_weather parameter, set by BiomeComposer from BiomeType)
 * 2. Explicit model definition (if w_{epoch} parameter already exists on the hex grid)
 *
 * If a hex grid already has a weather descriptor for the given epoch, it is NOT overwritten.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WeatherGeneratorService {

    private static final String PARAM_WEATHER = "ge_weather";

    private final WHexGridService hexGridService;

    /**
     * Generate weather descriptor for a hex grid based on its ge_weather parameter.
     * Does nothing if the hex grid already has a weather descriptor for the given epoch.
     *
     * @param worldId World ID
     * @param hexQ Hex axial Q coordinate
     * @param hexR Hex axial R coordinate
     * @param epoch Epoch number to generate for
     * @return true if a weather descriptor was generated, false if skipped
     */
    public boolean generateWeather(String worldId, int hexQ, int hexR, int epoch) {
        WorldId wid = WorldId.of(worldId).orElseThrow(
                () -> new IllegalArgumentException("Invalid worldId: " + worldId));

        List<WHexGrid> grids = hexGridService.findAllByWorldIdAndPosition(wid.getId(), TypeUtil.hexVector2(hexQ, hexR));

        if (grids.isEmpty()) {
            log.warn("No hex grid found at {},{} in world {}", hexQ, hexR, worldId);
            return false;
        }

        // Find the grid matching the epoch (or any grid if epoch list is empty = all epochs)
        WHexGrid hexGrid = grids.stream()
                .filter(g -> g.getEpoches().isEmpty() || g.getEpoches().contains(epoch))
                .findFirst()
                .orElse(null);

        if (hexGrid == null) {
            log.debug("No hex grid for epoch {} at {},{}", epoch, hexQ, hexR);
            return false;
        }

        String weatherKey = "w_" + epoch;

        // Skip if weather descriptor already exists (explicit model definition takes priority)
        if (hexGrid.getParameters() != null && hexGrid.getParameters().containsKey(weatherKey)) {
            log.debug("Weather descriptor already exists for epoch {} at {},{} - skipping", epoch, hexQ, hexR);
            return false;
        }

        // Get weather descriptor from biome defaults (ge_weather parameter)
        Map<String, String> params = hexGrid.getParameters();
        if (params == null) {
            log.debug("No parameters on hex grid at {},{}", hexQ, hexR);
            return false;
        }

        String descriptor = params.get(PARAM_WEATHER);
        if (descriptor == null || descriptor.isBlank()) {
            log.debug("No ge_weather parameter on hex grid at {},{}", hexQ, hexR);
            return false;
        }

        // Store weather descriptor for the epoch
        if (hexGrid.getParameters() == null) {
            hexGrid.setParameters(new LinkedHashMap<>());
        }
        hexGrid.getParameters().put(weatherKey, descriptor);
        hexGridService.save(hexGrid);

        log.info("Generated weather for hex {},{} epoch {} in world {} (from ge_weather)",
                hexQ, hexR, epoch, worldId);
        return true;
    }
}
