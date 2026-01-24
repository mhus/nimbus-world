package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * Prepared town with concrete positions and internal structure.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PreparedTown extends PreparedStructure {
    private List<VillageBuildingDefinition> buildings;
    private List<VillageStreetDefinition> streets;
    private Map<String, String> parameters;
}
