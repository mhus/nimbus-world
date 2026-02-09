package de.mhus.nimbus.world.generator.composer.build;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.Map;

/**
 * FlatProvider implementation that uses a pre-loaded Map of flats.
 * Useful for tests and small worlds where all flats can be kept in memory.
 */
@RequiredArgsConstructor
public class MapFlatProvider implements FlatProvider {

    private final Map<HexVector2, WFlat> flats;

    @Override
    public WFlat getFlat(HexVector2 coordinate) {
        return flats.get(coordinate);
    }

    @Override
    public Collection<HexVector2> getCoordinates() {
        return flats.keySet();
    }
}
