package de.mhus.nimbus.world.generator.composer.build;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.generator.WFlatService;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * FlatProvider implementation that loads flats on demand from the database.
 * Uses a suffix pattern to filter flats by flatId (e.g., "genesis_0_" to match "genesis_0_0_0", "genesis_0_0_1", etc.
 * Format: genesis_{epoch}_{q}_{r})
 */
@Slf4j
public class DatabaseFlatProvider implements FlatProvider {

    private final WFlatService flatService;
    private final String worldId;
    private final String flatIdSuffix;
    private final int epoch;

    public DatabaseFlatProvider(WFlatService flatService, String worldId, String flatIdSuffix, int epoch) {
        this.flatService = flatService;
        this.worldId = worldId;
        this.flatIdSuffix = flatIdSuffix;
        this.epoch = epoch;
    }

    @Override
    public int getEpoch() {
        return epoch;
    }

    @Override
    public WFlat getFlat(HexVector2 coordinate) {
        String flatId = flatIdSuffix + coordinate.getQ() + "_" + coordinate.getR();
        log.debug("Loading flat from database: worldId={}, flatId={}", worldId, flatId);

        try {
            return flatService.findByWorldId(worldId).stream()
                    .filter(f -> flatId.equals(f.getFlatId()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Failed to load flat {}: {}", flatId, e.getMessage());
            return null;
        }
    }

    @Override
    public Collection<HexVector2> getCoordinates() {
        // Load all flats for this world that match the suffix pattern
        log.debug("Loading all flat coordinates for worldId={}, suffix={}", worldId, flatIdSuffix);

        try {
            List<WFlat> allFlats = flatService.findByWorldId(worldId);
            return allFlats.stream()
                    .filter(f -> f.getFlatId() != null && f.getFlatId().startsWith(flatIdSuffix))
                    .map(f -> {
                        // Parse coordinate from flatId (e.g., "genesis_0_1" -> HexVector2(0, 1))
                        String[] parts = f.getFlatId().substring(flatIdSuffix.length()).split("_");
                        if (parts.length == 2) {
                            try {
                                int q = Integer.parseInt(parts[0]);
                                int r = Integer.parseInt(parts[1]);
                                return TypeUtil.hexVector2(q, r);
                            } catch (NumberFormatException e) {
                                log.warn("Failed to parse coordinate from flatId {}: {}", f.getFlatId(), e.getMessage());
                            }
                        }
                        return null;
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to load flat coordinates: {}", e.getMessage(), e);
            return List.of();
        }
    }
}
