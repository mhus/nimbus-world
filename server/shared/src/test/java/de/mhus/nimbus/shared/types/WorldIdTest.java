package de.mhus.nimbus.shared.types;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WorldId class.
 */
class WorldIdTest {

    @Nested
    class ValidationTests {

        @Test
        void validate_ValidBasicWorldId_ShouldReturnTrue() {
            assertTrue(WorldId.validate("region1:world1"));
        }

        @Test
        void validate_ValidWorldIdWithZone_ShouldReturnTrue() {
            assertTrue(WorldId.validate("region1:world1:zone1"));
        }

        @Test
        void validate_ValidWorldIdWithInstance_ShouldReturnTrue() {
            assertTrue(WorldId.validate("region1:world1!instance1"));
        }

        @Test
        void validate_BranchSyntax_ShouldReturnFalse() {
            assertFalse(WorldId.validate("region1:world1@branch1"));
        }

        @Test
        void validate_ValidCollectionId_ShouldReturnTrue() {
            assertTrue(WorldId.validate("@collection1:collectinId"));
        }

        @Test
        void validate_ValidWithUnderscoresAndDashes_ShouldReturnTrue() {
            assertTrue(WorldId.validate("region_1:world-name"));
            assertTrue(WorldId.validate("region-test:world_test"));
        }

        @Test
        void validate_NullId_ShouldReturnFalse() {
            assertFalse(WorldId.validate(null));
        }

        @Test
        void validate_EmptyId_ShouldReturnFalse() {
            assertFalse(WorldId.validate(""));
        }

        @Test
        void validate_BlankId_ShouldReturnFalse() {
            assertFalse(WorldId.validate("   "));
        }

        @Test
        void validate_TooShortId_ShouldReturnFalse() {
            assertFalse(WorldId.validate("a:"));
            assertFalse(WorldId.validate("ab"));
        }

        @Test
        void validate_InvalidCharacters_ShouldReturnFalse() {
            assertFalse(WorldId.validate("region#:world1"));
            assertFalse(WorldId.validate("region1:world$"));
            assertFalse(WorldId.validate("region 1:world1"));
            assertFalse(WorldId.validate("region1:world.1"));
        }

        @Test
        void validate_MissingParts_ShouldReturnFalse() {
            assertFalse(WorldId.validate("region1:"));
            assertFalse(WorldId.validate(":world1"));
            assertFalse(WorldId.validate("region1"));
        }

        @Test
        void validate_InvalidCollectionFormat_ShouldReturnFalse() {
            assertFalse(WorldId.validate("@collection1"));
            assertFalse(WorldId.validate("@:collectinId"));
            assertFalse(WorldId.validate("@collection1:"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz123:world", // 65 chars
            "region:abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz123" // 65 chars
        })
        void validate_TooLongParts_ShouldReturnFalse(String id) {
            assertFalse(WorldId.validate(id));
        }
    }

    @Nested
    class FactoryMethodTests {

        @Test
        void of_ValidId_ShouldReturnOptionalWithWorldId() {
            Optional<WorldId> result = WorldId.of("region1:world1");
            assertTrue(result.isPresent());
            assertEquals("region1:world1", result.get().getId());
        }

        @Test
        void of_InvalidId_ShouldReturnEmptyOptional() {
            Optional<WorldId> result = WorldId.of("invalid");
            assertTrue(result.isEmpty());
        }

        @Test
        void of_NullId_ShouldReturnEmptyOptional() {
            Optional<WorldId> result = WorldId.of(null);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class ParsingTests {

        @Test
        void basicWorldId_ShouldParseCorrectly() {
            WorldId worldId = WorldId.of("region1:world1").orElseThrow();

            assertEquals("region1", worldId.getRegionId());
            assertEquals("world1", worldId.getWorldName());
            assertNull(worldId.getZone());
            assertNull(worldId.getInstance());
        }

        @Test
        void worldIdWithZone_ShouldParseCorrectly() {
            WorldId worldId = WorldId.of("region1:world1:zone1").orElseThrow();

            assertEquals("region1", worldId.getRegionId());
            assertEquals("world1", worldId.getWorldName());
            assertEquals("zone1", worldId.getZone());
            assertNull(worldId.getInstance());
        }

        @Test
        void worldIdWithInstance_ShouldParseCorrectly() {
            WorldId worldId = WorldId.of("region1:world1!instance1").orElseThrow();

            assertEquals("region1", worldId.getRegionId());
            assertEquals("world1", worldId.getWorldName());
            assertNull(worldId.getZone());
            assertEquals("instance1", worldId.getInstance());
        }

        @Test
        void collectionId_ShouldParseCorrectly() {
            WorldId worldId = WorldId.of("@collection1:collectinId").orElseThrow();

            assertEquals("@collection1", worldId.getRegionId());
            assertEquals("collectinId", worldId.getWorldName());
            assertNull(worldId.getZone());
            assertNull(worldId.getInstance());
        }
    }

    @Nested
    class TypeCheckTests {

        @Test
        void isCollection_WithCollectionId_ShouldReturnTrue() {
            WorldId worldId = WorldId.of("@collection1:collectinId").orElseThrow();
            assertTrue(worldId.isCollection());
        }

        @Test
        void isCollection_WithRegularId_ShouldReturnFalse() {
            WorldId worldId = WorldId.of("region1:world1").orElseThrow();
            assertFalse(worldId.isCollection());
        }

        @Test
        void isMain_WithBasicId_ShouldReturnTrue() {
            WorldId worldId = WorldId.of("region1:world1").orElseThrow();
            assertTrue(worldId.isMain());
        }

        @Test
        void isMain_WithZone_ShouldReturnFalse() {
            WorldId worldId = WorldId.of("region1:world1:zone1").orElseThrow();
            assertFalse(worldId.isMain());
        }

        @Test
        void isMain_WithInstance_ShouldReturnFalse() {
            WorldId worldId = WorldId.of("region1:world1!instance1").orElseThrow();
            assertFalse(worldId.isMain());
        }

        @Test
        void isZone_WithZone_ShouldReturnTrue() {
            WorldId worldId = WorldId.of("region1:world1:zone1").orElseThrow();
            assertTrue(worldId.isZone());
        }

        @Test
        void isZone_WithoutZone_ShouldReturnFalse() {
            WorldId worldId = WorldId.of("region1:world1").orElseThrow();
            assertFalse(worldId.isZone());
        }

        @Test
        void isInstance_WithInstance_ShouldReturnTrue() {
            WorldId worldId = WorldId.of("region1:world1!instance1").orElseThrow();
            assertTrue(worldId.isInstance());
        }

        @Test
        void isInstance_WithoutInstance_ShouldReturnFalse() {
            WorldId worldId = WorldId.of("region1:world1").orElseThrow();
            assertFalse(worldId.isInstance());
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toString_ShouldReturnOriginalId() {
            String originalId = "region1:world1:zone1!instance1";
            WorldId worldId = WorldId.of(originalId).orElseThrow();
            assertEquals(originalId, worldId.toString());
        }

        @Test
        void toString_CollectionId_ShouldReturnOriginalId() {
            String originalId = "@collection1:collectinId";
            WorldId worldId = WorldId.of(originalId).orElseThrow();
            assertEquals(originalId, worldId.toString());
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void multipleParsingCalls_ShouldNotAffectResult() {
            WorldId worldId = WorldId.of("region1:world1:zone1!instance1").orElseThrow();

            // Call getters multiple times to test caching
            assertEquals("region1", worldId.getRegionId());
            assertEquals("region1", worldId.getRegionId());
            assertEquals("world1", worldId.getWorldName());
            assertEquals("world1", worldId.getWorldName());
            assertEquals("zone1", worldId.getZone());
            assertEquals("zone1", worldId.getZone());
            assertEquals("instance1", worldId.getInstance());
            assertEquals("instance1", worldId.getInstance());
        }

        @Test
        void maxLengthParts_ShouldBeValid() {
            String maxLengthPart = "a".repeat(64);
            String validId = maxLengthPart + ":" + maxLengthPart;
            assertTrue(WorldId.validate(validId));

            WorldId worldId = WorldId.of(validId).orElseThrow();
            assertEquals(maxLengthPart, worldId.getRegionId());
            assertEquals(maxLengthPart, worldId.getWorldName());
        }

        @Test
        void minLengthParts_ShouldBeValid() {
            String validId = "a:b";
            assertTrue(WorldId.validate(validId));

            WorldId worldId = WorldId.of(validId).orElseThrow();
            assertEquals("a", worldId.getRegionId());
            assertEquals("b", worldId.getWorldName());
        }
    }

    @Nested
    class BugFixTests {

        @Test
        void parseId_WithAllComponents_ShouldParseCorrectly() {
            // Test that all components work together correctly
            WorldId worldId = WorldId.of("region1:world1:zone1!instance1").orElseThrow();
            assertEquals("region1", worldId.getRegionId());
            assertEquals("world1", worldId.getWorldName());
            assertEquals("zone1", worldId.getZone());
            assertEquals("instance1", worldId.getInstance());
        }
    }
}
