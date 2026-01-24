package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Rich Enum defining all structure types with their default configurations.
 * Each structure type specifies:
 * - Default builder to use (typically "island" for flat terrain)
 * - Default parameters for structure generation
 * - Associated structure class for type-specific behavior
 *
 * Example usage in JSON:
 * <pre>
 * {
 *   "featureType": "village",
 *   "type": "HAMLET",
 *   "name": "small-hamlet",
 *   "size": "SMALL"
 * }
 * </pre>
 */
public enum StructureType {
    /**
     * Small hamlet with few buildings (7-10 buildings, single hex)
     */
    HAMLET(Village.class, "island", Map.of(
        "g_offset", "1",
        "default_level", "95",
        "default_material", "1"
    )),

    /**
     * Small village (10-15 buildings, 1-2 hexes)
     */
    SMALL_VILLAGE(Village.class, "island", Map.of(
        "g_offset", "1",
        "default_level", "95",
        "default_material", "1"
    )),

    /**
     * Medium village (15-25 buildings, 2-3 hexes)
     */
    VILLAGE(Village.class, "island", Map.of(
        "g_offset", "1",
        "default_level", "95",
        "default_material", "1"
    )),

    /**
     * Large village (25-40 buildings, 3-5 hexes)
     */
    LARGE_VILLAGE(Village.class, "island", Map.of(
        "g_offset", "1",
        "default_level", "95",
        "default_material", "1"
    )),

    /**
     * Small town (40-60 buildings, 5-7 hexes, cross pattern)
     */
    TOWN(Town.class, "island", Map.of(
        "g_offset", "1",
        "default_level", "95",
        "default_material", "1",
        "has_wall", "false"
    )),

    /**
     * Large town (60-100 buildings, 7-12 hexes)
     */
    LARGE_TOWN(Town.class, "island", Map.of(
        "g_offset", "1",
        "default_level", "95",
        "default_material", "1",
        "has_wall", "true"
    )),

    /**
     * City (100+ buildings, 12+ hexes)
     */
    CITY(Town.class, "island", Map.of(
        "g_offset", "1",
        "default_level", "95",
        "default_material", "1",
        "has_wall", "true",
        "has_districts", "true"
    ));

    private final Class<? extends Structure> structureClass;
    private final String defaultBuilder;
    private final Map<String, String> defaultParameters;

    StructureType(Class<? extends Structure> structureClass, String defaultBuilder, Map<String, String> defaultParameters) {
        this.structureClass = structureClass;
        this.defaultBuilder = defaultBuilder;
        this.defaultParameters = Collections.unmodifiableMap(new HashMap<>(defaultParameters));
    }

    public String getDefaultBuilder() {
        return defaultBuilder;
    }

    public Map<String, String> getDefaultParameters() {
        return defaultParameters;
    }

    public Class<? extends Structure> getStructureClass() {
        return structureClass;
    }

    /**
     * Creates a new instance of the structure with default configuration applied.
     */
    public Structure createInstance() {
        try {
            Structure structure = structureClass.getDeclaredConstructor().newInstance();
            structure.setType(this);
            structure.applyDefaults();
            return structure;
        } catch (Exception e) {
            throw new RuntimeException("Cannot create structure instance for type: " + this, e);
        }
    }

    public static StructureType fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return StructureType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid StructureType value: " + value);
        }
    }
}
