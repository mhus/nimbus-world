package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.generated.types.Vector2Int;
import de.mhus.nimbus.world.shared.util.HexLocalUtil;
import de.mhus.nimbus.world.shared.world.HexLocalPosition;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Regular point with standard positioning logic.
 * This is the default point type for most use cases like:
 * - City centers
 * - Villages
 * - Landmarks
 * - Quest markers
 * - Spawn points
 *
 * Positioned using biome-relative coordinates:
 * - direction + biomeDistance (e.g., NE + FAR)
 * - biomeSide + sideOffset (e.g., NORTH_EAST side at 0.5 offset)
 * - relativeToPoints (relative to other points)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PositionPoint extends Point {

    /**
     * Composes the position for this regular point.
     * Uses biome-relative positioning logic from the Point base class.
     *
     * PositionPoint places points WITHIN a biome using local hex coordinates.
     * For points at biome edges, use EdgePoint instead.
     *
     * @param biome The biome this point belongs to
     * @param context The compose context with all biomes, points, and maps
     * @return Composed HexLocalPosition (shared type) within the biome's center grid
     */
    public de.mhus.nimbus.world.shared.world.HexLocalPosition composePosition(Area biome, ComposeContext context) {
        // 1. If biomeSide + sideOffset is set, position NEAR that side (still inside)
        // Note: For actual edge positions, use EdgePoint instead
        if (getBiomeSide() != null) {
            return composePositionNearSide(context);
        }

        // 2. If direction + biomeDistance is set, position relative to center
        if (getDirection() != null && getBiomeDistance() != null) {
            return composePositionByDirection(context);
        }

        // 3. If relativeToPoints is set, position relative to other points
        if (getRelativeToPoints() != null && !getRelativeToPoints().isEmpty()) {
            return composePositionRelativeToPoints(context);
        }

        // Default: Place at center of biome (0,0 in hex coordinates, divider 5)
        return composeDefaultCenterPosition(context);
    }

    /**
     * Composes position NEAR a biome side using biomeSide + sideOffset.
     * Places point in the outer ring of the local hex grid, near the specified side.
     *
     * Note: This places the point INSIDE the biome, near a side.
     * For actual edge positions (on the boundary), use EdgePoint instead.
     *
     * With DEFAULT_POSITION_DIVIDER=5, the outer ring positions are:
     * - <0;2> Far North, <2;0> Far East, <2;-2> Far South East, etc.
     *
     * @param context The compose context
     * @return HexLocalPosition near the specified side
     */
    private HexLocalPosition composePositionNearSide(ComposeContext context) {
        // Place point at outer ring (distance = divider/2) in the direction of biomeSide
        Direction side = getBiomeSide();
        int distance = HexLocalUtil.DEFAULT_POSITION_DIVIDER / 2; // = 2 for divider 5

        // Calculate hex position at outer ring
        int q = 0;
        int r = 0;

        switch (side) {
            case N:  // North
                r = -distance;
                break;
            case NE: // North-East
                q = distance;
                r = -distance;
                break;
            case E:  // East
                q = distance;
                break;
            case SE: // South-East
                r = distance;
                break;
            case S:  // South
                q = -distance;
                r = distance;
                break;
            case SW: // South-West
                q = -distance;
                break;
            case W:  // West
                q = -distance;
                break;
            case NW: // North-West
                r = -distance;
                break;
            default:
                // CENTER or unknown - stay at center
                break;
        }

        // sideOffset can adjust position along the side (0.0 to 1.0)
        // For now, we ignore sideOffset since we're placing at a single hex cell
        // TODO: Use sideOffset to interpolate between adjacent outer ring cells

        HexVector2 hexPosition =
            HexVector2.builder()
                .q(q)
                .r(r)
                .build();

        int divider = HexLocalUtil.DEFAULT_POSITION_DIVIDER;
        int size = context.getHexGridSize() / divider;

        return new de.mhus.nimbus.world.shared.world.HexLocalPosition(hexPosition, divider, size);
    }

    /**
     * Composes position by direction + biomeDistance.
     * Places point at a specific hex cell within the biome using local hex coordinates.
     *
     * The position is calculated in the local hex grid (with divider 5):
     * - CENTER (0 hexes) → <0;0>
     * - NEAR (1 hex) → <1;0>, <0;1>, etc. (first ring)
     * - NORMAL (2 hexes) → <2;0>, <0;2>, etc. (second ring, but within valid range)
     * - FAR (3+ hexes) → May exceed valid range (divider/2 = 2.5)
     *
     * @param context The compose context
     * @return HexLocalPosition with calculated position
     */
    private de.mhus.nimbus.world.shared.world.HexLocalPosition composePositionByDirection(ComposeContext context) {
        int distance = getBiomeDistance().getHexes();

        // Calculate hex position from direction
        // Using axial coordinates (q, r) based on pointy-top hex directions
        int q = 0;
        int r = 0;

        switch (getDirection()) {
            case N:  // North: r decreases
                r = -distance;
                break;
            case NE: // North-East: q increases, r decreases (diagonal)
                q = distance;
                r = -distance;
                break;
            case E:  // East: q increases
                q = distance;
                break;
            case SE: // South-East: r increases
                r = distance;
                break;
            case S:  // South: q decreases, r increases (diagonal)
                q = -distance;
                r = distance;
                break;
            case SW: // South-West: q decreases
                q = -distance;
                break;
            case W:  // West: q decreases (mirrored to East)
                q = -distance;
                break;
            case NW: // North-West: r decreases (mirrored to SE)
                r = -distance;
                break;
            default:
                // CENTER or unknown - stay at 0,0
                break;
        }

        HexVector2 hexPosition =
            HexVector2.builder()
                .q(q)
                .r(r)
                .build();

        int divider = HexLocalUtil.DEFAULT_POSITION_DIVIDER;
        int size = context.getHexGridSize() / divider;

        return new de.mhus.nimbus.world.shared.world.HexLocalPosition(hexPosition, divider, size);
    }

    /**
     * Composes position relative to other points using relativeToPoints.
     * Takes the first relative position definition and calculates position based on:
     * - Reference point's position
     * - Direction from reference point
     * - Distance in local hex cells
     *
     * If multiple relativeToPoints are defined, only the first is used.
     * If reference point is not found or not yet composed, falls back to center.
     *
     * @param context The compose context with all points
     * @return HexLocalPosition relative to another point
     */
    private HexLocalPosition composePositionRelativeToPoints(ComposeContext context) {
        // Take first relative position (could be extended to handle multiple)
        RelativeToPoint relativePos = getRelativeToPoints().get(0);

        // Find reference point in context
        Point referencePoint = context.getPointMap().get(relativePos.getPointId());
        if (referencePoint == null) {
            // Reference point not found - fall back to center
            return composeDefaultCenterPosition(context);
        }

        // Get reference point's composed position
        if (referencePoint.getPointComposed() == null ||
            referencePoint.getPointComposed().getHexLocalPosition() == null) {
            // Reference point not yet composed - fall back to center
            return composeDefaultCenterPosition(context);
        }

        HexLocalPosition refPosition = referencePoint.getPointComposed().getHexLocalPosition();

        // Calculate offset based on direction and distance
        Direction direction = relativePos.getDirection();
        int distance = relativePos.getDistance() != null ? relativePos.getDistance() : 1;

        // Calculate direction offset in hex coordinates
        int offsetQ = 0;
        int offsetR = 0;

        if (direction != null) {
            switch (direction) {
                case N:  // North
                    offsetR = -distance;
                    break;
                case NE: // North-East
                    offsetQ = distance;
                    offsetR = -distance;
                    break;
                case E:  // East
                    offsetQ = distance;
                    break;
                case SE: // South-East
                    offsetR = distance;
                    break;
                case S:  // South
                    offsetQ = -distance;
                    offsetR = distance;
                    break;
                case SW: // South-West
                    offsetQ = -distance;
                    break;
                case W:  // West
                    offsetQ = -distance;
                    break;
                case NW: // North-West
                    offsetR = -distance;
                    break;
                default:
                    // No offset
                    break;
            }
        }

        // Calculate new position by adding offset to reference position
        HexVector2 newHexPosition = HexVector2.builder()
            .q(refPosition.position().getQ() + offsetQ)
            .r(refPosition.position().getR() + offsetR)
            .build();

        int divider = HexLocalUtil.DEFAULT_POSITION_DIVIDER;
        int size = context.getHexGridSize() / divider;

        return new de.mhus.nimbus.world.shared.world.HexLocalPosition(newHexPosition, divider, size);
    }

    /**
     * Default center position (0,0 in local hex coordinates).
     * This is the center cell of the local hex grid.
     *
     * @param context The compose context
     * @return HexLocalPosition at center <0;0>
     */
    private de.mhus.nimbus.world.shared.world.HexLocalPosition composeDefaultCenterPosition(ComposeContext context) {
        // Create center position <0;0>
        de.mhus.nimbus.generated.types.HexVector2 hexPosition =
            de.mhus.nimbus.generated.types.HexVector2.builder()
                .q(0)
                .r(0)
                .build();

        int divider = HexLocalUtil.DEFAULT_POSITION_DIVIDER;
        int size = context.getHexGridSize() / divider;

        return new de.mhus.nimbus.world.shared.world.HexLocalPosition(hexPosition, divider, size);
    }
}
