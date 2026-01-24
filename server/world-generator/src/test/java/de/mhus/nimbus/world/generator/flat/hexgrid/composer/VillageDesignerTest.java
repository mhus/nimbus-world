package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class VillageDesignerTest {

    @Test
    public void testLoadHamletTemplate() {
        VillageTemplate hamlet = VillageTemplateLoader.load("hamlet-medieval");

        assertNotNull(hamlet);
        assertEquals("hamlet-medieval", hamlet.getName());
        assertEquals(VillageSize.HAMLET, hamlet.getSize());
        assertEquals(VillageStyle.MEDIEVAL, hamlet.getStyle());
        assertNotNull(hamlet.getPlaza());
        assertNotNull(hamlet.getBuildings());
        assertTrue(hamlet.getBuildings().size() > 0);

        log.info("Loaded hamlet template: {} buildings", hamlet.getBuildings().size());
    }

    @Test
    public void testDesignHamlet() {
        VillageTemplate template = VillageTemplateLoader.load("hamlet-medieval");
        VillageDesigner designer = new VillageDesigner();

        VillageDesignResult result = designer.design(template, 95);

        assertNotNull(result);
        assertNotNull(result.getLayout());
        assertNotNull(result.getGridConfigs());
        assertEquals(1, result.getGridConfigs().size()); // Hamlet = 1 Grid

        // Check HexGridConfig
        HexGridConfig config = result.getGridConfigs().values().iterator().next();
        assertNotNull(config);
        assertNotNull(config.getGridPosition());
        assertEquals(95, config.getBaseLevel());

        // Check village parameter
        String villageParam = config.toVillageParameter();
        assertNotNull(villageParam);
        assertTrue(villageParam.contains("plots"));
        log.info("Village parameter: {}", villageParam);

        // Check road parameter
        String roadParam = config.toRoadParameter();
        assertNotNull(roadParam);
        log.info("Road parameter: {}", roadParam);
    }

    @Test
    public void testDesign2x1Village() {
        VillageTemplate template = VillageTemplateLoader.load("village-2x1-medieval");
        VillageDesigner designer = new VillageDesigner();

        VillageDesignResult result = designer.design(template, 95);

        assertNotNull(result);
        assertEquals(2, result.getGridConfigs().size()); // 2x1 = 2 Grids

        // Print all grid configs
        for (Map.Entry<HexVector2, HexGridConfig> entry : result.getGridConfigs().entrySet()) {
            HexVector2 pos = entry.getKey();
            HexGridConfig config = entry.getValue();

            log.info("\n=== Grid [{},{}] ===", pos.getQ(), pos.getR());
            log.info("village={}", config.toVillageParameter());
            log.info("road={}", config.toRoadParameter());
        }
    }

    @Test
    public void testDesign5CrossTown() {
        VillageTemplate template = VillageTemplateLoader.load("town-5cross-medieval");
        VillageDesigner designer = new VillageDesigner();

        VillageDesignResult result = designer.design(template, 95);

        assertNotNull(result);
        assertEquals(5, result.getGridConfigs().size()); // 5-cross = 5 Grids

        // Verify center grid exists
        HexVector2 center = HexVector2.builder().q(1).r(1).build();
        HexGridConfig centerConfig = result.getGridConfigs().get(center);
        assertNotNull(centerConfig, "Center grid should exist");

        // Check for boundary roads
        int totalBoundaryRoads = 0;
        for (HexGridConfig config : result.getGridConfigs().values()) {
            totalBoundaryRoads += config.getBoundaryRoads() != null ? config.getBoundaryRoads().size() : 0;
        }

        log.info("Total boundary roads: {}", totalBoundaryRoads);
    }

    @Test
    public void testConvertToVillage() {
        VillageTemplate template = VillageTemplateLoader.load("hamlet-medieval");
        VillageDesigner designer = new VillageDesigner();
        VillageDesignResult result = designer.design(template, 95);

        Village village = result.toVillage("test-hamlet");

        assertNotNull(village);
        assertEquals("test-hamlet", village.getName());
        assertNotNull(village.getBuildings());
        assertTrue(village.getBuildings().size() > 0);

        log.info("Village: {} buildings, {} streets",
            village.getBuildings().size(),
            village.getStreets() != null ? village.getStreets().size() : 0);
    }

    @Test
    public void testJsonSerialization() {
        VillageTemplate original = VillageTemplateLoader.load("hamlet-medieval");

        // Serialize to JSON
        String json = original.toJson();
        assertNotNull(json);
        assertTrue(json.contains("hamlet-medieval"));

        log.info("Serialized template length: {} chars", json.length());

        // Deserialize back
        VillageTemplate restored = VillageTemplate.fromJson(json);
        assertNotNull(restored);
        assertEquals(original.getName(), restored.getName());
        assertEquals(original.getSize(), restored.getSize());
        assertEquals(original.getBuildings().size(), restored.getBuildings().size());
    }

    @Test
    public void testListTemplates() {
        var templates = VillageTemplateLoader.listTemplates();

        assertNotNull(templates);
        assertTrue(templates.size() > 0);
        assertTrue(templates.contains("hamlet-medieval"));
        assertTrue(templates.contains("town-5cross-medieval"));

        log.info("Available templates: {}", templates);
    }
}
