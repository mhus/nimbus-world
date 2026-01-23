package de.mhus.nimbus.world.shared.util;

/**
 * FastNoiseLite - Simple noise generation for terrain.
 * Simplified implementation for Perlin-style noise generation.
 */
public class FastNoiseLite {

    private int seed;
    private float frequency = 0.01f;

    public FastNoiseLite() {
        this.seed = 1337;
    }

    public FastNoiseLite(int seed) {
        this.seed = seed;
    }

    public void SetSeed(int seed) {
        this.seed = seed;
    }

    public void SetFrequency(float frequency) {
        this.frequency = frequency;
    }

    /**
     * Get 2D noise value at coordinates.
     * Returns value between -1.0 and 1.0.
     */
    public float GetNoise(float x, float y) {
        x *= frequency;
        y *= frequency;

        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int x1 = x0 + 1;
        int y1 = y0 + 1;

        float sx = x - x0;
        float sy = y - y0;

        float n0 = gradientDot(hash(x0, y0), x - x0, y - y0);
        float n1 = gradientDot(hash(x1, y0), x - x1, y - y0);
        float ix0 = lerp(n0, n1, fade(sx));

        n0 = gradientDot(hash(x0, y1), x - x0, y - y1);
        n1 = gradientDot(hash(x1, y1), x - x1, y - y1);
        float ix1 = lerp(n0, n1, fade(sx));

        return lerp(ix0, ix1, fade(sy));
    }

    private int hash(int x, int y) {
        int n = seed;
        n ^= x * 1619;
        n ^= y * 31337;
        n = n * n * n * 60493;
        n = (n >> 13) ^ n;
        return n;
    }

    private float gradientDot(int hash, float x, float y) {
        int h = hash & 7;
        float u = h < 4 ? x : y;
        float v = h < 4 ? y : x;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    private float fade(float t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private float lerp(float a, float b, float t) {
        return a + t * (b - a);
    }
}
