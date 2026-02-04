package de.mhus.nimbus.world.generator.composer.flow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Single route definition for RoadBuilder
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RouteDefinition {
    private WHexGrid.EDGE side;  // optional: start from grid side
    private Integer lx;          // optional: start from absolute position
    private Integer lz;          // optional: start from absolute position
    private int width;
    private int level;
    private String type;
}
