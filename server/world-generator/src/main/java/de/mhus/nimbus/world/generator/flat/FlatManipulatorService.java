package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing and executing flat terrain manipulators.
 * <p>
 * Manipulators are automatically discovered from Spring context.
 * Each manipulator modifies a WFlat region in a specific way.
 */
@Service
@Slf4j
public class FlatManipulatorService {

    private final Map<String, FlatManipulator> manipulators;

    /**
     * Constructor with lazy injection of all FlatManipulator beans.
     * Lazy injection prevents circular dependencies and allows manipulators
     * to be optional (service works even if no manipulators are available).
     *
     * @param manipulatorList List of all FlatManipulator beans in Spring context
     */
    public FlatManipulatorService(@Lazy List<FlatManipulator> manipulatorList) {
        this.manipulators = new HashMap<>();

        // Register all manipulators by name
        for (FlatManipulator manipulator : manipulatorList) {
            String name = manipulator.getName();
            if (name == null || name.isBlank()) {
                log.warn("Skipping manipulator with null/blank name: {}", manipulator.getClass().getName());
                continue;
            }

            if (manipulators.containsKey(name)) {
                log.warn("Duplicate manipulator name '{}': {} (previous: {}), skipping",
                        name, manipulator.getClass().getName(),
                        manipulators.get(name).getClass().getName());
                continue;
            }

            manipulators.put(name, manipulator);
            log.info("Registered flat manipulator: {}", name);
        }

        log.info("FlatManipulatorService initialized with {} manipulators", manipulators.size());
    }

    /**
     * Execute a manipulator on a WFlat region.
     *
     * @param name Name of the manipulator to execute
     * @param flat The WFlat to manipulate (will be modified in place)
     * @param x X coordinate of the region start (relative to flat, 0-based)
     * @param z Z coordinate of the region start (relative to flat, 0-based)
     * @param sizeX Width of the region to manipulate
     * @param sizeZ Height of the region to manipulate
     * @param parameters Manipulator-specific parameters
     * @throws IllegalArgumentException if manipulator not found or parameters invalid
     */
    public void executeManipulator(String name, WFlat flat, int x, int z, int sizeX, int sizeZ,
                                    Map<String, String> parameters) {
        log.debug("Executing manipulator: name={}, x={}, z={}, sizeX={}, sizeZ={}, parameters={}",
                name, x, z, sizeX, sizeZ, parameters);

        // Validate manipulator name
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Manipulator name required");
        }

        // Find manipulator
        FlatManipulator manipulator = manipulators.get(name);
        if (manipulator == null) {
            throw new IllegalArgumentException("Manipulator not found: " + name +
                    ". Available manipulators: " + manipulators.keySet());
        }

        // Validate flat
        if (flat == null) {
            throw new IllegalArgumentException("WFlat required");
        }

        // Validate region bounds
        if (x < 0 || z < 0) {
            throw new IllegalArgumentException("Region coordinates must be non-negative: x=" + x + ", z=" + z);
        }
        if (sizeX <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("Region size must be positive: sizeX=" + sizeX + ", sizeZ=" + sizeZ);
        }
        if (x + sizeX > flat.getSizeX() || z + sizeZ > flat.getSizeZ()) {
            throw new IllegalArgumentException(
                    String.format("Region out of bounds: region=(%d,%d,%d,%d), flat=(%d,%d)",
                            x, z, sizeX, sizeZ, flat.getSizeX(), flat.getSizeZ()));
        }

        // Execute manipulator
        try {
            manipulator.manipulate(flat, x, z, sizeX, sizeZ, parameters != null ? parameters : new HashMap<>());
            log.info("Manipulator executed successfully: name={}, region=({},{},{},{})",
                    name, x, z, sizeX, sizeZ);
        } catch (Exception e) {
            log.error("Manipulator execution failed: name={}", name, e);
            throw new IllegalArgumentException("Manipulator execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get all available manipulator names.
     *
     * @return Set of manipulator names
     */
    public java.util.Set<String> getAvailableManipulators() {
        return manipulators.keySet();
    }

    /**
     * Check if a manipulator is available.
     *
     * @param name Manipulator name
     * @return true if manipulator exists
     */
    public boolean hasManipulator(String name) {
        return manipulators.containsKey(name);
    }
}
