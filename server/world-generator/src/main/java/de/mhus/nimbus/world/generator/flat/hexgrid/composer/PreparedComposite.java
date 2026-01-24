package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Prepared composite with nested prepared features.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PreparedComposite extends PreparedArea {
    private List<PreparedFeature> preparedFeatures = new ArrayList<>();

    public List<PreparedArea> getPreparedAreas() {
        return preparedFeatures.stream()
            .filter(f -> f instanceof PreparedArea)
            .map(f -> (PreparedArea) f)
            .toList();
    }

    public List<PreparedFlow> getPreparedFlows() {
        return preparedFeatures.stream()
            .filter(f -> f instanceof PreparedFlow)
            .map(f -> (PreparedFlow) f)
            .toList();
    }
}
