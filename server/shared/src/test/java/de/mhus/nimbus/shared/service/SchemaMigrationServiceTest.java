package de.mhus.nimbus.shared.service;

import de.mhus.nimbus.shared.persistence.SchemaMigrator;
import de.mhus.nimbus.shared.types.SchemaVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SchemaMigrationService.
 */
@ExtendWith(MockitoExtension.class)
class SchemaMigrationServiceTest {

    private SchemaMigrationService migrationService;

    // Test migrators
    private TestMigrator migrator_0_to_1;
    private TestMigrator migrator_1_to_2;
    private TestMigrator migrator_2_to_3;

    @BeforeEach
    void setUp() {
        migrator_0_to_1 = new TestMigrator("TestEntity", "0", "1.0.0", "v1");
        migrator_1_to_2 = new TestMigrator("TestEntity", "1.0.0", "2.0.0", "v2");
        migrator_2_to_3 = new TestMigrator("TestEntity", "2.0.0", "3.0.0", "v3");

        List<SchemaMigrator> migrators = Arrays.asList(
                migrator_0_to_1,
                migrator_1_to_2,
                migrator_2_to_3
        );

        migrationService = new SchemaMigrationService(migrators);
    }

    @Test
    void shouldMigrateSingleStep() throws Exception {
        // Given
        String entityJson = "{\"id\":\"123\",\"name\":\"test\"}";

        // When
        String result = migrationService.migrate(entityJson, "TestEntity", SchemaVersion.create("1.0.0"), SchemaVersion.create("0"));

        // Then
        assertThat(result).contains("\"migrated\":\"v1\"");
        assertThat(result).contains("\"_schema\":\"1.0.0\"");
    }

    @Test
    void shouldMigrateMultipleSteps() throws Exception {
        // Given
        String entityJson = "{\"id\":\"123\",\"name\":\"test\"}";

        // When
        String result = migrationService.migrate(entityJson, "TestEntity", SchemaVersion.create("3.0.0"), SchemaVersion.create("0"));

        // Then
        assertThat(result).contains("\"migrated\":\"v1\"");
        assertThat(result).contains("\"migrated2\":\"v2\"");
        assertThat(result).contains("\"migrated3\":\"v3\"");
        assertThat(result).contains("\"_schema\":\"3.0.0\"");
    }

    @Test
    void shouldSkipMigrationWhenAlreadyAtTargetVersion() throws Exception {
        // Given
        String entityJson = "{\"id\":\"123\",\"name\":\"test\",\"_schema\":\"2.0.0\"}";

        // When
        String result = migrationService.migrate(entityJson, "TestEntity", SchemaVersion.create("2.0.0"), SchemaVersion.create("2.0.0"));

        // Then
        assertThat(result).isEqualTo(entityJson);
        assertThat(result).doesNotContain("\"migrated\":\"v1\"");
    }

    @Test
    void shouldMigrateFromSpecificVersion() throws Exception {
        // Given - entity at version 1.0.0
        String entityJson = "{\"id\":\"123\",\"name\":\"test\",\"_schema\":\"1.0.0\"}";

        // When
        String result = migrationService.migrate(entityJson, "TestEntity", SchemaVersion.create("2.0.0"), SchemaVersion.create("1.0.0"));

        // Then
        assertThat(result).contains("\"migrated2\":\"v2\"");
        assertThat(result).contains("\"_schema\":\"2.0.0\"");
        assertThat(result).doesNotContain("\"migrated\":\"v1\""); // Should not apply first migrator
    }

    @Test
    void shouldThrowExceptionWhenNoMigrationPathExists() {
        // Given
        String entityJson = "{\"id\":\"123\",\"name\":\"test\"}";

        // When/Then
        assertThatThrownBy(() -> migrationService.migrate(entityJson, "TestEntity", SchemaVersion.create("99.0.0"), SchemaVersion.create("0")))
                .isInstanceOf(SchemaMigrationService.MigrationException.class)
                .hasMessageContaining("No migration path found");
    }

    @Test
    void shouldThrowExceptionWhenEntityTypeNotFound() {
        // Given
        String entityJson = "{\"id\":\"123\",\"name\":\"test\"}";

        // When/Then
        assertThatThrownBy(() -> migrationService.migrate(entityJson, "UnknownEntity", SchemaVersion.create("1.0.0"), SchemaVersion.create("0")))
                .isInstanceOf(SchemaMigrationService.MigrationException.class)
                .hasMessageContaining("No migration path found");
    }

    @Test
    void shouldHandleEntityWithoutSchemaField() throws Exception {
        // Given - entity without _schema field (defaults to version "0")
        String entityJson = "{\"id\":\"123\",\"name\":\"test\"}";

        // When
        String result = migrationService.migrate(entityJson, "TestEntity", SchemaVersion.create("1.0.0"), SchemaVersion.create("0"));

        // Then
        assertThat(result).contains("\"migrated\":\"v1\"");
        assertThat(result).contains("\"_schema\":\"1.0.0\"");
    }

    @Test
    void shouldCheckMigrationPathExists() {
        // When/Then
        assertThat(migrationService.hasMigrationPath("TestEntity", SchemaVersion.create("0"), SchemaVersion.create("3.0.0"))).isTrue();
        assertThat(migrationService.hasMigrationPath("TestEntity", SchemaVersion.create("1.0.0"), SchemaVersion.create("2.0.0"))).isTrue();
        assertThat(migrationService.hasMigrationPath("TestEntity", SchemaVersion.create("0"), SchemaVersion.create("99.0.0"))).isFalse();
        assertThat(migrationService.hasMigrationPath("UnknownEntity", SchemaVersion.create("0"), SchemaVersion.create("1.0.0"))).isFalse();
    }

    @Test
    void shouldReturnSameVersionWhenCheckingMigrationPath() {
        // When/Then
        assertThat(migrationService.hasMigrationPath("TestEntity", SchemaVersion.create("1.0.0"), SchemaVersion.create("1.0.0"))).isTrue();
    }

    @Test
    void shouldApplyMigratorsInCorrectOrder() throws Exception {
        // Given - Test mit speziellen Migratoren, die ihre Reihenfolge protokollieren
        TestMigrator orderMigrator1 = new TestMigrator("OrderEntity", "0", "1.0.0", "step1") {
            @Override
            public String migrate(String entityJson) {
                // Füge order-Feld hinzu oder erweitere es
                if (entityJson.contains("\"order\"")) {
                    return entityJson.replace("\"order\":\"", "\"order\":\"step1,");
                } else {
                    String trimmed = entityJson.trim();
                    if (trimmed.endsWith("}")) {
                        trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
                    }
                    if (trimmed.length() > 1 && !trimmed.endsWith(",")) {
                        trimmed += ",";
                    }
                    return trimmed + "\"order\":\"step1\"}";
                }
            }
        };

        TestMigrator orderMigrator2 = new TestMigrator("OrderEntity", "1.0.0", "2.0.0", "step2") {
            @Override
            public String migrate(String entityJson) {
                return entityJson.replace("\"order\":\"step1", "\"order\":\"step1,step2");
            }
        };

        TestMigrator orderMigrator3 = new TestMigrator("OrderEntity", "2.0.0", "3.0.0", "step3") {
            @Override
            public String migrate(String entityJson) {
                return entityJson.replace("\"order\":\"step1,step2", "\"order\":\"step1,step2,step3");
            }
        };

        // Migratoren absichtlich in ungeordneter Reihenfolge hinzufügen
        List<SchemaMigrator> unorderedMigrators = Arrays.asList(
                orderMigrator3, // 2.0.0 -> 3.0.0
                orderMigrator1, // 0 -> 1.0.0
                orderMigrator2  // 1.0.0 -> 2.0.0
        );

        SchemaMigrationService orderService = new SchemaMigrationService(unorderedMigrators);
        String entityJson = "{\"id\":\"123\",\"name\":\"test\"}";

        // When - Migration von Version 0 auf 3.0.0
        String result = orderService.migrate(entityJson, "OrderEntity", SchemaVersion.create("3.0.0"), SchemaVersion.create("0"));

        // Then - Die Schritte müssen in der korrekten Reihenfolge angewendet worden sein
        assertThat(result).contains("\"order\":\"step1,step2,step3\"");
        assertThat(result).contains("\"_schema\":\"3.0.0\"");

        // Zusätzliche Überprüfung: Der Order-String sollte genau diese Sequenz enthalten
        assertThat(result).containsPattern("\"order\"\\s*:\\s*\"step1,step2,step3\"");
    }

    @Test
    void shouldApplyPartialMigrationInCorrectOrder() throws Exception {
        // Given - Teste partielle Migration (nicht von Anfang)
        TestMigrator orderMigrator2 = new TestMigrator("PartialEntity", "1.0.0", "2.0.0", "step2") {
            @Override
            public String migrate(String entityJson) {
                String trimmed = entityJson.trim();
                if (trimmed.endsWith("}")) {
                    trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
                }
                if (trimmed.length() > 1 && !trimmed.endsWith(",")) {
                    trimmed += ",";
                }
                return trimmed + "\"migration_step\":\"2\"}";
            }
        };

        TestMigrator orderMigrator3 = new TestMigrator("PartialEntity", "2.0.0", "3.0.0", "step3") {
            @Override
            public String migrate(String entityJson) {
                return entityJson.replace("\"migration_step\":\"2\"", "\"migration_step\":\"2,3\"");
            }
        };

        List<SchemaMigrator> partialMigrators = Arrays.asList(orderMigrator3, orderMigrator2);
        SchemaMigrationService partialService = new SchemaMigrationService(partialMigrators);

        // Entity startet bereits bei Version 1.0.0
        String entityJson = "{\"id\":\"123\",\"name\":\"test\",\"_schema\":\"1.0.0\"}";

        // When - Migration von Version 1.0.0 auf 3.0.0
        String result = partialService.migrate(entityJson, "PartialEntity", SchemaVersion.create("3.0.0"), SchemaVersion.create("1.0.0"));

        // Then - Nur Schritt 2 und 3 sollten angewendet werden, in dieser Reihenfolge
        assertThat(result).contains("\"migration_step\":\"2,3\"");
        assertThat(result).contains("\"_schema\":\"3.0.0\"");
        assertThat(result).doesNotContain("step1"); // Schritt 1 sollte übersprungen werden
    }

    @Test
    void shouldThrowExceptionWhenMigratorFails() {
        // Given
        TestMigrator failingMigrator = new TestMigrator("TestEntity", "0", "1.0.0", "v1") {
            @Override
            public String migrate(String entityJson) {
                throw new RuntimeException("Migration failed");
            }
        };

        SchemaMigrationService service = new SchemaMigrationService(Collections.singletonList(failingMigrator));
        String entityJson = "{\"id\":\"123\",\"name\":\"test\"}";

        // When/Then
        assertThatThrownBy(() -> service.migrate(entityJson, "TestEntity", SchemaVersion.create("1.0.0"), SchemaVersion.create("0")))
                .isInstanceOf(SchemaMigrationService.MigrationException.class)
                .hasMessageContaining("Migration failed");
    }

    /**
     * Test implementation of SchemaMigrator.
     */
    static class TestMigrator implements SchemaMigrator {
        private final String entityType;
        private final SchemaVersion fromVersion;
        private final SchemaVersion toVersion;
        private final String markerValue;

        TestMigrator(String entityType, String fromVersion, String toVersion, String markerValue) {
            this.entityType = entityType;
            this.fromVersion = SchemaVersion.create(fromVersion);
            this.toVersion = SchemaVersion.create(toVersion);
            this.markerValue = markerValue;
        }

        @Override
        public String getEntityType() {
            return entityType;
        }

        @Override
        public SchemaVersion getFromVersion() {
            return fromVersion;
        }

        @Override
        public SchemaVersion getToVersion() {
            return toVersion;
        }

        @Override
        public String migrate(String entityJson) {
            // Simple migration: add a marker field
            String trimmed = entityJson.trim();
            if (trimmed.endsWith("}")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
            }

            if (trimmed.length() > 1 && !trimmed.endsWith(",")) {
                trimmed += ",";
            }

            // Use different field names for different versions to test chaining
            String fieldName = switch (toVersion.toString()) {
                case "1.0.0" -> "migrated";
                case "2.0.0" -> "migrated2";
                case "3.0.0" -> "migrated3";
                default -> "migrated_" + toVersion;
            };

            return trimmed + "\"" + fieldName + "\":\"" + markerValue + "\"}";
        }

        @Override
        public int compareTo(SchemaMigrator other) {
            // Compare by entityType first, then by fromVersion, then by toVersion
            int entityComparison = this.entityType.compareTo(other.getEntityType());
            if (entityComparison != 0) return entityComparison;

            int fromComparison = this.fromVersion.compareTo(other.getFromVersion());
            if (fromComparison != 0) return fromComparison;

            return this.toVersion.compareTo(other.getToVersion());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof TestMigrator other)) return false;
            return entityType.equals(other.entityType) &&
                   fromVersion.equals(other.fromVersion) &&
                   toVersion.equals(other.toVersion);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(entityType, fromVersion, toVersion);
        }
    }
}
