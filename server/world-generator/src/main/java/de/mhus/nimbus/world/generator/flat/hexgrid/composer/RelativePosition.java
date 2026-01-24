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
        return distanceFrom != null ? distanceFrom : distance.getFrom();
    }

    public int getEffectiveDistanceTo() {
        return distanceTo != null ? distanceTo : distance.getTo();
    }
}
