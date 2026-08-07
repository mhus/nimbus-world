package de.mhus.nimbus.world.generator;

import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import de.mhus.nimbus.world.generator.fauna.FaunaAnimalDefinition;
import de.mhus.nimbus.world.generator.flora.FloraPlantDefinition;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the {@code @Builder.Default} values of the definition classes against Jackson's creator
 * detection.
 * <p>
 * Jackson 3 picks a <b>public</b> multi-argument constructor as a properties-based creator even when
 * a no-args constructor exists (parameter names are available, the build compiles with
 * {@code -parameters}). Lombok's {@code @AllArgsConstructor} generates exactly such a constructor, so
 * deserialization bypassed the no-args constructor and every {@code @Builder.Default} came out as the
 * JVM zero value: flora weights 0.0 instead of 1.0, fauna amounts 0 instead of 1 (no animals spawn at
 * all), and {@code HexComposition.featureHexGridRegistry} null instead of an empty map, which crashed
 * the composite pipeline.
 * <p>
 * The fix is {@code @AllArgsConstructor(access = AccessLevel.PRIVATE)} — invisible to Jackson, still
 * usable by the generated builder. These tests fail if anyone makes such a constructor public again.
 */
class JacksonBuilderDefaultsTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void floraPlantKeepsItsDefaultsWhenDeserialized() {
        FloraPlantDefinition plant =
                mapper.readValue("{\"name\":\"minimal\",\"model\":\"stacked\"}", FloraPlantDefinition.class);

        assertThat(plant.getWeight()).isEqualTo(1.0);
        assertThat(plant.getClusterSpread()).isEqualTo(2);
    }

    /** amountMax=0 would mean the generator silently spawns no animals at all. */
    @Test
    void faunaAnimalKeepsItsDefaultsWhenDeserialized() {
        FaunaAnimalDefinition animal =
                mapper.readValue("{\"name\":\"deer\",\"model\":\"deer\"}", FaunaAnimalDefinition.class);

        assertThat(animal.getAmountMin()).isEqualTo(1);
        assertThat(animal.getAmountMax()).isEqualTo(1);
        assertThat(animal.getGroupsMin()).isEqualTo(1);
        assertThat(animal.getGroupsMax()).isEqualTo(1);
    }

    /** A null registry made getOrCreateFeatureHexGrid throw and aborted the whole composition. */
    @Test
    void hexCompositionKeepsItsRegistryWhenDeserialized() {
        HexComposition composition =
                mapper.readValue("{\"name\":\"c1\",\"worldId\":\"w:test\"}", HexComposition.class);

        assertThat(composition.getFeatureHexGridRegistry()).isNotNull().isEmpty();
        assertThat(composition.getVersion()).isEqualTo("1.0.0");
    }
}
