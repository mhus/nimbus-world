package de.mhus.nimbus.world.shared.util;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.generated.types.Vector2Int;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.shared.world.HexLocalPosition;
import de.mhus.nimbus.world.shared.world.HexLocalEdgeVector;
import de.mhus.nimbus.world.shared.world.WHexGrid;

/**
 * Utility class for local hexagonal grid operations.
 * Spec in generator-hex-grid-local-coordinates.md
 *
 * Side Coordinates: <NE2/4> or < SW1 / 3 >
 * Default side divider is 4.
 *
 * Position Coordinates: <0;0> or <  10 ; -5 / 4> or < -3 ; 2 # 100 >
 * Default position divider is 4.
 */
public class HexLocalUtil {

    public static final int DEFAULT_SIDE_DIVIDER = 4;
    public static final int DEFAULT_POSITION_DIVIDER = 4;
    private static final double sqrt3 = Math.sqrt(3);


    public enum LOCAL_TYPE {
        EDGE,
        POSITION,
        UNKNOWN
    }


    /**
     * Parse a hex local side vector from a string representation.
     * Format example: <NE2/4>
     *
     * @param str the string representation of the hex local side vector
     * @return the parsed HexLocalSideVector
     * @throws IllegalArgumentException if the string format is invalid
     * @throws NullPointerException if the input string is null
     */
    public static HexLocalEdgeVector parseHexLocalEdgeVector(String str) {
        if (str == null) throw new NullPointerException("Input string is null");
        int pos = str.indexOf('<');
        int pos2 = str.indexOf('>');
        if (pos < 0 || pos2 < 0 || pos2 <= pos) {
            throw new IllegalArgumentException("Invalid format: missing angle brackets");
        }
        String inner = str.substring(pos + 1, pos2).trim();
        int sideStr = 0;
        for (; sideStr < inner.length(); sideStr++) {
            char c = inner.charAt(sideStr);
            if (c < 'A' || c > 'Z') break;
        }
        if (sideStr == 0) {
            throw new IllegalArgumentException("Invalid format: missing side");
        }
        String sidePart = inner.substring(0, sideStr);
        String restPart = inner.substring(sideStr).trim();
        WHexGrid.EDGE side = WHexGrid.EDGE.fromString(sidePart);
        if (side == null) {
            throw new IllegalArgumentException("Invalid side: " + sidePart);
        }
        int numerator = 0;
        int denominator = DEFAULT_SIDE_DIVIDER;
        if (restPart.contains("/")) {
            String[] parts = restPart.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid format: too many '/' characters");
            }
            numerator = Integer.parseInt(parts[0].trim());
            denominator = Integer.parseInt(parts[1].trim());
        } else if (!restPart.isEmpty()) {
            numerator = Integer.parseInt(restPart);
        }
        return new HexLocalEdgeVector(side, numerator, denominator);
    }

    public static String toString(HexLocalEdgeVector vector) {
        StringBuilder sb = new StringBuilder();
        sb.append('<');
        sb.append(vector.side().getShortName());
        sb.append(vector.numerator());
        if (vector.denominator() != DEFAULT_SIDE_DIVIDER && vector.denominator() != 0) {
            sb.append('/');
            sb.append(vector.denominator());
        }
        sb.append('>');
        return sb.toString();
    }

    /**
     * Parse a hex local position from a string representation.
     * Format example: <0;0> or <  10 ; -5 / 4> or < -3 ; 2 # 100 >
     *
     * @param str the string representation of the hex local position
     * @return the parsed HexLocalPosition
     */
    public HexLocalPosition parseHexLocalPosition(String str, int hexGridSize) {
        if (str == null) throw new NullPointerException("Input string is null");
        int pos = str.indexOf('<');
        int pos2 = str.indexOf('>');
        if (pos < 0 || pos2 < 0 || pos2 <= pos) {
            throw new IllegalArgumentException("Invalid format: missing angle brackets");
        }
        String inner = str.substring(pos + 1, pos2).trim();
        int divider = 0;
        int size = 0;
        pos = inner.indexOf('/');
        if (pos < 0) {
            pos = inner.indexOf('#');
            if (pos < 0) {
                divider = DEFAULT_POSITION_DIVIDER;
            } else {
                String sizePart = inner.substring(pos + 1).trim();
                size = Integer.parseInt(sizePart);
                if (hexGridSize > 0) {
                    divider = hexGridSize / size;
                }
                inner = inner.substring(0, pos).trim();
            }
        } else {
            String dividerPart = inner.substring(pos + 1).trim();
            divider = Integer.parseInt(dividerPart);
            if (hexGridSize > 0) {
                size = hexGridSize / divider;
            }
            inner = inner.substring(0, pos).trim();
        }

        HexVector2 hex = TypeUtil.parseHexCoord(inner);
        return new HexLocalPosition(hex, divider, size);
    }

    public static String toString(HexLocalPosition position) {
        StringBuilder sb = new StringBuilder();
        sb.append('<');
        sb.append(position.position().getQ());
        sb.append(';');
        sb.append(position.position().getR());
        if (position.divider() != DEFAULT_POSITION_DIVIDER && position.divider() > 0) {
            sb.append('/');
            sb.append(position.divider());
        } else
        if (position.size() > 0) {
            sb.append('#');
            sb.append(position.size());
        }
        sb.append('>');
        return sb.toString();
    }

    public static LOCAL_TYPE getType(String str) {
        if (str == null) return null;
        str = str.trim();
        if (str.startsWith("<") && str.endsWith(">")) {
            String inner = str.substring(1, str.length() - 1).trim();
            if (inner.contains(";")) {
                return LOCAL_TYPE.POSITION;
            } else {
                return LOCAL_TYPE.EDGE;
            }
        }
        return LOCAL_TYPE.UNKNOWN;
    }

    /**
     * Return the coordinate from center in local hex grid coordinates.
     * @param pos the local hex position
     * @return Could be outside the hex grid!
     */
    public static Vector2Int toHexGridLocalCenter(HexLocalPosition pos) {
        int x = (pos.position().getQ() * pos.size()) / pos.divider();
        int z = (pos.position().getR() * pos.size()) / pos.divider();
        return TypeUtil.vector2int(x, z);
    }

    /**
     * Return the coordinate from center in local hex grid coordinates.
     * The coordinate is at one of the edges of the hex grid.
     *
     * Each side is measured from North to South (not clockwise):
     * - NW: from N corner to NW corner
     * - NE: from N corner to NE corner
     * - E: from NE corner to SE corner
     * - SE: from SE corner to S corner
     * - SW: from SW corner to S corner
     * - W: from NW corner to SW corner
     *
     * IMPORTANT: This method returns coordinates RELATIVE to the hex center.
     * The returned coordinates can be negative and are in the range [-radius, +radius].
     * To convert to absolute WFlat coordinates, add WFlat.widthX/2 and WFlat.widthZ/2:
     *   lx = WFlat.widthX/2 + result.x
     *   lz = WFlat.widthZ/2 + result.z
     *
     * @param edge the local hex edge vector
     * @param hexGridSize the size of the hex grid (NOT the WFlat size)
     * @return The coordinate relative to hex center. Range: [-hexGridSize/2, +hexGridSize/2]
     */
    public static Vector2Int toHexgridLocalCenter(HexLocalEdgeVector edge, int hexGridSize) {
        double radius = hexGridSize / 2.0;

        // Define corner positions for pointy-top hexagon
        // Corners: N, NE, SE, S, SW, NW
        double[][] corners = {
            {0, -radius},           // N  (0)
            {radius * sqrt3 / 2, -radius / 2},   // NE (1)
            {radius * sqrt3 / 2, radius / 2},    // SE (2)
            {0, radius},            // S  (3)
            {-radius * sqrt3 / 2, radius / 2},   // SW (4)
            {-radius * sqrt3 / 2, -radius / 2}   // NW (5)
        };

        // Determine start and end corners for each edge (North to South direction)
        int startCorner, endCorner;
        switch (edge.side()) {
            case NORTH_WEST:  // NW: N -> NW
                startCorner = 0; endCorner = 5;
                break;
            case NORTH_EAST:  // NE: N -> NE
                startCorner = 0; endCorner = 1;
                break;
            case EAST:        // E: NE -> SE
                startCorner = 1; endCorner = 2;
                break;
            case SOUTH_EAST:  // SE: SE -> S
                startCorner = 2; endCorner = 3;
                break;
            case SOUTH_WEST:  // SW: SW -> S
                startCorner = 4; endCorner = 3;
                break;
            case WEST:        // W: NW -> SW
                startCorner = 5; endCorner = 4;
                break;
            default:
                throw new IllegalArgumentException("Unknown edge: " + edge.side());
        }

        // Interpolate between start and end corner
        double t = (double) edge.numerator() / edge.denominator();
        double x = corners[startCorner][0] + t * (corners[endCorner][0] - corners[startCorner][0]);
        double z = corners[startCorner][1] + t * (corners[endCorner][1] - corners[startCorner][1]);

        // Return coordinates relative to center (can be negative)
        // Caller must add WFlat.width/2 to get absolute coordinates
        int relX = (int) Math.round(x);
        int relZ = (int) Math.round(z);

        return TypeUtil.vector2int(relX, relZ);
    }

    /**
     * Convenience method to parse a string and return coordinates relative to hex center.
     * Automatically detects whether the string is an EDGE or POSITION format.
     *
     * Examples:
     * - EDGE format: "<NE2/4>", "<SW1/3>" - returns position on hex edge
     * - POSITION format: "<0;0>", "<1;-1/4>" - returns position within hex
     *
     * IMPORTANT: Returns coordinates RELATIVE to hex center.
     * To convert to absolute WFlat coordinates:
     *   lx = WFlat.widthX/2 + result.x
     *   lz = WFlat.widthZ/2 + result.z
     *
     * @param value the string representation (e.g., "<NE2/4>" or "<0;0>")
     * @param hexGridSize the size of the hex grid (NOT the WFlat size)
     * @return coordinates relative to hex center
     * @throws IllegalArgumentException if format is invalid or unknown
     */
    public static Vector2Int toHexgridLocalCenter(String value, int hexGridSize) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Value cannot be null or blank");
        }

        LOCAL_TYPE type = getType(value);

        switch (type) {
            case EDGE:
                // Parse as edge coordinate (e.g., "<NE2/4>")
                HexLocalEdgeVector edge = parseHexLocalEdgeVector(value);
                return toHexgridLocalCenter(edge, hexGridSize);

            case POSITION:
                // Parse as position coordinate (e.g., "<0;0>")
                HexLocalUtil util = new HexLocalUtil();
                HexLocalPosition pos = util.parseHexLocalPosition(value, hexGridSize);
                return toHexGridLocalCenter(pos);

            default:
                throw new IllegalArgumentException("Unknown or invalid local coordinate format: " + value);
        }
    }

}
