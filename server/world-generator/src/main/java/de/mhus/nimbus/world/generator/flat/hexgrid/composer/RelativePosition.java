package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RelativePosition {

    private Direction direction;
    private DistanceRange distance;
    private String anchor;
    @Builder.Default
    private int priority = 5;
    private Integer distanceFrom;
    private Integer distanceTo;

    public int getEffectiveDistanceFrom() {
        if (distanceFrom != null) return distanceFrom;
        if (distance != null) return distance.getFrom();
        return 0; // Default if both are null
    }

    public int getEffectiveDistanceTo() {
        if (distanceTo != null) return distanceTo;
        if (distance != null) return distance.getTo();
        return 0; // Default if both are null
    }
}
