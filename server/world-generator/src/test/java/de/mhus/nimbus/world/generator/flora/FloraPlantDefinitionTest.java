package de.mhus.nimbus.world.generator.flora;

import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.generator.modelbuilder.FloraConstraints;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;

class FloraPlantDefinitionTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

    @Test
    void toConstraints_allFieldsSet() {
        FloraPlantDefinition plant = FloraPlantDefinition.builder()
                .name("test_plant")
                .model("tree")
                .land(true)
                .water(false)
                .sea(false)
                .emerse(true)
                .maxHeight(10)
                .minWater(2)
                .maxWater(8)
                .build();

        FloraConstraints constraints = plant.toConstraints();

        assertThat(constraints.land()).isTrue();
        assertThat(constraints.water()).isFalse();
        assertThat(constraints.sea()).isFalse();
        assertThat(constraints.emerse()).isTrue();
        assertThat(constraints.maxHeight()).hasValue(10);
        assertThat(constraints.minWater()).hasValue(2);
        assertThat(constraints.maxWater()).hasValue(8);
    }

    @Test
    void toConstraints_defaultValues() {
        FloraPlantDefinition plant = FloraPlantDefinition.builder()
                .name("default_plant")
                .model("stacked")
                .build();

        FloraConstraints constraints = plant.toConstraints();

        assertThat(constraints.land()).isFalse();
        assertThat(constraints.water()).isFalse();
        assertThat(constraints.sea()).isFalse();
        assertThat(constraints.emerse()).isFalse();
        assertThat(constraints.maxHeight()).isEmpty();
        assertThat(constraints.minWater()).isEmpty();
        assertThat(constraints.maxWater()).isEmpty();
    }

    @Test
    void toConstraints_onlyLand() {
        FloraPlantDefinition plant = FloraPlantDefinition.builder()
                .name("grass")
                .model("stacked")
                .land(true)
                .build();

        FloraConstraints constraints = plant.toConstraints();

        assertThat(constraints.land()).isTrue();
        assertThat(constraints.water()).isFalse();
        assertThat(constraints.sea()).isFalse();
        assertThat(constraints.fitsPosition(0, FloraCategory.LAND)).isTrue();
        assertThat(constraints.fitsPosition(5, FloraCategory.WATER)).isFalse();
        assertThat(constraints.fitsPosition(5, FloraCategory.SEA)).isFalse();
    }

    @Test
    void jackson_deserialization_fullPlant() throws Exception {
        String json = """
                {
                  "name": "birch_tree",
                  "model": "birch_tree",
                  "parameters": { "log": "m:birch_log", "leaves": "m:birch_leaves" },
                  "land": true,
                  "water": false,
                  "sea": false,
                  "weight": 3.0,
                  "maxHeight": 10
                }
                """;

        FloraPlantDefinition plant = objectMapper.readValue(json, FloraPlantDefinition.class);

        assertThat(plant.getName()).isEqualTo("birch_tree");
        assertThat(plant.getModel()).isEqualTo("birch_tree");
        assertThat(plant.getParameters()).containsEntry("log", "m:birch_log");
        assertThat(plant.getParameters()).containsEntry("leaves", "m:birch_leaves");
        assertThat(plant.isLand()).isTrue();
        assertThat(plant.isWater()).isFalse();
        assertThat(plant.isSea()).isFalse();
        assertThat(plant.getWeight()).isEqualTo(3.0);
        assertThat(plant.getMaxHeight()).isEqualTo(10);
    }

    @Test
    void jackson_deserialization_stackedPlant() throws Exception {
        String json = """
                {
                  "name": "tall_grass",
                  "model": "stacked",
                  "blocks": ["m:tall_grass"],
                  "land": true,
                  "weight": 10.0
                }
                """;

        FloraPlantDefinition plant = objectMapper.readValue(json, FloraPlantDefinition.class);

        assertThat(plant.getName()).isEqualTo("tall_grass");
        assertThat(plant.getModel()).isEqualTo("stacked");
        assertThat(plant.getBlocks()).containsExactly("m:tall_grass");
        assertThat(plant.isLand()).isTrue();
        assertThat(plant.getWeight()).isEqualTo(10.0);
        assertThat(plant.getClusterCount()).isNull();
        assertThat(plant.getClusterSpread()).isEqualTo(2);
    }

    @Test
    void jackson_deserialization_clusterPlant() throws Exception {
        String json = """
                {
                  "name": "mushroom_cluster",
                  "model": "stacked",
                  "blocks": ["m:mushroom"],
                  "land": true,
                  "weight": 2.0,
                  "clusterCount": 3,
                  "clusterSpread": 2
                }
                """;

        FloraPlantDefinition plant = objectMapper.readValue(json, FloraPlantDefinition.class);

        assertThat(plant.getClusterCount()).isEqualTo(3);
        assertThat(plant.getClusterSpread()).isEqualTo(2);
    }

    @Test
    void jackson_deserialization_defaults() throws Exception {
        String json = """
                {
                  "name": "minimal",
                  "model": "stacked"
                }
                """;

        FloraPlantDefinition plant = objectMapper.readValue(json, FloraPlantDefinition.class);

        assertThat(plant.isLand()).isFalse();
        assertThat(plant.isWater()).isFalse();
        assertThat(plant.isSea()).isFalse();
        assertThat(plant.isEmerse()).isFalse();
        assertThat(plant.getWeight()).isEqualTo(1.0);
        assertThat(plant.getClusterSpread()).isEqualTo(2);
        assertThat(plant.getMaxHeight()).isNull();
        assertThat(plant.getMinWater()).isNull();
        assertThat(plant.getMaxWater()).isNull();
        assertThat(plant.getBlocks()).isNull();
        assertThat(plant.getParameters()).isNull();
    }

    @Test
    void jackson_deserialization_ignoresUnknownFields() throws Exception {
        String json = """
                {
                  "name": "test",
                  "model": "stacked",
                  "unknownField": "should be ignored"
                }
                """;

        FloraPlantDefinition plant = objectMapper.readValue(json, FloraPlantDefinition.class);
        assertThat(plant.getName()).isEqualTo("test");
    }

    @Test
    void jackson_deserialization_floraTypeDefinition() throws Exception {
        String json = """
                {
                  "plants": [
                    {
                      "name": "birch_tree",
                      "model": "birch_tree",
                      "land": true,
                      "weight": 3.0
                    },
                    {
                      "name": "tall_grass",
                      "model": "stacked",
                      "blocks": ["m:tall_grass"],
                      "land": true,
                      "weight": 10.0
                    }
                  ]
                }
                """;

        FloraTypeDefinition typeDef = objectMapper.readValue(json, FloraTypeDefinition.class);

        assertThat(typeDef.getPlants()).hasSize(2);
        assertThat(typeDef.getPlants().get(0).getName()).isEqualTo("birch_tree");
        assertThat(typeDef.getPlants().get(0).getWeight()).isEqualTo(3.0);
        assertThat(typeDef.getPlants().get(1).getName()).isEqualTo("tall_grass");
        assertThat(typeDef.getPlants().get(1).getBlocks()).containsExactly("m:tall_grass");
    }
}
