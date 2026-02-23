package de.mhus.nimbus.world.generator.flora;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FloraCategoryTest {

    @Test
    void land_whenNoWater() {
        // waterLevel == groundLevel means no water above ground
        assertThat(FloraCategory.determine(20, 20, 50)).isEqualTo(FloraCategory.LAND);
    }

    @Test
    void land_whenWaterBelowGround() {
        // waterLevel < groundLevel
        assertThat(FloraCategory.determine(30, 20, 50)).isEqualTo(FloraCategory.LAND);
    }

    @Test
    void sea_whenUnderwaterAtSeaLevel() {
        // waterLevel > groundLevel AND waterLevel == seaLevel -> SEA
        assertThat(FloraCategory.determine(10, 50, 50)).isEqualTo(FloraCategory.SEA);
    }

    @Test
    void sea_whenUnderwaterBelowSeaLevel() {
        // waterLevel > groundLevel AND waterLevel < seaLevel -> SEA
        assertThat(FloraCategory.determine(10, 40, 50)).isEqualTo(FloraCategory.SEA);
    }

    @Test
    void water_whenUnderwaterAboveSeaLevel() {
        // waterLevel > groundLevel AND waterLevel > seaLevel -> WATER (freshwater)
        assertThat(FloraCategory.determine(10, 60, 50)).isEqualTo(FloraCategory.WATER);
    }

    @Test
    void water_whenNoSeaLevel() {
        // waterLevel > groundLevel AND seaLevel == null -> WATER
        assertThat(FloraCategory.determine(10, 30, null)).isEqualTo(FloraCategory.WATER);
    }

    @Test
    void land_whenNoSeaLevelAndNoWater() {
        // waterLevel == groundLevel AND seaLevel == null -> LAND
        assertThat(FloraCategory.determine(20, 20, null)).isEqualTo(FloraCategory.LAND);
    }

    @Test
    void edgeCase_groundLevelZero() {
        assertThat(FloraCategory.determine(0, 0, 50)).isEqualTo(FloraCategory.LAND);
        assertThat(FloraCategory.determine(0, 10, 50)).isEqualTo(FloraCategory.SEA);
        assertThat(FloraCategory.determine(0, 60, 50)).isEqualTo(FloraCategory.WATER);
    }
}
