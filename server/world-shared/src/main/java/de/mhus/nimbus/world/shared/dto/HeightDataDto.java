package de.mhus.nimbus.world.shared.dto;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.shared.annotations.TypeScript;

/**
 * DTO for height data of a single chunk column.
 * Represents height information for a specific XZ position within a chunk.
 */
@GenerateTypeScript("dto")
public record HeightDataDto(
        int x,
        int z,
        int maxHeight,
        int groundLevel,

        @TypeScript(optional = true)
        Integer waterLevel
) {
}
