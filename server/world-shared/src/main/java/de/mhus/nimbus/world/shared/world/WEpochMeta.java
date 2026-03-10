package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Epoch metadata embedded in WWorld.
 * Defines available epochs for a world with their names and descriptions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@GenerateTypeScript("dto")
public class WEpochMeta {

    /**
     * Epoch number (0-based, sequential).
     */
    private int epoch;

    /**
     * Technical name for this epoch (e.g., "base", "farming", "magic").
     */
    private String name;

    /**
     * Human-readable description of what this epoch adds.
     */
    private String description;

    /**
     * World status level required for this epoch (default 0).
     * Used to gate epoch availability based on world progression.
     */
    @Builder.Default
    private int worldStatus = 0;

}
