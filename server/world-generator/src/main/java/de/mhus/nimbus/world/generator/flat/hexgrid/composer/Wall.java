package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Wall extends Flow {
    private List<String> waypointIds;
    private String endPointId;
    private Integer height;
    private String material;

    public static WallBuilder builder() {
        return new WallBuilder();
    }

    /**
     * Applies wall-specific default configuration from FlowType.WALL
     */
    @Override
    protected void applyFlowDefaults(Map<String, String> defaults) {
        if (height == null && defaults.containsKey("default_height")) {
            height = Integer.parseInt(defaults.get("default_height"));
        }
        if (material == null && defaults.containsKey("default_material")) {
            material = defaults.get("default_material");
        }
        if (getWidthBlocks() == null && defaults.containsKey("default_width")) {
            setWidthBlocks(Integer.parseInt(defaults.get("default_width")));
        }
    }
}
