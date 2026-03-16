package de.mhus.nimbus.world.shared.dto;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.shared.annotations.TypeScript;

/**
 * DTO for block info response from block editor endpoint.
 * Contains the block data, edit state, and chunk info metadata.
 */
@GenerateTypeScript("dto")
public record BlockInfoDto(
        Block block,
        boolean readOnly,

        @TypeScript(optional = true)
        String layer,

        @TypeScript(optional = true)
        String group,

        @TypeScript(optional = true)
        String groupName,

        /** Layer (and model) this block originates from, from WChunkInfo. */
        @TypeScript(optional = true)
        String chunkInfoLayer,

        /** Group this block belongs to, from WChunkInfo. */
        @TypeScript(optional = true)
        String chunkInfoGroup
) {
}
