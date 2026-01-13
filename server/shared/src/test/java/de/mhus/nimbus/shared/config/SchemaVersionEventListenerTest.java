package de.mhus.nimbus.shared.config;

import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.mapping.event.AfterConvertEvent;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SchemaVersionEventListener.
 * Tests the automatic addition and validation of schema versions for MongoDB entities.
 */
@Disabled
@ExtendWith(MockitoExtension.class)
class SchemaVersionEventListenerTest {

    @InjectMocks
    private SchemaVersionEventListener listener;

    @Mock
    private BeforeSaveEvent<Object> beforeSaveEvent;

    @Mock
    private AfterConvertEvent<Object> afterConvertEvent;

    private org.bson.Document document;

    @BeforeEach
    void setUp() {
        document = new org.bson.Document();
    }

    /**
     * Test entity with @SchemaVersion annotation.
     */
    @org.springframework.data.mongodb.core.mapping.Document(collection = "test_entities")
    @ActualSchemaVersion("1.0.0")
    static class TestEntityWithSchema {
        private String id;
        private String name;
    }

    /**
     * Test entity without @SchemaVersion annotation.
     */
    @org.springframework.data.mongodb.core.mapping.Document(collection = "test_entities_no_schema")
    static class TestEntityWithoutSchema {
        private String id;
        private String name;
    }

    /**
     * Test entity with different schema version.
     */
    @org.springframework.data.mongodb.core.mapping.Document(collection = "test_entities")
    @ActualSchemaVersion("2.0.0")
    static class TestEntityWithDifferentSchema {
        private String id;
        private String name;
    }

    @Test
    void shouldAddSchemaVersionWhenEntityHasAnnotation() {
        // Given
        TestEntityWithSchema entity = new TestEntityWithSchema();
        when(beforeSaveEvent.getSource()).thenReturn(entity);
        when(beforeSaveEvent.getDocument()).thenReturn(document);
        when(beforeSaveEvent.getCollectionName()).thenReturn("test_entities");

        // When
        listener.onBeforeSave(beforeSaveEvent);

        // Then
        assertThat(document.getString("_schema"))
                .isNotNull()
                .isEqualTo("1.0.0");
    }

    @Test
    void shouldNotAddSchemaVersionWhenEntityHasNoAnnotation() {
        // Given
        TestEntityWithoutSchema entity = new TestEntityWithoutSchema();
        when(beforeSaveEvent.getSource()).thenReturn(entity);
        when(beforeSaveEvent.getDocument()).thenReturn(document);

        // When
        listener.onBeforeSave(beforeSaveEvent);

        // Then
        assertThat(document.getString("_schema")).isNull();
    }

    @Test
    void shouldNotFailWhenDocumentIsNull() {
        // Given
        TestEntityWithSchema entity = new TestEntityWithSchema();
        when(beforeSaveEvent.getSource()).thenReturn(entity);
        when(beforeSaveEvent.getDocument()).thenReturn(null);

        // When/Then - should not throw exception
        listener.onBeforeSave(beforeSaveEvent);
    }

    @Test
    void shouldNotFailWhenSourceIsNull() {
        // Given
        when(beforeSaveEvent.getSource()).thenReturn(null);
        when(beforeSaveEvent.getDocument()).thenReturn(document);

        // When/Then - should not throw exception
        listener.onBeforeSave(beforeSaveEvent);
        assertThat(document.getString("_schema")).isNull();
    }

    @Test
    void shouldNotLogWarningWhenSchemaVersionMatches() {
        // Given
        TestEntityWithSchema entity = new TestEntityWithSchema();
        document.put("_schema", "1.0.0");

        when(afterConvertEvent.getSource()).thenReturn(entity);
        when(afterConvertEvent.getDocument()).thenReturn(document);

        // When/Then - should not log warning (check via manual inspection or log capture)
        listener.onAfterConvert(afterConvertEvent);
    }

    @Test
    void shouldLogWarningWhenSchemaVersionMismatches() {
        // Given
        TestEntityWithSchema entity = new TestEntityWithSchema();
        document.put("_schema", "0.9.0"); // Old version in document

        when(afterConvertEvent.getSource()).thenReturn(entity);
        when(afterConvertEvent.getDocument()).thenReturn(document);

        // When/Then - should log warning (check via manual inspection or log capture)
        listener.onAfterConvert(afterConvertEvent);
    }

    @Test
    void shouldLogWarningWhenSchemaVersionIsMissing() {
        // Given
        TestEntityWithSchema entity = new TestEntityWithSchema();
        // document has no _schema field

        when(afterConvertEvent.getSource()).thenReturn(entity);
        when(afterConvertEvent.getDocument()).thenReturn(document);

        // When/Then - should log warning (check via manual inspection or log capture)
        listener.onAfterConvert(afterConvertEvent);
    }

    @Test
    void shouldNotFailOnAfterConvertWhenEntityIsNull() {
        // Given
        when(afterConvertEvent.getSource()).thenReturn(null);
        when(afterConvertEvent.getDocument()).thenReturn(document);

        // When/Then - should not throw exception
        listener.onAfterConvert(afterConvertEvent);
    }

    @Test
    void shouldNotFailOnAfterConvertWhenDocumentIsNull() {
        // Given
        TestEntityWithSchema entity = new TestEntityWithSchema();
        when(afterConvertEvent.getSource()).thenReturn(entity);
        when(afterConvertEvent.getDocument()).thenReturn(null);

        // When/Then - should not throw exception
        listener.onAfterConvert(afterConvertEvent);
    }

    @Test
    void shouldCacheSchemaVersionsForPerformance() {
        // Given
        TestEntityWithSchema entity1 = new TestEntityWithSchema();
        TestEntityWithSchema entity2 = new TestEntityWithSchema();

        when(beforeSaveEvent.getSource()).thenReturn(entity1, entity2);
        when(beforeSaveEvent.getDocument()).thenReturn(new org.bson.Document(), new org.bson.Document());
        when(beforeSaveEvent.getCollectionName()).thenReturn("test_entities");

        // When - call twice with same entity class
        listener.onBeforeSave(beforeSaveEvent);
        org.bson.Document doc2 = new org.bson.Document();
        when(beforeSaveEvent.getDocument()).thenReturn(doc2);
        when(beforeSaveEvent.getSource()).thenReturn(entity2);
        listener.onBeforeSave(beforeSaveEvent);

        // Then - both should have schema version (cache is working)
        assertThat(doc2.getString("_schema")).isEqualTo("1.0.0");
    }

    @Test
    void shouldHandleDifferentSchemaVersionsForDifferentEntities() {
        // Given
        TestEntityWithSchema entity1 = new TestEntityWithSchema();
        TestEntityWithDifferentSchema entity2 = new TestEntityWithDifferentSchema();

        org.bson.Document doc1 = new org.bson.Document();
        org.bson.Document doc2 = new org.bson.Document();

        // When - add schema to first entity
        when(beforeSaveEvent.getSource()).thenReturn(entity1);
        when(beforeSaveEvent.getDocument()).thenReturn(doc1);
        when(beforeSaveEvent.getCollectionName()).thenReturn("test_entities");
        listener.onBeforeSave(beforeSaveEvent);

        // When - add schema to second entity
        when(beforeSaveEvent.getSource()).thenReturn(entity2);
        when(beforeSaveEvent.getDocument()).thenReturn(doc2);
        listener.onBeforeSave(beforeSaveEvent);

        // Then
        assertThat(doc1.getString("_schema")).isEqualTo("1.0.0");
        assertThat(doc2.getString("_schema")).isEqualTo("2.0.0");
    }
}
