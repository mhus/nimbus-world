package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import lombok.Data;

import java.util.*;

/**
 * Context for biome composition process
 * Tracks occupied coordinates and placed biomes
 */
@Data
public class CompositionContext {
    /**
     * All occupied hex coordinates
     */
    private Set<HexVector2> occupiedCoordinates = new HashSet<>();

    /**
     * Successfully placed biomes
     */
    private List<PlacedBiome> placedBiomes = new ArrayList<>();

    /**
     * Named anchor points for positioning
     * Key: anchor name, Value: HexVector2 coordinate
     */
    private Map<String, HexVector2> anchors = new HashMap<>();

    /**
     * Random generator for this composition
     */
    private Random random;

    /**
     * Maximum retries for placing a single biome
     */
    private int maxRetriesPerBiome = 50;

    /**
     * Maximum total retries for entire composition
     */
    private int maxTotalRetries = 3;

    public CompositionContext(long seed) {
        this.random = new Random(seed);
        // Origin is always an anchor
        anchors.put("origin", HexVector2.builder().q(0).r(0).build());
    }

    /**
     * Checks if a coordinate is occupied
     */
    public boolean isOccupied(HexVector2 coord) {
        return occupiedCoordinates.stream()
            .anyMatch(c -> c.getQ() == coord.getQ() && c.getR() == coord.getR());
    }

    /**
     * Marks a coordinate as occupied
     */
    public void occupy(HexVector2 coord) {
        occupiedCoordinates.add(coord);
    }

    /**
     * Gets an anchor coordinate
     */
    public HexVector2 getAnchor(String name) {
        if (name == null || name.isEmpty()) {
            return anchors.get("origin");
        }
        return anchors.get(name);
    }

    /**
     * Adds an anchor point
     */
    public void addAnchor(String name, HexVector2 coord) {
        anchors.put(name, coord);
    }

    /**
     * Clears all placed biomes and occupied coordinates
     */
    public void reset() {
        occupiedCoordinates.clear();
        placedBiomes.clear();
        anchors.clear();
        anchors.put("origin", HexVector2.builder().q(0).r(0).build());
    }
}
