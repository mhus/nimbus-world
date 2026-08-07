package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import lombok.AccessLevel;
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
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@GenerateTypeScript("dto")
public class WEpochMeta {

    /**
     * Epoch number (0-based, sequential).
     */
    private int epoch;

    /**
     * Parent epoch from which this epoch was derived.
     * Null for the base epoch (epoch 0). Used to track epoch lineage.
     */
    private Integer parentEpoch;

    /**
     * Technical name for this epoch (e.g., "base", "farming", "magic").
     */
    private String name;

    /**
     * Human-readable description of what this epoch adds.
     */
    private String description;

    /**
     * World status key for this epoch (e.g., "default", "winter", "summer").
     * Used as modifier key in block types to select epoch-specific block appearance.
     */
    @Builder.Default
    private String worldStatus = "default";

    /**
     * Splash screen image URL shown when this epoch becomes active.
     */
    private String splashScreen;

    /**
     * Splash screen audio URL played when this epoch becomes active.
     */
    private String splashScreenAudio;

}
