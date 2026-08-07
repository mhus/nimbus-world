package de.mhus.nimbus.world.shared;

import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WChunk;
import de.mhus.nimbus.world.shared.world.WWorld;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the MongoDB mapping of the entities whose all-args constructor was made private (so Jackson
 * stops using it as a creator and {@code @Builder.Default} values survive JSON deserialization).
 * <p>
 * Spring Data does its own object construction, independent of Jackson: it prefers the no-args
 * constructor and can use non-public ones anyway. This test proves that empirically instead of
 * relying on that assumption — reading a document that omits a field must still yield the declared
 * default, and reading one that carries a value must still yield that value.
 *
 * @see de.mhus.nimbus.world.shared.world.WChunk
 */
class MongoMappingDefaultsTest {

    private final MappingMongoConverter converter = converter();

    private static MappingMongoConverter converter() {
        // Same wiring Boot's MongoDataAutoConfiguration does: without the simple-type holder from
        // MongoCustomConversions the context would try to map java.time.Instant as an entity.
        MongoCustomConversions conversions = new MongoCustomConversions(List.of());
        MongoMappingContext context = new MongoMappingContext();
        context.setAutoIndexCreation(false);
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.afterPropertiesSet();
        MappingMongoConverter c = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        c.setCustomConversions(conversions);
        c.afterPropertiesSet();
        return c;
    }

    /** {@code enabled} defaults to true — losing it would read every stored world as disabled. */
    @Test
    void worldDefaultsSurviveAMissingField() {
        WWorld world = converter.read(WWorld.class, new Document("worldId", "w:test"));

        assertThat(world).isNotNull();
        assertThat(world.getWorldId()).isEqualTo("w:test");
        assertThat(world.isEnabled()).isTrue();
        assertThat(world.getOwner()).isNotNull();
    }

    @Test
    void storedValuesStillWin() {
        WWorld world = converter.read(WWorld.class,
                new Document("worldId", "w:test").append("enabled", false));

        assertThat(world.isEnabled()).isFalse();
    }

    @Test
    void chunkDefaultsSurviveAMissingField() {
        WChunk chunk = converter.read(WChunk.class, new Document("worldId", "w:test"));

        assertThat(chunk.getWorldId()).isEqualTo("w:test");
        assertThat(chunk.isCompressed()).isFalse();
        assertThat(chunk.getEpoches()).isNotNull();
    }

    /** Nested class whose all-args constructor was made private as well. */
    @Test
    void nestedMaterialDefinitionKeepsItsDefaults() {
        WFlat.MaterialDefinition material = converter.read(WFlat.MaterialDefinition.class,
                new Document("blockDef", "n:s@s:100"));

        assertThat(material.getBlockDef()).isEqualTo("n:s@s:100");
        assertThat(material.isBlockMapDelta())
                .isEqualTo(new WFlat.MaterialDefinition().isBlockMapDelta());
        assertThat(material.getBlockAtLevels()).isNotNull();
    }

}
