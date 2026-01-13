package de.mhus.nimbus.world.shared.dto;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.shared.annotations.TypeScript;
import de.mhus.nimbus.world.shared.layer.LayerType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DTO for WLayer entity.
 * Used for API responses and TypeScript generation.
 *
 * Note: mountX/Y/Z are in WLayerModel.
 * groups is available both in WLayer (for GROUND layers) and WLayerModel (for MODEL layers).
 */
@GenerateTypeScript("dto")
public record LayerDto(
        @TypeScript(optional = true)
        String id,

        String worldId,

        String name,

        @TypeScript(follow = true)
        LayerType layerType,

        @TypeScript(optional = true)
        String layerDataId,

        boolean allChunks,

        @TypeScript(optional = true)
        List<String> affectedChunks,

        int order,

        boolean enabled,

        boolean baseGround,

        @TypeScript(optional = true)
        Map<String, Integer> groups,

        @TypeScript(optional = true)
        Instant createdAt,

        @TypeScript(optional = true)
        Instant updatedAt
) {
}
