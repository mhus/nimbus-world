package de.mhus.nimbus.world.generator.composer.town;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TownBuildingDefinition {

    private TownPosition position;
    private String buildingType;
    private String id;
    private String size;
    private Map<String, String> parameters;
}
