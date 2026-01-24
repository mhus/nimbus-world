package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Rich Enum defining all flow types with their default configurations.
 * Each flow type specifies:
 * - Associated flow class for type-specific behavior
 * - Default parameters for flow generation
 *
 * This enum is server-side only and not exposed to TypeScript.
 */
public enum FlowType {
    ROAD(Road.class, Map.of(
        "default_level", "95",
        "default_roadType", "cobblestone",
        "default_width", "4"
    )),

    RIVER(River.class, Map.of(
        "default_depth", "3",
        "default_level", "85",
        "default_width", "4"
    )),

    WALL(Wall.class, Map.of(
        "default_height", "10",
        "default_material", "stone",
        "default_width", "2"
    ));

    private final Class<? extends Flow> flowClass;
    private final Map<String, String> defaultParameters;

    FlowType(Class<? extends Flow> flowClass, Map<String, String> defaultParameters) {
        this.flowClass = flowClass;
        this.defaultParameters = Collections.unmodifiableMap(new HashMap<>(defaultParameters));
    }

    public Class<? extends Flow> getFlowClass() {
        return flowClass;
    }

    public Map<String, String> getDefaultParameters() {
        return defaultParameters;
    }

    /**
     * Creates a new instance of the flow with default configuration applied.
     */
    public Flow createInstance() {
        try {
            Flow flow = flowClass.getDeclaredConstructor().newInstance();
            flow.setType(this);
            flow.applyDefaults();
            return flow;
        } catch (Exception e) {
            throw new RuntimeException("Cannot create flow instance for type: " + this, e);
        }
    }

    public static FlowType fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return FlowType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid FlowType value: " + value);
        }
    }
}
