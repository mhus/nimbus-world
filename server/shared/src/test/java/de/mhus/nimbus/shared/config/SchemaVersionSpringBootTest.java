package de.mhus.nimbus.shared.config;

import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full @SpringBootTest that verifies _schema behavior across ALL MongoDB write paths.
 * Uses Testcontainers with a real MongoDB to test the complete Spring Boot pipeline
 * including SchemaAwareMongoTemplate.
 */
@SpringBootTest(classes = SchemaVersionSpringBootTest.TestApplication.class)
@Testcontainers
class SchemaVersionSpringBootTest {

    private static final String COLLECTION = "test_schema_entities";

    @SpringBootApplication
    @EnableMongoRepositories(considerNestedRepositories = true)
    static class TestApplication {
        public static void main(String[] args) {
            SpringApplication.run(TestApplication.class, args);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @org.springframework.data.mongodb.core.mapping.Document(collection = COLLECTION)
    @ActualSchemaVersion("1.0.0")
    static class TestSchemaEntity implements Identifiable {
        @Id
        private String id;
        private String name;
        private String value;
    }

    @Repository
    interface TestSchemaEntityRepository extends MongoRepository<TestSchemaEntity, String> {
    }

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0")
            .withExposedPorts(27017);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private TestSchemaEntityRepository repository;

    @Autowired
    private SchemaVersionEventListener listener;

    @BeforeEach
    void cleanUp() {
        mongoTemplate.dropCollection(COLLECTION);
    }

    // --- Helper ---

    private Document readRawDocument(String id) {
        return mongoTemplate.findOne(
                new Query(Criteria.where("_id").is(id)),
                Document.class,
                COLLECTION
        );
    }

    // =========================================================================
    // Infrastructure verification
    // =========================================================================

    @Test
    @DisplayName("MongoTemplate should be SchemaAwareMongoTemplate")
    void mongoTemplateIsSchemaAware() {
        assertThat(mongoTemplate).isInstanceOf(SchemaAwareMongoTemplate.class);
    }

    @Test
    @DisplayName("SchemaVersionEventListener should be registered as Spring bean")
    void listenerIsRegistered() {
        assertThat(listener).isNotNull();
    }

    // =========================================================================
    // Save paths - _schema set via SchemaVersionEventListener.onBeforeSave
    // =========================================================================

    @Nested
    @DisplayName("Save paths - _schema via EventListener")
    class SavePaths {

        @Test
        @DisplayName("repository.save() should add _schema")
        void repositorySave() {
            var entity = TestSchemaEntity.builder().name("repo-save").value("v1").build();
            var saved = repository.save(entity);

            Document doc = readRawDocument(saved.getId());
            assertThat(doc).isNotNull();
            assertThat(doc.getString("_schema")).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("mongoTemplate.save(entity) should add _schema")
        void mongoTemplateSaveEntity() {
            var entity = TestSchemaEntity.builder().name("template-save").value("v1").build();
            var saved = mongoTemplate.save(entity);

            Document doc = readRawDocument(saved.getId());
            assertThat(doc).isNotNull();
            assertThat(doc.getString("_schema")).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("repository.save() update should keep _schema")
        void repositoryUpdateKeepsSchema() {
            var entity = TestSchemaEntity.builder().name("update-test").value("v1").build();
            var saved = repository.save(entity);

            saved.setValue("v2");
            repository.save(saved);

            Document doc = readRawDocument(saved.getId());
            assertThat(doc).isNotNull();
            assertThat(doc.getString("_schema")).isEqualTo("1.0.0");
            assertThat(doc.getString("value")).isEqualTo("v2");
        }
    }

    // =========================================================================
    // Update paths - _schema set via SchemaAwareMongoTemplate
    // =========================================================================

    @Nested
    @DisplayName("Update paths with Class parameter - _schema via SchemaAwareMongoTemplate")
    class UpdatePathsWithClass {

        @Test
        @DisplayName("updateFirst(query, update, Class) should set _schema")
        void updateFirstSetsSchema() {
            var entity = TestSchemaEntity.builder().name("uf-test").value("v1").build();
            var saved = repository.save(entity);

            // Remove _schema to simulate old data
            mongoTemplate.updateFirst(
                    new Query(Criteria.where("_id").is(saved.getId())),
                    new Update().unset("_schema"),
                    COLLECTION
            );
            assertThat(readRawDocument(saved.getId()).getString("_schema")).isNull();

            // updateFirst with Class parameter should restore _schema
            mongoTemplate.updateFirst(
                    new Query(Criteria.where("_id").is(saved.getId())),
                    new Update().set("value", "v2"),
                    TestSchemaEntity.class
            );

            Document doc = readRawDocument(saved.getId());
            assertThat(doc.getString("value")).isEqualTo("v2");
            assertThat(doc.getString("_schema")).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("updateMulti(query, update, Class) should set _schema")
        void updateMultiSetsSchema() {
            repository.save(TestSchemaEntity.builder().name("um-1").value("old").build());
            repository.save(TestSchemaEntity.builder().name("um-2").value("old").build());

            // Remove _schema
            mongoTemplate.updateMulti(
                    new Query(Criteria.where("value").is("old")),
                    new Update().unset("_schema"),
                    COLLECTION
            );

            // updateMulti with Class should restore _schema
            mongoTemplate.updateMulti(
                    new Query(Criteria.where("value").is("old")),
                    new Update().set("value", "new"),
                    TestSchemaEntity.class
            );

            var docs = mongoTemplate.find(
                    new Query(Criteria.where("name").regex("^um-")),
                    Document.class,
                    COLLECTION
            );
            assertThat(docs).hasSize(2);
            for (Document doc : docs) {
                assertThat(doc.getString("_schema"))
                        .as("_schema for " + doc.getString("name"))
                        .isEqualTo("1.0.0");
            }
        }

        @Test
        @DisplayName("upsert(query, update, Class) creating new document should set _schema")
        void upsertCreateSetsSchema() {
            mongoTemplate.upsert(
                    new Query(Criteria.where("name").is("upsert-new")),
                    new Update()
                            .set("name", "upsert-new")
                            .set("value", "v1"),
                    TestSchemaEntity.class
            );

            Document doc = mongoTemplate.findOne(
                    new Query(Criteria.where("name").is("upsert-new")),
                    Document.class,
                    COLLECTION
            );
            assertThat(doc).isNotNull();
            assertThat(doc.getString("_schema")).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("upsert(query, update, Class) updating existing should keep _schema")
        void upsertUpdateKeepsSchema() {
            var entity = TestSchemaEntity.builder().name("upsert-existing").value("v1").build();
            repository.save(entity);

            mongoTemplate.upsert(
                    new Query(Criteria.where("name").is("upsert-existing")),
                    new Update().set("value", "v2"),
                    TestSchemaEntity.class
            );

            Document doc = mongoTemplate.findOne(
                    new Query(Criteria.where("name").is("upsert-existing")),
                    Document.class,
                    COLLECTION
            );
            assertThat(doc).isNotNull();
            assertThat(doc.getString("value")).isEqualTo("v2");
            assertThat(doc.getString("_schema")).isEqualTo("1.0.0");
        }
    }

    // =========================================================================
    // String-only overloads - no Class → no _schema enrichment
    // =========================================================================

    @Nested
    @DisplayName("Update paths with String collection only - no _schema enrichment")
    class UpdatePathsStringOnly {

        @Test
        @DisplayName("updateFirst(query, update, String) should NOT add _schema")
        void updateFirstStringOnly() {
            var entity = TestSchemaEntity.builder().name("str-uf").value("v1").build();
            var saved = repository.save(entity);

            // Remove _schema
            mongoTemplate.updateFirst(
                    new Query(Criteria.where("_id").is(saved.getId())),
                    new Update().unset("_schema"),
                    COLLECTION
            );

            // updateFirst with String-only has no Class to resolve → no _schema
            mongoTemplate.updateFirst(
                    new Query(Criteria.where("_id").is(saved.getId())),
                    new Update().set("value", "v2"),
                    COLLECTION
            );

            Document doc = readRawDocument(saved.getId());
            assertThat(doc.getString("value")).isEqualTo("v2");
            assertThat(doc.getString("_schema"))
                    .as("String-only updateFirst cannot resolve _schema")
                    .isNull();
        }
    }

    // =========================================================================
    // Raw Document saves - no _schema from listener (unchanged behavior)
    // =========================================================================

    @Nested
    @DisplayName("Raw Document saves - no _schema")
    class RawDocumentPaths {

        @Test
        @DisplayName("mongoTemplate.save(rawDocument, collection) should NOT add _schema")
        void rawDocumentSave() {
            Document raw = new Document();
            raw.put("name", "raw-save");
            raw.put("value", "v1");

            mongoTemplate.save(raw, COLLECTION);

            Object id = raw.get("_id");
            Document doc = readRawDocument(id.toString());
            assertThat(doc).isNotNull();
            assertThat(doc.getString("_schema"))
                    .as("raw Document save has no @ActualSchemaVersion")
                    .isNull();
        }

        @Test
        @DisplayName("mongoTemplate.insert(rawDocument, collection) should NOT add _schema")
        void rawDocumentInsert() {
            Document raw = new Document();
            raw.put("name", "raw-insert");
            raw.put("value", "v1");

            mongoTemplate.insert(raw, COLLECTION);

            Object id = raw.get("_id");
            Document doc = readRawDocument(id.toString());
            assertThat(doc).isNotNull();
            assertThat(doc.getString("_schema"))
                    .as("raw Document insert has no @ActualSchemaVersion")
                    .isNull();
        }
    }
}
