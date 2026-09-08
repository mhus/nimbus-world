package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.Area;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Einstiegspunkt in eine Welt (Spawn oder Portal-Bereich)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WEntryPoint {
    private String name;
    private Area area;
}
