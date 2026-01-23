package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Plot definition for VillageBuilder
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VillagePlotDefinition {
    private String id;
    private int lx;
    private int lz;
    private Integer sizeX;  // for rectangular plots
    private Integer sizeZ;  // for rectangular plots
    private Integer size;   // for circular plots
    private int level;
    private int material;
    private Integer road;   // optional: connect to road index
}
