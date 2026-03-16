package de.mhus.nimbus.world.shared.dto;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.shared.annotations.TypeScript;

/**
 * DTO for height data of a single chunk column.
 * Represents height information for a specific XZ position within a chunk.
 * waterLevel = -1 means no water.
 * maxHeight is only set when it differs from the world default (world.stop.y).
 */
@GenerateTypeScript("dto")
public record HeightDataDto(
        int groundLevel,
        int waterLevel,

        @TypeScript(optional = true)
        Integer maxHeight
) {
    public boolean hasWater() {
        return waterLevel >= 0;
    }
}
