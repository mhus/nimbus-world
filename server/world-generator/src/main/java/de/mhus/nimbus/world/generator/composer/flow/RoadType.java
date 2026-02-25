package de.mhus.nimbus.world.generator.composer.flow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Road surface type enum.
 * Determines the material and visual appearance of the road.
 */
public enum RoadType {
    STREET("street"),
    TRAIL("trail");

    private final String value;

    RoadType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static RoadType fromString(String value) {
        if (value == null) {
            return null;
        }
        for (RoadType type : values()) {
            if (type.value.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException(
            "Invalid roadType: '" + value + "'. Valid values are: " +
            Arrays.stream(values()).map(RoadType::getValue).collect(Collectors.joining(", ")));
    }
}
