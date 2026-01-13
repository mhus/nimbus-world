package de.mhus.nimbus.world.control.service;

import de.mhus.nimbus.generated.types.Block;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a block definition in the editor palette.
 * Used for paste operations in the block editor.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaletteBlockDefinition {

    /**
     * The block definition to paste.
     */
    private Block block;

    /**
     * Display name for the palette entry.
     */
    private String name;

    /**
     * Icon URL or texture reference for the palette entry.
     */
    private String icon;
}
