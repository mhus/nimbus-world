package de.mhus.nimbus.shared.utils;

import de.mhus.nimbus.generated.types.Area;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.generated.types.Vector2;
import de.mhus.nimbus.generated.types.Vector2Int;
import de.mhus.nimbus.generated.types.Vector2Pair;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.generated.types.Vector3Pair;

public class TypeUtil {

    public static Area area(int x, int z, int sizeX, int sizeZ) {
        return area(x, 0, z, sizeX, 255, sizeZ);
    }

    public static Area area(int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
        return Area.builder()
                .position(vector3(x, y, z))
                .size(vector3(sizeX, sizeY, sizeZ))
                .build();
    }

    public static Vector3Int vector3(int x, int y, int z) {
        return Vector3Int.builder()
                .x(x)
                .y(y)
                .z(z)
                .build();
    }

    public static Vector3 vector3(double x, double y, double z) {
        return Vector3.builder()
                .x(x)
                .y(y)
                .z(z)
                .build();
    }

    public static Vector2 vector2(double x, double z) {
        return Vector2.builder()
                .x(x)
                .z(z)
                .build();
    }

    public static Vector2 vector2(int x, int z) {
        return Vector2.builder()
                .x(x)
                .z(z)
                .build();
    }

    public static Vector2Pair vector2Pair(Vector2 a, Vector2 b) {
        return Vector2Pair.builder()
                .a(a)
                .b(b)
                .build();
    }

    public static Vector2Pair vector2Pair(double ax, double az, double bx, double bz) {
        return Vector2Pair.builder()
                .a(vector2(ax, az))
                .b(vector2(bx, bz))
                .build();
    }

    public static Vector3Pair vector3Pair(Vector3 a, Vector3 b) {
        return Vector3Pair.builder()
                .a(a)
                .b(b)
                .build();
    }

    public static Vector3Pair vector3Pair(double ax, double ay, double az, double bx, double by, double bz) {
        return Vector3Pair.builder()
                .a(vector3(ax, ay, az))
                .b(vector3(bx, by, bz))
                .build();
    }

    public static HexVector2 hexVector2(int q, int r) {
        return HexVector2.builder()
                .q(q)
                .r(r)
                .build();
    }

    // --- World Coordinates (x,y,z) ---
    public static Vector3 parseWorldCoord(String s) {
        if (s == null) throw new IllegalArgumentException("Input is null");
        String[] parts = s.split(",");
        if (parts.length != 3) throw new IllegalArgumentException("Invalid world coordinate: " + s);
        return vector3(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
    }
    public static String toStringWorldCoord(Vector3 v) {
        return String.format("%s,%s,%s", v.getX(), v.getY(), v.getZ());
    }
    public static String toStringWorldCoord(Vector3Int v) {
        return String.format("%s,%s,%s", v.getX(), v.getY(), v.getZ());
    }

    // --- Flat World Coordinates (x,z) ---
    public static Vector2 parseFlatWorldCoord(String s) {
        if (s == null) throw new IllegalArgumentException("Input is null");
        String[] parts = s.split(",");
        if (parts.length != 2) throw new IllegalArgumentException("Invalid flat world coordinate: " + s);
        return vector2(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
    }
    public static String toStringFlatWorldCoord(Vector2 v) {
        return String.format("%s,%s", v.getX(), v.getZ());
    }

    // --- Chunk Coordinates (cx:cz) ---
    public static int[] parseChunkCoord(String s) {
        if (s == null) throw new IllegalArgumentException("Input is null");
        String[] parts = s.split(":");
        if (parts.length != 2) throw new IllegalArgumentException("Invalid chunk coordinate: " + s);
        return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
    }
    public static String toStringChunkCoord(int cx, int cz) {
        return cx + ":" + cz;
    }
    public static String toStringChunkCoord(double cx, double cz) {
        return (int)Math.floor(cx) + ":" + (int)Math.floor(cz);
    }

    // --- Hex Grid Coordinates (q;r) ---
    public static HexVector2 parseHexCoord(String s) {
        if (s == null) throw new IllegalArgumentException("Input is null");
        String[] parts = s.split(";");
        if (parts.length != 2) throw new IllegalArgumentException("Invalid hex coordinate: " + s);
        return hexVector2(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
    public static String toStringHexCoord(HexVector2 v) {
        return v.getQ() + ";" + v.getR();
    }

    // --- Local Coordinates (x/y/z or x/z) ---
    public static Vector3Int parseLocalCoord3(String s) {
        if (s == null) throw new IllegalArgumentException("Input is null");
        String[] parts = s.split("/");
        if (parts.length != 3) throw new IllegalArgumentException("Invalid local 3D coordinate: " + s);
        return vector3(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }
    public static Vector2 parseLocalCoord2(String s) {
        if (s == null) throw new IllegalArgumentException("Input is null");
        String[] parts = s.split("/");
        if (parts.length != 2) throw new IllegalArgumentException("Invalid local 2D coordinate: " + s);
        return vector2(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
    public static String toStringLocalCoord3(Vector3Int v) {
        return v.getX() + "/" + v.getY() + "/" + v.getZ();
    }
    public static String toStringLocalCoord2(Vector2 v) {
        return ((int)v.getX()) + "/" + ((int)v.getZ());
    }

    // --- Size (sizex x sizez) ---
    public static Vector2 parseSize(String s) {
        if (s == null) throw new IllegalArgumentException("Input is null");
        String[] parts = s.split("x");
        if (parts.length != 2) throw new IllegalArgumentException("Invalid size: " + s);
        return vector2(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
    }
    public static String toStringSize(Vector2 v) {
        return v.getX() + "x" + v.getZ();
    }

    // --- Area (Koordinate+Size) ---
    public static Area parseArea(String s) {
        if (s == null) throw new IllegalArgumentException("Input is null");
        String[] parts = s.split("\\+");
        if (parts.length != 2) throw new IllegalArgumentException("Invalid area: " + s);
        String coord = parts[0];
        String size = parts[1];
        Vector2 sz = parseSize(size);
        // Try to detect coordinate type
        if (coord.contains(",")) {
            if (coord.split(",").length == 3) {
                Vector3 pos = parseWorldCoord(coord);
                return area((int)pos.getX(), (int)pos.getY(), (int)pos.getZ(), (int)sz.getX(), 255, (int)sz.getZ());
            } else {
                Vector2 pos = parseFlatWorldCoord(coord);
                return area((int)pos.getX(), (int)pos.getZ(), (int)sz.getX(), (int)sz.getZ());
            }
        } else if (coord.contains(":")) {
            int[] c = parseChunkCoord(coord);
            return area(c[0], c[1], (int)sz.getX(), (int)sz.getZ());
        } else if (coord.contains(";")) {
            HexVector2 h = parseHexCoord(coord);
            return Area.builder().position(vector3(h.getQ(), 0, h.getR())).size(vector3((int)sz.getX(), 255, (int)sz.getZ())).build();
        } else if (coord.contains("/")) {
            if (coord.split("/").length == 3) {
                Vector3Int pos = parseLocalCoord3(coord);
                return Area.builder().position(vector3(pos.getX(), pos.getY(), pos.getZ())).size(vector3((int)sz.getX(), 255, (int)sz.getZ())).build();
            } else {
                Vector2 pos = parseLocalCoord2(coord);
                return Area.builder().position(vector3((int)pos.getX(), 0, (int)pos.getZ())).size(vector3((int)sz.getX(), 255, (int)sz.getZ())).build();
            }
        } else {
            throw new IllegalArgumentException("Unknown area coordinate type: " + coord);
        }
    }
    public static String toStringArea(Area a) {
        Vector3Int pos = a.getPosition();
        Vector3Int size = a.getSize();
        return toStringWorldCoord(pos) + "+" + toStringSize(vector2(size.getX(), size.getZ()));
    }

    public static Vector2Int vector2int(int x, int z) {
        return Vector2Int.builder()
                .x(x)
                .z(z)
                .build();
    }

    public static String toStringHexCoord(int q, int r) {
        return q + ";" + r;
    }
}
