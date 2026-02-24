package de.mhus.nimbus.world.generator.flora;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.mhus.nimbus.world.generator.modelbuilder.FloraConstraints;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Definition of a single plant within a flora type.
 * Contains model reference, placement constraints, weight for selection,
 * and optional clustering parameters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FloraPlantDefinition {

    private String name;
    private String model;
    private List<String> blocks;
    private Map<String, String> parameters;

    @Builder.Default
    private boolean land = false;
    @Builder.Default
    private boolean water = false;
    @Builder.Default
    private boolean sea = false;
    @Builder.Default
    private boolean emerse = false;

    private Integer maxHeight;
    private Integer minWater;
    private Integer maxWater;

    @Builder.Default
    private double weight = 1.0;
    private Integer clusterCount;
    @Builder.Default
    private int clusterSpread = 2;

    /**
     * Convert the inline constraint fields to a {@link FloraConstraints} record.
     */
    public FloraConstraints toConstraints() {
        return new FloraConstraints(
                maxHeight != null ? OptionalInt.of(maxHeight) : OptionalInt.empty(),
                minWater != null ? OptionalInt.of(minWater) : OptionalInt.empty(),
                maxWater != null ? OptionalInt.of(maxWater) : OptionalInt.empty(),
                land,
                water,
                sea,
                emerse
        );
    }
}
