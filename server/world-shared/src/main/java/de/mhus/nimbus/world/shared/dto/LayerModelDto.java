package de.mhus.nimbus.world.shared.dto;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.shared.annotations.TypeScript;

import java.time.Instant;
import java.util.Map;

/**
 * DTO for WLayerModel entity.
 * Used for API responses and TypeScript generation.
 */
@GenerateTypeScript("dto")
public record LayerModelDto(
        @TypeScript(optional = true)
        String id,

        String worldId,

        @TypeScript(optional = true)
        String name,

        @TypeScript(optional = true)
        String title,

        String layerDataId,

        int mountX,

        int mountY,

        int mountZ,

        int rotation,

        @TypeScript(optional = true)
        String referenceModelId,

        int order,

        Map<String, Integer> groups,

        @TypeScript(optional = true)
        Instant createdAt,

        @TypeScript(optional = true)
        Instant updatedAt
) {
}
