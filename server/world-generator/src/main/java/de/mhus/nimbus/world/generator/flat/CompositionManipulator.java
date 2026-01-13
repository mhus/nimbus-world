package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Composition manipulator.
 * Executes multiple manipulators sequentially to create complex landscapes.
 * Supports predefined presets and custom compositions.
 * <p>
 * Parameters:
 * - preset: Predefined composition (default: "custom")
 *   - "volcanic-island": Islands → Crater → SharpPeak → WaterSoften
 *   - "mountain-valley": Mountain → Lakes → Spider → Soften
 *   - "archipelago": Islands → Spider → WaterSoften
 *   - "lunar": Crater → Crater → Crater → ShakedBox → SoftenRaster
 *   - "custom": Manual definition via steps parameter
 * - steps: Comma-separated list of manipulator names (required for preset="custom")
 * - <manipulator-name>.<param>: Parameters for specific manipulator
 * <p>
 * Example usage:
 * <pre>
 * {
 *   "preset": "volcanic-island",
 *   "islands.mainIslandSize": "60",
 *   "crater.outerRadius": "25",
 *   "sharp-peak.height": "120",
 *   "water-soften.passes": "8"
 * }
 * </pre>
 */
@Component
@Slf4j
public class CompositionManipulator implements FlatManipulator {

    public static final String NAME = "composition";
    public static final String PARAM_PRESET = "preset";
    public static final String PARAM_STEPS = "steps";

    private static final String DEFAULT_PRESET = "custom";

    @Autowired
    @Lazy
    private FlatManipulatorService manipulatorService;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void manipulate(WFlat flat, int x, int z, int sizeX, int sizeZ,
                          Map<String, String> parameters) {
        log.debug("Starting composition manipulation: region=({},{},{},{})", x, z, sizeX, sizeZ);

        // Parse preset or steps
        String preset = parameters.getOrDefault(PARAM_PRESET, DEFAULT_PRESET);
        List<String> steps = getSteps(preset, parameters);

        if (steps.isEmpty()) {
            log.warn("No steps defined for composition, skipping");
            return;
        }

        log.info("Executing composition: preset={}, steps={}", preset, steps);

        // Execute each manipulator
        int successCount = 0;
        int failureCount = 0;

        for (String manipulatorName : steps) {
            log.debug("Executing composition step: {}", manipulatorName);

            try {
                // Extract sub-parameters for this manipulator
                Map<String, String> subParams = extractSubParameters(parameters, manipulatorName);

                // Execute manipulator via service (includes validation)
                manipulatorService.executeManipulator(
                        manipulatorName, flat, x, z, sizeX, sizeZ, subParams);

                successCount++;
                log.debug("Composition step completed: {}", manipulatorName);

            } catch (IllegalArgumentException e) {
                failureCount++;
                log.warn("Composition step failed: {}, error: {}", manipulatorName, e.getMessage());
                // Continue with next step even if one fails
            }
        }

        log.info("Composition manipulation completed: preset={}, steps={}, success={}, failures={}",
                preset, steps, successCount, failureCount);
    }

    /**
     * Get list of manipulator names based on preset or steps parameter.
     *
     * @param preset Preset name
     * @param parameters All parameters
     * @return List of manipulator names to execute
     */
    private List<String> getSteps(String preset, Map<String, String> parameters) {
        switch (preset.toLowerCase()) {
            case "volcanic-island":
                return List.of("islands", "crater", "sharp-peak", "water-soften");

            case "mountain-valley":
                return List.of("mountain", "lakes", "spider", "soften");

            case "archipelago":
                return List.of("islands", "spider", "water-soften");

            case "lunar":
                // Multiple craters by repeating crater 3 times
                return List.of("crater", "crater", "crater", "shaked-box", "soften-raster");

            case "custom":
            default:
                String stepsParam = parameters.get(PARAM_STEPS);
                if (stepsParam == null || stepsParam.isBlank()) {
                    log.warn("No steps defined for custom preset");
                    return List.of();
                }
                // Split by comma and trim whitespace
                return Arrays.stream(stepsParam.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
        }
    }

    /**
     * Extract sub-parameters for a specific manipulator from the full parameter map.
     * Parameters with prefix "<manipulator-name>." are extracted and the prefix is removed.
     *
     * @param parameters Full parameter map
     * @param manipulatorName Manipulator name
     * @return Sub-parameters for this manipulator
     */
    private Map<String, String> extractSubParameters(Map<String, String> parameters, String manipulatorName) {
        Map<String, String> subParams = new HashMap<>();
        String prefix = manipulatorName + ".";

        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String subKey = entry.getKey().substring(prefix.length());
                subParams.put(subKey, entry.getValue());
            }
        }

        return subParams;
    }
}
