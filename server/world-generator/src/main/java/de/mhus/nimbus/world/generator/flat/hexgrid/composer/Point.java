package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Point represents a specific location within a biome.
 * Points are used to mark important locations (cities, landmarks, spawn points)
 * and can later be used as connection points for Flows.
 *
 * Unlike Areas, Points don't occupy multiple hexagons - they are placed at a
 * specific coordinate (q, r) with local position (lx, lz) within that hex grid.
 *
 * Example: Minas Tirith, Mount Doom, Village Centers, Quest Markers
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Point extends Feature {

    /**
     * Relative positions for placement (similar to Area).
     * Defines where the point should be placed relative to an anchor.
     */
    private List<RelativePosition> positions;

    /**
     * Snap configuration for precise placement within/near biomes.
     * Defines how the point should be positioned relative to other features.
     */
    private SnapConfig snap;

    /**
     * Custom parameters for the point (e.g., role, level, type).
     */
    private Map<String, String> parameters;

    // Calculated values (runtime, set during composition)

    /**
     * Hex coordinate where this point is located (calculated during composition).
     */
    private HexVector2 placedCoordinate;

    /**
     * Local X position within the hex grid (0-511 for 512x512 grid).
     * Calculated during composition.
     */
    private Integer placedLx;

    /**
     * Local Z position within the hex grid (0-511 for 512x512 grid).
     * Calculated during composition.
     */
    private Integer placedLz;

    /**
     * Name of the biome this point was placed in.
     * Set during composition.
     */
    private String placedInBiome;

    /**
     * Prepared positions with calculated angles.
     * Set during preparation phase.
     */
    private List<PreparedPosition> preparedPositions;

    /**
     * Prepares this point for composition by calculating concrete values from positions.
     */
    public void prepareForComposition() {
        // Prepare positions (convert RelativePosition → PreparedPosition)
        if (positions != null && !positions.isEmpty()) {
            preparedPositions = new java.util.ArrayList<>();
            for (RelativePosition pos : positions) {
                preparedPositions.add(preparePosition(pos));
            }
        }
    }

    /**
     * Converts RelativePosition to PreparedPosition with concrete values.
     */
    private PreparedPosition preparePosition(RelativePosition position) {
        PreparedPosition prepared = new PreparedPosition();
        prepared.setOriginal(position);
        prepared.setDirection(position.getDirection());
        prepared.setDirectionAngle(convertDirectionToAngle(position.getDirection()));
        prepared.setDistanceFrom(position.getEffectiveDistanceFrom());
        prepared.setDistanceTo(position.getEffectiveDistanceTo());
        prepared.setAnchor(position.getAnchor());
        prepared.setPriority(position.getPriority());
        return prepared;
    }

    /**
     * Converts Direction enum to angle in degrees.
     */
    private int convertDirectionToAngle(Direction direction) {
        if (direction == null) return 0;
        // Pointy-top hex has 6 sides at 60° intervals (no N/S, starts with NE):
        // NE(0°), E(60°), SE(120°), SW(180°), W(240°), NW(300°)
        // N and S are mapped to nearest hex directions
        return switch (direction) {
            case N -> 330;    // North (top spike) → rounds to NW/NE
            case NE -> 0;     // Northeast: top-right side
            case E -> 60;     // East: right side
            case SE -> 120;   // Southeast: bottom-right side
            case S -> 150;    // South (bottom spike) → rounds to SE/SW
            case SW -> 180;   // Southwest: bottom-left side
            case W -> 240;    // West: left side
            case NW -> 300;   // Northwest: top-left side
        };
    }

    /**
     * Returns true if this point has been placed (has calculated coordinates).
     */
    public boolean isPlaced() {
        return placedCoordinate != null && placedLx != null && placedLz != null;
    }

    /**
     * Gets a human-readable position string.
     */
    public String getPlacedPositionString() {
        if (!isPlaced()) {
            return "not placed";
        }
        return String.format("hex[%d,%d] local[%d,%d] in %s",
            placedCoordinate.getQ(), placedCoordinate.getR(),
            placedLx, placedLz,
            placedInBiome != null ? placedInBiome : "unknown");
    }
}
