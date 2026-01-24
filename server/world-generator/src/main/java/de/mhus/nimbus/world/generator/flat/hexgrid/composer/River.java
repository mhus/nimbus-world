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
public class River extends Flow {
    private List<String> waypointIds;
    private String mergeToId;
    private Integer depth;
    private Integer level;

    public static RiverBuilder builder() {
        return new RiverBuilder();
    }

    /**
     * Applies river-specific default configuration from FlowType.RIVER
     */
    @Override
    protected void applyFlowDefaults(Map<String, String> defaults) {
        if (depth == null && defaults.containsKey("default_depth")) {
            depth = Integer.parseInt(defaults.get("default_depth"));
        }
        if (level == null && defaults.containsKey("default_level")) {
            level = Integer.parseInt(defaults.get("default_level"));
        }
        if (getWidthBlocks() == null && defaults.containsKey("default_width")) {
            setWidthBlocks(Integer.parseInt(defaults.get("default_width")));
        }
    }
}
