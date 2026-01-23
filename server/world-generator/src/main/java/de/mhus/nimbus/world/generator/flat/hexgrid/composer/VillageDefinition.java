package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@GenerateTypeScript("dto")
public class VillageDefinition {

    private String name;
    private String title;
    @Builder.Default
    private BiomeType type = BiomeType.VILLAGE;
    @Builder.Default
    private BiomeShape shape = BiomeShape.CIRCLE;
    private List<RelativePosition> relativePositions;
    private List<VillageBuildingDefinition> buildings;
    private List<VillageStreetDefinition> streets;
    private Integer calculatedHexGridWidth;
    private Integer calculatedHexGridHeight;
    private Map<String, String> parameters;
    private String description;

    public String getDisplayTitle() {
        return title != null ? title : name;
    }
}
