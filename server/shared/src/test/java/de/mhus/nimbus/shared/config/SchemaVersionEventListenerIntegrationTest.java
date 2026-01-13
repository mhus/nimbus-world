package de.mhus.nimbus.shared.config;

import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.Document;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for SchemaVersionEventListener with real MongoDB.
 * Uses Testcontainers to spin up a MongoDB instance for testing.
 */
@DataMongoTest
@Testcontainers
@Import({SchemaVersionEventListener.class, SchemaVersionEventListenerIntegrationTest.TestConfig.class})
@Disabled
class SchemaVersionEventListenerIntegrationTest {

    /**
     * Test configuration for MongoDB repositories.
     */
    @Configuration
    @EnableAutoConfiguration
    @EnableMongoRepositories(considerNestedRepositories = true)
    @EnableMongoAuditing
    static class TestConfig {
    }

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0")
            .withExposedPorts(27017);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private TestEntityRepository testEntityRepository;

    /**
     * Test entity with schema version annotation.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @org.springframework.data.mongodb.core.mapping.Document(collection = "test_entities")
    @ActualSchemaVersion("1.0.0")
    static class TestEntity implements Identifiable {
        @org.springframework.data.annotation.Id
        private String id;
        private String name;
        private String description;
    }

    /**
     * Repository for test entity.
     */
    interface TestEntityRepository extends MongoRepository<TestEntity, String> {
    }

    @Test
    void shouldAddSchemaVersionWhenSavingEntity() {
        // Given - create a new entity
        TestEntity entity = TestEntity.builder()
                .name("test-entity")
                .description("Test description")
                .build();

        // When - save the entity using repository
        TestEntity savedEntity = testEntityRepository.save(entity);

        // Then - verify entity was saved with an ID
        assertThat(savedEntity.getId()).isNotNull();

        // And - verify the _schema field was added to MongoDB document
        Query query = new Query(Criteria.where("_id").is(savedEntity.getId()));
        Document document = mongoTemplate.findOne(query, Document.class, "test_entities");

        assertThat(document).isNotNull();
        assertThat(document.getString("_schema"))
                .isNotNull()
                .isEqualTo("1.0.0");
    }

    @Test
    void shouldAddSchemaVersionWhenUpdatingEntity() {
        // Given - create and save an entity
        TestEntity entity = TestEntity.builder()
                .name("test-entity")
                .description("Original description")
                .build();
        TestEntity savedEntity = testEntityRepository.save(entity);

        // When - update the entity
        savedEntity.setDescription("Updated description");
        testEntityRepository.save(savedEntity);

        // Then - verify the _schema field is still present in MongoDB document
        Query query = new Query(Criteria.where("_id").is(savedEntity.getId()));
        Document document = mongoTemplate.findOne(query, Document.class, "test_entities");

        assertThat(document).isNotNull();
        assertThat(document.getString("_schema"))
                .isNotNull()
                .isEqualTo("1.0.0");
        assertThat(document.getString("description")).isEqualTo("Updated description");
    }

    @Test
    void shouldAddSchemaVersionWhenSavingMultipleEntities() {
        // Given - create multiple entities
        TestEntity entity1 = TestEntity.builder()
                .name("entity-1")
                .description("First entity")
                .build();

        TestEntity entity2 = TestEntity.builder()
                .name("entity-2")
                .description("Second entity")
                .build();

        // When - save multiple entities
        testEntityRepository.save(entity1);
        testEntityRepository.save(entity2);

        // Then - verify both entities have _schema field in MongoDB
        Query query1 = new Query(Criteria.where("_id").is(entity1.getId()));
        Document document1 = mongoTemplate.findOne(query1, Document.class, "test_entities");

        Query query2 = new Query(Criteria.where("_id").is(entity2.getId()));
        Document document2 = mongoTemplate.findOne(query2, Document.class, "test_entities");

        assertThat(document1).isNotNull();
        assertThat(document1.getString("_schema")).isEqualTo("1.0.0");

        assertThat(document2).isNotNull();
        assertThat(document2.getString("_schema")).isEqualTo("1.0.0");
    }

    @Test
    void shouldAddSchemaVersionWhenUsingMongoTemplateDirectly() {
        // Given - create a new entity
        TestEntity entity = TestEntity.builder()
                .name("direct-save-entity")
                .description("Saved via MongoTemplate")
                .build();

        // When - save using MongoTemplate directly
        TestEntity savedEntity = mongoTemplate.save(entity, "test_entities");

        // Then - verify the _schema field was added
        Query query = new Query(Criteria.where("_id").is(savedEntity.getId()));
        Document document = mongoTemplate.findOne(query, Document.class, "test_entities");

        assertThat(document).isNotNull();
        assertThat(document.getString("_schema"))
                .isNotNull()
                .isEqualTo("1.0.0");
    }
}
