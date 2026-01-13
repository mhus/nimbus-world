package de.mhus.nimbus.world.shared.dto;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.shared.annotations.TypeScript;

/**
 * DTO for block origin information.
 * Contains metadata about where a block comes from (layer, terrain, model).
 */
@GenerateTypeScript("dto")
public record BlockOriginDto(
        // Layer information
        @TypeScript(optional = true)
        String layerId,

        @TypeScript(optional = true)
        String layerName,

        @TypeScript(optional = true)
        String layerType,

        @TypeScript(optional = true)
        Integer layerOrder,

        // Terrain information
        @TypeScript(optional = true)
        String terrainId,

        @TypeScript(optional = true)
        String terrainChunkKey,

        // Model information (only for MODEL layers)
        @TypeScript(optional = true)
        String modelId,

        @TypeScript(optional = true)
        String modelName,

        @TypeScript(optional = true)
        String modelTitle,

        @TypeScript(optional = true)
        Integer mountX,

        @TypeScript(optional = true)
        Integer mountY,

        @TypeScript(optional = true)
        Integer mountZ,

        @TypeScript(optional = true)
        Integer group,

        @TypeScript(optional = true)
        String groupName,  // Name of the group if block is in a group

        @TypeScript(optional = true)
        String metadata
) {
}
