package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FlowType Rich Enum pattern
 */
class FlowTypeTest {

    @Test
    void testCreateInstance_Road_ReturnsRoad() {
        Flow flow = FlowType.ROAD.createInstance();

        assertNotNull(flow);
        assertInstanceOf(Road.class, flow);
        assertEquals(FlowType.ROAD, flow.getType());

        Road road = (Road) flow;
        assertEquals(95, road.getLevel());
        assertEquals("cobblestone", road.getRoadType());
        assertEquals(4, road.getEffectiveWidthBlocks());
    }

    @Test
    void testCreateInstance_River_ReturnsRiver() {
        Flow flow = FlowType.RIVER.createInstance();

        assertNotNull(flow);
        assertInstanceOf(River.class, flow);
        assertEquals(FlowType.RIVER, flow.getType());

        River river = (River) flow;
        assertEquals(3, river.getDepth());
        assertEquals(85, river.getLevel());
        assertEquals(4, river.getEffectiveWidthBlocks());
    }

    @Test
    void testCreateInstance_Wall_ReturnsWall() {
        Flow flow = FlowType.WALL.createInstance();

        assertNotNull(flow);
        assertInstanceOf(Wall.class, flow);
        assertEquals(FlowType.WALL, flow.getType());

        Wall wall = (Wall) flow;
        assertEquals(10, wall.getHeight());
        assertEquals("stone", wall.getMaterial());
        assertEquals(2, wall.getEffectiveWidthBlocks());
    }

    @Test
    void testDefaultParameters_Road() {
        var defaults = FlowType.ROAD.getDefaultParameters();

        assertNotNull(defaults);
        assertEquals("95", defaults.get("default_level"));
        assertEquals("cobblestone", defaults.get("default_roadType"));
        assertEquals("4", defaults.get("default_width"));
    }

    @Test
    void testDefaultParameters_River() {
        var defaults = FlowType.RIVER.getDefaultParameters();

        assertNotNull(defaults);
        assertEquals("3", defaults.get("default_depth"));
        assertEquals("85", defaults.get("default_level"));
        assertEquals("4", defaults.get("default_width"));
    }

    @Test
    void testDefaultParameters_Wall() {
        var defaults = FlowType.WALL.getDefaultParameters();

        assertNotNull(defaults);
        assertEquals("10", defaults.get("default_height"));
        assertEquals("stone", defaults.get("default_material"));
        assertEquals("2", defaults.get("default_width"));
    }

    @Test
    void testDefaultParameters_CanBeOverridden() {
        Road road = (Road) FlowType.ROAD.createInstance();

        // Default level is 95
        assertEquals(95, road.getLevel());

        // Can be overridden
        road.setLevel(100);
        assertEquals(100, road.getLevel());
    }

    @Test
    void testApplyDefaults_OnlyAppliesWhenNull() {
        // Create road with pre-set level
        Road road = new Road();
        road.setType(FlowType.ROAD);
        road.setLevel(80);  // Pre-set value

        // Apply defaults
        road.applyDefaults();

        // Pre-set value should NOT be overridden
        assertEquals(80, road.getLevel());

        // But null values should get defaults
        assertEquals("cobblestone", road.getRoadType());
    }

    @Test
    void testApplyDefaults_OnManuallyCreatedFlows() {
        // Manually create Road
        Road road = new Road();
        road.setType(FlowType.ROAD);
        road.setStartPointId("biome1");
        road.setEndPointId("biome2");

        // Before apply defaults
        assertNull(road.getLevel());
        assertNull(road.getRoadType());
        assertNull(road.getWidthBlocks());

        // Apply defaults
        road.applyDefaults();

        // After apply defaults
        assertEquals(95, road.getLevel());
        assertEquals("cobblestone", road.getRoadType());
        assertEquals(4, road.getWidthBlocks());
    }

    @Test
    void testApplyDefaults_River() {
        River river = new River();
        river.setType(FlowType.RIVER);
        river.applyDefaults();

        assertEquals(3, river.getDepth());
        assertEquals(85, river.getLevel());
        assertEquals(4, river.getWidthBlocks());
    }

    @Test
    void testApplyDefaults_Wall() {
        Wall wall = new Wall();
        wall.setType(FlowType.WALL);
        wall.applyDefaults();

        assertEquals(10, wall.getHeight());
        assertEquals("stone", wall.getMaterial());
        assertEquals(2, wall.getWidthBlocks());
    }

    @Test
    void testGetFlowClass() {
        assertEquals(Road.class, FlowType.ROAD.getFlowClass());
        assertEquals(River.class, FlowType.RIVER.getFlowClass());
        assertEquals(Wall.class, FlowType.WALL.getFlowClass());
    }

    @Test
    void testFromString() {
        assertEquals(FlowType.ROAD, FlowType.fromString("road"));
        assertEquals(FlowType.ROAD, FlowType.fromString("ROAD"));
        assertEquals(FlowType.RIVER, FlowType.fromString("river"));
        assertEquals(FlowType.WALL, FlowType.fromString("wall"));
        assertNull(FlowType.fromString(null));
        assertThrows(IllegalArgumentException.class, () -> FlowType.fromString("invalid"));
    }

    @Test
    void testBuilderWithDefaults() {
        // Builder pattern still works, but doesn't auto-apply defaults
        Road road = Road.builder()
            .endPointId("biome2")
            .build();

        // Manually set parent fields and type
        road.setStartPointId("biome1");
        road.setType(FlowType.ROAD);
        road.applyDefaults();

        assertEquals("biome1", road.getStartPointId());
        assertEquals("biome2", road.getEndPointId());
        assertEquals(95, road.getLevel());
        assertEquals("cobblestone", road.getRoadType());
    }

    @Test
    void testDifferentFlowTypes_SameDefaults_DifferentClasses() {
        // All flows can have width, but different default values
        Road road = (Road) FlowType.ROAD.createInstance();
        River river = (River) FlowType.RIVER.createInstance();
        Wall wall = (Wall) FlowType.WALL.createInstance();

        assertEquals(4, road.getEffectiveWidthBlocks());
        assertEquals(4, river.getEffectiveWidthBlocks());
        assertEquals(2, wall.getEffectiveWidthBlocks());  // Walls are narrower

        // But each has different specific properties
        assertNotNull(road.getRoadType());
        assertNotNull(river.getDepth());
        assertNotNull(wall.getMaterial());
    }
}
