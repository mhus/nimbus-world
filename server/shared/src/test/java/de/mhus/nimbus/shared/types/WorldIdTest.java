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
            assertTrue(WorldId.validate("region1:world1:zone1:instance1"));
            assertTrue(WorldId.validate("region1:world1::instance1"));
        }

        @Test
        void validate_ValidFullId_ShouldReturnTrue() {
            assertTrue(WorldId.validate("region1:world1::"));
        }

        @Test
        void validate_OldBangSyntax_ShouldReturnFalse() {
            assertFalse(WorldId.validate("region1:world1!instance1"));
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
            assertEquals("", worldId.getZone());
            assertEquals("", worldId.getInstance());
        }

        @Test
        void worldIdWithZone_ShouldParseCorrectly() {
            WorldId worldId = WorldId.of("region1:world1:zone1").orElseThrow();

            assertEquals("region1", worldId.getRegionId());
            assertEquals("world1", worldId.getWorldName());
            assertEquals("zone1", worldId.getZone());
            assertEquals("", worldId.getInstance());
        }

        @Test
        void worldIdWithZoneAndInstance_ShouldParseCorrectly() {
            WorldId worldId = WorldId.of("region1:world1:zone1:instance1").orElseThrow();

            assertEquals("region1", worldId.getRegionId());
            assertEquals("world1", worldId.getWorldName());
            assertEquals("zone1", worldId.getZone());
            assertEquals("instance1", worldId.getInstance());
        }

        @Test
        void worldIdWithEmptyZoneAndInstance_ShouldParseCorrectly() {
            WorldId worldId = WorldId.of("region1:world1::instance1").orElseThrow();

            assertEquals("region1", worldId.getRegionId());
            assertEquals("world1", worldId.getWorldName());
            assertEquals("", worldId.getZone());
            assertEquals("instance1", worldId.getInstance());
        }

        @Test
        void collectionId_ShouldParseCorrectly() {
            WorldId worldId = WorldId.of("@collection1:collectinId").orElseThrow();

            assertEquals("@collection1", worldId.getRegionId());
            assertEquals("collectinId", worldId.getWorldName());
            assertEquals("", worldId.getZone());
            assertEquals("", worldId.getInstance());
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
            WorldId worldId = WorldId.of("region1:world1::instance1").orElseThrow();
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
            WorldId worldId = WorldId.of("region1:world1::instance1").orElseThrow();
            assertTrue(worldId.isInstance());
        }

        @Test
        void isInstance_WithoutInstance_ShouldReturnFalse() {
            WorldId worldId = WorldId.of("region1:world1").orElseThrow();
            assertFalse(worldId.isInstance());
        }

        @Test
        void isInstanceOrZone_WithZone_ShouldReturnTrue() {
            WorldId worldId = WorldId.of("region1:world1:zone1").orElseThrow();
            assertTrue(worldId.isInstanceOrZone());
        }

        @Test
        void isInstanceOrZone_WithInstance_ShouldReturnTrue() {
            WorldId worldId = WorldId.of("region1:world1::instance1").orElseThrow();
            assertTrue(worldId.isInstanceOrZone());
        }

        @Test
        void isInstanceOrZone_WithMainWorld_ShouldReturnFalse() {
            WorldId worldId = WorldId.of("region1:world1").orElseThrow();
            assertFalse(worldId.isInstanceOrZone());
        }

        @Test
        void isMain_WithFullIdEmptyParts_ShouldReturnTrue() {
            WorldId worldId = WorldId.of("region1:world1::").orElseThrow();
            assertTrue(worldId.isMain());
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toString_ShouldReturnOriginalId() {
            String originalId = "region1:world1:zone1:instance1";
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
            WorldId worldId = WorldId.of("region1:world1:zone1:instance1").orElseThrow();

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
    class BaseWorldTests {

        @Test
        void isBase_WithMainWorld_ShouldReturnTrue() {
            WorldId worldId = WorldId.of("region1:world1").orElseThrow();
            assertTrue(worldId.isBase());
        }

        @Test
        void isBase_WithZoneWorld_ShouldReturnTrue() {
            WorldId worldId = WorldId.of("region1:world1:zone1").orElseThrow();
            assertTrue(worldId.isBase());
        }

        @Test
        void isBase_WithInstance_ShouldReturnFalse() {
            WorldId worldId = WorldId.of("region1:world1::instance1").orElseThrow();
            assertFalse(worldId.isBase());
        }

        @Test
        void isBase_WithZoneAndInstance_ShouldReturnFalse() {
            WorldId worldId = WorldId.of("region1:world1:zone1:instance1").orElseThrow();
            assertFalse(worldId.isBase());
        }

        @Test
        void toBaseWorldId_FromInstance_ShouldStripInstance() {
            WorldId worldId = WorldId.of("region1:world1::instance1").orElseThrow();
            assertEquals("region1:world1", worldId.toBaseWorldId().getId());
        }

        @Test
        void toBaseWorldId_FromZoneInstance_ShouldKeepZone() {
            WorldId worldId = WorldId.of("region1:world1:zone1:instance1").orElseThrow();
            assertEquals("region1:world1:zone1", worldId.toBaseWorldId().getId());
        }

        @Test
        void toBaseWorldId_FromBaseWorld_ShouldReturnSame() {
            WorldId worldId = WorldId.of("region1:world1").orElseThrow();
            assertSame(worldId, worldId.toBaseWorldId());
        }

        @Test
        void toBaseWorldId_FromZoneWorld_ShouldReturnSame() {
            WorldId worldId = WorldId.of("region1:world1:zone1").orElseThrow();
            assertSame(worldId, worldId.toBaseWorldId());
        }

        @Test
        void toMainWorld_FromZone_ShouldStripZone() {
            WorldId worldId = WorldId.of("region1:world1:zone1").orElseThrow();
            assertEquals("region1:world1", worldId.toMainWorld().getId());
        }

        @Test
        void toMainWorld_FromZoneInstance_ShouldStripZoneAndInstance() {
            WorldId worldId = WorldId.of("region1:world1:zone1:instance1").orElseThrow();
            assertEquals("region1:world1", worldId.toMainWorld().getId());
        }

        @Test
        void toMainWorld_FromMainWorld_ShouldReturnSameValue() {
            WorldId worldId = WorldId.of("region1:world1").orElseThrow();
            assertEquals("region1:world1", worldId.toMainWorld().getId());
        }
    }

    @Nested
    class ToWorldWithInstanceTests {

        @Test
        void toWorldWithInstance_FromMainWorld_ShouldAddInstance() {
            WorldId worldId = WorldId.of("region1:world1").orElseThrow();
            WorldId result = worldId.toWorldWithInstance("inst1");
            assertEquals("region1:world1::inst1", result.getId());
            assertTrue(result.isInstance());
            assertFalse(result.isZone());
        }

        @Test
        void toWorldWithInstance_FromZoneWorld_ShouldKeepZone() {
            WorldId worldId = WorldId.of("region1:world1:zone1").orElseThrow();
            WorldId result = worldId.toWorldWithInstance("inst1");
            assertEquals("region1:world1:zone1:inst1", result.getId());
            assertTrue(result.isInstance());
            assertTrue(result.isZone());
        }

        @Test
        void toWorldWithInstance_FromExistingInstance_ShouldReplaceInstance() {
            WorldId worldId = WorldId.of("region1:world1:zone1:old").orElseThrow();
            WorldId result = worldId.toWorldWithInstance("new");
            assertEquals("region1:world1:zone1:new", result.getId());
        }

        @Test
        void toWorldWithInstance_WithNull_ShouldThrow() {
            WorldId worldId = WorldId.of("region1:world1").orElseThrow();
            assertThrows(IllegalArgumentException.class, () -> worldId.toWorldWithInstance(null));
        }

        @Test
        void toWorldWithInstance_WithBlank_ShouldThrow() {
            WorldId worldId = WorldId.of("region1:world1").orElseThrow();
            assertThrows(IllegalArgumentException.class, () -> worldId.toWorldWithInstance("  "));
        }
    }

    @Nested
    class EditorInstanceTests {

        @Test
        void isEditorInstance_WithXPrefix_ShouldReturnTrue() {
            WorldId worldId = WorldId.of("region1:world1::x0").orElseThrow();
            assertTrue(worldId.isEditorInstance());
        }

        @Test
        void isEditorInstance_WithXAndEpoch_ShouldReturnTrue() {
            WorldId worldId = WorldId.of("region1:world1::x2").orElseThrow();
            assertTrue(worldId.isEditorInstance());
        }

        @Test
        void isEditorInstance_WithRegularInstance_ShouldReturnFalse() {
            WorldId worldId = WorldId.of("region1:world1::abc1").orElseThrow();
            assertFalse(worldId.isEditorInstance());
        }

        @Test
        void isEditorInstance_WithBaseWorld_ShouldReturnFalse() {
            WorldId worldId = WorldId.of("region1:world1").orElseThrow();
            assertFalse(worldId.isEditorInstance());
        }

        @Test
        void isEditorInstance_WithZoneAndXPrefix_ShouldReturnTrue() {
            WorldId worldId = WorldId.of("region1:world1:zone1:x3").orElseThrow();
            assertTrue(worldId.isEditorInstance());
        }

        @Test
        void getEditorEpoch_ShouldReturnEpochNumber() {
            WorldId worldId = WorldId.of("region1:world1::x2").orElseThrow();
            assertEquals(2, worldId.getEditorEpoch());
        }

        @Test
        void getEditorEpoch_WithZero_ShouldReturnZero() {
            WorldId worldId = WorldId.of("region1:world1::x0").orElseThrow();
            assertEquals(0, worldId.getEditorEpoch());
        }

        @Test
        void getEditorEpoch_WithLargeNumber_ShouldReturn() {
            WorldId worldId = WorldId.of("region1:world1::x42").orElseThrow();
            assertEquals(42, worldId.getEditorEpoch());
        }

        @Test
        void editorInstance_ToBaseWorldId_ShouldStripInstance() {
            WorldId worldId = WorldId.of("region1:world1::x2").orElseThrow();
            assertEquals("region1:world1", worldId.toBaseWorldId().getId());
            assertFalse(worldId.toBaseWorldId().isEditorInstance());
        }
    }

    @Nested
    class EqualsTests {

        @Test
        void equals_SameFullId_DifferentFormat_ShouldBeEqual() {
            WorldId a = WorldId.of("region1:world1").orElseThrow();
            WorldId b = WorldId.of("region1:world1::").orElseThrow();
            assertEquals(a, b);
        }

        @Test
        void equals_DifferentWorlds_ShouldNotBeEqual() {
            WorldId a = WorldId.of("region1:world1").orElseThrow();
            WorldId b = WorldId.of("region1:world2").orElseThrow();
            assertNotEquals(a, b);
        }

        @Test
        void compareTo_SameFullId_DifferentFormat_ShouldBeZero() {
            WorldId a = WorldId.of("region1:world1").orElseThrow();
            WorldId b = WorldId.of("region1:world1::").orElseThrow();
            assertEquals(0, a.compareTo(b));
        }
    }

    @Nested
    class FullIdAndNormalizationTests {

        @Test
        void getFullId_FromBasicId_ShouldReturnAllParts() {
            WorldId worldId = WorldId.of("region1:world1").orElseThrow();
            assertEquals("region1:world1::", worldId.getFullId());
        }

        @Test
        void getFullId_FromZoneId_ShouldReturnAllParts() {
            WorldId worldId = WorldId.of("region1:world1:zone1").orElseThrow();
            assertEquals("region1:world1:zone1:", worldId.getFullId());
        }

        @Test
        void getFullId_FromFullId_ShouldReturnSame() {
            WorldId worldId = WorldId.of("region1:world1:zone1:instance1").orElseThrow();
            assertEquals("region1:world1:zone1:instance1", worldId.getFullId());
        }

        @Test
        void getFullId_FromEmptyZoneInstance_ShouldReturnAllParts() {
            WorldId worldId = WorldId.of("region1:world1::instance1").orElseThrow();
            assertEquals("region1:world1::instance1", worldId.getFullId());
        }

        @Test
        void validate_FullIdWithEmptyZoneAndInstance_ShouldReturnTrue() {
            assertTrue(WorldId.validate("region1:world1::"));
        }

        @Test
        void getId_NormalizesTrailingColons() {
            WorldId worldId = WorldId.of("region1:world1::").orElseThrow();
            assertEquals("region1:world1", worldId.getId());
        }

        @Test
        void getId_NormalizesTrailingColonAfterZone() {
            WorldId worldId = WorldId.of("region1:world1:zone1:").orElseThrow();
            assertEquals("region1:world1:zone1", worldId.getId());
        }

        @Test
        void getId_KeepsInstanceWithEmptyZone() {
            WorldId worldId = WorldId.of("region1:world1::instance1").orElseThrow();
            assertEquals("region1:world1::instance1", worldId.getId());
        }

        @Test
        void normalizedId_WithEmptyZoneAndInstance_ShouldBeMain() {
            WorldId worldId = WorldId.of("region1:world1::").orElseThrow();
            assertTrue(worldId.isMain());
            assertFalse(worldId.isInstance());
            assertFalse(worldId.isZone());
        }
    }
}
