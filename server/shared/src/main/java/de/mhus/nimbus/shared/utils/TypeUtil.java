package de.mhus.nimbus.shared.utils;

import de.mhus.nimbus.generated.types.Area;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.generated.types.Vector2;
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

}
