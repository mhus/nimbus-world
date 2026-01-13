package de.mhus.nimbus.world.shared.dto;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.shared.annotations.TypeScript;
import de.mhus.nimbus.world.shared.layer.LayerType;

import java.util.List;
import java.util.Map;

/**
 * DTO for creating a new WLayer.
 * Used for API requests and TypeScript generation.
 *
 * Note: mountX/Y/Z are deprecated - these fields are now in WLayerModel.
 * groups is available both in WLayer (for GROUND layers) and WLayerModel (for MODEL layers).
 */
@GenerateTypeScript("dto")
public record CreateLayerRequest(
        String name,

        @TypeScript(follow = true, optional = true)
        LayerType layerType,

        @Deprecated
        @TypeScript(optional = true)
        Integer mountX,

        @Deprecated
        @TypeScript(optional = true)
        Integer mountY,

        @Deprecated
        @TypeScript(optional = true)
        Integer mountZ,

        @Deprecated
        @TypeScript(optional = true)
        Boolean ground,

        @TypeScript(optional = true)
        Boolean allChunks,

        @TypeScript(optional = true)
        List<String> affectedChunks,

        @TypeScript(optional = true)
        Integer order,

        @TypeScript(optional = true)
        Boolean enabled,

        @TypeScript(optional = true)
        Boolean baseGround,

        @TypeScript(optional = true)
        Map<String, Integer> groups
) {
}
